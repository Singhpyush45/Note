package com.appname.notes.core.ink.template

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Stores layouts as JSON. Reading is forgiving: fields from newer versions are ignored and components of a
 * kind this version doesn't know are skipped, so a page still opens with everything it can draw.
 */
object TemplateJson {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true // stored values never depend on defaults that a later version might change
        explicitNulls = false
    }

    /** "type" values this version can draw. */
    private val KNOWN = setOf("text", "box", "line", "lines", "dots", "checklist", "table", "staves", "circles")

    fun encode(layout: TemplateLayout): String = json.encodeToString(TemplateLayout.serializer(), layout)

    /** The layout in [text], or null if it isn't one (damaged). */
    fun decodeOrNull(text: String?): TemplateLayout? {
        if (text.isNullOrBlank()) return null
        return try {
            val tree = json.parseToJsonElement(text).jsonObject
            val components = (tree["components"] as? JsonArray)?.filter { element ->
                val type = (element as? JsonObject)?.get("type") as? JsonPrimitive
                type?.contentOrNull in KNOWN
            }
            val cleaned = if (components == null) tree else JsonObject(tree + ("components" to JsonArray(components)))
            val layout = json.decodeFromJsonElement(TemplateLayout.serializer(), cleaned)
            // Components that are themselves invalid (e.g. a table without columns) would have thrown above.
            if (layout.width > 0f && layout.height > 0f) layout else null
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
