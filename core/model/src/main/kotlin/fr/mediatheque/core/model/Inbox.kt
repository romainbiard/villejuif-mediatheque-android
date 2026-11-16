// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * An entry in the notification center (the bell in My Account). The site has no notifications API:
 * everything is derived from loans, holds, and the library's message, then kept on the device.
 */
@Serializable
data class InboxItem(
    /** Stable from one sync to the next: this is what prevents duplicates. */
    val id: String,
    val kind: Kind,
    val title: String,
    val body: String,
    @Serializable(with = InstantSerializer::class)
    val date: Instant,
    val isRead: Boolean = false,
    /** Loan concerned, for "Prolonger" (renew) from the list. */
    val loanId: String? = null
) {
    @Serializable
    enum class Kind { LOAN_DUE_SOON, LOAN_LATE, HOLD_READY, LIBRARY_MESSAGE }
}

@Serializable
data class Inbox(val items: List<InboxItem> = emptyList()) {
    val unreadCount: Int get() = items.count { !it.isRead }

    /**
     * Adds the new entries (unread), keeps the read state of the old ones, and removes those older than
     * [RETENTION_DAYS] days. Returns the updated inbox and the entries that were added.
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
        /** How long the history is kept on the device. */
        const val RETENTION_DAYS = 30L
    }
}
