// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.notifications

import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.domain.ReminderPlanner
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import java.time.DayOfWeek
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderWorkTest {
    private val now = Paris.instant(2026, 11, 17, 12)

    @Test
    fun aReminderWaitsUntilItsTime() {
        val reminder = ReminderPlanner.loanReminders(SampleData.loans, daysBefore = 1, now = now).first()
        // "Chien pourri", due November 19: the day before at 10 am.
        assertEquals(Paris.instant(2026, 11, 18, 10), reminder.fireAt)
        val request = ReminderWork.loanRequest(reminder, now)
        assertEquals(Duration.ofHours(22).toMillis(), request.workSpec.initialDelay)
        assertTrue(ReminderWork.LOAN_TAG in request.tags)
    }

    @Test
    fun aPastReminderShowsAtOnce() {
        assertEquals(Duration.ZERO, ReminderWork.delay(now.minusSeconds(60), now))
    }

    @Test
    fun theReminderSurvivesTheWorkData() {
        val reminder = ReminderPlanner.loanReminders(SampleData.loans, daysBefore = 1, now = now).first {
            it.kind ==
                Reminder.Kind.LOAN_DUE_IN_DAYS
        }
        assertEquals(reminder, ReminderWork.reminder(ReminderWork.inputData(reminder)))
        val today = reminder.copy(kind = Reminder.Kind.LOAN_DUE_TODAY, daysBefore = null, documentTitles = listOf("A", "B"))
        assertEquals(today, ReminderWork.reminder(ReminderWork.inputData(today)))
    }

    @Test
    fun theAgendaReminderIsOnMondayAtNine() {
        // Tuesday November 17, 2026: next Monday is November 23.
        assertEquals(Paris.instant(2026, 11, 23, 9), ReminderWork.nextAgendaDigest(now))
        // On a Monday before 9 am, the same morning; at 9 am, the week after.
        assertEquals(Paris.instant(2026, 11, 23, 9), ReminderWork.nextAgendaDigest(Paris.instant(2026, 11, 23, 8)))
        assertEquals(Paris.instant(2026, 11, 30, 9), ReminderWork.nextAgendaDigest(Paris.instant(2026, 11, 23, 9)))
        assertEquals(DayOfWeek.MONDAY, ReminderWork.nextAgendaDigest(now).atZone(Paris.zone).dayOfWeek)
        val request = ReminderWork.agendaRequest(now)
        assertEquals(TimeUnit.DAYS.toMillis(5) + TimeUnit.HOURS.toMillis(21), request.workSpec.initialDelay)
    }
}
