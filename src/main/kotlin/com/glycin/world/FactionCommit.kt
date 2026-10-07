package com.glycin.world

import com.glycin.api.model.Cell
import com.glycin.git.errorMessage
import com.glycin.git.GitRunner
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

@ApplicationScoped
class FactionCommit(
    private val paths: WorldPaths,
    private val git: GitRunner,
    private val staging: Staging,
    private val conflictReader: ConflictReader,
) {

    suspend fun commit(anchor: Cell, authorName: String, message: String): String? {
        conflictReader.activeOp()?.let { return "a $it is in progress" }
        val env = mapOf("GIT_INDEX_FILE" to INDEX_FILE)
        try {
            val seeded = git.runGit("read-tree", "HEAD", cwd = paths.repo, env = env, echo = false)
            if (!seeded.ok) return seeded.errorMessage()
            staging.stageQuietly(anchor, env)?.let { return it }
            val commit = git.runGit(
                "commit", "-q", "--author=$authorName <${email(authorName)}>", "-m", message,
                cwd = paths.repo, env = env, echo = false,
            )
            if (!commit.ok) return commit.errorMessage()
        } finally {
            withContext(Dispatchers.IO) { Files.deleteIfExists(paths.repo.resolve(INDEX_FILE)) }
        }
        return staging.stageQuietly(anchor)
    }

    private fun email(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), ".").trim('.') + "@silicon.rome"

    companion object {
        private const val INDEX_FILE = ".git/adversary.index"
    }
}
