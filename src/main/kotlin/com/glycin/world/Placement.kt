package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.Cell

object Placement {

    fun validate(cells: Array<CharArray>, row: Int, col: Int, char: Char, category: Category): String? {
        TileLegend.validatePlacement(char, category)?.let { return it }
        for (cell in TileLegend.footprintCells(row, col, char)) {
            if (cell.row !in cells.indices || cell.col !in cells[cell.row].indices) {
                return "footprint of '$char' at ($row, $col) leaves the map at (${cell.row}, ${cell.col})"
            }
            val occupant = cells[cell.row][cell.col]
            if (occupant != GridFile.EMPTY) {
                return "cell (${cell.row}, ${cell.col}) is occupied by '$occupant'"
            }
        }
        return null
    }

    fun footprintWrites(row: Int, col: Int, char: Char): List<Pair<Cell, Char>> =
        TileLegend.footprintCells(row, col, char).map { cell ->
            cell to if (cell.row == row && cell.col == col) char else TileLegend.claimChar(char)
        }

    // Buildings can't overlap on a valid grid, so the first match wins.
    fun findAnchor(cells: Array<CharArray>, row: Int, col: Int): Cell? {
        val claim = cells[row][col]
        val entry = TileLegend.byChar[claim.lowercaseChar()] ?: return null
        for (rowOffset in 0 until entry.fpRows) {
            for (colOffset in 0 until entry.fpCols) {
                val anchorRow = row - rowOffset
                val anchorCol = col - colOffset
                if (anchorRow < 0 || anchorCol < 0) continue
                if (cells[anchorRow][anchorCol] == entry.char) return Cell(anchorRow, anchorCol)
            }
        }
        return null
    }
}
