package com.glycin.world

import com.glycin.api.model.MapSnapshot
import com.glycin.api.model.RepoStatus
import com.glycin.git.GitDriverInstaller
import com.glycin.git.GitRunner
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eclipse.microprofile.config.inject.ConfigProperty
import org.jboss.logging.Logger
import java.nio.file.Files

@ApplicationScoped
class WorldRepo(
    private val paths: WorldPaths,
    private val repoQueries: RepoQueries,
    private val gridFile: GridFile,
    private val objectStore: ObjectStore,
    private val metaFiles: MetaFiles,
    private val walletFile: WalletFile,
    private val staging: Staging,
    private val conflictReader: ConflictReader,
    private val wonderClaims: WonderClaims,
    private val git: GitRunner,
    private val driverInstaller: GitDriverInstaller,
    @param:ConfigProperty(name = "world.seed", defaultValue = "0")
    private val worldSeed: Int,
) {

    fun onStart(@Observes ev: StartupEvent) {
        runBlocking { initIfNeeded() }
    }

    suspend fun initIfNeeded() {
        withContext(Dispatchers.IO) {
            Files.createDirectories(paths.repo)
            Files.createDirectories(paths.objects)
        }
        val freshInit = !Files.exists(paths.repo.resolve(".git"))
        if (freshInit) {
            log.infof("Initializing world repo at %s", paths.repo)
            git.runGit("init", "-b", "main", cwd = paths.repo)
            git.runGit("config", "user.name", "Git and Conquer", cwd = paths.repo)
            git.runGit("config", "user.email", "demo@gitandconquer.local", cwd = paths.repo)
            val world = WorldGenerator.generate(worldSeed)
            log.infof("Generated world with seed %d", world.seed)
            gridFile.write(world.cells)
            world.objects.forEach { objectStore.write(it) }
            metaFiles.writeMapMeta(world)
            withContext(Dispatchers.IO) {
                Files.writeString(paths.objects.resolve(".gitkeep"), "")
            }
        } else {
            log.infof("World repo already exists at %s", paths.repo)
        }

        driverInstaller.installAll()
        metaFiles.writeLegend()
        walletFile.ensureExists()

        if (freshInit) {
            git.runGit("add", "-A", cwd = paths.repo)
            git.runGit("commit", "-m", "Initial world", cwd = paths.repo)
        }
    }

    suspend fun snapshot(): MapSnapshot {
        val (head, branch) = headAndBranch()
        val objects = objectStore.listAll()
        return MapSnapshot(
            head = head,
            branch = branch,
            baseCommit = repoQueries.baseCommit(branch, head),
            width = GridFile.SIZE,
            height = GridFile.SIZE,
            grid = gridFile.raw(),
            wallet = walletFile.read(),
            objects = objects,
            uncommitted = staging.states(),
            wonder = wonderClaims.state(objects),
        )
    }

    suspend fun status(): RepoStatus {
        val out = git.runGit("status", "--porcelain=v2", "--branch", "--no-renames", cwd = paths.repo).stdout
        var head = ""
        var branch = ""
        var staged = false
        var unstaged = false
        val entries = StringBuilder()
        for (line in out.lineSequence()) {
            when {
                line.startsWith("# branch.oid ") -> head = line.removePrefix("# branch.oid ").trim()
                line.startsWith("# branch.head ") -> branch = line.removePrefix("# branch.head ").trim()
                line.startsWith("#") || line.isBlank() -> continue
                else -> {
                    entries.append(line).append('\n')
                    when (line[0]) {
                        '1', '2' -> {
                            if (line.getOrNull(2) != '.') staged = true
                            if (line.getOrNull(3) != '.') unstaged = true
                        }
                        else -> unstaged = true
                    }
                }
            }
        }
        return RepoStatus(
            branch = resolveBranch(branch),
            head = head,
            dirty = staged || unstaged,
            staged = staged,
            unstaged = unstaged,
            fingerprint = Integer.toHexString(entries.toString().hashCode()),
            operation = conflictReader.activeOp(),
            replay = conflictReader.replay(changesPending = staged || unstaged),
        )
    }

    suspend fun headAndBranch(): Pair<String, String> {
        val lines = git.runGit("rev-parse", "HEAD", "--abbrev-ref", "HEAD", cwd = paths.repo)
            .stdout.lines()
        return (lines.getOrNull(0)?.trim().orEmpty()) to resolveBranch(lines.getOrNull(1)?.trim().orEmpty())
    }

    // During a rebase HEAD is detached, so show the branch being rebased instead.
    private suspend fun resolveBranch(reported: String): String =
        if (reported == "HEAD" || reported == "(detached)") conflictReader.rebasingBranch() ?: reported else reported

    companion object {
        private val log: Logger = Logger.getLogger(WorldRepo::class.java)
    }
}
