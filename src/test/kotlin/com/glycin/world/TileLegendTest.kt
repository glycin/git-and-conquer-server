package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.Cell
import com.glycin.drivers.ConflictMarkers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TileLegendTest {

    @Test
    fun `every char is unique`() {
        val chars = TileLegend.entries.map { it.char }
        assertEquals(chars.size, chars.toSet().size)
    }

    @Test
    fun `every type is unique`() {
        val types = TileLegend.entries.map { it.type }
        assertEquals(types.size, types.toSet().size)
    }

    @Test
    fun `no entry uses a reserved char`() {
        TileLegend.entries.forEach {
            assertTrue(it.char !in TileLegend.reservedChars, "char '${it.char}' is reserved")
        }
    }

    @Test
    fun `reserved chars cover empty tile and conflict markers`() {
        assertEquals(
            setOf(GridFile.EMPTY, ConflictMarkers.OPEN, ConflictMarkers.SEPARATOR, ConflictMarkers.CLOSE),
            TileLegend.reservedChars,
        )
    }

    @Test
    fun `deposits are copper gold on c g`() {
        val byType = TileLegend.deposits.associate { it.type to it.char }
        assertEquals(mapOf("copper" to 'c', "gold" to 'g'), byType)
        TileLegend.deposits.forEach { assertEquals(Category.DEPOSIT, it.category) }
    }

    @Test
    fun `forType finds entries and returns null for unknown types`() {
        assertEquals('t', TileLegend.forType("silicon_tree")?.char)
        assertEquals('b', TileLegend.forType("barracks")?.char)
        assertNull(TileLegend.forType("unobtainium"))
    }

    @Test
    fun `validatePlacement accepts a known char with its own category`() {
        assertNull(TileLegend.validatePlacement('c', Category.DEPOSIT))
        assertNull(TileLegend.validatePlacement('b', Category.BUILDING))
        assertNull(TileLegend.validatePlacement('t', Category.TREE))
    }

    @Test
    fun `validatePlacement accepts unknown lowercase chars`() {
        assertNull(TileLegend.validatePlacement('x', Category.BUILDING))
    }

    @Test
    fun `validatePlacement rejects uppercase chars as claim namespace`() {
        assertNotNull(TileLegend.validatePlacement('H', Category.BUILDING))
        assertNotNull(TileLegend.validatePlacement('Z', Category.DEPOSIT))
    }

    @Test
    fun `footprints mirror the client tile registry`() {
        assertEquals(4 to 4, TileLegend.byChar['h']!!.let { it.fpCols to it.fpRows })
        assertEquals(3 to 3, TileLegend.byChar['k']!!.let { it.fpCols to it.fpRows })
        for (ch in listOf('b', 'e', 'w', 's', 'a')) {
            assertEquals(2 to 2, TileLegend.byChar[ch]!!.let { it.fpCols to it.fpRows }, "char '$ch'")
        }
        for (ch in listOf('f', 'o', 'm', 't', 'r', 'c', 'g')) {
            assertEquals(1 to 1, TileLegend.byChar[ch]!!.let { it.fpCols to it.fpRows }, "char '$ch'")
        }
    }

    @Test
    fun `claim chars are recognized only for multi-tile entries`() {
        assertTrue(TileLegend.isClaimChar('H'))
        assertTrue(TileLegend.isClaimChar('B'))
        assertFalse(TileLegend.isClaimChar('C'), "deposits are single-tile")
        assertFalse(TileLegend.isClaimChar('h'), "anchors are not claims")
        assertFalse(TileLegend.isClaimChar('Z'), "unknown chars are not claims")
    }

    @Test
    fun `footprintCells enumerates the full rectangle from the anchor`() {
        val cells = TileLegend.footprintCells(10, 20, 'b')
        assertEquals(
            setOf(Cell(10, 20), Cell(10, 21), Cell(11, 20), Cell(11, 21)),
            cells.toSet(),
        )
        assertEquals(listOf(Cell(5, 5)), TileLegend.footprintCells(5, 5, 'c'))
        assertEquals(listOf(Cell(5, 5)), TileLegend.footprintCells(5, 5, 'x'))
    }

    @Test
    fun `validatePlacement rejects reserved chars`() {
        TileLegend.reservedChars.forEach {
            assertNotNull(TileLegend.validatePlacement(it, Category.BUILDING), "char '$it' should be rejected")
        }
    }

    @Test
    fun `validatePlacement rejects a known char with the wrong category`() {
        assertNotNull(TileLegend.validatePlacement('c', Category.BUILDING))
        assertNotNull(TileLegend.validatePlacement('b', Category.DEPOSIT))
    }
}
