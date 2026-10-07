package com.glycin.git

import com.glycin.api.ws.EventBroadcaster
import com.glycin.world.WorldPaths
import io.quarkus.websockets.next.OpenConnections
import io.quarkus.websockets.next.WebSocketConnection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

class PackingTest {

    private object NoConnections : OpenConnections {
        override fun iterator(): MutableIterator<WebSocketConnection> = mutableListOf<WebSocketConnection>().iterator()
        override fun stream(): Stream<WebSocketConnection> = Stream.empty()
    }

    private lateinit var repo: Path
    private lateinit var packing: Packing

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        repo = tempDir
        packing = Packing(WorldPaths(tempDir.toString()), GitRunner(EventBroadcaster(NoConnections)))
        git("init", "-b", "main")
        git("config", "user.name", "test")
        git("config", "user.email", "test@example.com")
        Files.writeString(repo.resolve("map.grid"), "....")
        git("add", "-A")
        git("commit", "-m", "seed")
        Files.writeString(repo.resolve("map.grid"), "..b.")
        git("add", "-A")
        git("commit", "-m", "build")
    }

    @Test
    fun `a fresh repo is all loose objects`() = runBlocking {
        val stats = packing.stats()
        assertEquals(6, stats.loose)
        assertEquals(0, stats.packs)
        assertEquals(0, stats.packed)
    }

    @Test
    fun `gc folds every loose object into one pack`() = runBlocking {
        val result = packing.gc()
        assertTrue(result.ok, result.error)
        assertEquals(6, result.before.loose)
        assertEquals(0, result.after.loose)
        assertEquals(1, result.after.packs)
        assertEquals(6, result.after.packed)
        assertTrue(Files.exists(repo.resolve(".git/packed-refs")))
    }

    @Test
    fun `gitDir is the absolute dot-git path`() = runBlocking {
        assertEquals(repo.toRealPath().resolve(".git").toString(), packing.gitDir())
    }

    private fun git(vararg args: String) {
        val process = ProcessBuilder(listOf("git") + args).directory(repo.toFile()).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed: $out" }
    }
}
