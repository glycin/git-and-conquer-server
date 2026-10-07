package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlacementTest {

    private fun emptyGrid(): Array<CharArray> =
        Array(GridFile.SIZE) { CharArray(GridFile.SIZE) { GridFile.EMPTY } }

    private fun place(cells: Array<CharArray>, row: Int, col: Int, char: Char) {
        Placement.footprintWrites(row, col, char).forEach { (cell, c) -> cells[cell.row][cell.col] = c }
    }

    @Test
    fun `accepts a multi-tile building on empty ground`() {
        assertNull(Placement.validate(emptyGrid(), 10, 10, 'h', Category.BUILDING))
    }

    @Test
    fun `rejects a footprint that leaves the map`() {
        val cells = emptyGrid()
        assertNotNull(Placement.validate(cells, 126, 10, 'h', Category.BUILDING))
        assertNotNull(Placement.validate(cells, 10, 126, 'h', Category.BUILDING))
        assertNotNull(Placement.validate(cells, -1, 10, 'b', Category.BUILDING))
        assertNull(Placement.validate(cells, 124, 124, 'h', Category.BUILDING))
    }

    @Test
    fun `rejects overlap with an existing footprint`() {
        val cells = emptyGrid()
        place(cells, 10, 10, 'h')

        val error = Placement.validate(cells, 13, 13, 'b', Category.BUILDING)
        assertNotNull(error)
        assertTrue(error!!.contains("occupied"), error)
    }

    @Test
    fun `accepts a building flush against another footprint`() {
        val cells = emptyGrid()
        place(cells, 10, 10, 'h')

        assertNull(Placement.validate(cells, 10, 14, 'b', Category.BUILDING))
        assertNull(Placement.validate(cells, 14, 10, 'b', Category.BUILDING))
    }

    @Test
    fun `footprintWrites puts the anchor char on the anchor and claims elsewhere`() {
        val writes = Placement.footprintWrites(10, 10, 'b').toMap()
        assertEquals('b', writes[Cell(10, 10)])
        assertEquals('B', writes[Cell(10, 11)])
        assertEquals('B', writes[Cell(11, 10)])
        assertEquals('B', writes[Cell(11, 11)])
        assertEquals(4, writes.size)
    }

    @Test
    fun `findAnchor resolves a claim cell to its own building not a neighbor`() {
        val cells = emptyGrid()
        place(cells, 10, 10, 'b')
        place(cells, 10, 12, 'b')

        assertEquals(Cell(10, 10), Placement.findAnchor(cells, 11, 11))
        assertEquals(Cell(10, 12), Placement.findAnchor(cells, 11, 13))
        assertEquals(Cell(10, 12), Placement.findAnchor(cells, 10, 13))
    }

    @Test
    fun `findAnchor returns null for a claim with no legend entry`() {
        val cells = emptyGrid()
        cells[5][5] = 'Z'
        assertNull(Placement.findAnchor(cells, 5, 5))
    }

    @Test
    fun `the tabularium cannot be placed by hand`() {
        val cells = emptyGrid()
        val error = Placement.validate(cells, 10, 10, 'u', Category.BUILDING)
        assertNotNull(error)
        assertTrue(error!!.contains("landmark"), error)
    }
}
