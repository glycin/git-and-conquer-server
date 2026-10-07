package com.glycin.world

import com.glycin.api.model.Cell
import com.glycin.api.model.CellConflict
import com.glycin.api.model.ConflictSides
import com.glycin.api.model.ConflictsResponse
import com.glycin.api.model.MapObject
import com.glycin.api.model.ObjectConflict
import com.glycin.api.model.ReplayStep
import com.glycin.drivers.ConflictMarkers
import com.glycin.git.GitRunner
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path

@ApplicationScoped
class ConflictReader(
    private val paths: WorldPaths,
    private val git: GitRunner,
) {

    suspend fun read(): ConflictsResponse {
        val unmerged = unmergedPaths()
        // Unmerged paths with no operation file (like after merge --quit) are still a conflict.
        val activeOp = activeOp()
        val op = activeOp ?: if (unmerged.isNotEmpty()) "conflict" else "none"
        if (unmerged.isEmpty()) return ConflictsResponse(operation = op)

        val cells = if (WorldPaths.GRID_FILENAME in unmerged) readGridConflicts() else emptyList()
        val objects = unmerged.mapNotNull { readObjectConflict(it) }
        val buildings = if (cells.isEmpty()) emptyList() else BuildingConflicts.derive(
            conflictCells = cells.map { Cell(it.row, it.col) },
            oursGrid = readGridStage(2),
            theirsGrid = readGridStage(3),
        )

        return ConflictsResponse(
            operation = op,
            cells = cells,
            objects = objects,
            buildings = buildings,
            sides = sides(activeOp),
        )
    }

    suspend fun unmergedPaths(): List<String> =
        git.runGit("diff", "--name-only", "--diff-filter=U", cwd = paths.repo)
            .stdout.lines()
            .filter { it.isNotBlank() }

    suspend fun unmergedCount(): Int = unmergedPaths().size

    // Hash instead of count, because the next rebase step can stop with the same number of conflicts.
    suspend fun fingerprint(): String {
        val unmerged = unmergedPaths().sorted().joinToString(",")
        val step = STEP_FILES.firstNotNullOfOrNull { readGitFile(it) }
        return "$unmerged|${activeOp()}|$step"
    }

    suspend fun readObjectStage(stage: Int, path: String): MapObject? {
        // Missing stages are fine (add/add has no stage 1), no need to log them.
        val result = git.runGit("show", ":$stage:$path", cwd = paths.repo, quiet = true)
        if (!result.ok) return null
        return runCatching { WorldJson.decodeObject(result.stdout) }.getOrNull()
    }

    // stage 2 = ours, stage 3 = theirs. null if it's missing or not a full grid.
    suspend fun readGridStage(stage: Int): Array<CharArray>? {
        val result = git.runGit("show", ":$stage:${WorldPaths.GRID_FILENAME}", cwd = paths.repo, quiet = true)
        if (!result.ok) return null
        return GridFile.parse(result.stdout)
    }

    suspend fun activeOp(): String? = withContext(Dispatchers.IO) {
        val gitDir = gitDir()
        when {
            Files.isDirectory(gitDir.resolve("rebase-merge")) -> "rebase"
            Files.isDirectory(gitDir.resolve("rebase-apply")) -> "rebase"
            Files.exists(gitDir.resolve("MERGE_HEAD")) -> "merge"
            Files.exists(gitDir.resolve("CHERRY_PICK_HEAD")) -> "cherry-pick"
            Files.exists(gitDir.resolve("REVERT_HEAD")) -> "revert"
            else -> null
        }
    }

    // HEAD is detached during a rebase, but git stashes the branch name in the rebase folder.
    suspend fun rebasingBranch(): String? =
        (readGitFile("rebase-merge/head-name") ?: readGitFile("rebase-apply/head-name"))
            ?.removePrefix("refs/heads/")

    suspend fun replay(changesPending: Boolean): ReplayStep? {
        if (!Files.isDirectory(gitDir().resolve("rebase-merge"))) return null
        val done = replayLines("rebase-merge/done")
        val remaining = replayLines("rebase-merge/git-rebase-todo")
        val current = done.lastOrNull()?.split(' ', limit = 3)
        return ReplayStep(
            step = done.size,
            total = done.size + remaining.size,
            sha = current?.getOrNull(1)?.take(7).orEmpty(),
            subject = current?.getOrNull(2).orEmpty(),
            onto = readGitFile("rebase-merge/onto").orEmpty(),
            branch = rebasingBranch().orEmpty(),
            paused = !changesPending,
        )
    }

    private suspend fun replayLines(relative: String): List<String> =
        readGitFile(relative)?.lines().orEmpty()
            .map { it.trim() }
            .filter { it.substringBefore(' ') in REPLAY_COMMANDS }

    private suspend fun sides(op: String?): ConflictSides? = when (op) {
        "merge" -> ConflictSides(ours = currentLabel(), theirs = labelFor("MERGE_HEAD"))
        "cherry-pick" -> ConflictSides(ours = currentLabel(), theirs = labelFor("CHERRY_PICK_HEAD"))
        "revert" -> ConflictSides(ours = currentLabel(), theirs = "revert of " + shortSha("REVERT_HEAD"))
        "rebase" -> ConflictSides(
            ours = readGitFile("rebase-merge/onto")?.let { labelFor(it) } ?: "onto",
            theirs = rebasingBranch() ?: shortSha("REBASE_HEAD"),
        )
        else -> null
    }

    private suspend fun currentLabel(): String {
        val branch = git.runGit("rev-parse", "--abbrev-ref", "HEAD", cwd = paths.repo).stdoutTrimmed()
        return if (branch == "HEAD" || branch.isBlank()) shortSha("HEAD") else branch
    }

    private suspend fun labelFor(ref: String): String {
        val branch = git
            .runGit(
                "for-each-ref", "--points-at", ref, "--format=%(refname:short)", "refs/heads",
                cwd = paths.repo, quiet = true,
            )
            .stdout.lineSequence().firstOrNull { it.isNotBlank() }
        return branch ?: shortSha(ref)
    }

    private suspend fun shortSha(ref: String): String =
        git.runGit("rev-parse", "--short", ref, cwd = paths.repo, quiet = true).stdoutTrimmed().ifBlank { ref }

    private suspend fun readGitFile(relative: String): String? = withContext(Dispatchers.IO) {
        val file = gitDir().resolve(relative)
        if (Files.isRegularFile(file)) Files.readString(file).trim().ifBlank { null } else null
    }

    private fun gitDir(): Path = paths.repo.resolve(".git")

    private suspend fun readGridConflicts(): List<CellConflict> {
        val content = withContext(Dispatchers.IO) { Files.readString(paths.grid) }
        val baseLines = git
            .runGit("show", ":1:${WorldPaths.GRID_FILENAME}", cwd = paths.repo, quiet = true)
            .takeIf { it.ok }
            ?.stdout?.split('\n') ?: emptyList()

        val out = mutableListOf<CellConflict>()
        for ((row, line) in content.split('\n').withIndex()) {
            ConflictMarkers.forEachMarker(line) { hit ->
                val base = baseLines.getOrNull(row)?.getOrNull(hit.col) ?: '.'
                out += CellConflict(row = row, col = hit.col, ours = hit.ours, theirs = hit.theirs, base = base)
            }
        }
        return out
    }

    private suspend fun readObjectConflict(path: String): ObjectConflict? {
        val cell = WorldPaths.parseObjectPath(path) ?: return null
        val base = readObjectStage(1, path)
        val ours = readObjectStage(2, path)
        val theirs = readObjectStage(3, path)
        // Unmerged but nothing to read: already resolved, we just caught it mid-flight.
        if (base == null && ours == null && theirs == null) return null
        return ObjectConflict(row = cell.row, col = cell.col, ours = ours, theirs = theirs, base = base)
    }

    companion object {
        private val STEP_FILES = listOf("REBASE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "MERGE_HEAD")
        private val REPLAY_COMMANDS = setOf("pick", "p", "edit", "e", "reword", "r", "squash", "s", "fixup", "f")
    }
}
