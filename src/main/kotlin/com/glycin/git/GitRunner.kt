package com.glycin.git

import com.glycin.api.model.GitEvent
import com.glycin.api.ws.EventBroadcaster
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jboss.logging.Logger
import java.nio.file.Path

@ApplicationScoped
class GitRunner(private val broadcaster: EventBroadcaster) {

    // quiet = don't warn when a failure is expected. stdin always gets closed so git never sits there waiting.
    suspend fun runGit(
        vararg args: String,
        cwd: Path,
        quiet: Boolean = false,
        input: String? = null,
        env: Map<String, String> = emptyMap(),
        echo: Boolean = true,
        showEnv: Boolean = true,
    ): GitResult = withContext(Dispatchers.IO) {
        val command = listOf("git") + args
        val shellLine = ShellLine.render(command, if (showEnv) env else emptyMap())
        log.infof("[git %s] %s", cwd.fileName, shellLine)
        if (!showEnv && env.isNotEmpty()) log.debugf("[git %s] %s", cwd.fileName, ShellLine.render(command, env))

        val process = ProcessBuilder(command)
            .directory(cwd.toFile())
            .redirectErrorStream(false)
            .apply { environment().putAll(env) }
            .start()

        val stdinDeferred = async {
            process.outputStream.use { out -> input?.let { out.write(it.toByteArray()) } }
        }
        val stdoutDeferred = async { process.inputStream.bufferedReader().use { it.readText() } }
        val stderrDeferred = async { process.errorStream.bufferedReader().use { it.readText() } }

        stdinDeferred.await()
        val exit = process.waitFor()
        val result = GitResult(
            exit = exit,
            stdout = stdoutDeferred.await(),
            stderr = stderrDeferred.await(),
        )
        if (!result.ok && !quiet) {
            log.warnf("[git %s] exit=%d stderr=%s", cwd.fileName, exit, result.stderr.trim())
        }
        if (echo && ShellLine.mutates(args.toList())) {
            broadcaster.broadcast(GitEvent.GitCommand(shellLine, exit))
        }
        result
    }

    companion object {
        private val log: Logger = Logger.getLogger(GitRunner::class.java)
    }
}
