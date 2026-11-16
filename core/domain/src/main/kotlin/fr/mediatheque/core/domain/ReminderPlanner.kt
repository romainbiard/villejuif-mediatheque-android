// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.domain

import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Paris
import java.time.Instant
import java.time.LocalTime

/**
 * A local notification to schedule. It holds facts, not sentences: the notifications module words it
 * in the device's language.
 */
data class Reminder(
    /** Stable: scheduling the same reminder again replaces it. */
    val id: String,
    val kind: Kind,
    val fireAt: Instant,
    /** Titles of the documents concerned (several loans due the same day share one reminder). */
    val documentTitles: List<String>,
    /** Due date of the loans. */
    val dueDate: Instant? = null,
    /** How many days before the due date, for [Kind.LOAN_DUE_IN_DAYS]. */
    val daysBefore: Int? = null,
    /** Pickup library and the site's status, for [Kind.HOLD_READY]. */
    val place: String? = null,
    val statusText: String? = null
) {
    enum class Kind { LOAN_DUE_IN_DAYS, LOAN_DUE_TODAY, HOLD_READY }

    val isLoanReminder: Boolean get() = id.startsWith(LOAN_PREFIX)

    companion object {
        const val LOAN_PREFIX = "loan-"
    }
}

object ReminderPlanner {
    /** Time of day at which due-date reminders go off. */
    private val reminderTime: LocalTime = LocalTime.of(10, 0)

    /**
     * One reminder [daysBefore] days before each due date, and one on the day itself. Loans due the same day
     * share them. Only reminders still in the future are kept, earliest first.
     */
    fun loanReminders(loans: List<Loan>, daysBefore: Int, now: Instant): List<Reminder> = loans
        .groupBy { Paris.dayOf(it.dueDate) }
        .flatMap { (day, dayLoans) ->
            val titles = dayLoans.map { it.title }
            val dueDate = Paris.startOf(day)
            val key = dueDate.epochSecond
            listOf(
                Reminder(
                    id = "${Reminder.LOAN_PREFIX}before-$key",
                    kind = Reminder.Kind.LOAN_DUE_IN_DAYS,
                    fireAt = day.minusDays(daysBefore.toLong()).atTime(reminderTime).atZone(Paris.zone).toInstant(),
                    documentTitles = titles,
                    dueDate = dueDate,
                    daysBefore = daysBefore
                ),
                Reminder(
                    id = "${Reminder.LOAN_PREFIX}due-$key",
                    kind = Reminder.Kind.LOAN_DUE_TODAY,
                    fireAt = day.atTime(reminderTime).atZone(Paris.zone).toInstant(),
                    documentTitles = titles,
                    dueDate = dueDate
                )
            )
        }
        .filter { it.fireAt > now }
        .sortedBy { it.fireAt }

    /**
     * A hold waiting at the library. The title is generic and the document's title only in the body,
     * hidden on the lock screen when the patron hides notification contents.
     */
    fun holdReadyReminder(hold: Hold, now: Instant) = Reminder(
        id = "hold-ready-${hold.id}",
        kind = Reminder.Kind.HOLD_READY,
        fireAt = now,
        documentTitles = listOf(hold.title),
        place = hold.pickupLibrary?.shortName ?: hold.pickupLibraryName,
        statusText = hold.statusText
    )
}
