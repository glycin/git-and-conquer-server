package com.glycin.api.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Cell(val row: Int, val col: Int)

@Serializable
enum class Category { BUILDING, TREE, ROCK, DEPOSIT }

@Serializable
data class MapObject(
    val row: Int,
    val col: Int,
    val category: Category,
    val type: String,
    val char: Char,
    val faction: String? = null,
    val rotation: Float = 0f,
    val fpCols: Int = 1,
    val fpRows: Int = 1,
    val metadata: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class WalletState(
    val silicon: Int = 0,
    val copper: Int = 0,
    val gold: Int = 0,
    val tokens: Int = 0,
)

// COMMITTED anchors are left out of the snapshot's `uncommitted` list.
@Serializable
enum class GitState { WORKING_TREE, STAGED, COMMITTED }

@Serializable
data class CellState(val row: Int, val col: Int, val state: GitState)

// Tags are repo-wide, so a claim holds on every timeline.
@Serializable
data class WonderClaim(
    val tag: String,
    val faction: String,
    val taggedAt: Long,
    val expiresAt: Long,
    val active: Boolean,
)

@Serializable
data class WonderState(
    val row: Int,
    val col: Int,
    val claim: WonderClaim? = null,
    val claimsByPlayer: Int = 0,
)

@Serializable
data class MapSnapshot(
    val head: String,
    val branch: String,
    val baseCommit: String = "",
    val width: Int,
    val height: Int,
    val grid: String,
    val wallet: WalletState,
    val objects: List<MapObject> = emptyList(),
    val uncommitted: List<CellState> = emptyList(),
    val wonder: WonderState? = null,
)

@Serializable
data class Delta(
    val added: List<MapObject> = emptyList(),
    val removed: List<Cell> = emptyList(),
    val modified: List<MapObject> = emptyList(),
    val newHead: String,
    val newBranch: String,
)

// fingerprint lets the client notice things like an external `git add`, which never fires a WebSocket event.
@Serializable
data class RepoStatus(
    val branch: String,
    val head: String,
    val dirty: Boolean,
    val staged: Boolean = false,
    val unstaged: Boolean = false,
    val fingerprint: String = "",
    val operation: String? = null,
    val replay: ReplayStep? = null,
)

@Serializable
data class ReplayStep(
    val step: Int,
    val total: Int,
    val sha: String = "",
    val subject: String = "",
    val onto: String = "",
    val branch: String = "",
    val paused: Boolean = false,
)

@Serializable
data class PlaceRequest(
    val row: Int,
    val col: Int,
    val char: Char,
    val category: Category,
    val type: String,
    val faction: String? = null,
    val rotation: Float = 0f,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val commitAs: String? = null,
)

@Serializable
data class DestroyRequest(
    val row: Int,
    val col: Int,
)

@Serializable
data class ModifyRequest(
    val row: Int,
    val col: Int,
    val patch: Map<String, JsonElement>,
)

// No cell means everything (`git add -A` / `git reset`).
@Serializable
data class StageRequest(
    val row: Int? = null,
    val col: Int? = null,
)

@Serializable
data class CommitRequest(
    val message: String? = null,
    val wallet: WalletState? = null,
)

@Serializable
data class TagWonderRequest(val faction: String)

@Serializable
data class StartMatchRequest(
    val adversary: String,
    val adversaryName: String? = null,
)

// conflict is only set when a git op stops on conflicts.
@Serializable
data class OpResult(
    val ok: Boolean,
    val commitSha: String? = null,
    val snapshot: MapSnapshot,
    val conflict: ConflictInfo? = null,
    val error: String? = null,
) {
    companion object {
        fun success(snapshot: MapSnapshot, commitSha: String? = null): OpResult =
            OpResult(ok = true, commitSha = commitSha, snapshot = snapshot)

        fun failure(snapshot: MapSnapshot, message: String): OpResult =
            OpResult(ok = false, snapshot = snapshot, error = message)

        fun conflict(snapshot: MapSnapshot, operation: String, paths: List<String>): OpResult =
            OpResult(ok = false, snapshot = snapshot, conflict = ConflictInfo(operation, paths))
    }
}

// Sizes are in KiB, straight from `git count-objects -v`.
@Serializable
data class ObjectStats(
    val loose: Long = 0,
    val looseKb: Long = 0,
    val packs: Long = 0,
    val packed: Long = 0,
    val packedKb: Long = 0,
)

@Serializable
data class GcResult(
    val ok: Boolean,
    val error: String? = null,
    val before: ObjectStats,
    val after: ObjectStats,
)

@Serializable
data class RepoPaths(val repo: String, val gitDir: String)

@Serializable
enum class ResetMode { SOFT, MIXED, HARD }

@Serializable
data class BranchRequest(val name: String, val fromRef: String? = null)

@Serializable
data class CheckoutRequest(val ref: String)

@Serializable
data class RevertRequest(val sha: String)

@Serializable
data class CherryPickRequest(val sha: String)

@Serializable
enum class FfMode { AUTO, ONLY, NEVER }

@Serializable
data class MergeRequest(val ref: String, val strategy: String? = null, val ff: FfMode = FfMode.AUTO)

@Serializable
data class RebaseRequest(
    val ontoRef: String,
    val upstream: String? = null,
    val branch: String? = null,
    val stepwise: Boolean = false,
)

@Serializable
data class ResetRequest(val ref: String, val mode: ResetMode = ResetMode.MIXED)

@Serializable
data class TagRequest(val name: String, val ref: String? = null)

@Serializable
data class ConflictInfo(
    val operation: String,
    val conflictedPaths: List<String> = emptyList(),
)

@Serializable
data class CommitInfo(
    val sha: String,
    val shortSha: String,
    val parents: List<String>,
    val author: String,
    val message: String,
    val timestamp: Long,
)

@Serializable
data class LogResponse(val entries: List<CommitInfo>)

@Serializable
data class GraphBranch(val name: String, val head: String, val baseCommit: String = "")

@Serializable
data class GraphResponse(
    val current: String,
    val head: String,
    val detached: Boolean,
    val commits: List<CommitInfo>,
    val branches: List<GraphBranch>,
)

@Serializable
data class BranchInfo(
    val name: String,
    val head: String,
    val ahead: Int,
    val behind: Int,
)

@Serializable
data class BranchesResponse(
    val current: String,
    val branches: List<BranchInfo>,
)

@Serializable
enum class Choice { OURS, THEIRS, CUSTOM }

@Serializable
data class CellConflict(
    val row: Int,
    val col: Int,
    val ours: Char,
    val theirs: Char,
    val base: Char,
)

@Serializable
data class ObjectConflict(
    val row: Int,
    val col: Int,
    // null = not there on that side
    val ours: MapObject? = null,
    val theirs: MapObject? = null,
    val base: MapObject? = null,
)

// incoming = the rival coming in from theirs.
@Serializable
data class BuildingConflict(
    val row: Int,
    val col: Int,
    val char: Char,
    val fpCols: Int = 1,
    val fpRows: Int = 1,
    val incoming: Boolean,
    val conflictCells: List<Cell> = emptyList(),
)

// Careful, rebase flips them: ours is the branch you rebase onto, theirs is the one being replayed.
@Serializable
data class ConflictSides(val ours: String, val theirs: String)

@Serializable
data class ConflictsResponse(
    val operation: String,  // "merge" | "cherry-pick" | "rebase" | "revert" | "none"
    val cells: List<CellConflict> = emptyList(),
    val objects: List<ObjectConflict> = emptyList(),
    val buildings: List<BuildingConflict> = emptyList(),
    val sides: ConflictSides? = null,
) {
    val total: Int get() = cells.size + objects.size
}

@Serializable
data class ResolveCellRequest(
    val row: Int,
    val col: Int,
    val choice: Choice,
    val customChar: Char? = null,
)

@Serializable
data class ResolveObjectRequest(
    val row: Int,
    val col: Int,
    val choice: Choice,
    val customObject: MapObject? = null,  // also send null with choice=CUSTOM to delete the object
)

// Keeps the building at (row, col) and wipes out its rivals completely.
@Serializable
data class ResolveBuildingRequest(
    val row: Int,
    val col: Int,
    val choice: Choice,  // OURS or THEIRS, CUSTOM isn't supported here
)

@Serializable
data class TreeEntry(val name: String, val type: String, val sha: String)

@Serializable
data class CommitNode(
    val sha: String,
    val tree: String,
    val message: String,
    val root: List<TreeEntry>,
    val objects: List<TreeEntry>,
)

@Serializable
data class MerkleExplain(
    val available: Boolean,
    val head: CommitNode?,
    val hasParent: Boolean,
    val parent: CommitNode?,
)

@Serializable
data class MergeExplain(
    val available: Boolean,
    val source: String,
    val oursLabel: String,
    val theirsLabel: String,
    val base: String,
    val ours: String,
    val theirs: String,
)
