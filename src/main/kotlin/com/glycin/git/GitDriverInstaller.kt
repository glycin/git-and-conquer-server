package com.glycin.git

import com.glycin.world.WorldPaths
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jboss.logging.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

// The driver path is machine specific, so it goes in .git/config. The attributes go in .gitattributes and get committed.
@ApplicationScoped
class GitDriverInstaller(
    private val paths: WorldPaths,
    private val git: GitRunner,
) {

    suspend fun installAll() {
        val binary = locateBinary()?.toString()?.replace('\\', '/') ?: run {
            log.warnf(
                "Native git-drivers binary not found in any of %s; merges and diffs of the world will fall back to " +
                    "git's line-based defaults. Build it with `./gradlew :git-drivers:nativeCompile` and restart.",
                CANDIDATE_DIRS,
            )
            return
        }
        log.infof("Registering git drivers from %s", binary)
        git.runGit("config", "merge.mapmerge.name", "2D map merge", cwd = paths.repo)
        git.runGit("config", "merge.mapmerge.driver", """"$binary" mapmerge %O %A %B""", cwd = paths.repo)
        appendAttribute("${WorldPaths.GRID_FILENAME} merge=mapmerge")
        git.runGit("config", "diff.mapdiff.command", """"$binary" mapdiff""", cwd = paths.repo)
        appendAttribute("${WorldPaths.GRID_FILENAME} diff=mapdiff")
        git.runGit("config", "merge.walletmerge.name", "wallet max merge", cwd = paths.repo)
        git.runGit("config", "merge.walletmerge.driver", """"$binary" walletmerge %O %A %B""", cwd = paths.repo)
        appendAttribute("${WorldPaths.WALLET_FILENAME} merge=walletmerge")
    }

    private fun locateBinary(): Path? = CANDIDATE_DIRS
        .flatMap { dir -> BINARY_NAMES.map { Paths.get(dir, BINARY_SUBPATH, it).toAbsolutePath().normalize() } }
        .firstOrNull { Files.isExecutable(it) }

    private suspend fun appendAttribute(line: String) = withContext(Dispatchers.IO) {
        val attrs = paths.repo.resolve(".gitattributes")
        val existing = if (Files.exists(attrs)) Files.readString(attrs) else ""
        if ("$line\n" !in existing) {
            Files.writeString(attrs, existing + line + "\n")
        }
    }

    companion object {
        private val log: Logger = Logger.getLogger(GitDriverInstaller::class.java)
        private val CANDIDATE_DIRS = listOf("git-drivers", "../git-drivers", "server/git-drivers")
        private const val BINARY_SUBPATH = "build/native/nativeCompile"
        private val BINARY_NAMES = listOf("git-drivers", "git-drivers.exe")
    }
}
