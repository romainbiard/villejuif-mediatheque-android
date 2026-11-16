// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.dto

import fr.mediatheque.core.bokeh.BokehError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

// Reading Bokeh JSON, field by field.
//
// Bokeh mixes numbers and strings, and sends `null` for fields it usually fills in
// (a hold's status, a loan's author...). Only identifiers stay strict:
// everything else falls back to an empty value rather than failing the whole response.

/** Root JSON object of a response, otherwise [BokehError.UnexpectedFormat]. */
internal fun parseObject(text: String): JsonObject = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (error: SerializationException) {
    throw BokehError.UnexpectedFormat("Unreadable JSON: ${error.message}")
} ?: throw BokehError.UnexpectedFormat("JSON: an object was expected")

private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

private fun malformed(key: String): Nothing = throw BokehError.UnexpectedFormat("invalid field \"$key\"")

// Strict: the key must exist with the right type.

internal fun JsonObject.requiredString(key: String): String = primitive(key)?.takeIf { it.isString }?.content ?: malformed(key)

internal fun JsonObject.requiredInt(key: String): Int = primitive(key)?.takeUnless { it.isString }?.intOrNull ?: malformed(key)

internal fun JsonObject.requiredObject(key: String): JsonObject = this[key] as? JsonObject ?: malformed(key)

/** Absent or `null` -> `null`; if present, it must be a string. */
internal fun JsonObject.optionalString(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: malformed(key)
}

/** Absent or `null` -> empty list; if present, it must be a list of strings. */
internal fun JsonObject.stringList(key: String): List<String> {
    val element = this[key] ?: return emptyList()
    if (element is JsonNull) return emptyList()
    val array = element as? JsonArray ?: malformed(key)
    return array.map { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content ?: malformed(key) }
}

// Lenient.

/** A string or an integer, `null` otherwise. */
internal fun JsonObject.lenientString(key: String): String? {
    val primitive = primitive(key) ?: return null
    if (primitive.isString) return primitive.content
    return primitive.longOrNull?.toString()
}

/** A string or a number, `""` if missing or `null`. */
internal fun JsonObject.lenientText(key: String): String = lenientString(key).orEmpty()

/** A number or a numeric string, `0` if missing or `null`. */
internal fun JsonObject.lenientCount(key: String): Int {
    val primitive = primitive(key) ?: return 0
    return (if (primitive.isString) primitive.content.toIntOrNull() else primitive.intOrNull) ?: 0
}

/** A boolean, `0`/`1` or `"0"`/`"1"`, `false` if missing or `null`. */
internal fun JsonObject.lenientFlag(key: String): Boolean {
    val primitive = primitive(key) ?: return false
    if (!primitive.isString) primitive.booleanOrNull?.let { return it }
    return lenientCount(key) != 0
}

/** `null` if missing, `null`, or unreadable. */
internal fun <T> JsonObject.lenientObject(key: String, read: (JsonObject) -> T): T? {
    val element = this[key] as? JsonObject ?: return null
    return try {
        read(element)
    } catch (_: BokehError.UnexpectedFormat) {
        null
    }
}

/** The elements that can be read: a malformed loan or hold no longer hides the others. */
internal fun <T> JsonObject.lossyList(key: String, read: (JsonObject) -> T): List<T> {
    val array = this[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { element: JsonElement ->
        val item = element as? JsonObject ?: return@mapNotNull null
        try {
            read(item)
        } catch (_: BokehError.UnexpectedFormat) {
            null
        }
    }
}
