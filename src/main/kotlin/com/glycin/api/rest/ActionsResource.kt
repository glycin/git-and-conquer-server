package com.glycin.api.rest

import com.glycin.api.model.Cell
import com.glycin.api.model.CommitRequest
import com.glycin.api.model.OpResult
import com.glycin.api.model.DestroyRequest
import com.glycin.api.model.MapObject
import com.glycin.api.model.ModifyRequest
import com.glycin.api.model.PlaceRequest
import com.glycin.api.model.StartMatchRequest
import com.glycin.api.model.StageRequest
import com.glycin.api.model.TagWonderRequest
import com.glycin.git.GitRunner
import com.glycin.git.errorMessage
import com.glycin.world.ConflictReader
import com.glycin.world.FactionCommit
import com.glycin.world.GridFile
import com.glycin.world.MetaFiles
import com.glycin.world.ObjectStore
import com.glycin.world.Placement
import com.glycin.world.Staging
import com.glycin.world.TileLegend
import com.glycin.world.WalletFile
import com.glycin.world.WonderClaims
import com.glycin.world.WorldPaths
import com.glycin.world.WorldRepo
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/actions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ActionsResource(
    private val worldRepo: WorldRepo,
    private val gridFile: GridFile,
    private val objectStore: ObjectStore,
    private val walletFile: WalletFile,
    private val metaFiles: MetaFiles,
    private val paths: WorldPaths,
    private val git: GitRunner,
    private val conflictReader: ConflictReader,
    private val staging: Staging,
    private val wonderClaims: WonderClaims,
    private val factionCommit: FactionCommit,
) {

    @POST
    @Path("/place")
    suspend fun place(req: PlaceRequest): OpResult {
        conflictGuard()?.let { return it }
        val cells = gridFile.readCells()
        Placement.validate(cells, req.row, req.col, req.char, req.category)?.let {
            return worldRepo.failure(it)
        }
        val entry = TileLegend.byChar[req.char]
        val obj = MapObject(
            row = req.row,
            col = req.col,
            category = req.category,
            type = req.type,
            char = req.char,
            faction = req.faction,
            rotation = req.rotation,
            fpCols = entry?.fpCols ?: 1,
            fpRows = entry?.fpRows ?: 1,
            metadata = req.metadata,
        )
        objectStore.write(obj)
        gridFile.writeCells(Placement.footprintWrites(req.row, req.col, req.char))
        val author = req.commitAs ?: return worldRepo.success()
        val message = "$author builds a ${req.type} at (${req.row}, ${req.col})"
        factionCommit.commit(Cell(req.row, req.col), author, message)?.let {
            return worldRepo.failure("placed, but not committed: $it")
        }
        return worldRepo.committed()
    }

    @POST
    @Path("/destroy")
    suspend fun destroy(req: DestroyRequest): OpResult {
        conflictGuard()?.let { return it }
        if (req.row !in 0 until GridFile.SIZE || req.col !in 0 until GridFile.SIZE) {
            return worldRepo.failure("(${req.row}, ${req.col}) is outside the map")
        }
        val cells = gridFile.readCells()
        val char = cells[req.row][req.col]
        if (char == GridFile.EMPTY) {
            return worldRepo.failure("nothing at (${req.row}, ${req.col})")
        }
        if (TileLegend.isClaimChar(char)) {
            val anchor = Placement.findAnchor(cells, req.row, req.col)
            val hint = anchor?.let { " anchored at (${it.row}, ${it.col})" }.orEmpty()
            return worldRepo.failure("(${req.row}, ${req.col}) is part of a building's footprint$hint")
        }
        val entry = TileLegend.byChar[char]
        if (entry != null && entry.landmark) {
            return worldRepo.failure("the ${entry.type} cannot be destroyed")
        }
        objectStore.delete(req.row, req.col)
        val clears = TileLegend.footprintCells(req.row, req.col, char)
            .filter { it.row in cells.indices && it.col in cells[it.row].indices }
            .filter { cells[it.row][it.col] == char || cells[it.row][it.col] == TileLegend.claimChar(char) }
            .map { it to GridFile.EMPTY }
        gridFile.writeCells(clears)
        return worldRepo.success()
    }

    @POST
    @Path("/modify")
    suspend fun modify(req: ModifyRequest): OpResult {
        conflictGuard()?.let { return it }
        objectStore.read(req.row, req.col)
            ?: return worldRepo.failure("no object at (${req.row}, ${req.col})")
        val updated = objectStore.modify(req.row, req.col, req.patch)
        gridFile.writeCell(updated.row, updated.col, updated.char)
        return worldRepo.success()
    }

    // The grid is full of conflict markers until the conflict is resolved or aborted.
    private suspend fun conflictGuard(): OpResult? =
        if (conflictReader.unmergedPaths().isNotEmpty() || gridFile.hasConflictMarkers()) {
            worldRepo.failure("a conflict is in progress")
        } else {
            null
        }

    @POST
    @Path("/stage")
    suspend fun stage(req: StageRequest): OpResult {
        conflictGuard()?.let { return it }
        staging.stage(req.cell())?.let { return worldRepo.failure(it) }
        return worldRepo.success()
    }

    @POST
    @Path("/unstage")
    suspend fun unstage(req: StageRequest): OpResult {
        conflictGuard()?.let { return it }
        staging.unstage(req.cell())?.let { return worldRepo.failure(it) }
        return worldRepo.success()
    }

    private fun StageRequest.cell(): Cell? =
        if (row != null && col != null) Cell(row, col) else null

    // The wallet is the only thing Commit stages by itself.
    @POST
    @Path("/commit")
    suspend fun commit(req: CommitRequest): OpResult {
        // Conflicts get committed through /git/conflicts/complete, not here.
        if (conflictReader.unmergedPaths().isNotEmpty()) {
            return worldRepo.failure("unresolved conflicts")
        }
        req.wallet?.let {
            walletFile.write(it)
            git.runGit("add", "--", WorldPaths.WALLET_FILENAME, cwd = paths.repo)
        }
        val staged = git.runGit("diff", "--cached", "--name-status", "--no-renames", cwd = paths.repo).stdout
        if (staged.isBlank()) {
            return worldRepo.failure("nothing staged")
        }
        val message = req.message ?: defaultCommitMessage(staged)
        val commit = git.runGit("commit", "-m", message, cwd = paths.repo)
        if (!commit.ok) {
            return worldRepo.failure(commit.errorMessage())
        }
        return worldRepo.committed()
    }

    // The client checks for the Software Architect. The server only makes sure there's one claim at a time.
    @POST
    @Path("/tag-wonder")
    suspend fun tagWonder(req: TagWonderRequest): OpResult {
        wonderClaims.claim(req.faction)?.let { return worldRepo.failure(it) }
        return worldRepo.success()
    }

    @POST
    @Path("/start-match")
    suspend fun startMatch(req: StartMatchRequest): OpResult {
        if (conflictReader.unmergedPaths().isNotEmpty()) {
            return worldRepo.failure("unresolved conflicts")
        }
        if (!metaFiles.writeAdversary(req.adversary)) {
            return worldRepo.failure("meta/map.json is missing")
        }
        val metaPath = WorldPaths.META_DIRNAME + "/" + MetaFiles.MAP_FILENAME
        val message = "Match start: vs " + (req.adversaryName ?: req.adversary)
        val commit = git.runGit("commit", "-m", message, "--", metaPath, cwd = paths.repo)
        if (!commit.ok) {
            return worldRepo.failure(commit.errorMessage())
        }
        return worldRepo.committed()
    }

    private fun defaultCommitMessage(staged: String): String {
        var built = 0
        var destroyed = 0
        var modified = 0
        var treasury = false
        for (line in staged.lineSequence()) {
            if (line.isBlank()) continue
            val status = line[0]
            val path = line.substringAfter('\t').trim()
            if (path == WorldPaths.WALLET_FILENAME) {
                treasury = true
                continue
            }
            if (!path.startsWith("objects/")) continue
            when (status) {
                'D' -> destroyed++
                'A' -> built++
                else -> modified++
            }
        }
        val parts = buildList {
            if (built > 0) add("$built built")
            if (destroyed > 0) add("$destroyed destroyed")
            if (modified > 0) add("$modified modified")
            if (treasury) add("treasury updated")
        }
        return if (parts.isEmpty()) "Battlefield update"
        else "Battlefield update: " + parts.joinToString(", ")
    }
}
