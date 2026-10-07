package com.glycin.git

import com.glycin.api.model.GitEvent
import com.glycin.api.ws.EventBroadcaster
import com.glycin.world.ConflictReader
import com.glycin.world.Diff
import com.glycin.world.WorldPaths
import com.glycin.world.WorldRepo
import io.quarkus.runtime.ShutdownEvent
import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jboss.logging.Logger
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.WatchKey
import java.nio.file.WatchService
import kotlin.time.Duration.Companion.milliseconds

@ApplicationScoped
class RepoWatcher(
    private val paths: WorldPaths,
    private val worldRepo: WorldRepo,
    private val diff: Diff,
    private val conflictReader: ConflictReader,
    private val broadcaster: EventBroadcaster,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var watchService: WatchService? = null

    fun onStart(@Observes event: StartupEvent) {
        scope.launch { watchLoop() }
    }

    fun onShutdown(@Observes event: ShutdownEvent) {
        try {
            watchService?.close()
        } catch (_: Throwable) { /* ignored */ }
        scope.cancel()
    }

    private suspend fun watchLoop() {
        val gitDir = awaitGitDir() ?: run {
            log.warnf("No .git at %s after %d ms; watcher idle", paths.repo.resolve(".git"), GIT_DIR_WAIT_MS)
            return
        }

        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        gitDir.register(service, ENTRY_MODIFY, ENTRY_CREATE, ENTRY_DELETE)

        val refsHeads = gitDir.resolve("refs").resolve("heads")
        Files.createDirectories(refsHeads)
        val refsKey = refsHeads.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
        val refsTags = gitDir.resolve("refs").resolve("tags")
        Files.createDirectories(refsTags)
        val tagsKey = refsTags.register(service, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)

        log.infof("RepoWatcher started on %s", gitDir)

        var (lastHead, lastBranch) = worldRepo.headAndBranch()
        var lastConflictFingerprint = conflictReader.fingerprint()
        var lastConflictCount = conflictReader.unmergedCount()
        log.infof("RepoWatcher seed HEAD = %s (%s), conflicts = %d", lastHead, lastBranch, lastConflictCount)

        try {
            while (currentCoroutineContext().isActive) {
                val key = service.take()
                var tagsChanged = key === tagsKey && key.pollEvents().isNotEmpty()
                var relevant = tagsChanged || isRelevant(key, refsKey)
                key.reset()

                delay(DEBOUNCE_MS.milliseconds)
                while (true) {
                    val nextKey = service.poll() ?: break
                    if (nextKey === tagsKey) {
                        if (nextKey.pollEvents().isNotEmpty()) tagsChanged = true
                    } else if (isRelevant(nextKey, refsKey)) {
                        relevant = true
                    }
                    nextKey.reset()
                }

                // Ignore our own git reads and lock files, or the watcher keeps waking itself up.
                if (!relevant && !tagsChanged) continue

                if (tagsChanged) {
                    log.info("Tags changed")
                    broadcaster.broadcast(GitEvent.TagsChanged)
                }
                if (!relevant) continue

                val (currentHead, currentBranch) = worldRepo.headAndBranch()
                if (currentHead != lastHead) {
                    val delta = diff.diff(lastHead, currentHead)
                    log.infof(
                        "HEAD moved: %s -> %s (%s) [+%d ~%d -%d]",
                        lastHead, currentHead, delta.newBranch,
                        delta.added.size, delta.modified.size, delta.removed.size,
                    )
                    broadcaster.broadcast(GitEvent.HeadMoved(delta))
                    lastHead = currentHead
                }
                if (currentBranch != lastBranch) {
                    log.infof("Branch switched: %s -> %s", lastBranch, currentBranch)
                    broadcaster.broadcast(GitEvent.BranchSwitched(currentBranch, currentHead))
                    lastBranch = currentBranch
                }

                // Hash instead of count, because the next rebase step can stop with the same number of conflicts.
                val currentConflictFingerprint = conflictReader.fingerprint()
                val currentConflictCount = conflictReader.unmergedCount()
                if (currentConflictFingerprint != lastConflictFingerprint && (currentConflictCount > 0 || lastConflictCount > 0)) {
                    if (currentConflictCount > 0) {
                        val conflicts = conflictReader.read()
                        log.infof(
                            "Conflicted: op=%s, cells=%d, objects=%d",
                            conflicts.operation, conflicts.cells.size, conflicts.objects.size,
                        )
                        broadcaster.broadcast(
                            GitEvent.Conflicted(
                                operation = conflicts.operation,
                                cellCount = conflicts.cells.size,
                                objectCount = conflicts.objects.size,
                            ),
                        )
                    } else {
                        log.infof("Conflicts cleared (was %d)", lastConflictCount)
                        broadcaster.broadcast(GitEvent.Conflicted("none", 0, 0))
                    }
                    lastConflictCount = currentConflictCount
                }
                lastConflictFingerprint = currentConflictFingerprint
            }
        } catch (_: ClosedWatchServiceException) {
            // shutting down, all good
        } catch (_: CancellationException) {
            // shutting down, all good
        } catch (error: Throwable) {
            log.errorf(error, "RepoWatcher loop crashed")
        }
    }

    private fun isRelevant(key: WatchKey, refsKey: WatchKey): Boolean {
        val events = key.pollEvents()
        if (key === refsKey) return events.isNotEmpty()
        return events.any { event ->
            val name = (event.context() as? Path)?.toString()
            name != null && (name in RELEVANT_GIT_FILES || name.startsWith("rebase-"))
        }
    }

    private suspend fun awaitGitDir(): Path? {
        val gitDir = paths.repo.resolve(".git")
        val deadline = System.currentTimeMillis() + GIT_DIR_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(gitDir)) return gitDir
            delay(100.milliseconds)
        }
        return null
    }

    companion object {
        private val log: Logger = Logger.getLogger(RepoWatcher::class.java)
        private const val DEBOUNCE_MS = 100L
        private const val GIT_DIR_WAIT_MS = 5_000L

        // No index, *.lock, logs/ or objects/ here. Our own reads touch those all the time.
        private val RELEVANT_GIT_FILES = setOf(
            "HEAD", "ORIG_HEAD", "MERGE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD", "REBASE_HEAD",
        )
    }
}
