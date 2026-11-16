// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.serialization.Serializable

/** Regular opening hours of a library, parsed from "Mardi : 13h - 19h". */
@Serializable
data class OpeningHours(val slotsByDay: Map<DayOfWeek, List<Slot>>) {
    @Serializable
    data class Slot(
        /** Minutes since midnight. */
        val opens: Int,
        val closes: Int
    )

    sealed interface Status {
        data class Open(val closesAt: Instant) : Status

        data class Closed(val reopensAt: Instant?) : Status
    }

    /** Open or closed at [now], and the time of the next change. */
    fun status(now: Instant): Status {
        val local = now.atZone(Paris.zone)
        val today = local.toLocalDate()
        val minutes = local.hour * 60 + local.minute
        val slots = slotsByDay[today.dayOfWeek].orEmpty()

        slots.firstOrNull { it.opens <= minutes && minutes < it.closes }?.let {
            return Status.Open(closesAt = at(today, it.closes))
        }
        slots.firstOrNull { minutes < it.opens }?.let {
            return Status.Closed(reopensAt = at(today, it.opens))
        }
        for (offset in 1L..7L) {
            val day = today.plusDays(offset)
            val slot = slotsByDay[day.dayOfWeek]?.firstOrNull() ?: continue
            return Status.Closed(reopensAt = at(day, slot.opens))
        }
        return Status.Closed(reopensAt = null)
    }

    /** Day with no time slot at all (Sunday and Monday for the network). */
    fun isClosedAllDay(day: LocalDate): Boolean = slotsByDay[day.dayOfWeek].isNullOrEmpty()

    private fun at(day: LocalDate, minutes: Int): Instant =
        day.atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(Paris.zone).toInstant()

    companion object {
        private val days = mapOf(
            "lundi" to DayOfWeek.MONDAY,
            "mardi" to DayOfWeek.TUESDAY,
            "mercredi" to DayOfWeek.WEDNESDAY,
            "jeudi" to DayOfWeek.THURSDAY,
            "vendredi" to DayOfWeek.FRIDAY,
            "samedi" to DayOfWeek.SATURDAY,
            "dimanche" to DayOfWeek.SUNDAY
        )
        private val range = Regex("""(\d{1,2})h(\d{2})?\s*-\s*(\d{1,2})h(\d{2})?""")

        /** Lines from the site: "Mardi : 13h - 19h", "Samedi : 10h - 12h30 / 14h - 18h". */
        fun parse(lines: List<String>): OpeningHours {
            val slots = mutableMapOf<DayOfWeek, MutableList<Slot>>()
            for (line in lines) {
                val parts = line.split(':', limit = 2)
                if (parts.size != 2) continue
                val day = days[parts[0].trim().lowercase(Paris.locale)] ?: continue
                for (match in range.findAll(parts[1])) {
                    val (openHour, openMinute, closeHour, closeMinute) = match.destructured
                    slots.getOrPut(day) { mutableListOf() } += Slot(
                        opens = openHour.toInt() * 60 + (openMinute.toIntOrNull() ?: 0),
                        closes = closeHour.toInt() * 60 + (closeMinute.toIntOrNull() ?: 0)
                    )
                }
            }
            return OpeningHours(slots)
        }
    }
}

/** Practical information about a library. */
@Serializable
data class LibraryInfo(
    val library: Library?,
    val slug: String,
    val name: String,
    val addressLines: List<String>,
    val phone: String?,
    val contactUrl: String?,
    val hours: OpeningHours,
    val hourLines: List<String>
) {
    val id: String get() = library?.code ?: slug
}
