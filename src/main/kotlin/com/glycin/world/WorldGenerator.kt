package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.Cell
import com.glycin.api.model.MapObject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

// Port of the client's offline generator (GitClient.OfflineSeedWorld). Same constants, so change them together.
object WorldGenerator {

    data class Base(val row: Int, val col: Int, val faction: String)

    class GeneratedWorld(
        val seed: Int,
        val cells: Array<CharArray>,
        val objects: List<MapObject>,
        val bases: List<Base>,
        val wonder: Cell,
    )

    val bases: List<Base> = listOf(
        Base(20, 20, "player"),
        Base(100, 100, "maximus"),
    )

    val wonderAnchor: Cell = Cell(
        GridFile.SIZE / 2 - TileLegend.tabularium.fpRows / 2,
        GridFile.SIZE / 2 - TileLegend.tabularium.fpCols / 2,
    )

    private const val BASE_CLEAR_RADIUS = 7
    private const val WONDER_CLEAR_RADIUS = 8
    private const val STARTER_VEIN_MIN_DIST = 8
    private const val STARTER_VEIN_MAX_DIST = 14
    private const val TREE_NOISE_SCALE = 0.09
    private const val TREE_THRESHOLD = 0.68
    private const val ROCK_NOISE_SCALE = 0.15
    private const val ROCK_THRESHOLD = 0.72
    private const val VEIN_ATTEMPTS = 60

    private val resourceChars = charArrayOf('t', 'c', 'g')

    fun generate(seed: Int): GeneratedWorld {
        val actualSeed = if (seed != 0) seed else Random.nextInt(1, Int.MAX_VALUE)
        val rng = Random(actualSeed)
        val size = GridFile.SIZE
        val cells = Array(size) { CharArray(size) { GridFile.EMPTY } }
        val objects = mutableListOf<MapObject>()

        val commandCenter = checkNotNull(TileLegend.forType("command_center"))
        for (base in bases) {
            Placement.footprintWrites(base.row, base.col, commandCenter.char).forEach { (cell, tile) ->
                cells[cell.row][cell.col] = tile
            }
            objects += MapObject(
                row = base.row,
                col = base.col,
                category = commandCenter.category,
                type = commandCenter.type,
                char = commandCenter.char,
                faction = base.faction,
                fpCols = commandCenter.fpCols,
                fpRows = commandCenter.fpRows,
            )
        }

        val tabularium = TileLegend.tabularium
        Placement.footprintWrites(wonderAnchor.row, wonderAnchor.col, tabularium.char).forEach { (cell, tile) ->
            cells[cell.row][cell.col] = tile
        }
        objects += MapObject(
            row = wonderAnchor.row,
            col = wonderAnchor.col,
            category = tabularium.category,
            type = tabularium.type,
            char = tabularium.char,
            fpCols = tabularium.fpCols,
            fpRows = tabularium.fpRows,
        )

        val treeNoise = Noise(rng)
        val rockNoise = Noise(rng)
        for (row in 1 until size - 1) {
            for (col in 1 until size - 1) {
                if (cells[row][col] != GridFile.EMPTY) continue
                if (nearBase(row, col, BASE_CLEAR_RADIUS) || nearWonder(row, col)) continue
                if (treeNoise.sample(col * TREE_NOISE_SCALE, row * TREE_NOISE_SCALE) > TREE_THRESHOLD &&
                    rng.nextDouble() < 0.85
                ) {
                    cells[row][col] = 't'
                    continue
                }
                if (rockNoise.sample(col * ROCK_NOISE_SCALE, row * ROCK_NOISE_SCALE) > ROCK_THRESHOLD &&
                    rng.nextDouble() < 0.35
                ) {
                    cells[row][col] = 'r'
                }
            }
        }

        for (tile in resourceChars) {
            for (base in bases) {
                placeVeinNearBase(rng, cells, base, tile, objects)
            }
        }
        placeVeins(rng, cells, 'c', count = 5, objects)
        placeVeins(rng, cells, 'g', count = 2, objects)

        return GeneratedWorld(seed = actualSeed, cells = cells, objects = objects, bases = bases, wonder = wonderAnchor)
    }

