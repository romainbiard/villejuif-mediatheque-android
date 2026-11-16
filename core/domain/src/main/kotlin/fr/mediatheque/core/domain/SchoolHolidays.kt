// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.domain

import fr.mediatheque.core.model.Paris
import java.time.Instant
import java.time.LocalDate

/** A school holiday period (zone C, Villejuif's academy). The UI names it from [kind]. */
data class SchoolHoliday(
    val kind: Kind,
    /** First day of the holidays, at midnight in Paris. */
    val start: Instant,
    /** Day school resumes (excluded). */
    val end: Instant
) {
    enum class Kind { ALL_SAINTS, CHRISTMAS, WINTER, SPRING, ASCENSION_BRIDGE, SUMMER }
}

object SchoolHolidays {
    val zoneC: List<SchoolHoliday> = listOf(
        holiday(SchoolHoliday.Kind.ALL_SAINTS, LocalDate.of(2026, 10, 17), LocalDate.of(2026, 11, 2)),
        holiday(SchoolHoliday.Kind.CHRISTMAS, LocalDate.of(2026, 12, 19), LocalDate.of(2027, 1, 4)),
        holiday(SchoolHoliday.Kind.WINTER, LocalDate.of(2027, 2, 6), LocalDate.of(2027, 2, 22)),
        holiday(SchoolHoliday.Kind.SPRING, LocalDate.of(2027, 4, 3), LocalDate.of(2027, 4, 19)),
        holiday(SchoolHoliday.Kind.ASCENSION_BRIDGE, LocalDate.of(2027, 5, 7), LocalDate.of(2027, 5, 8)),
        holiday(SchoolHoliday.Kind.SUMMER, LocalDate.of(2027, 7, 3), LocalDate.of(2027, 9, 2)),
        holiday(SchoolHoliday.Kind.ALL_SAINTS, LocalDate.of(2027, 10, 23), LocalDate.of(2027, 11, 8)),
        holiday(SchoolHoliday.Kind.CHRISTMAS, LocalDate.of(2027, 12, 18), LocalDate.of(2028, 1, 3)),
        holiday(SchoolHoliday.Kind.WINTER, LocalDate.of(2028, 2, 12), LocalDate.of(2028, 2, 28)),
        holiday(SchoolHoliday.Kind.SPRING, LocalDate.of(2028, 4, 15), LocalDate.of(2028, 5, 2)),
        holiday(SchoolHoliday.Kind.ASCENSION_BRIDGE, LocalDate.of(2028, 5, 25), LocalDate.of(2028, 5, 29)),
        // Only the start is published: the 2028 school year is assumed to start on September 1.
        holiday(SchoolHoliday.Kind.SUMMER, LocalDate.of(2028, 7, 4), LocalDate.of(2028, 9, 1))
    )

    /** The holidays in progress at [now], otherwise the next ones. */
    fun currentOrNext(now: Instant, holidays: List<SchoolHoliday> = zoneC): SchoolHoliday? = holidays.firstOrNull { it.end > now }

    private fun holiday(kind: SchoolHoliday.Kind, start: LocalDate, end: LocalDate) =
        SchoolHoliday(kind, Paris.startOf(start), Paris.startOf(end))
}
