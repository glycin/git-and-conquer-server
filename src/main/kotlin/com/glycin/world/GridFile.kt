package com.glycin.world

import com.glycin.api.model.Cell
import com.glycin.drivers.ConflictMarkers
import com.glycin.drivers.MapGrid
import jakarta.enterprise.context.ApplicationScoped
import java.nio.file.Files

@ApplicationScoped
class GridFile(private val paths: WorldPaths) {

    fun raw(): String = Files.readString(paths.grid)

    fun readCells(): Array<CharArray> {
        val lines = Files.readAllLines(paths.grid)
        check(lines.size == SIZE) { "Expected $SIZE rows, got ${lines.size}" }
        return Array(SIZE) { row ->
            val line = lines[row]
            check(line.length == SIZE) { "Row $row has ${line.length} cols, expected $SIZE" }
            line.toCharArray()
        }
    }

    fun writeCell(row: Int, col: Int, char: Char) {
        writeCells(listOf(Cell(row, col) to char))
    }

    fun writeCells(changes: List<Pair<Cell, Char>>) {
        changes.forEach { (cell, _) ->
            require(cell.row in 0 until SIZE) { "row ${cell.row} out of range [0, $SIZE)" }
            require(cell.col in 0 until SIZE) { "col ${cell.col} out of range [0, $SIZE)" }
        }
        val cells = readCells()
        changes.forEach { (cell, char) -> cells[cell.row][cell.col] = char }
        write(cells)
    }

    fun hasConflictMarkers(): Boolean = ConflictMarkers.containsMarker(raw())

    fun write(cells: Array<CharArray>) {
        Files.writeString(paths.grid, render(cells))
    }

    companion object {
        const val SIZE = 128
        const val EMPTY = MapGrid.EMPTY

        fun render(cells: Array<CharArray>): String = buildString(SIZE * (SIZE + 1)) {
            for (row in 0 until SIZE) {
                append(cells[row])
                append('\n')
            }
        }

        // null if this isn't a full grid (conflict markers make rows wider, a missing blob is empty).
        fun parse(text: String): Array<CharArray>? {
            val lines = text.split('\n')
                .map { it.trimEnd('\r') }
                .filter { it.isNotEmpty() }
            if (lines.size != SIZE || lines.any { it.length != SIZE }) return null
            return Array(SIZE) { lines[it].toCharArray() }
        }
    }
}