    private fun nearBase(row: Int, col: Int, radius: Int): Boolean {
        val radiusSquared = radius * radius
        return bases.any { base ->
            val dr = row - base.row
            val dc = col - base.col
            dr * dr + dc * dc < radiusSquared
        }
    }

    private fun nearWonder(row: Int, col: Int): Boolean {
        val dr = row - GridFile.SIZE / 2
        val dc = col - GridFile.SIZE / 2
        return dr * dr + dc * dc < WONDER_CLEAR_RADIUS * WONDER_CLEAR_RADIUS
    }

    private fun placeVeinNearBase(
        rng: Random,
        cells: Array<CharArray>,
        base: Base,
        tile: Char,
        objects: MutableList<MapObject>,
    ) {
        repeat(VEIN_ATTEMPTS) {
            val angle = rng.nextDouble() * PI * 2.0
            val distance = STARTER_VEIN_MIN_DIST + rng.nextDouble() * (STARTER_VEIN_MAX_DIST - STARTER_VEIN_MIN_DIST)
            val row = base.row + (sin(angle) * distance).roundToInt()
            val col = base.col + (cos(angle) * distance).roundToInt()
            if (tryPlaceVein(rng, cells, row, col, tile, veinSize(rng, tile), objects)) return
        }
    }

    private fun placeVeins(
        rng: Random,
        cells: Array<CharArray>,
        tile: Char,
        count: Int,
        objects: MutableList<MapObject>,
    ) {
        val size = cells.size
        repeat(count) {
            for (attempt in 0 until VEIN_ATTEMPTS) {
                val row = rng.nextInt(2, size - 2)
                val col = rng.nextInt(2, size - 2)
                if (nearBase(row, col, BASE_CLEAR_RADIUS) || nearWonder(row, col)) continue
                if (tryPlaceVein(rng, cells, row, col, tile, veinSize(rng, tile), objects)) break
            }
        }
    }

    private fun veinSize(rng: Random, tile: Char): Int = when (tile) {
        'g' -> rng.nextInt(2, 5)
        'c' -> rng.nextInt(3, 6)
        else -> rng.nextInt(4, 8)
    }

    // Every vein cell clears a 3x3 of scenery so harvesters can get to it. Buildings and other deposits stay put.
    private fun tryPlaceVein(
        rng: Random,
        cells: Array<CharArray>,
        row: Int,
        col: Int,
        tile: Char,
        size: Int,
        objects: MutableList<MapObject>,
    ): Boolean {
        if (!carveable(cells, row, col)) return false
        val entry = checkNotNull(TileLegend.byChar[tile])
        var placed = 0
        var walkRow = row
        var walkCol = col
        for (step in 0 until size * 4) {
            if (placed >= size) break
            if (carveable(cells, walkRow, walkCol) && !nearWonder(walkRow, walkCol)) {
                clearSceneryAround(cells, walkRow, walkCol)
                cells[walkRow][walkCol] = tile
                if (entry.category == Category.DEPOSIT) {
                    objects += MapObject(row = walkRow, col = walkCol, category = entry.category, type = entry.type, char = entry.char)
                }
                placed++
            }
            if (rng.nextInt(2) == 0) walkRow += if (rng.nextInt(2) == 0) 1 else -1
            else walkCol += if (rng.nextInt(2) == 0) 1 else -1
            if (walkRow < 1 || walkRow >= cells.size - 1 || walkCol < 1 || walkCol >= cells.size - 1) break
        }
        return placed > 0
    }

    private fun carveable(cells: Array<CharArray>, row: Int, col: Int): Boolean {
        if (row < 1 || row >= cells.size - 1 || col < 1 || col >= cells.size - 1) return false
        val tile = cells[row][col]
        return tile == GridFile.EMPTY || tile == 't' || tile == 'r'
    }

    private fun clearSceneryAround(cells: Array<CharArray>, row: Int, col: Int) {
        for (nearRow in row - 1..row + 1) {
            for (nearCol in col - 1..col + 1) {
                if (nearRow < 0 || nearRow >= cells.size || nearCol < 0 || nearCol >= cells.size) continue
                if (cells[nearRow][nearCol] == 't' || cells[nearRow][nearCol] == 'r') cells[nearRow][nearCol] = GridFile.EMPTY
            }
        }
    }
}
