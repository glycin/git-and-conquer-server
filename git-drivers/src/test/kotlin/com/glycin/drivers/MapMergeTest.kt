package com.glycin.drivers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapMergeTest {

    private val base = listOf("....", "....", "....")

    @Test
    fun `untouched cells keep the base`() {
        val result = MapMerge.merge(base, base, base)
        assertEquals(base, result.lines)
        assertTrue(result.clean)
    }

    @Test
    fun `each side wins the cells only it changed`() {
        val ours = listOf("b...", "....", "....")
        val theirs = listOf("....", "..t.", "....")
        val result = MapMerge.merge(base, ours, theirs)
        assertEquals(listOf("b...", "..t.", "...."), result.lines)
        assertTrue(result.clean)
    }

    @Test
    fun `same change on both sides is clean`() {
        val both = listOf("....", ".r..", "....")
        val result = MapMerge.merge(base, both, both)
        assertEquals(both, result.lines)
        assertEquals(0, result.conflicts)
    }

    @Test
    fun `different changes to one cell become a marker`() {
        val ours = listOf("....", ".b..", "....")
        val theirs = listOf("....", ".t..", "....")
        val result = MapMerge.merge(base, ours, theirs)
        assertEquals(listOf("....", ".<b|t>..", "...."), result.lines)
        assertEquals(1, result.conflicts)
    }

    @Test
    fun `a side clearing a cell the other built conflicts`() {
        val built = listOf("....", "....", "..b.")
        val ours = listOf("....", "....", "....")
        val theirs = listOf("....", "....", "..t.")
        val result = MapMerge.merge(built, ours, theirs)
        assertEquals("..<.|t>.", result.lines[2])
        assertEquals(1, result.conflicts)
    }

    @Test
    fun `missing rows and columns read as empty cells`() {
        val ours = listOf("..b")
        val theirs = listOf("....", ".t")
        val result = MapMerge.merge(emptyList(), ours, theirs)
        assertEquals(listOf("..b.", ".t"), result.lines)
        assertTrue(result.clean)
    }

    @Test
    fun `conflicts are counted per cell`() {
        val ours = listOf("bb..", "....", "....")
        val theirs = listOf("tt..", "....", "....")
        assertEquals(2, MapMerge.merge(base, ours, theirs).conflicts)
    }
}
