package com.glycin.world

import com.glycin.api.model.Category
import com.glycin.api.model.Cell
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.nio.file.Files

// legend.json gets rebuilt from TileLegend on every start. map.json is written once, when the world is born.
@ApplicationScoped
class MetaFiles(private val paths: WorldPaths) {

    @Serializable
    data class LegendEntry(
        val char: Char,
        val category: Category,
        val type: String,
        val fpCols: Int,
        val fpRows: Int,
        val claimChar: Char? = null,
    )

    @Serializable
    data class BaseMeta(val row: Int, val col: Int, val faction: String)

    @Serializable
    data class MapMeta(
        val width: Int,
        val height: Int,
        val seed: Int,
        val bases: List<BaseMeta>,
        val adversary: String? = null,
        val wonder: Cell? = null,
    )

    fun writeLegend() {
        Files.createDirectories(paths.meta)
        val legend = TileLegend.entries.map {
            LegendEntry(
                char = it.char,
                category = it.category,
                type = it.type,
                fpCols = it.fpCols,
                fpRows = it.fpRows,
                claimChar = if (it.multiTile) TileLegend.claimChar(it.char) else null,
            )
        }
        Files.writeString(
            paths.meta.resolve(LEGEND_FILENAME),
            WorldJson.PRETTY.encodeToString(ListSerializer(LegendEntry.serializer()), legend),
        )
    }

    fun writeMapMeta(world: WorldGenerator.GeneratedWorld) {
        Files.createDirectories(paths.meta)
        val meta = MapMeta(
            width = GridFile.SIZE,
            height = GridFile.SIZE,
            seed = world.seed,
            bases = world.bases.map { BaseMeta(it.row, it.col, it.faction) },
            wonder = world.wonder,
        )
        Files.writeString(
            paths.meta.resolve(MAP_FILENAME),
            WorldJson.PRETTY.encodeToString(MapMeta.serializer(), meta),
        )
    }

    fun readMapMeta(): MapMeta? {
        val file = paths.meta.resolve(MAP_FILENAME)
        if (!Files.exists(file)) return null
        return WorldJson.PRETTY.decodeFromString(MapMeta.serializer(), Files.readString(file))
    }

    fun writeAdversary(adversary: String): Boolean {
        val meta = readMapMeta() ?: return false
        Files.writeString(
            paths.meta.resolve(MAP_FILENAME),
            WorldJson.PRETTY.encodeToString(MapMeta.serializer(), meta.copy(adversary = adversary)),
        )
        return true
    }

    companion object {
        const val LEGEND_FILENAME = "legend.json"
        const val MAP_FILENAME = "map.json"
    }
}
