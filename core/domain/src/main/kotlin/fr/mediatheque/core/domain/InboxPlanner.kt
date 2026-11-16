// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.domain

import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.InboxItem
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Paris
import java.time.Instant

object InboxPlanner {
    /** Entries derived from the account: upcoming or overdue returns, holds ready for pickup. */
    fun items(snapshot: AccountSnapshot, daysBefore: Int, now: Instant): List<InboxItem> {
        val today = Paris.dayOf(now)
        val items = mutableListOf<InboxItem>()

        for (loan in snapshot.loans) {
            val dueDay = Paris.dayOf(loan.dueDate)
            val dueDate = Paris.startOf(dueDay)
            val dueKey = dueDate.epochSecond
            val place = loan.library?.shortName ?: loan.libraryName
            when (val state = loan.dueState(now)) {
                is Loan.DueState.Late -> items += InboxItem(
                    id = "late-${loan.id}-$dueKey",
                    kind = InboxItem.Kind.LOAN_LATE,
                    date = Paris.startOf(dueDay.plusDays(1)),
                    loanId = loan.id,
                    documentTitle = loan.title,
                    dueDate = dueDate,
                    place = place
                )

                is Loan.DueState.DueSoon -> if (state.days <= daysBefore) {
                    // Dated the day the reminder should have gone off, never in the future.
                    val reminderDay = Paris.startOf(dueDay.minusDays(daysBefore.toLong()))
                    items += InboxItem(
                        id = "due-${loan.id}-$dueKey",
                        kind = InboxItem.Kind.LOAN_DUE_SOON,
                        date = minOf(maxOf(reminderDay, Paris.startOf(today)), now),
                        loanId = loan.id,
                        documentTitle = loan.title,
                        dueDate = dueDate,
                        daysLeft = state.days,
                        place = place
                    )
                }

                is Loan.DueState.OnTime -> Unit
            }
        }

        for (hold in snapshot.holds.filter { it.isReadyForPickup }) {
            items += InboxItem(
                id = "hold-${hold.id}",
                kind = InboxItem.Kind.HOLD_READY,
                date = now,
                documentTitle = hold.title,
                place = hold.pickupLibrary?.shortName ?: hold.pickupLibraryName,
                statusText = hold.statusText
            )
        }
        return items
    }

    /** The message the library shows on the account (`account.message`), as plain text. */
    fun libraryMessage(text: String, now: Instant, isRead: Boolean = false) = InboxItem(
        id = "message-${stableHash(text)}",
        kind = InboxItem.Kind.LIBRARY_MESSAGE,
        date = now,
        isRead = isRead,
        message = text.replace(Regex("<[^>]+>"), "").trim()
    )

    /** 64-bit FNV-1a: the same message gets the same identifier from one sync, and one launch, to the next. */
    internal fun stableHash(text: String): String {
        var hash = FNV_OFFSET
        for (byte in text.toByteArray(Charsets.UTF_8)) {
            hash = (hash xor (byte.toLong() and 0xFF)) * FNV_PRIME
        }
        return hash.toULong().toString(16)
    }

    private const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    private const val FNV_PRIME = 0x100000001b3L
}
