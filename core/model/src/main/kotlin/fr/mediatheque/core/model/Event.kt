// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/** Une animation de l'agenda. */
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
    /** Vrai pour un événement de plusieurs jours sans heure (exposition, prix…). */
    val isAllDay: Boolean,
    val type: String?,
    val audience: String?,
    /** Phrase du site, par exemple « Samedi 31 octobre 2026 de 10:30 à 11:00 ». */
    val dateDescription: String?
) {
    /** Vrai si l'événement a lieu, au moins en partie, le jour [day] (à Paris). */
    fun occursOn(day: LocalDate): Boolean {
        val first = Paris.dayOf(start ?: return false)
        val last = Paris.dayOf(end ?: start)
        return day in first..last
    }
}

/** Détail lu dans l'iCal d'un événement. */
data class EventDetail(val description: String?, val latitude: Double?, val longitude: Double?)
