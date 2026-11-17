// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import fr.mediatheque.core.model.Paris
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Dates and times of Villejuif, always in Paris time whatever the device's time zone (opening hours, due dates,
 * events), in the patron's language.
 */
class ParisFormat(private val locale: Locale) {
    private fun pattern(pattern: String) = DateTimeFormatter.ofPattern(pattern, locale).withZone(Paris.zone)

    /** "19:00" in French, "7:00 PM" in English. */
    fun time(instant: Instant): String = DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(Paris.zone)
        .format(instant)

    /** "mardi". */
    fun weekday(instant: Instant): String = pattern("EEEE").format(instant)

    /** "mer." */
    fun shortWeekday(day: LocalDate): String = DateTimeFormatter.ofPattern("EEE", locale).format(day)

    /** "4 novembre". */
    fun dayMonth(instant: Instant): String = pattern(if (locale.language == "en") "MMMM d" else "d MMMM").format(instant)

    /** "March 14, 2027" / "14 mars 2027". */
    fun dayMonthYear(instant: Instant): String = pattern(if (locale.language == "en") "MMMM d, yyyy" else "d MMMM yyyy").format(instant)

    /** "mercredi 4 novembre". */
    fun weekdayDayMonth(instant: Instant): String = pattern(if (locale.language == "en") "EEEE, MMMM d" else "EEEE d MMMM").format(instant)

    fun weekdayDayMonth(day: LocalDate): String = weekdayDayMonth(Paris.startOf(day))
}

/** The format for the current configuration's language. */
@Composable
@ReadOnlyComposable
fun parisFormat(): ParisFormat = ParisFormat(ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.FRANCE)

/** "mercredi 4 novembre" → "Mercredi 4 novembre", at the start of a line. */
fun String.capitalizedSentence(locale: Locale = Locale.getDefault()): String = replaceFirstChar { it.titlecase(locale) }
