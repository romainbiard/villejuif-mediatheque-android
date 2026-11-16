// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Une entrée du centre de notifications (la cloche de Mon compte). Le site n'a pas d'API de notifications :
 * tout est déduit des prêts, des réservations et du message de la médiathèque, puis gardé sur l'appareil.
 */
@Serializable
data class InboxItem(
    /** Stable d'une synchronisation à l'autre : c'est ce qui évite les doublons. */
    val id: String,
    val kind: Kind,
    val title: String,
    val body: String,
    @Serializable(with = InstantSerializer::class)
    val date: Instant,
    val isRead: Boolean = false,
    /** Prêt concerné, pour « Prolonger » depuis la liste. */
    val loanId: String? = null
) {
    @Serializable
    enum class Kind { LOAN_DUE_SOON, LOAN_LATE, HOLD_READY, LIBRARY_MESSAGE }
}

@Serializable
data class Inbox(val items: List<InboxItem> = emptyList()) {
    val unreadCount: Int get() = items.count { !it.isRead }

    /**
     * Ajoute les nouvelles entrées (non lues), garde l'état lu des anciennes et retire celles de plus de
     * [RETENTION_DAYS] jours. Rend la boîte à jour et les entrées ajoutées.
     */
    fun merge(fresh: List<InboxItem>, now: Instant): Merge {
        val known = items.map { it.id }.toSet()
        val added = fresh.filter { it.id !in known }
        val limit = now.atZone(Paris.zone).minusDays(RETENTION_DAYS).toInstant()
        val merged = (items + added)
            .filter { it.date >= limit }
            .sortedByDescending { it.date }
        return Merge(Inbox(merged), added)
    }

    data class Merge(val inbox: Inbox, val added: List<InboxItem>)

    fun markRead(id: String): Inbox = copy(items = items.map { if (it.id == id) it.copy(isRead = true) else it })

    fun markAllRead(): Inbox = copy(items = items.map { it.copy(isRead = true) })

    companion object {
        /** Durée de conservation de l'historique sur l'appareil. */
        const val RETENTION_DAYS = 30L
    }
}
