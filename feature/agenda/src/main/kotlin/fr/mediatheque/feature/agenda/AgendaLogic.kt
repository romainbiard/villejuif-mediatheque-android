// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import fr.mediatheque.core.domain.SchoolHoliday
import fr.mediatheque.core.domain.SchoolHolidays
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.folded
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Quick period filters ("Quand"). */
enum class AgendaPeriod {
    ALL,
    WEEKEND,
    WEDNESDAYS,
    SCHOOL_HOLIDAYS
    ;

    /** Days covered, from [today]. Empty for [ALL]. */
    fun days(today: LocalDate): List<LocalDate> = when (this) {
        ALL -> emptyList()

        // The coming Saturday and Sunday, or what is left of the current weekend.
        WEEKEND -> if (today.dayOfWeek == DayOfWeek.SUNDAY) {
            listOf(today)
        } else {
            val saturday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            listOf(saturday, saturday.plusDays(1))
        }

        WEDNESDAYS -> {
            val first = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.WEDNESDAY))
            (0 until WEDNESDAY_WEEKS).map { first.plusWeeks(it.toLong()) }
        }

        SCHOOL_HOLIDAYS -> {
            val holiday = SchoolHolidays.currentOrNext(Paris.startOf(today)) ?: return emptyList()
            val start = maxOf(Paris.dayOf(holiday.start), today)
            val end = Paris.dayOf(holiday.end)
            generateSequence(start) { it.plusDays(1) }.takeWhile { it < end }.toList()
        }
    }

    companion object {
        /** Wednesdays shown: the next 8 weeks. */
        const val WEDNESDAY_WEEKS = 8
    }
}

data class AgendaFilters(
    val places: Set<String> = emptySet(),
    val types: Set<String> = emptySet(),
    val audiences: Set<String> = emptySet(),
    val period: AgendaPeriod = AgendaPeriod.ALL
) {
    /** Filters applied, the period counting as one. */
    val count: Int get() = places.size + types.size + audiences.size + if (period == AgendaPeriod.ALL) 0 else 1

    val isEmpty: Boolean get() = count == 0

    fun matches(event: Event): Boolean = (places.isEmpty() || event.place in places) &&
        (types.isEmpty() || event.type in types) &&
        (audiences.isEmpty() || event.audience in audiences)
}

/** One day of a period, with its timed events. */
data class PeriodDay(val day: LocalDate, val events: List<Event>)

/** What the agenda shows for a list of events, filters, a selected day, and today. Pure: tested on its own. */
class AgendaLogic(private val events: List<Event>, private val filters: AgendaFilters, private val today: LocalDate) {
    private val filtered = events.filter(filters::matches)

    /** The next 14 days, for the day picker. */
    val days: List<LocalDate> = (0 until VISIBLE_DAYS).map { today.plusDays(it.toLong()) }

    /** Timed events on [day]. */
    fun eventsOn(day: LocalDate): List<Event> = filtered.filter { !it.isAllDay && it.occursOn(day) }

    /** Exhibitions, prizes… that span [day]. */
    fun ongoingOn(day: LocalDate): List<Event> = filtered.filter { it.isAllDay && it.occursOn(day) }

    /** Days of the picker with at least one timed event (a dot under the date). */
    val daysWithEvents: Set<LocalDate> = days.filter { eventsOn(it).isNotEmpty() }.toSet()

    /** The next day with an event after [day], for the empty state. */
    fun nextDayWithEvents(day: LocalDate): LocalDate? = filtered
        .filter { !it.isAllDay }
        .mapNotNull { event -> event.start?.let(Paris::dayOf)?.takeIf { it > day } }
        .minOrNull()

    /** Days of the selected period that have timed events. */
    val periodDays: List<PeriodDay> = filters.period.days(today).mapNotNull { day ->
        eventsOn(day).takeIf { it.isNotEmpty() }?.let { PeriodDay(day, it) }
    }

    /** Long events that span at least one day of the period. */
    val periodOngoing: List<Event> = filters.period.days(today).let { days ->
        filtered.filter { event -> event.isAllDay && days.any(event::occursOn) }
    }

    /** How many upcoming events [candidate] filters would show ("Voir N événements"). */
    fun upcomingCount(candidate: AgendaFilters): Int {
        val periodDays = candidate.period.days(today)
        return events.count { event ->
            val last = (event.end ?: event.start)?.let(Paris::dayOf) ?: return@count false
            candidate.matches(event) && last >= today &&
                (candidate.period == AgendaPeriod.ALL || periodDays.any(event::occursOn))
        }
    }

    /** Places offered as filters, the three libraries first. */
    val availablePlaces: List<String> = distinct(events.mapNotNull { it.place }) { place ->
        Library.entries.indexOfFirst { place.folded().contains(it.shortName.folded()) }.takeIf { it >= 0 } ?: Library.entries.size
    }
    val availableTypes: List<String> = distinct(events.mapNotNull { it.type })
    val availableAudiences: List<String> = distinct(events.mapNotNull { it.audience })

    private fun distinct(values: List<String>, priority: (String) -> Int = { 0 }): List<String> =
        values.distinct().sortedWith(compareBy<String>(priority).thenBy { it.folded() })

    companion object {
        const val VISIBLE_DAYS = 14

        /** The network is closed on Sundays and Mondays. */
        fun isLibraryClosingDay(day: LocalDate) = day.dayOfWeek == DayOfWeek.SUNDAY || day.dayOfWeek == DayOfWeek.MONDAY

        fun schoolHoliday(today: LocalDate): SchoolHoliday? = SchoolHolidays.currentOrNext(Paris.startOf(today))
    }
}
