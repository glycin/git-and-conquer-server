package com.glycin.drivers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapDiffTest {

    @Test
    fun `lists every changed cell with its kind of change`() {
        val old = listOf("....", ".b..", "..r.")
        val new = listOf("t...", "....", "..b.")
        val out = MapDiff.render(old, new, "map.grid", color = false)
        assertEquals(
            """
            == map.grid : 3 cells changed ==
              + (row   0, col   0)  '.'  ->  't'
              - (row   1, col   1)  'b'  ->  '.'
              ~ (row   2, col   2)  'r'  ->  'b'

            """.trimIndent(),
            out,
        )
    }

    @Test
    fun `a single change is labelled in the singular`() {
        val out = MapDiff.render(listOf("."), listOf("b"), "map.grid", color = false)
        assertTrue(out.startsWith("== map.grid : 1 cell changed =="))
    }

    @Test
    fun `color mode wraps the header in ansi codes`() {
        val esc = 27.toChar()
        val out = MapDiff.render(listOf("."), listOf("."), "map.grid", color = true)
        assertTrue(out.startsWith("$esc[1m$esc[36m== map.grid : 0 cells changed ==$esc[0m\n"))
    }
}
