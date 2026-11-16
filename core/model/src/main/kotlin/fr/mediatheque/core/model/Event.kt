// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** An event in the calendar. */
@Serializable
data class Event(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val place: String?,
    val library: Library?,
    @Serializable(with = InstantSerializer::class)
    val start: Instant?,
    @Serializable(with = InstantSerializer::class)
    val end: Instant?,
    /** True for a multi-day event with no time of day (exhibition, prize…). */
    val isAllDay: Boolean,
    val type: String?,
    val audience: String?,
    /** Sentence from the site, for example "Samedi 31 octobre 2026 de 10:30 à 11:00". */
    val dateDescription: String?
) {
    /** True if the event takes place, at least in part, on [day] (in Paris). */
    fun occursOn(day: LocalDate): Boolean {
        val first = Paris.dayOf(start ?: return false)
        val last = Paris.dayOf(end ?: start)
        return day in first..last
    }
}

/** Detail parsed from an event's iCal. */
data class EventDetail(val description: String?, val latitude: Double?, val longitude: Double?)
