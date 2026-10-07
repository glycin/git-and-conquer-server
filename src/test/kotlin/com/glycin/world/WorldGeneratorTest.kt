package com.glycin.world

import com.glycin.api.model.Category
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorldGeneratorTest {

    private fun gridString(cells: Array<CharArray>): String =
        cells.joinToString("\n") { String(it) }

    @Test
    fun `same seed generates an identical world`() {
        val a = WorldGenerator.generate(42)
        val b = WorldGenerator.generate(42)
        assertEquals(gridString(a.cells), gridString(b.cells))
        assertEquals(a.objects, b.objects)
        assertEquals(42, a.seed)
    }

    @Test
    fun `the tabularium sits at the map center inside a clear disc`() {
        val world = WorldGenerator.generate(42)
        val anchor = world.wonder
        assertEquals(61, anchor.row)
        assertEquals(61, anchor.col)
        assertEquals('u', world.cells[anchor.row][anchor.col])
        assertEquals('U', world.cells[anchor.row + 5][anchor.col + 5])
        val obj = world.objects.single { it.type == "tabularium" }
        assertEquals(anchor.row, obj.row)
        assertEquals(anchor.col, obj.col)
        assertEquals(null, obj.faction)
        for (r in 0 until GridFile.SIZE) {
            for (c in 0 until GridFile.SIZE) {
                val dr = r - 64
                val dc = c - 64
                if (dr * dr + dc * dc >= 64) continue
                assertTrue(world.cells[r][c] in charArrayOf(GridFile.EMPTY, 'u', 'U'), "'${world.cells[r][c]}' at ($r, $c) inside the wonder disc")
            }
        }
    }

    @Test
    fun `different seeds generate different worlds`() {
        val a = WorldGenerator.generate(1)
        val b = WorldGenerator.generate(2)
        assertNotEquals(gridString(a.cells), gridString(b.cells))
    }

    @Test
    fun `seed zero picks and records a random seed`() {
        val world = WorldGenerator.generate(0)
        assertNotEquals(0, world.seed)
    }

    @Test
    fun `both bases are command centers with their faction and claim cells`() {
        val world = WorldGenerator.generate(42)
        val commandCenters = world.objects.filter { it.type == "command_center" }
        assertEquals(setOf("player" to (20 to 20), "maximus" to (100 to 100)),
            commandCenters.map { it.faction to (it.row to it.col) }.toSet())

        for (base in WorldGenerator.bases) {
            assertEquals('h', world.cells[base.row][base.col])
            for (dr in 0 until 4) {
                for (dc in 0 until 4) {
                    if (dr == 0 && dc == 0) continue
                    assertEquals('H', world.cells[base.row + dr][base.col + dc], "claim at +($dr, $dc)")
                }
            }
        }
    }

    @Test
    fun `scenery keeps out of the base clear discs`() {
        val world = WorldGenerator.generate(42)
        for (base in WorldGenerator.bases) {
            for (r in base.row - 6..base.row + 6) {
                for (c in base.col - 6..base.col + 6) {
                    if (r < 0 || r >= GridFile.SIZE || c < 0 || c >= GridFile.SIZE) continue
                    val dr = r - base.row
                    val dc = c - base.col
                    if (dr * dr + dc * dc >= 49) continue
                    assertTrue(world.cells[r][c] !in charArrayOf('t', 'r'), "scenery at ($r, $c) inside base disc")
                }
            }
        }
    }

    @Test
    fun `every deposit cell has an object file entry and a free neighbor`() {
        val world = WorldGenerator.generate(42)
        val depositObjects = world.objects
            .filter { it.category == Category.DEPOSIT }
            .associateBy { it.row to it.col }

        var found = 0
        for (r in 0 until GridFile.SIZE) {
            for (c in 0 until GridFile.SIZE) {
                if (world.cells[r][c] !in charArrayOf('c', 'g')) continue
                found++
                assertTrue((r to c) in depositObjects, "deposit at ($r, $c) has no object entry")
                val hasFreeNeighbor = (-1..1).any { dr ->
                    (-1..1).any { dc ->
                        val neighborRow = r + dr
                        val neighborCol = c + dc
                        (dr != 0 || dc != 0) &&
                            neighborRow in 0 until GridFile.SIZE && neighborCol in 0 until GridFile.SIZE &&
                            world.cells[neighborRow][neighborCol] == GridFile.EMPTY
                    }
                }
                assertTrue(hasFreeNeighbor, "deposit at ($r, $c) is walled in")
            }
        }
        assertEquals(depositObjects.size, found, "object entries must match deposit cells 1:1")
        assertTrue(found >= 4, "expected at least the four starter veins, got $found deposit cells")
    }

    @Test
    fun `starter veins of every resource lie within reach of each base`() {
        val world = WorldGenerator.generate(42)
        for (base in WorldGenerator.bases) {
            for (ch in charArrayOf('c', 'g')) {
                val near = world.objects.any {
                    it.char == ch && distance(it.row - base.row, it.col - base.col) <= 20.0
                }
                assertTrue(near, "no '$ch' vein near base (${base.row}, ${base.col})")
            }
            val treeNear = (0 until GridFile.SIZE).any { r ->
                (0 until GridFile.SIZE).any { c ->
                    world.cells[r][c] == 't' && distance(r - base.row, c - base.col) <= 20.0
                }
            }
            assertTrue(treeNear, "no silicon tree near base (${base.row}, ${base.col})")
        }
    }

    private fun distance(dr: Int, dc: Int): Double =
        kotlin.math.sqrt((dr * dr + dc * dc).toDouble())
}
