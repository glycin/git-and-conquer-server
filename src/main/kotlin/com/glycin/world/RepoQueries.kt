package com.glycin.world

import com.glycin.api.model.BranchInfo
import com.glycin.api.model.BranchesResponse
import com.glycin.api.model.CommitInfo
import com.glycin.api.model.GraphBranch
import com.glycin.api.model.GraphResponse
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

@ApplicationScoped
class RepoQueries(private val paths: WorldPaths) {

    suspend fun log(branch: String?, limit: Int): List<CommitInfo> = withContext(Dispatchers.IO) {
        openRepo().use { repo ->
            Git(repo).use { git ->
                val logCommand = git.log().setMaxCount(limit)
                if (branch != null) {
                    val id = repo.resolve(branch) ?: return@use emptyList()
                    logCommand.add(id)
                }
                logCommand.call().map { commit ->
                    CommitInfo(
                        sha = commit.name,
                        shortSha = commit.name.take(7),
                        parents = commit.parents.map { it.name },
                        author = commit.authorIdent.name,
                        message = commit.shortMessage,
                        timestamp = commit.commitTime.toLong(),
                    )
                }
            }
        }
    }

    // Start from every branch tip plus HEAD, so a detached HEAD still sees its own history.
    suspend fun graph(limit: Int): GraphResponse = withContext(Dispatchers.IO) {
        openRepo().use { repo ->
            val fullBranch = repo.fullBranch ?: ""
            val detached = !fullBranch.startsWith("refs/heads/")
            val current = if (detached) "" else fullBranch.removePrefix("refs/heads/")
            val headId = repo.resolve("HEAD")

            Git(repo).use { git ->
                // When detached, branchList() sneaks in a fake branch called "HEAD".
                val refs = git.branchList().call().filter { it.name.startsWith("refs/heads/") }
                val tips = refs.map {
                    val name = it.name.removePrefix("refs/heads/")
                    GraphBranch(name = name, head = it.objectId.name, baseCommit = baseCommitOf(repo, name, it.objectId.name))
                }

                val starts = (refs.map { it.objectId } + listOfNotNull(headId)).distinct()
                val commits = if (starts.isEmpty()) emptyList() else {
                    val logCommand = git.log().setMaxCount(limit)
                    starts.forEach { logCommand.add(it) }
                    logCommand.call().map { commit ->
                        CommitInfo(
                            sha = commit.name,
                            shortSha = commit.name.take(7),
                            parents = commit.parents.map { it.name },
                            author = commit.authorIdent.name,
                            message = commit.shortMessage,
                            timestamp = commit.commitTime.toLong(),
                        )
                    }
                }

                GraphResponse(
                    current = current,
                    head = headId?.name ?: "",
                    detached = detached,
                    commits = commits,
                    branches = tips,
                )
            }
        }
    }

    // ahead/behind are counted against compareTo, or HEAD if there isn't one.
    suspend fun branches(compareTo: String?): BranchesResponse = withContext(Dispatchers.IO) {
        openRepo().use { repo ->
            val current = repo.fullBranch?.removePrefix("refs/heads/") ?: "HEAD"
            val compareName = compareTo ?: current
            val compareId = repo.resolve(compareTo?.let { "refs/heads/$it" } ?: "HEAD")

            val infos = Git(repo).use { git ->
                git.branchList().call().filter { it.name.startsWith("refs/heads/") }.map { ref ->
                    val name = ref.name.removePrefix("refs/heads/")
                    val head = ref.objectId.name
                    val (ahead, behind) = if (name != compareName && compareId != null) {
                        aheadBehind(repo, ref.objectId, compareId)
                    } else {
                        0 to 0
                    }
                    BranchInfo(name = name, head = head, ahead = ahead, behind = behind)
                }
            }
            BranchesResponse(current = current, branches = infos)
        }
    }

    private fun aheadBehind(repo: Repository, branchId: ObjectId, compareId: ObjectId): Pair<Int, Int> {
        val ahead = RevWalk(repo).use { walk ->
            walk.markStart(walk.parseCommit(branchId))
            walk.markUninteresting(walk.parseCommit(compareId))
            walk.count()
        }
        val behind = RevWalk(repo).use { walk ->
            walk.markStart(walk.parseCommit(compareId))
            walk.markUninteresting(walk.parseCommit(branchId))
            walk.count()
        }
        return ahead to behind
    }

    suspend fun baseCommit(branch: String, head: String): String = withContext(Dispatchers.IO) {
        openRepo().use { repo -> baseCommitOf(repo, branch, head) }
    }

    private fun baseCommitOf(repo: Repository, branch: String, head: String): String =
        repo.getReflogReader("refs/heads/$branch")
            ?.reverseEntries
            ?.lastOrNull()
            ?.newId
            ?.name
            ?: head

    private fun openRepo(): Repository =
        FileRepositoryBuilder()
            .setGitDir(paths.repo.resolve(".git").toFile())
            .readEnvironment()
            .findGitDir()
            .build()
}
