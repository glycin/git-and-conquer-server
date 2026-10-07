package com.glycin.world

import com.glycin.api.model.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildingConflictsTest {

    private fun emptyGrid(): Array<CharArray> =
        Array(GridFile.SIZE) { CharArray(GridFile.SIZE) { GridFile.EMPTY } }

    private fun place(cells: Array<CharArray>, row: Int, col: Int, char: Char) {
        Placement.footprintWrites(row, col, char).forEach { (cell, c) -> cells[cell.row][cell.col] = c }
    }

    @Test
    fun `overlapping footprints yield one defender and one incoming building`() {
        val ours = emptyGrid()
        place(ours, 10, 10, 'h')
        val theirs = emptyGrid()
        place(theirs, 12, 12, 'b')

        val conflictCells = listOf(Cell(12, 12), Cell(12, 13), Cell(13, 12), Cell(13, 13))
        val result = BuildingConflicts.derive(conflictCells, ours, theirs)

        assertEquals(2, result.size)
        val defender = result.single { !it.incoming }
        assertEquals(Triple(10, 10, 'h'), Triple(defender.row, defender.col, defender.char))
        assertEquals(4 to 4, defender.fpCols to defender.fpRows)
        assertEquals(conflictCells.toSet(), defender.conflictCells.toSet())

        val attacker = result.single { it.incoming }
        assertEquals(Triple(12, 12, 'b'), Triple(attacker.row, attacker.col, attacker.char))
        assertEquals(conflictCells.toSet(), attacker.conflictCells.toSet())
    }

    @Test
    fun `same-anchor rivals produce one building per side`() {
        val ours = emptyGrid()
        place(ours, 20, 20, 'b')
        val theirs = emptyGrid()
        place(theirs, 20, 20, 'e')

        val conflictCells = listOf(Cell(20, 20), Cell(20, 21), Cell(21, 20), Cell(21, 21))
        val result = BuildingConflicts.derive(conflictCells, ours, theirs)

        assertEquals(2, result.size)
        assertEquals('b', result.single { !it.incoming }.char)
        assertEquals('e', result.single { it.incoming }.char)
    }

    @Test
    fun `scenery against a building yields only the building side`() {
        val ours = emptyGrid()
        ours[30][30] = 't'
        val theirs = emptyGrid()
        place(theirs, 30, 30, 'm')

        val result = BuildingConflicts.derive(listOf(Cell(30, 30)), ours, theirs)

        assertEquals(1, result.size)
        assertTrue(result.single().incoming)
        assertEquals('m', result.single().char)
    }

    @Test
    fun `missing stage grids yield no buildings`() {
        assertTrue(BuildingConflicts.derive(listOf(Cell(1, 1)), null, null).isEmpty())
    }
}
