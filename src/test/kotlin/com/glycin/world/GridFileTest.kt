package com.glycin.world

import com.glycin.api.model.Cell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GridFileTest {

    private lateinit var paths: WorldPaths
    private lateinit var gridFile: GridFile

    @BeforeEach
    fun setup(@TempDir tempDir: Path) {
        paths = WorldPaths(tempDir.toString())
        gridFile = GridFile(paths)
    }

    @Test
    fun `an empty grid is SIZE x SIZE of EMPTY`() {
        gridFile.initialize()

        val cells = gridFile.readCells()
        assertEquals(GridFile.SIZE, cells.size)
        cells.forEach { row ->
            assertEquals(GridFile.SIZE, row.size)
            row.forEach { assertEquals(GridFile.EMPTY, it) }
        }
    }

    @Test
    fun `write produces SIZE chars per row, not the array-debug format`() {
        // Regression: contentToString() used to write "[., ., ...]" and broke readCells.
        gridFile.initialize()

        val raw = gridFile.raw()
        val firstNewline = raw.indexOf('\n')
        assertEquals(GridFile.SIZE, firstNewline, "first row must be exactly SIZE chars before \\n")
        assertEquals(GridFile.SIZE * (GridFile.SIZE + 1), raw.length, "whole file must be SIZE * (SIZE + 1)")
    }

    @Test
    fun `writeCell then readCells round-trips the change`() {
        gridFile.initialize()

        gridFile.writeCell(50, 60, 'b')

        val cells = gridFile.readCells()
        assertEquals('b', cells[50][60])
        assertEquals(GridFile.EMPTY, cells[50][59])
        assertEquals(GridFile.EMPTY, cells[51][60])
        assertEquals(GridFile.EMPTY, cells[0][0])
    }

    @Test
    fun `multiple writeCell calls accumulate`() {
        gridFile.initialize()

        gridFile.writeCell(0, 0, 'a')
        gridFile.writeCell(127, 127, 'z')
        gridFile.writeCell(64, 64, 'm')

        val cells = gridFile.readCells()
        assertEquals('a', cells[0][0])
        assertEquals('m', cells[64][64])
        assertEquals('z', cells[127][127])
    }

    @Test
    fun `writeCell rejects out-of-range row`() {
        gridFile.initialize()
        assertThrows(IllegalArgumentException::class.java) { gridFile.writeCell(-1, 0, 'b') }
        assertThrows(IllegalArgumentException::class.java) { gridFile.writeCell(GridFile.SIZE, 0, 'b') }
    }

    @Test
    fun `writeCell rejects out-of-range col`() {
        gridFile.initialize()
        assertThrows(IllegalArgumentException::class.java) { gridFile.writeCell(0, -1, 'b') }
        assertThrows(IllegalArgumentException::class.java) { gridFile.writeCell(0, GridFile.SIZE, 'b') }
    }

    @Test
    fun `writeCells applies every change in one write`() {
        gridFile.initialize()

        gridFile.writeCells(
            listOf(
                Cell(10, 10) to 'h',
                Cell(10, 11) to 'H',
                Cell(11, 10) to 'H',
                Cell(11, 11) to 'H',
            ),
        )

        val cells = gridFile.readCells()
        assertEquals('h', cells[10][10])
        assertEquals('H', cells[10][11])
        assertEquals('H', cells[11][10])
        assertEquals('H', cells[11][11])
        assertEquals(GridFile.EMPTY, cells[10][12])
    }

    @Test
    fun `writeCells rejects out-of-range cells before writing anything`() {
        gridFile.initialize()

        assertThrows(IllegalArgumentException::class.java) {
            gridFile.writeCells(listOf(Cell(0, 0) to 'b', Cell(GridFile.SIZE, 0) to 'b'))
        }
        assertEquals(GridFile.EMPTY, gridFile.readCells()[0][0])
    }

    @Test
    fun `hasConflictMarkers detects a marker and stays false on clean grids`() {
        gridFile.initialize()
        assertTrue(!gridFile.hasConflictMarkers())

        val raw = gridFile.raw()
        Files.writeString(paths.grid, raw.replaceFirst(".", "<b|t>"))
        assertTrue(gridFile.hasConflictMarkers())
    }

    @Test
    fun `readCells rejects a file with the wrong number of rows`() {
        Files.writeString(paths.grid, ".".repeat(GridFile.SIZE) + "\n")

        val ex = assertThrows(IllegalStateException::class.java) { gridFile.readCells() }
        assertTrue(ex.message!!.contains("rows"), "message should mention rows: ${ex.message}")
    }

    @Test
    fun `readCells rejects a row with the wrong length`() {
        val content = buildString {
            append(".".repeat(GridFile.SIZE + 5))
            append('\n')
            repeat(GridFile.SIZE - 1) {
                append(".".repeat(GridFile.SIZE))
                append('\n')
            }
        }
        Files.writeString(paths.grid, content)

        val ex = assertThrows(IllegalStateException::class.java) { gridFile.readCells() }
        assertTrue(ex.message!!.contains("Row 0"), "message should pinpoint Row 0: ${ex.message}")
    }
}

private fun GridFile.initialize() = write(Array(GridFile.SIZE) { CharArray(GridFile.SIZE) { GridFile.EMPTY } })
