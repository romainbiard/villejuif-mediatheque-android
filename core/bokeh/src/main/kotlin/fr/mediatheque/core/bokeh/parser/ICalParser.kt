// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.parser

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Un VEVENT de `/cms/ical/id_article/{id}`. */
data class ICalEventDto(
    val uid: String? = null,
    val summary: String? = null,
    val start: Instant? = null,
    val location: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val description: String? = null,
    val imageUrl: String? = null
)

/** Analyseur RFC 5545 minimal : il couvre ce que Bokeh produit (lignes repliées, `TZID=Europe/Paris`, `GEO`). */
object ICalParser {
    private val parisZone: ZoneId = ZoneId.of("Europe/Paris")
    private val dayFormat = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val dateTimeFormat = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    fun events(text: String): List<ICalEventDto> {
        val events = mutableListOf<ICalEventDto>()
        var current: ICalEventDto? = null
        for (line in unfold(text)) {
            val property = Property.parse(line)
            when {
                property.name == "BEGIN" && property.value == "VEVENT" -> current = ICalEventDto()

                property.name == "END" && property.value == "VEVENT" -> {
                    current?.let { events += it }
                    current = null
                }

                else -> current = current?.let { apply(property, it) }
            }
        }
        return events
    }

    private fun apply(property: Property, event: ICalEventDto): ICalEventDto = when (property.name) {
        "UID" -> event.copy(uid = property.value)

        "SUMMARY" -> event.copy(summary = unescape(property.value))

        "LOCATION" -> event.copy(location = unescape(property.value))

        "DESCRIPTION" -> event.copy(description = unescape(property.value).trim())

        "ATTACH" -> event.copy(imageUrl = property.value.urlOrNull())

        "DTSTART" -> event.copy(start = date(property))

        "GEO" -> {
            val parts = property.value.split(';').mapNotNull { it.toDoubleOrNull() }
            if (parts.size == 2) event.copy(latitude = parts[0], longitude = parts[1]) else event
        }

        else -> event
    }

    /** Déplie les lignes repliées (une ligne qui commence par une espace prolonge la précédente). */
    internal fun unfold(text: String): List<String> {
        val lines = mutableListOf<String>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            if ((raw.startsWith(' ') || raw.startsWith('\t')) && lines.isNotEmpty()) {
                lines[lines.lastIndex] = lines.last() + raw.drop(1)
            } else {
                lines += raw
            }
        }
        return lines
    }

    internal fun unescape(value: String): String = value
        .replace("\\n", "\n")
        .replace("\\N", "\n")
        .replace("\\,", ",")
        .replace("\\;", ";")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")

    private fun date(property: Property): Instant? {
        val value = property.value
        return try {
            val zone = property.parameters["TZID"]?.let { ZoneId.of(it) } ?: parisZone
            when {
                value.endsWith("Z") -> LocalDateTime.parse(value.dropLast(1), dateTimeFormat).toInstant(ZoneOffset.UTC)
                value.length == 8 -> LocalDate.parse(value, dayFormat).atStartOfDay(zone).toInstant()
                else -> LocalDateTime.parse(value, dateTimeFormat).atZone(zone).toInstant()
            }
        } catch (_: DateTimeParseException) {
            null
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * `NOM;PARAM=val;…:valeur`. Bokeh produit parfois des paramètres étranges
     * (`ATTACH;X-BKH-=IMAGE:https://…`) : on coupe au premier `:` hors guillemets.
     */
    internal data class Property(val name: String, val parameters: Map<String, String>, val value: String) {
        companion object {
            fun parse(line: String): Property {
                var inQuotes = false
                var split = -1
                for ((index, character) in line.withIndex()) {
                    if (character == '"') inQuotes = !inQuotes
                    if (character == ':' && !inQuotes) {
                        split = index
                        break
                    }
                }
                if (split < 0) return Property(line, emptyMap(), "")
                val head = line.substring(0, split).split(';').filter { it.isNotEmpty() }
                val parameters = head.drop(1).mapNotNull { parameter ->
                    val pair = parameter.split('=', limit = 2)
                    if (pair.size == 2) pair[0].uppercase() to pair[1] else null
                }.toMap()
                return Property(head.firstOrNull()?.uppercase().orEmpty(), parameters, line.substring(split + 1))
            }
        }
    }
}
