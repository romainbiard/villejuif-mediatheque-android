// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** Formats de date du portail, tous à l'heure de Paris. */
object BokehDates {
    private val frenchDatePattern = Regex("""(\d{2})/(\d{2})/(\d{4})(?:\s+à\s+(\d{1,2}):(\d{2}))?""")

    /** `2026-11-20` (API JSON) : début de ce jour à Paris. */
    fun isoDay(text: String): Instant? {
        val parts = text.split('-').mapNotNull { it.toIntOrNull() }
        if (parts.size != 3 || parts[0] <= 0) return null
        return try {
            Paris.startOf(LocalDate.of(parts[0], parts[1], parts[2]))
        } catch (_: DateTimeException) {
            null
        }
    }

    /** Date lue dans le HTML, et si une heure l'accompagnait. */
    data class FrenchDate(val instant: Instant, val hasTime: Boolean)

    /** `31/10/2026` ou `31/10/2026 à 10:30` (HTML). */
    fun frenchDate(text: String): FrenchDate? {
        val match = frenchDatePattern.find(text) ?: return null
        val (day, month, year, hour, minute) = match.destructured
        return try {
            val time = if (hour.isEmpty()) {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt()).atStartOfDay()
            } else {
                LocalDateTime.of(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt())
            }
            FrenchDate(time.atZone(Paris.zone).toInstant(), hasTime = hour.isNotEmpty())
        } catch (_: DateTimeException) {
            null
        }
    }

    /** Première date `jj/mm/aaaa` d'un texte libre (« Pas disponible avant le 14/11/2026 »). */
    fun firstDate(text: String): Instant? = frenchDate(text)?.instant
}
