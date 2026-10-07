package com.glycin.drivers

// One line per changed cell: `+` built, `-` cleared, `~` replaced.
object MapDiff {

    private const val ABSENT = ' '

    fun render(old: List<String>, new: List<String>, path: String, color: Boolean): String {
        val palette = if (color) Ansi else Plain
        val body = StringBuilder()
        var changedCells = 0
        val rowCount = maxOf(old.size, new.size)
        for (row in 0 until rowCount) {
            val oldRow = old.getOrElse(row) { "" }
            val newRow = new.getOrElse(row) { "" }
            val colCount = maxOf(oldRow.length, newRow.length)
            for (col in 0 until colCount) {
                val oldTile = oldRow.getOrElse(col) { ABSENT }
                val newTile = newRow.getOrElse(col) { ABSENT }
                if (oldTile == newTile) continue
                val style = when {
                    oldTile == MapGrid.EMPTY -> ChangeStyle('+', palette.green, palette.dim, palette.green)
                    newTile == MapGrid.EMPTY -> ChangeStyle('-', palette.red, palette.red, palette.dim)
                    else -> ChangeStyle('~', palette.yellow, palette.red, palette.green)
                }
                body.append("  ").append(style.symbolColor).append(style.symbol).append(palette.reset)
                    .append(" (row ").append(row.toString().padStart(3))
                    .append(", col ").append(col.toString().padStart(3)).append(")  ")
                    .append(style.oldColor).append('\'').append(oldTile).append('\'').append(palette.reset)
                    .append("  ").append(palette.dim).append("->").append(palette.reset).append("  ")
                    .append(style.newColor).append('\'').append(newTile).append('\'').append(palette.reset)
                    .append('\n')
                changedCells++
            }
        }
        val summary = if (changedCells == 1) "1 cell changed" else "$changedCells cells changed"
        return "${palette.bold}${palette.cyan}== $path : $summary ==${palette.reset}\n$body"
    }

    private class ChangeStyle(val symbol: Char, val symbolColor: String, val oldColor: String, val newColor: String)

    private open class Palette(
        val reset: String, val dim: String, val bold: String,
        val red: String, val green: String, val yellow: String, val cyan: String,
    )

    private object Plain : Palette("", "", "", "", "", "", "")

    private object Ansi : Palette(
        reset = "\u001B[0m", dim = "\u001B[2m", bold = "\u001B[1m",
        red = "\u001B[31m", green = "\u001B[32m", yellow = "\u001B[33m", cyan = "\u001B[36m",
    )
}
