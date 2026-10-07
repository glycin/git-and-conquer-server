package com.glycin.world

import com.glycin.api.model.WalletState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class WalletFileTest {

    private lateinit var paths: WorldPaths
    private lateinit var walletFile: WalletFile

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        paths = WorldPaths(tempDir.toString())
        walletFile = WalletFile(paths)
    }

    @Test
    fun `write then read round-trips`() {
        val wallet = WalletState(silicon = 900, copper = 120, gold = 420, tokens = 40)
        walletFile.write(wallet)
        assertEquals(wallet, walletFile.read())
    }

    @Test
    fun `missing file reads as start balances`() {
        assertEquals(WalletFile.START, walletFile.read())
    }

    @Test
    fun `ensureExists seeds start balances once and never overwrites`() {
        walletFile.ensureExists()
        assertEquals(WalletFile.START, walletFile.read())

        walletFile.write(WalletState(silicon = 1, copper = 2, gold = 3, tokens = 4))
        walletFile.ensureExists()
        assertEquals(WalletState(silicon = 1, copper = 2, gold = 3, tokens = 4), walletFile.read())
    }

    @Test
    fun `malformed and unknown lines are ignored`() {
        Files.writeString(paths.wallet, "silicon 250\ngarbage\ncopper notanumber\nmithril 999\ntokens 7\n")
        assertEquals(WalletState(silicon = 250, copper = 0, gold = 0, tokens = 7), walletFile.read())
    }

    @Test
    fun `written file is one lf-terminated line per resource`() {
        walletFile.write(WalletState(silicon = 5, copper = 6, gold = 7, tokens = 8))
        assertEquals("silicon 5\ncopper 6\ngold 7\ntokens 8\n", Files.readString(paths.wallet))
    }
}
