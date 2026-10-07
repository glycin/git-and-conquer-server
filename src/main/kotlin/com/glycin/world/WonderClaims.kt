package com.glycin.world

import com.glycin.api.model.MapObject
import com.glycin.api.model.WonderClaim
import com.glycin.api.model.WonderState
import com.glycin.git.GitRunner
import jakarta.enterprise.context.ApplicationScoped
import java.time.Clock

// A claim is an annotated tag called tabularium-<faction>-<n>, and the clock starts at its tagger date.
// We never delete tags, and keep the names flat so RepoWatcher spots them in refs/tags/.
@ApplicationScoped
class WonderClaims(
    private val paths: WorldPaths,
    private val git: GitRunner,
    private val clock: Clock,
) {

    suspend fun state(objects: List<MapObject>): WonderState? {
        val anchor = objects.firstOrNull { it.type == TileLegend.tabularium.type } ?: return null
        val claims = history()
        return WonderState(
            row = anchor.row,
            col = anchor.col,
            claim = claims.firstOrNull()?.let { toClaim(it) },
            claimsByPlayer = claims.count { it.faction == PLAYER_FACTION },
        )
    }

    // Returns an error message, or null if it worked.
    suspend fun claim(faction: String): String? {
        if (faction.isBlank() || !faction.all { it.isLetterOrDigit() }) return "faction must be alphanumeric"
        val claims = history()
        claims.firstOrNull()?.let { current ->
            val active = toClaim(current)
            if (active.active) {
                return "the Tabularium is already tagged by ${current.faction} for another ${active.expiresAt - now()} s"
            }
        }
        val name = "$TAG_PREFIX$faction-${claims.size + 1}"
        val result = git.runGit("tag", "-a", name, "-m", "$faction claims the Tabularium", cwd = paths.repo)
        if (!result.ok) return result.stderr.trim().ifBlank { result.stdout.trim() }
        return null
    }

    private class Tagged(val name: String, val faction: String, val taggedAt: Long)

    // Newest first. Lightweight tags have no date, so they don't count as claims.
    private suspend fun history(): List<Tagged> {
        val out = git.runGit(
            "for-each-ref", "--sort=-taggerdate", "--format=%(refname:short) %(taggerdate:unix)",
            "refs/tags/$TAG_PREFIX*", cwd = paths.repo, quiet = true,
        ).stdout
        return out.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(' ')
            if (parts.size != 2) return@mapNotNull null
            val name = parts[0]
            val taggedAt = parts[1].toLongOrNull() ?: return@mapNotNull null
            val faction = name.removePrefix(TAG_PREFIX).substringBeforeLast('-')
            if (faction.isBlank()) return@mapNotNull null
            Tagged(name, faction, taggedAt)
        }.toList()
    }

    private fun toClaim(tagged: Tagged): WonderClaim {
        val expiresAt = tagged.taggedAt + CLAIM_SECONDS
        return WonderClaim(
            tag = tagged.name,
            faction = tagged.faction,
            taggedAt = tagged.taggedAt,
            expiresAt = expiresAt,
            active = now() < expiresAt,
        )
    }

    private fun now(): Long = clock.millis() / 1000

    companion object {
        const val TAG_PREFIX = "tabularium-"
        const val CLAIM_SECONDS = 5L * 60L
        const val PLAYER_FACTION = "player"
    }
}
