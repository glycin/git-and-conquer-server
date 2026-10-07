package com.glycin.world

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RepoQueriesTest {

    private lateinit var repo: Path
    private lateinit var queries: RepoQueries

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        repo = tempDir
        queries = RepoQueries(WorldPaths(tempDir.toString()))
        git("init", "-b", "main")
        git("config", "user.name", "test")
        git("config", "user.email", "test@example.com")
    }

    @Test
    fun `main's base is its first commit, whatever comes after`() = runBlocking {
        val root = commit("one")
        commit("two")
        val head = commit("three")

        assertEquals(root, queries.baseCommit("main", head))
    }

    @Test
    fun `a branch keeps its creation commit through commits, rebase and merge`() = runBlocking {
        commit("one")
        val fork = commit("two")
        git("branch", "feature", fork)
        commit("three")
        git("checkout", "feature")
        commit("four")
        git("rebase", "main")
        git("checkout", "main")
        git("merge", "feature")
        val head = git("rev-parse", "HEAD")

        assertEquals(fork, queries.baseCommit("feature", head))
        assertEquals(git("rev-list", "--max-parents=0", "HEAD"), queries.baseCommit("main", head))
    }

    @Test
    fun `no reflog falls back to the head sha`() = runBlocking {
        val head = commit("one")
        assertEquals(head, queries.baseCommit("HEAD", head))
    }

    private fun commit(name: String): String {
        Files.writeString(repo.resolve("$name.txt"), name)
        git("add", "-A")
        git("commit", "-m", name)
        return git("rev-parse", "HEAD")
    }

    private fun git(vararg args: String): String {
        val proc = ProcessBuilder("git", *args).directory(repo.toFile()).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$out" }
        return out.trim()
    }
}
