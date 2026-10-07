package com.glycin.world

import com.glycin.api.model.WalletState
import jakarta.enterprise.context.ApplicationScoped
import java.nio.file.Files

@ApplicationScoped
class WalletFile(private val paths: WorldPaths) {

    fun read(): WalletState {
        if (!Files.exists(paths.wallet)) return START
        val values = Files.readString(paths.wallet)
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val amount = parts.getOrNull(1)?.toIntOrNull()
                if (amount != null) parts[0] to amount else null
            }
            .toMap()
        return WalletState(
            silicon = values["silicon"] ?: 0,
            copper = values["copper"] ?: 0,
            gold = values["gold"] ?: 0,
            tokens = values["tokens"] ?: 0,
        )
    }

    fun write(wallet: WalletState) {
        Files.writeString(
            paths.wallet,
            "silicon ${wallet.silicon}\ncopper ${wallet.copper}\ngold ${wallet.gold}\ntokens ${wallet.tokens}\n",
        )
    }

    fun ensureExists() {
        if (!Files.exists(paths.wallet)) write(START)
    }

    companion object {
        val START = WalletState(silicon = 500, copper = 300, gold = 100, tokens = 0)
    }
}
