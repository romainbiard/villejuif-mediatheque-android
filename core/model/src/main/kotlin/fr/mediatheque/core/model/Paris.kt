// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/**
 * All dates in the app are Villejuif dates (hours, due dates, events),
 * whatever the phone's time zone: we always compute in Paris time.
 */
object Paris {
    val zone: ZoneId = ZoneId.of("Europe/Paris")
    val locale: Locale = Locale.FRANCE

    /** Calendar day in Paris of [instant]. */
    fun dayOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Start of [day] in Paris. */
    fun startOf(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    fun instant(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()
}

/** Text folded for comparison without case or accents ("Médiathèque" → "mediatheque"). */
fun String.folded(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
