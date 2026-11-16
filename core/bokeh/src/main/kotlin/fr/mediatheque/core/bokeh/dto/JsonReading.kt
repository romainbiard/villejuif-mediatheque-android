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

// Lecture du JSON de Bokeh, champ par champ.
//
// Bokeh mélange nombres et textes, et envoie `null` pour des champs qu'il remplit d'habitude
// (le statut d'une réservation, l'auteur d'un prêt…). Seuls les identifiants restent stricts :
// le reste retombe sur une valeur vide plutôt que de faire échouer toute la réponse.

/** Objet JSON racine d'une réponse, sinon [BokehError.UnexpectedFormat]. */
internal fun parseObject(text: String): JsonObject = try {
    Json.parseToJsonElement(text) as? JsonObject
} catch (error: SerializationException) {
    throw BokehError.UnexpectedFormat("JSON illisible : ${error.message}")
} ?: throw BokehError.UnexpectedFormat("JSON : un objet était attendu")

private fun JsonObject.primitive(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

private fun malformed(key: String): Nothing = throw BokehError.UnexpectedFormat("champ « $key » invalide")

// Stricts : la clé doit exister avec le bon type.

internal fun JsonObject.requiredString(key: String): String = primitive(key)?.takeIf { it.isString }?.content ?: malformed(key)

internal fun JsonObject.requiredInt(key: String): Int = primitive(key)?.takeUnless { it.isString }?.intOrNull ?: malformed(key)

internal fun JsonObject.requiredObject(key: String): JsonObject = this[key] as? JsonObject ?: malformed(key)

/** Absent ou `null` → `null` ; présent, ce doit être un texte. */
internal fun JsonObject.optionalString(key: String): String? {
    val element = this[key] ?: return null
    if (element is JsonNull) return null
    return (element as? JsonPrimitive)?.takeIf { it.isString }?.content ?: malformed(key)
}

/** Absent ou `null` → liste vide ; présent, ce doit être une liste de textes. */
internal fun JsonObject.stringList(key: String): List<String> {
    val element = this[key] ?: return emptyList()
    if (element is JsonNull) return emptyList()
    val array = element as? JsonArray ?: malformed(key)
    return array.map { (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content ?: malformed(key) }
}

// Tolérants.

/** Un texte ou un nombre entier, `null` sinon. */
internal fun JsonObject.lenientString(key: String): String? {
    val primitive = primitive(key) ?: return null
    if (primitive.isString) return primitive.content
    return primitive.longOrNull?.toString()
}

/** Un texte ou un nombre, `""` s'il manque ou vaut `null`. */
internal fun JsonObject.lenientText(key: String): String = lenientString(key).orEmpty()

/** Un nombre ou un texte numérique, `0` s'il manque ou vaut `null`. */
internal fun JsonObject.lenientCount(key: String): Int {
    val primitive = primitive(key) ?: return 0
    return (if (primitive.isString) primitive.content.toIntOrNull() else primitive.intOrNull) ?: 0
}

/** Un booléen, `0`/`1` ou `"0"`/`"1"`, `false` s'il manque ou vaut `null`. */
internal fun JsonObject.lenientFlag(key: String): Boolean {
    val primitive = primitive(key) ?: return false
    if (!primitive.isString) primitive.booleanOrNull?.let { return it }
    return lenientCount(key) != 0
}

/** `null` s'il manque, vaut `null` ou ne se lit pas. */
internal fun <T> JsonObject.lenientObject(key: String, read: (JsonObject) -> T): T? {
    val element = this[key] as? JsonObject ?: return null
    return try {
        read(element)
    } catch (_: BokehError.UnexpectedFormat) {
        null
    }
}

/** Les éléments qui se lisent : un prêt ou une réservation mal formé ne cache plus les autres. */
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
