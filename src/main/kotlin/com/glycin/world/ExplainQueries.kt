package com.glycin.world

import com.glycin.api.model.CommitNode
import com.glycin.api.model.MergeExplain
import com.glycin.api.model.MerkleExplain
import com.glycin.api.model.TreeEntry
import com.glycin.git.GitRunner
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ExplainQueries(
    private val paths: WorldPaths,
    private val git: GitRunner,
) {

    suspend fun merkle(): MerkleExplain {
        val head = commitNode("HEAD") ?: return MerkleExplain(available = false, head = null, hasParent = false, parent = null)
        val parent = commitNode("${head.sha}^1")
        return MerkleExplain(available = true, head = head, hasParent = parent != null, parent = parent)
    }

    suspend fun merge(): MergeExplain = lastMerge() ?: divergedBranch() ?: UNAVAILABLE

    private suspend fun commitNode(rev: String): CommitNode? {
        val fields = read("log", "-1", "--format=%H%x00%T%x00%s", rev)?.trim()?.split('\u0000') ?: return null
        if (fields.size < 2) return null
        val sha = fields[0]
        return CommitNode(
            sha = sha,
            tree = fields[1],
            message = fields.getOrElse(2) { "" },
            root = lsTree(sha),
            objects = lsTree("$sha:${WorldPaths.OBJECTS_DIRNAME}"),
        )
    }

    private suspend fun lsTree(treeish: String): List<TreeEntry> =
        read("ls-tree", treeish)?.lineSequence()?.mapNotNull(::parseEntry)?.toList().orEmpty()

    private fun parseEntry(line: String): TreeEntry? {
        val tab = line.indexOf('\t')
        if (tab < 0) return null
        val meta = line.substring(0, tab).split(' ')
        if (meta.size < 3) return null
        return TreeEntry(name = line.substring(tab + 1), type = meta[1], sha = meta[2])
    }

    private suspend fun lastMerge(): MergeExplain? {
        val merge = read("rev-list", "--merges", "-n", "1", "HEAD")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val ours = revParse("$merge^1") ?: return null
        val theirs = revParse("$merge^2") ?: return null
        val base = read("merge-base", ours, theirs)?.trim() ?: return null
        return explain("merge commit ${merge.take(7)}", label(ours), label(theirs), base, ours, theirs)
    }

    private suspend fun divergedBranch(): MergeExplain? {
        val head = revParse("HEAD") ?: return null
        val current = revParse("--abbrev-ref", "HEAD").orEmpty()
        val branches = read("for-each-ref", "--format=%(refname:short)", "refs/heads")
            ?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() && it != current }.orEmpty()
        for (branch in branches) {
            val tip = revParse(branch) ?: continue
            val base = read("merge-base", head, tip)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            if (base == head || base == tip) continue
            return explain("preview of merging $branch into $current", current, branch, base, head, tip)
        }
        return null
    }

    private suspend fun explain(
        source: String,
        oursLabel: String,
        theirsLabel: String,
        base: String,
        ours: String,
        theirs: String,
    ): MergeExplain? {
        val baseGrid = grid(base) ?: return null
        val oursGrid = grid(ours) ?: return null
        val theirsGrid = grid(theirs) ?: return null
        return MergeExplain(
            available = true,
            source = source,
            oursLabel = oursLabel,
            theirsLabel = theirsLabel,
            base = baseGrid,
            ours = oursGrid,
            theirs = theirsGrid,
        )
    }

    private suspend fun label(sha: String): String =
        read("name-rev", "--name-only", "--refs=refs/heads/*", sha)?.trim()
            ?.takeIf { it.isNotEmpty() && it != "undefined" } ?: sha.take(7)

    private suspend fun grid(rev: String): String? = read("show", "$rev:${WorldPaths.GRID_FILENAME}")

    private suspend fun revParse(vararg args: String): String? =
        read("rev-parse", *args)?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun read(vararg args: String): String? {
        val result = git.runGit(*args, cwd = paths.repo, quiet = true)
        return if (result.ok) result.stdout else null
    }

    companion object {
        private val UNAVAILABLE = MergeExplain(
            available = false, source = "", oursLabel = "", theirsLabel = "", base = "", ours = "", theirs = "",
        )
    }
}
