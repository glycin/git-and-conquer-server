package com.glycin.drivers

// The `<ours|theirs>` cell marker, shared by the merge driver and the server.
object ConflictMarkers {
    const val OPEN = '<'
    const val SEPARATOR = '|'
    const val CLOSE = '>'
    const val WIDTH = 5

    data class Marker(val col: Int, val ours: Char, val theirs: Char, val offset: Int)

    fun render(ours: Char, theirs: Char): String = "$OPEN$ours$SEPARATOR$theirs$CLOSE"

    fun isMarkerAt(line: String, offset: Int): Boolean =
        offset + WIDTH - 1 < line.length &&
            line[offset] == OPEN && line[offset + 2] == SEPARATOR && line[offset + 4] == CLOSE

    inline fun forEachMarker(line: String, action: (Marker) -> Unit) {
        var col = 0
        var offset = 0
        while (offset < line.length) {
            if (isMarkerAt(line, offset)) {
                action(Marker(col, line[offset + 1], line[offset + 3], offset))
                offset += WIDTH
            } else {
                offset += 1
            }
            col += 1
        }
    }

    fun containsMarker(content: String): Boolean =
        content.indices.any { isMarkerAt(content, it) }
}
