// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.domain

import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Paris
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Answer to "When do I have to return my books?" (app shortcuts, widget), read from the on-device snapshot:
 * no network, and nothing beyond what the widget already shows. The UI words it.
 */
sealed interface ReturnsSummary {
    /** Never signed in, or signed out: prompts to sign in. */
    data object SignedOut : ReturnsSummary

    data object NoLoans : ReturnsSummary

    /** Overdue loans come first, then the next return that is not late, if any. */
    data class Late(val lateCount: Int, val firstLateTitle: String, val next: Upcoming?) : ReturnsSummary

    data class Upcoming(
        /** How many loans are due on the first due day. */
        val sameDayCount: Int,
        val firstTitle: String,
        val dueDate: Instant,
        /** Calendar days from today: 0 is today, 1 is tomorrow. */
        val daysLeft: Int,
        /** Loans due later than the first due day. */
        val laterCount: Int
    ) : ReturnsSummary

    companion object {
        fun of(snapshot: AccountSnapshot?, now: Instant): ReturnsSummary {
            snapshot ?: return SignedOut
            val loans = snapshot.upcomingLoans
            val first = loans.firstOrNull() ?: return NoLoans

            val late = loans.filter { it.dueState(now) is Loan.DueState.Late }
            if (late.isNotEmpty()) {
                val next = loans.firstOrNull { it !in late }?.let { upcoming(it, sameDay = listOf(it), total = 1, now = now) }
                return Late(lateCount = late.size, firstLateTitle = late.first().title, next = next)
            }
            val firstDay = Paris.dayOf(first.dueDate)
            val sameDay = loans.filter { Paris.dayOf(it.dueDate) == firstDay }
            return upcoming(first, sameDay, loans.size, now)
        }

        private fun upcoming(first: Loan, sameDay: List<Loan>, total: Int, now: Instant) = Upcoming(
            sameDayCount = sameDay.size,
            firstTitle = first.title,
            dueDate = first.dueDate,
            daysLeft = ChronoUnit.DAYS.between(Paris.dayOf(now), Paris.dayOf(first.dueDate)).toInt(),
            laterCount = total - sameDay.size
        )
    }
}
