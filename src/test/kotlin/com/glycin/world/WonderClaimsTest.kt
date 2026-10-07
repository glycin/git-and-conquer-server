package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.MapObject
import com.glycin.api.ws.EventBroadcaster
import com.glycin.git.GitRunner
import io.quarkus.websockets.next.OpenConnections
import io.quarkus.websockets.next.WebSocketConnection
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.stream.Stream

class WonderClaimsTest {

    private object NoConnections : OpenConnections {
        override fun iterator(): MutableIterator<WebSocketConnection> = mutableListOf<WebSocketConnection>().iterator()
        override fun stream(): Stream<WebSocketConnection> = Stream.empty()
    }

    private class FixedClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneOffset = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    private lateinit var repo: Path
    private lateinit var clock: FixedClock
    private lateinit var claims: WonderClaims

    private val tabularium = listOf(
        MapObject(row = 61, col = 61, category = Category.BUILDING, type = "tabularium", char = 'u', fpCols = 6, fpRows = 6),
    )

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        repo = tempDir
        clock = FixedClock(Instant.now())
        claims = WonderClaims(WorldPaths(tempDir.toString()), GitRunner(EventBroadcaster(NoConnections)), clock)
        git("init", "-b", "main")
        git("config", "user.name", "test")
        git("config", "user.email", "test@example.com")
        Files.writeString(repo.resolve("map.grid"), "....")
        git("add", "-A")
        git("commit", "-m", "seed")
    }

    @Test
    fun `no tags means no claim`() = runBlocking {
        val state = claims.state(tabularium)
        assertNotNull(state)
        assertNull(state!!.claim)
        assertEquals(0, state.claimsByPlayer)
        assertEquals(61, state.row)
    }

    @Test
    fun `a world without the tabularium has no wonder state`() = runBlocking {
        assertNull(claims.state(emptyList()))
    }

    @Test
    fun `claiming creates an annotated tag that is active until it expires`() = runBlocking {
        assertNull(claims.claim("player"))

        assertEquals("tabularium-player-1", git("tag", "-l"))
        assertEquals("tag", git("cat-file", "-t", "tabularium-player-1"))

        val claim = claims.state(tabularium)!!.claim!!
        assertEquals("player", claim.faction)
        assertTrue(claim.active)
        assertEquals(claim.taggedAt + WonderClaims.CLAIM_SECONDS, claim.expiresAt)
        assertEquals(1, claims.state(tabularium)!!.claimsByPlayer)

        clock.now = Instant.ofEpochSecond(claim.expiresAt)
        assertFalse(claims.state(tabularium)!!.claim!!.active)
    }

    @Test
    fun `a second claim is refused while the first is active`() = runBlocking {
        assertNull(claims.claim("player"))
        val refused = claims.claim("maximus")
        assertNotNull(refused)
        assertTrue(refused!!.contains("player"), refused)
        assertEquals("tabularium-player-1", git("tag", "-l"))
    }

    @Test
    fun `an expired claim can be taken over and the history keeps counting`() = runBlocking {
        assertNull(claims.claim("player"))
        clock.now = clock.now.plusSeconds(WonderClaims.CLAIM_SECONDS)
        assertNull(claims.claim("maximus"))

        val state = claims.state(tabularium)!!
        assertEquals("maximus", state.claim!!.faction)
        assertEquals("tabularium-maximus-2", state.claim!!.tag)
        assertEquals(1, state.claimsByPlayer)
        assertEquals(2, git("tag", "-l").lines().size)
    }

    @Test
    fun `an unrelated lightweight tag is not a claim`() = runBlocking {
        git("tag", "v1")
        git("tag", "tabularium-player-9")
        assertNull(claims.state(tabularium)!!.claim)
    }

    @Test
    fun `faction names are sanitized`() = runBlocking {
        assertNotNull(claims.claim("../evil"))
        assertNotNull(claims.claim(""))
        assertEquals("", git("tag", "-l"))
    }

    private fun git(vararg args: String): String {
        val proc = ProcessBuilder("git", *args).directory(repo.toFile()).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$out" }
        return out.trim()
    }
}
