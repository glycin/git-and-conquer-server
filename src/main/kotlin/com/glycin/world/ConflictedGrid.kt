package com.glycin.world

import com.glycin.drivers.ConflictMarkers

// map.grid split into cells, where a whole `<x|y>` marker counts as one cell.
class ConflictedGrid private constructor(private val rows: List<MutableList<String>>) {

    fun set(row: Int, col: Int, char: Char) {
        rows[row][col] = char.toString()
    }

    fun serialize(): String = rows.joinToString("\n") { it.joinToString("") }

    companion object {
        fun parse(content: String): ConflictedGrid {
            val rows = content.split('\n').map { line ->
                val tokens = mutableListOf<String>()
                var pos = 0
                while (pos < line.length) {
                    if (ConflictMarkers.isMarkerAt(line, pos)) {
                        tokens.add(line.substring(pos, pos + ConflictMarkers.WIDTH))
                        pos += ConflictMarkers.WIDTH
                    } else {
                        tokens.add(line[pos].toString())
                        pos += 1
                    }
                }
                tokens
            }
            return ConflictedGrid(rows)
        }
    }
}
