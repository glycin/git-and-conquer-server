package com.glycin.world

import com.glycin.api.model.Cell
import com.glycin.api.model.CellState
import com.glycin.api.model.GitState
import com.glycin.git.GitRunner
import jakarta.enterprise.context.ApplicationScoped

// Staging one building of map.grid by hand: put its cells on top of the index version,
// `hash-object -w` that and point the index at it with `update-index`.
@ApplicationScoped
class Staging(
    private val paths: WorldPaths,
    private val gridFile: GridFile,
    private val git: GitRunner,
) {

    suspend fun states(): List<CellState> {
        val states = HashMap<Cell, GitState>()
        var gridDirty = false
        val status = git.runGit("status", "--porcelain", "--no-renames", "-uall", cwd = paths.repo).stdout
        for (line in status.lineSequence()) {
            if (line.length < 4) continue
            val indexSide = line[0]
            val workingSide = line[1]
            val path = line.substring(3)
            if (path == WorldPaths.GRID_FILENAME) {
                gridDirty = true
                continue
            }
            val cell = WorldPaths.parseObjectPath(path) ?: continue
            if (indexSide == 'D' || workingSide == 'D') continue
            val state = if (workingSide != ' ') GitState.WORKING_TREE else GitState.STAGED
            states.merge(cell, state, ::leastCommitted)
        }

        if (gridDirty) {
            val working = GridFile.parse(gridFile.raw())
            val index = readGrid(":${WorldPaths.GRID_FILENAME}")
            val head = readGrid("HEAD:${WorldPaths.GRID_FILENAME}")
            if (working != null && index != null && head != null) {
                for (row in 0 until GridFile.SIZE) for (col in 0 until GridFile.SIZE) {
                    val char = working[row][col]
                    if (char == GridFile.EMPTY || TileLegend.isClaimChar(char)) continue
                    val state = when {
                        char != index[row][col] -> GitState.WORKING_TREE
                        index[row][col] != head[row][col] -> GitState.STAGED
                        else -> continue
                    }
                    states.merge(Cell(row, col), state, ::leastCommitted)
                }
            }
        }

        return states.entries
            .sortedWith(compareBy({ it.key.row }, { it.key.col }))
            .map { CellState(it.key.row, it.key.col, it.value) }
    }

    // Returns null if it worked, otherwise why it was refused.
    suspend fun stage(cell: Cell?): String? {
        if (cell == null) {
            git.runGit("add", "-A", cwd = paths.repo)
            return null
        }
        return restage(cell, toWorking = true)
    }

    suspend fun stageQuietly(cell: Cell, env: Map<String, String> = emptyMap()): String? =
        restage(cell, toWorking = true, env = env, echo = false)

    suspend fun unstage(cell: Cell?): String? {
        if (cell == null) {
            git.runGit("reset", "-q", cwd = paths.repo)
            return null
        }
        return restage(cell, toWorking = false)
    }

    private suspend fun restage(
        cell: Cell,
        toWorking: Boolean,
        env: Map<String, String> = emptyMap(),
        echo: Boolean = true,
    ): String? {
        if (cell.row !in 0 until GridFile.SIZE || cell.col !in 0 until GridFile.SIZE) {
            return "(${cell.row}, ${cell.col}) is outside the map"
        }
        val working = gridFile.readCells()
        val index = readGrid(":${WorldPaths.GRID_FILENAME}", env)
            ?: return "the index grid is unreadable"
        val head = readGrid("HEAD:${WorldPaths.GRID_FILENAME}", env)
            ?: return "HEAD has no grid"

        val anchor = listOf(working, index, head).firstNotNullOfOrNull { anchorIn(it, cell) }
            ?: return "nothing at (${cell.row}, ${cell.col})"
        val cells = HashSet<Cell>()
        for (grid in listOf(working, index, head)) cells += footprintIn(grid, anchor)

        val source = if (toWorking) working else head
        val updated = Array(GridFile.SIZE) { index[it].copyOf() }
        var changed = false
        for (cell in cells) {
            if (updated[cell.row][cell.col] != source[cell.row][cell.col]) {
                updated[cell.row][cell.col] = source[cell.row][cell.col]
                changed = true
            }
        }
        if (changed) writeIndexGrid(updated, env, echo)

        val objectPath = WorldPaths.objectRelativePath(anchor.row, anchor.col)
        if (toWorking) git.runGit("add", "--", objectPath, cwd = paths.repo, quiet = true, env = env, echo = echo)
        else git.runGit("reset", "-q", "--", objectPath, cwd = paths.repo, quiet = true, env = env, echo = echo)
        return null
    }

    private fun anchorIn(grid: Array<CharArray>, cell: Cell): Cell? {
        val char = grid[cell.row][cell.col]
        return when {
            char == GridFile.EMPTY -> null
            TileLegend.isClaimChar(char) -> Placement.findAnchor(grid, cell.row, cell.col)
            else -> cell
        }
    }

    private fun footprintIn(grid: Array<CharArray>, anchor: Cell): List<Cell> {
        val char = grid[anchor.row][anchor.col]
        if (char == GridFile.EMPTY || TileLegend.isClaimChar(char)) return listOf(anchor)
        val claim = TileLegend.claimChar(char)
        return TileLegend.footprintCells(anchor.row, anchor.col, char)
            .filter { it.row in 0 until GridFile.SIZE && it.col in 0 until GridFile.SIZE }
            .filter { it == anchor || grid[it.row][it.col] == claim }
    }

    private suspend fun readGrid(spec: String, env: Map<String, String> = emptyMap()): Array<CharArray>? {
        val result = git.runGit("show", spec, cwd = paths.repo, quiet = true, env = env)
        if (!result.ok) return null
        return GridFile.parse(result.stdout)
    }

    private suspend fun writeIndexGrid(cells: Array<CharArray>, env: Map<String, String>, echo: Boolean) {
        val sha = git.runGit(
            "hash-object", "-w", "--stdin", cwd = paths.repo, input = GridFile.render(cells), echo = echo,
        ).stdoutTrimmed()
        git.runGit(
            "update-index", "--add", "--cacheinfo", "100644,$sha,${WorldPaths.GRID_FILENAME}",
            cwd = paths.repo,
            env = env,
            echo = echo,
        )
    }

    private fun leastCommitted(a: GitState, b: GitState): GitState =
        if (a == GitState.WORKING_TREE || b == GitState.WORKING_TREE) GitState.WORKING_TREE else GitState.STAGED
}
