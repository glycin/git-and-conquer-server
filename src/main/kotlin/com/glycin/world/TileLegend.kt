package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.Cell
import com.glycin.drivers.ConflictMarkers

object TileLegend {

    data class Entry(
        val char: Char,
        val category: Category,
        val type: String,
        val fpCols: Int = 1,
        val fpRows: Int = 1,
        val landmark: Boolean = false,
    ) {
        val multiTile: Boolean get() = fpCols > 1 || fpRows > 1
    }

    // Same footprints as the client's TileRegistry. Change one, change the other.
    val entries: List<Entry> = listOf(
        Entry('b', Category.BUILDING, "barracks", fpCols = 2, fpRows = 2),
        Entry('e', Category.BUILDING, "token_generator", fpCols = 2, fpRows = 2),
        Entry('w', Category.BUILDING, "war_factory", fpCols = 2, fpRows = 2),
        Entry('s', Category.BUILDING, "scrum_church", fpCols = 2, fpRows = 2),
        Entry('a', Category.BUILDING, "conference_centre", fpCols = 2, fpRows = 2),
        Entry('h', Category.BUILDING, "command_center", fpCols = 4, fpRows = 4),
        Entry('k', Category.BUILDING, "castle", fpCols = 3, fpRows = 3),
        Entry('u', Category.BUILDING, "tabularium", fpCols = 6, fpRows = 6, landmark = true),
        Entry('f', Category.BUILDING, "silicon_refinery"),
        Entry('o', Category.BUILDING, "copper_refinery"),
        Entry('m', Category.BUILDING, "gold_refinery"),
        Entry('t', Category.TREE, "silicon_tree"),
        Entry('r', Category.ROCK, "boulder"),
        Entry('c', Category.DEPOSIT, "copper"),
        Entry('g', Category.DEPOSIT, "gold"),
    )

    val byChar: Map<Char, Entry> = entries.associateBy { it.char }

    val deposits: List<Entry> = entries.filter { it.category == Category.DEPOSIT }

    val tabularium: Entry = entries.first { it.type == "tabularium" }

    val reservedChars: Set<Char> = setOf(
        GridFile.EMPTY,
        ConflictMarkers.OPEN,
        ConflictMarkers.SEPARATOR,
        ConflictMarkers.CLOSE,
    )

    fun claimChar(char: Char): Char = char.uppercaseChar()

    fun isClaimChar(c: Char): Boolean =
        c.isUpperCase() && byChar[c.lowercaseChar()]?.multiTile == true

    fun footprintCells(row: Int, col: Int, char: Char): List<Cell> {
        val entry = byChar[char] ?: return listOf(Cell(row, col))
        return buildList {
            for (dr in 0 until entry.fpRows) {
                for (dc in 0 until entry.fpCols) {
                    add(Cell(row + dr, col + dc))
                }
            }
        }
    }

    fun forType(type: String): Entry? = entries.firstOrNull { it.type == type }

    fun validatePlacement(char: Char, category: Category): String? {
        if (char in reservedChars) return "char '$char' is reserved"
        if (char.isUpperCase()) return "char '$char' is reserved for footprint claims"
        val known = byChar[char] ?: return null
        if (known.landmark) return "'${known.type}' is a landmark seeded by the world generator"
        if (known.category != category) {
            return "char '$char' is ${known.category} (${known.type}), not $category"
        }
        return null
    }
}
