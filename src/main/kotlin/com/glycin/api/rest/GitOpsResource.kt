package com.glycin.api.rest

import com.glycin.api.model.BranchRequest
import com.glycin.api.model.CherryPickRequest
import com.glycin.api.model.CheckoutRequest
import com.glycin.api.model.FfMode
import com.glycin.api.model.GcResult
import com.glycin.api.model.OpResult
import com.glycin.api.model.MergeRequest
import com.glycin.api.model.RebaseRequest
import com.glycin.api.model.ResetRequest
import com.glycin.api.model.RevertRequest
import com.glycin.api.model.TagRequest
import com.glycin.git.GitResult
import com.glycin.git.GitRunner
import com.glycin.git.errorMessage
import com.glycin.git.Packing
import com.glycin.world.ConflictReader
import com.glycin.world.WorldPaths
import com.glycin.world.WorldRepo
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType

@Path("/git")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
class GitOpsResource(
    private val worldRepo: WorldRepo,
    private val paths: WorldPaths,
    private val git: GitRunner,
    private val conflictReader: ConflictReader,
    private val packing: Packing,
) {

    @POST
    @Path("/branch")
    suspend fun branch(req: BranchRequest): OpResult {
        val args = buildList {
            add("branch")
            add(req.name)
            req.fromRef?.let { add(it) }
        }
        return finish(git.runGit(*args.toTypedArray(), cwd = paths.repo), opName = null)
    }

    @POST
    @Path("/checkout")
    suspend fun checkout(req: CheckoutRequest): OpResult =
        guardReady() ?: finish(git.runGit("checkout", req.ref, cwd = paths.repo), opName = null)

    @POST
    @Path("/revert")
    suspend fun revert(req: RevertRequest): OpResult {
        guardReady()?.let { return it }
        val args = listOf("revert", "--no-edit") + mainline(req.sha) + req.sha
        return finish(git.runGit(*args.toTypedArray(), cwd = paths.repo), opName = "revert")
    }

    @POST
    @Path("/cherry-pick")
    suspend fun cherryPick(req: CherryPickRequest): OpResult {
        guardReady()?.let { return it }
        val args = listOf("cherry-pick") + mainline(req.sha) + req.sha
        return finish(git.runGit(*args.toTypedArray(), cwd = paths.repo), opName = "cherry-pick")
    }

    @POST
    @Path("/merge")
    suspend fun merge(req: MergeRequest): OpResult {
        guardReady()?.let { return it }
        val args = buildList {
            add("merge")
            add("--no-edit")
            add(
                when (req.ff) {
                    FfMode.AUTO -> "--ff"
                    FfMode.ONLY -> "--ff-only"
                    FfMode.NEVER -> "--no-ff"
                },
            )
            req.strategy?.let { add("-s"); add(it) }
            add(req.ref)
        }
        return finish(git.runGit(*args.toTypedArray(), cwd = paths.repo), opName = "merge")
    }

    @POST
    @Path("/rebase")
    suspend fun rebase(req: RebaseRequest): OpResult {
        guardReady()?.let { return it }
        val args = buildList {
            add("rebase")
            if (req.stepwise) add("-i")
            if (req.upstream != null) {
                add("--onto"); add(req.ontoRef); add(req.upstream)
                req.branch?.let { add(it) }
            } else {
                add(req.ontoRef)
            }
        }
        val env = if (req.stepwise) STEPWISE_EDITORS else emptyMap()
        val result = git.runGit(*args.toTypedArray(), cwd = paths.repo, env = env, showEnv = false)
        return finish(result, opName = "rebase")
    }

    @POST
    @Path("/reset")
    suspend fun reset(req: ResetRequest): OpResult {
        val mode = "--${req.mode.name.lowercase()}"
        return finish(git.runGit("reset", mode, req.ref, cwd = paths.repo), opName = null)
    }

    @POST
    @Path("/tag")
    suspend fun tag(req: TagRequest): OpResult {
        val args = buildList {
            add("tag")
            add(req.name)
            req.ref?.let { add(it) }
        }
        return finish(git.runGit(*args.toTypedArray(), cwd = paths.repo), opName = null)
    }

    @POST
    @Path("/gc")
    suspend fun gc(): GcResult = packing.gc()

    // Merge commits need a mainline. The first parent is the timeline the merge happened on.
    private suspend fun mainline(sha: String): List<String> {
        val parents = git.runGit("rev-list", "--parents", "-n", "1", sha, cwd = paths.repo, quiet = true)
            .stdoutTrimmed().split(' ').filter { it.isNotBlank() }.drop(1)
        return if (parents.size > 1) listOf("-m", "1") else emptyList()
    }

    // No time travel with a dirty tree, or uncommitted stuff leaks into other timelines. branch/tag don't care, and reset is how you clean up.
    private suspend fun guardReady(): OpResult? {
        conflictReader.activeOp()?.let {
            return worldRepo.failure("a $it is in progress")
        }
        if (!worldRepo.status().dirty) return null
        return worldRepo.failure("uncommitted changes, commit before time-travelling")
    }

    private suspend fun finish(result: GitResult, opName: String?): OpResult {
        if (result.ok) {
            return worldRepo.committed()
        }
        // Look for unmerged paths in the index. Git's error messages change per version and language.
        if (opName != null) {
            val conflicted = conflictReader.unmergedPaths()
            if (conflicted.isNotEmpty()) {
                return worldRepo.conflict(opName, conflicted)
            }
        }
        return worldRepo.failure(result.errorMessage())
    }

    companion object {
        private val EDIT_EVERY_PICK = listOf(
            "edit_all() {",
            "  { echo break",
            "    while IFS= read -r l; do",
            "      case \$l in \"pick \"*) l=\"edit \${l#pick }\" ;; esac",
            "      printf '%s\\n' \"\$l\"",
            "    done < \"\$1\"",
            "  } > \"\$1.step\" && mv \"\$1.step\" \"\$1\"",
            "}; edit_all",
        ).joinToString("\n")

        private val STEPWISE_EDITORS = mapOf(
            "GIT_SEQUENCE_EDITOR" to EDIT_EVERY_PICK,
            "GIT_EDITOR" to "true",
        )
    }
}
