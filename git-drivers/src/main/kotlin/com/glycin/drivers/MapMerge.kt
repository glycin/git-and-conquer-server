package com.glycin.drivers

// Three-way merge per cell. If both sides changed a cell differently, it becomes `<ours|theirs>`.
object MapMerge {

    data class Result(val lines: List<String>, val conflicts: Int) {
        val clean: Boolean get() = conflicts == 0
    }

    fun merge(base: List<String>, ours: List<String>, theirs: List<String>): Result {
        val rowCount = maxOf(base.size, ours.size, theirs.size)
        var conflicts = 0
        val lines = List(rowCount) { row ->
            val baseRow = base.getOrElse(row) { "" }
            val ourRow = ours.getOrElse(row) { "" }
            val theirRow = theirs.getOrElse(row) { "" }
            val colCount = maxOf(baseRow.length, ourRow.length, theirRow.length)
            buildString(colCount) {
                for (col in 0 until colCount) {
                    val baseTile = baseRow.getOrElse(col) { MapGrid.EMPTY }
                    val ourTile = ourRow.getOrElse(col) { MapGrid.EMPTY }
                    val theirTile = theirRow.getOrElse(col) { MapGrid.EMPTY }
                    when {
                        ourTile == baseTile -> append(theirTile)
                        theirTile == baseTile -> append(ourTile)
                        ourTile == theirTile -> append(ourTile)
                        else -> {
                            append(ConflictMarkers.render(ourTile, theirTile))
                            conflicts++
                        }
                    }
                }
            }
        }
        return Result(lines, conflicts)
    }
}
