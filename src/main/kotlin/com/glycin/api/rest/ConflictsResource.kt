package com.glycin.api.rest

import com.glycin.api.model.Choice
import com.glycin.api.model.ConflictsResponse
import com.glycin.api.model.OpResult
import com.glycin.api.model.ResolveBuildingRequest
import com.glycin.api.model.ResolveCellRequest
import com.glycin.api.model.ResolveObjectRequest
import com.glycin.drivers.ConflictMarkers
import com.glycin.git.GitResult
import com.glycin.git.GitRunner
import com.glycin.git.errorMessage
import com.glycin.world.ConflictReader
import com.glycin.world.ConflictedGrid
import com.glycin.world.GridFile
import com.glycin.world.ObjectStore
import com.glycin.world.Placement
import com.glycin.world.TileLegend
import com.glycin.world.WorldPaths
import com.glycin.world.WorldRepo
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

@Path("/git")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class ConflictsResource(
    private val reader: ConflictReader,
    private val worldRepo: WorldRepo,
    private val paths: WorldPaths,
    private val objectStore: ObjectStore,
    private val git: GitRunner,
) {

    @GET
    @Path("/conflicts")
    suspend fun list(): ConflictsResponse = reader.read()

    @POST
    @Path("/conflicts/resolve-cell")
    suspend fun resolveCell(req: ResolveCellRequest): OpResult {
        val content = withContext(Dispatchers.IO) {
            Files.readString(paths.grid)
        }
        val lines = content.split('\n')
        val line = lines.getOrNull(req.row)
            ?: return worldRepo.failure("row ${req.row} out of bounds")

        var hit: ConflictMarkers.Marker? = null
        ConflictMarkers.forEachMarker(line) { if (it.col == req.col) hit = it }
        val marker = hit
            ?: return worldRepo.failure("no conflict marker at (row ${req.row}, col ${req.col})")

        val char = when (req.choice) {
            Choice.OURS -> marker.ours
            Choice.THEIRS -> marker.theirs
            Choice.CUSTOM -> req.customChar
                ?: return worldRepo.failure("customChar required when choice=CUSTOM")
        }

        val newLine = line.substring(0, marker.offset) + char + line.substring(marker.offset + ConflictMarkers.WIDTH)
        val newContent = lines.toMutableList().also { it[req.row] = newLine }.joinToString("\n")
        withContext(Dispatchers.IO) {
            Files.writeString(paths.grid, newContent)
        }

        if (!ConflictMarkers.containsMarker(newContent)) {
            git.runGit("add", WorldPaths.GRID_FILENAME, cwd = paths.repo)
        }

        return worldRepo.success()
    }

    @POST
    @Path("/conflicts/resolve-object")
    suspend fun resolveObject(req: ResolveObjectRequest): OpResult {
        val relPath = WorldPaths.objectRelativePath(req.row, req.col)

        val obj = when (req.choice) {
            Choice.OURS -> reader.readObjectStage(2, relPath)
                ?: return worldRepo.failure("no 'ours' version at (row ${req.row}, col ${req.col})")
            Choice.THEIRS -> reader.readObjectStage(3, relPath)
                ?: return worldRepo.failure("no 'theirs' version at (row ${req.row}, col ${req.col})")
            Choice.CUSTOM -> req.customObject
        }

        if (obj == null) {
            objectStore.delete(req.row, req.col)
            git.runGit("rm", "-f", "--", relPath, cwd = paths.repo)
        } else {
            objectStore.write(obj)
            git.runGit("add", "--", relPath, cwd = paths.repo)
        }

        return worldRepo.success()
    }

    // The driver already merged the cells where the buildings don't overlap, so the loser's whole
    // footprint and object file have to go too. Per-cell resolving can't do that.
    @POST
    @Path("/conflicts/resolve-building")
    suspend fun resolveBuilding(req: ResolveBuildingRequest): OpResult {
        if (req.choice == Choice.CUSTOM) return worldRepo.failure("choice must be OURS or THEIRS")
        val incoming = req.choice == Choice.THEIRS
        val buildings = reader.read().buildings
        val winner = buildings.firstOrNull { it.row == req.row && it.col == req.col && it.incoming == incoming }
            ?: return worldRepo.failure(
                "no conflicted ${if (incoming) "theirs" else "ours"} building anchored at (${req.row}, ${req.col})",
            )
        val winnerCells = winner.conflictCells.toSet()
        val losers = buildings.filter { rival ->
            rival.incoming != incoming && rival.conflictCells.any { it in winnerCells }
        }

        val grid = ConflictedGrid.parse(withContext(Dispatchers.IO) { Files.readString(paths.grid) })
        Placement.footprintWrites(winner.row, winner.col, winner.char).forEach { (cell, ch) ->
            grid.set(cell.row, cell.col, ch)
        }
        val winnerFootprint = TileLegend.footprintCells(winner.row, winner.col, winner.char).toSet()
        for (loser in losers) {
            for (cell in TileLegend.footprintCells(loser.row, loser.col, loser.char)) {
                if (cell in winnerFootprint) continue
                grid.set(cell.row, cell.col, GridFile.EMPTY)
            }
        }
        val newContent = grid.serialize()
        withContext(Dispatchers.IO) {
            Files.writeString(paths.grid, newContent)
        }
        if (!ConflictMarkers.containsMarker(newContent)) {
            git.runGit("add", WorldPaths.GRID_FILENAME, cwd = paths.repo)
        }

        val winnerPath = WorldPaths.objectRelativePath(winner.row, winner.col)
        reader.readObjectStage(if (incoming) 3 else 2, winnerPath)?.let {
            objectStore.write(it)
            git.runGit("add", "--", winnerPath, cwd = paths.repo)
        }
        for (loser in losers) {
            if (loser.row == winner.row && loser.col == winner.col) continue
            objectStore.delete(loser.row, loser.col)
            git.runGit(
                "rm", "-f", "--ignore-unmatch", "--",
                WorldPaths.objectRelativePath(loser.row, loser.col),
                cwd = paths.repo,
            )
        }

        return worldRepo.success()
    }

    @POST
    @Path("/conflicts/complete")
    suspend fun complete(): OpResult {
        // The driver can merge cleanly and git still calls the file unmerged. No markers left? Take it.
        if (WorldPaths.GRID_FILENAME in reader.unmergedPaths()) {
            val grid = withContext(Dispatchers.IO) { Files.readString(paths.grid) }
            if (!ConflictMarkers.containsMarker(grid)) {
                git.runGit("add", WorldPaths.GRID_FILENAME, cwd = paths.repo)
            }
        }

        val stillUnmerged = reader.unmergedPaths()
        if (stillUnmerged.isNotEmpty()) {
            return worldRepo.failure("still unresolved: ${stillUnmerged.joinToString(", ")}")
        }

        val op = reader.activeOp()
        val result = when (op) {
            "merge" -> git.runGit("commit", "--no-edit", cwd = paths.repo)
            "cherry-pick", "rebase", "revert" -> continueOp(op)
            null -> git.runGit("commit", "-m", "Resolve conflicts", cwd = paths.repo)
            else -> return worldRepo.failure("unknown operation in progress")
        }

        if (result.ok) {
            return worldRepo.committed()
        }
        // Just the next conflicting step of a rebase/cherry-pick, not an error.
        val nextConflict = reader.unmergedPaths()
        if (nextConflict.isNotEmpty()) {
            return worldRepo.conflict(reader.activeOp() ?: op ?: "conflict", nextConflict)
        }
        return worldRepo.failure(result.errorMessage())
    }

    // --continue refuses a step that ended up empty. Git itself says to --skip it, so we do.
    private suspend fun continueOp(op: String): GitResult {
        val continued = git.runGit(op, "--continue", cwd = paths.repo, env = NO_EDITOR, showEnv = false)
        if (continued.ok || reader.unmergedPaths().isNotEmpty()) return continued
        val nothingStaged = git.runGit("diff", "--cached", "--quiet", cwd = paths.repo, quiet = true).ok
        if (!nothingStaged || reader.activeOp() != op) return continued
        return git.runGit(op, "--skip", cwd = paths.repo)
    }

    @POST
    @Path("/abort")
    suspend fun abort(): OpResult {
        val op = reader.activeOp() ?: return worldRepo.failure("no operation in progress")
        val result = when (op) {
            "merge" -> git.runGit("merge", "--abort", cwd = paths.repo)
            "cherry-pick" -> git.runGit("cherry-pick", "--abort", cwd = paths.repo)
            "rebase" -> git.runGit("rebase", "--abort", cwd = paths.repo)
            "revert" -> git.runGit("revert", "--abort", cwd = paths.repo)
            else -> return worldRepo.failure("unknown operation: $op")
        }
        return if (result.ok) {
            worldRepo.success()
        } else {
            worldRepo.failure(result.errorMessage())
        }
    }

    companion object {
        private val NO_EDITOR = mapOf("GIT_EDITOR" to "true")
    }
}
