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
 * Toutes les dates de l'app sont celles de Villejuif (horaires, échéances, animations),
 * quel que soit le fuseau du téléphone : on calcule toujours à l'heure de Paris.
 */
object Paris {
    val zone: ZoneId = ZoneId.of("Europe/Paris")
    val locale: Locale = Locale.FRANCE

    /** Jour calendaire à Paris de [instant]. */
    fun dayOf(instant: Instant): LocalDate = instant.atZone(zone).toLocalDate()

    /** Début de [day] à Paris. */
    fun startOf(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    fun instant(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()
}

/** Texte replié pour comparer sans casse ni accents (« Médiathèque » → « mediatheque »). */
fun String.folded(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase(Locale.ROOT)
