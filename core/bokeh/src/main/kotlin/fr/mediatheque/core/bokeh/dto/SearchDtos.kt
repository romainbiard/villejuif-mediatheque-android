// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.dto

import fr.mediatheque.core.bokeh.BokehError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** Response of `recherche/simple/.../format/json`, modeled on the received JSON. */
data class SearchResponseDto(val total: Int, val records: List<RecordDto>) {
    companion object {
        fun parse(text: String): SearchResponseDto = read(parseObject(text))

        internal fun read(json: JsonObject) = SearchResponseDto(
            total = json.requiredInt("total"),
            records = (json["records"] as? JsonArray).orEmpty().map { element ->
                RecordDto.read(element as? JsonObject ?: throw BokehError.UnexpectedFormat("invalid record"))
            }
        )
    }
}

data class RecordDto(
    val titles: List<String>,
    val serial: String?,
    val volume: String?,
    val creators: List<String>,
    val date: String,
    val publishers: List<String>,
    val type: DocumentType,
    val thumbnail: String?,
    val permalink: String,
    val items: List<ItemDto>
) {
    data class DocumentType(val code: String, val label: String)

    /** Numeric record ID, read from the permalink (`/id/474169`). */
    val noticeId: Int? get() = permalink.split('/').lastOrNull()?.toIntOrNull()

    companion object {
        internal fun read(json: JsonObject): RecordDto {
            val type = json.requiredObject("type")
            return RecordDto(
                titles = json.stringList("titles"),
                serial = json.optionalString("serial"),
                volume = json.lenientString("volume"),
                creators = json.stringList("creators"),
                date = json.optionalString("date").orEmpty(),
                publishers = json.stringList("publishers"),
                type = DocumentType(type.requiredString("code"), type.requiredString("label")),
                thumbnail = json.optionalString("thumbnail"),
                permalink = json.requiredString("permalink"),
                items = (json["items"] as? JsonArray).orEmpty().map { element ->
                    ItemDto.read(element as? JsonObject ?: throw BokehError.UnexpectedFormat("invalid copy"))
                }
            )
        }
    }
}

data class ItemDto(
    val annex: String?,
    val shelfMark: String,
    val activity: String,
    /** Zone 995 of the copy; `false` in the JSON for digital resources. */
    val zone995: List<Subfield>
) {
    data class Subfield(val code: String, val value: String)

    fun subfield(code: String): String? = zone995.firstOrNull { it.code == code }?.value

    companion object {
        internal fun read(json: JsonObject) = ItemDto(
            annex = json.lenientString("annexe"),
            shelfMark = json.optionalString("cote").orEmpty(),
            activity = json.optionalString("activite").orEmpty(),
            zone995 = (json["zone995"] as? JsonArray)?.let { array ->
                runCatching {
                    array.map { element ->
                        val subfield = element as JsonObject
                        Subfield(subfield.requiredString("code"), subfield.requiredString("valeur"))
                    }
                }.getOrNull()
            }.orEmpty()
        )
    }
}
