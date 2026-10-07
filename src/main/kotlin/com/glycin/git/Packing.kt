package com.glycin.git

import com.glycin.api.model.GcResult
import com.glycin.api.model.ObjectStats
import com.glycin.world.WorldPaths
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class Packing(
    private val paths: WorldPaths,
    private val git: GitRunner,
) {

    suspend fun stats(): ObjectStats {
        val out = git.runGit("count-objects", "-v", cwd = paths.repo, quiet = true).stdout
        val fields = out.lineSequence()
            .mapNotNull { line ->
                val key = line.substringBefore(':', "").trim()
                val value = line.substringAfter(':', "").trim().toLongOrNull()
                if (key.isEmpty() || value == null) null else key to value
            }
            .toMap()
        return ObjectStats(
            loose = fields["count"] ?: 0,
            looseKb = fields["size"] ?: 0,
            packs = fields["packs"] ?: 0,
            packed = fields["in-pack"] ?: 0,
            packedKb = fields["size-pack"] ?: 0,
        )
    }

    suspend fun gc(): GcResult {
        val before = stats()
        val result = git.runGit("gc", cwd = paths.repo)
        val after = stats()
        val error = if (result.ok) null else result.errorMessage()
        return GcResult(ok = result.ok, error = error, before = before, after = after)
    }

    suspend fun gitDir(): String =
        git.runGit("rev-parse", "--absolute-git-dir", cwd = paths.repo, quiet = true).stdoutTrimmed()
}
