package com.glycin.world

import com.glycin.api.model.BuildingConflict
import com.glycin.api.model.Category
import com.glycin.api.model.Cell

// Figures out which building owns each conflicted cell, on both the ours (stage 2) and theirs (stage 3) grid.
object BuildingConflicts {

    fun derive(
        conflictCells: List<Cell>,
        oursGrid: Array<CharArray>?,
        theirsGrid: Array<CharArray>?,
    ): List<BuildingConflict> =
        sideBuildings(conflictCells, oursGrid, incoming = false) +
            sideBuildings(conflictCells, theirsGrid, incoming = true)

    private fun sideBuildings(
        conflictCells: List<Cell>,
        grid: Array<CharArray>?,
        incoming: Boolean,
    ): List<BuildingConflict> {
        if (grid == null) return emptyList()
        val cellsByAnchor = linkedMapOf<Cell, MutableList<Cell>>()
        val charByAnchor = mutableMapOf<Cell, Char>()
        for (cell in conflictCells) {
            if (cell.row !in grid.indices || cell.col !in grid[cell.row].indices) continue
            val tile = grid[cell.row][cell.col]
            val anchor: Cell?
            when {
                TileLegend.byChar[tile]?.category == Category.BUILDING -> anchor = cell
                TileLegend.isClaimChar(tile) -> anchor = Placement.findAnchor(grid, cell.row, cell.col)
                else -> anchor = null
            }
            if (anchor == null) continue
            charByAnchor[anchor] = grid[anchor.row][anchor.col]
            cellsByAnchor.getOrPut(anchor) { mutableListOf() }.add(cell)
        }
        return cellsByAnchor.map { (anchor, cells) ->
            val char = charByAnchor.getValue(anchor)
            val entry = TileLegend.byChar[char]
            BuildingConflict(
                row = anchor.row,
                col = anchor.col,
                char = char,
                fpCols = entry?.fpCols ?: 1,
                fpRows = entry?.fpRows ?: 1,
                incoming = incoming,
                conflictCells = cells,
            )
        }
    }
}
