package com.appname.notes.core.domain.document

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** JSON (de)serialization of [NoteDocument], tolerant of content written by newer app versions. */
object DocumentJson {

    val json: Json = Json {
        ignoreUnknownKeys = true // new fields from newer versions are skipped, not fatal
        coerceInputValues = true // unknown enum values (e.g. a new block kind) fall back to defaults
        encodeDefaults = true
        explicitNulls = false
    }

    fun encode(document: NoteDocument): String = json.encodeToString(NoteDocument.serializer(), document)

    /** @throws SerializationException when [text] isn't a note document at all. */
    fun decode(text: String): NoteDocument = json.decodeFromString(NoteDocument.serializer(), text)
}

/**
 * Writes `{"type": "...", ...fields}` for each block and maps unknown types to [UnknownBlock].
 * Works on JSON trees because the document is only ever stored as JSON.
 */
internal object BlockSerializer : KSerializer<Block> {

    private const val TYPE = "type"
    private const val TYPE_TEXT = "text"

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Block")

    override fun serialize(encoder: Encoder, value: Block) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("Blocks are JSON-only")
        val element = when (value) {
            is TextBlock -> {
                val fields = jsonEncoder.json.encodeToJsonElement(TextBlock.serializer(), value).jsonObject
                JsonObject(mapOf(TYPE to JsonPrimitive(TYPE_TEXT)) + fields)
            }
            is UnknownBlock -> value.raw
        }
        jsonEncoder.encodeJsonElement(element)
    }

    override fun deserialize(decoder: Decoder): Block {
        val jsonDecoder = decoder as? JsonDecoder ?: throw SerializationException("Blocks are JSON-only")
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        val type = (obj[TYPE] as? JsonPrimitive)?.contentOrNull
        return when (type) {
            // A text block this version can't read (e.g. a kind added later) is kept as it is, read-only.
            TYPE_TEXT -> runCatching { jsonDecoder.json.decodeFromJsonElement(TextBlock.serializer(), JsonObject(obj - TYPE)) }
                .getOrElse { UnknownBlock((obj["id"] as? JsonPrimitive)?.contentOrNull ?: "", type, obj) }
            else -> UnknownBlock(
                id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: "",
                type = type ?: "",
                raw = obj,
            )
        }
    }
}
