package com.glycin.world

import com.glycin.api.model.MapObject
import jakarta.enterprise.context.ApplicationScoped
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.nio.file.Files
import java.nio.file.NoSuchFileException

@ApplicationScoped
class ObjectStore(private val paths: WorldPaths) {

    fun read(row: Int, col: Int): MapObject? = try {
        WorldJson.decodeObject(Files.readString(paths.objectFile(row, col)))
    } catch (_: NoSuchFileException) {
        null
    }

    fun write(obj: MapObject) {
        Files.createDirectories(paths.objects)
        val f = paths.objectFile(obj.row, obj.col)
        Files.writeString(f, WorldJson.encodeObject(obj))
    }

    fun delete(row: Int, col: Int): Boolean = Files.deleteIfExists(paths.objectFile(row, col))

    fun listAll(): List<MapObject> {
        if (!Files.exists(paths.objects)) return emptyList()
        return Files.list(paths.objects).use { stream ->
            stream.toList()
                .filter { it.fileName.toString().endsWith(".json") }
                // Conflicted files are full of git markers. /git/conflicts deals with those.
                .mapNotNull {
                    runCatching { WorldJson.decodeObject(Files.readString(it)) }.getOrNull()
                }
        }
    }

    fun modify(row: Int, col: Int, patch: Map<String, JsonElement>): MapObject {
        val current = read(row, col)
            ?: throw NoSuchElementException("No object at ($row, $col)")
        val currentObj = WorldJson.PRETTY.encodeToJsonElement(MapObject.serializer(), current) as JsonObject
        val merged = JsonObject(currentObj + patch)
        val updated = WorldJson.PRETTY.decodeFromJsonElement(MapObject.serializer(), merged)
        write(updated)
        return updated
    }
}
