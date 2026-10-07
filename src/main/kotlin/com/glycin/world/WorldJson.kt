package com.glycin.world

import com.glycin.api.model.MapObject
import kotlinx.serialization.json.Json

object WorldJson {
    val PRETTY: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    val LENIENT: Json = Json {
        ignoreUnknownKeys = true
    }

    val EVENTS: Json = Json {
        classDiscriminator = "type"
    }

    // Lenient, because old blobs in history might be missing newer fields.
    fun decodeObject(json: String): MapObject = LENIENT.decodeFromString(MapObject.serializer(), json)
    fun encodeObject(obj: MapObject): String = PRETTY.encodeToString(MapObject.serializer(), obj)
}
