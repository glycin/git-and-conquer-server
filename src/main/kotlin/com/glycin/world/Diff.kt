package com.glycin.world

import com.glycin.api.model.Cell
import com.glycin.api.model.Delta
import com.glycin.api.model.MapObject
import com.glycin.git.GitRunner
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class Diff(
    private val paths: WorldPaths,
    private val git: GitRunner,
) {

    suspend fun diff(fromRef: String, toRef: String): Delta {
        val names = git.runGit(
            "diff", "--name-only", "--diff-filter=ACDMR", fromRef, toRef,
            cwd = paths.repo,
        )

        val added = mutableListOf<MapObject>()
        val removed = mutableListOf<Cell>()
        val modified = mutableListOf<MapObject>()

        for (path in names.stdout.lines()) {
            val cell = WorldPaths.parseObjectPath(path) ?: continue

            val before = show(fromRef, path)
            val after = show(toRef, path)

            when {
                before == null && after != null -> added += WorldJson.decodeObject(after)
                before != null && after == null -> removed += cell
                before != null && after != null -> modified += WorldJson.decodeObject(after)
            }
        }

        val newHead = git.runGit("rev-parse", toRef, cwd = paths.repo).stdoutTrimmed()
        val newBranch = git.runGit("rev-parse", "--abbrev-ref", "HEAD", cwd = paths.repo).stdoutTrimmed()

        return Delta(
            added = added,
            removed = removed,
            modified = modified,
            newHead = newHead,
            newBranch = newBranch,
        )
    }

    private suspend fun show(ref: String, path: String): String? {
        val result = git.runGit("show", "$ref:$path", cwd = paths.repo, quiet = true)
        return if (result.ok) result.stdout else null
    }
}
