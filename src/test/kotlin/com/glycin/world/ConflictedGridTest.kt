package com.glycin.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ConflictedGridTest {

    @Test
    fun `parse and serialize round-trip preserves markers and trailing newline`() {
        val content = "..<b|t>..\n.....\n"
        assertEquals(content, ConflictedGrid.parse(content).serialize())
    }

    @Test
    fun `set on a marker column replaces the whole marker`() {
        val grid = ConflictedGrid.parse("..<b|t>..\n")
        grid.set(0, 2, 'b')
        assertEquals("..b..\n", grid.serialize())
    }

    @Test
    fun `set on a plain column leaves neighboring markers intact`() {
        val grid = ConflictedGrid.parse(".<H|B>x<H|B>.\n")
        grid.set(0, 2, '.')
        assertEquals(".<H|B>.<H|B>.\n", grid.serialize())
    }
}
