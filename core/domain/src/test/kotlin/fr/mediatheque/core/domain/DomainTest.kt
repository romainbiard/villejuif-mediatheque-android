// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.domain

import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.InboxItem
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Code39Test {
    @Test
    fun everyCharacterHasThreeWideElementsOutOfNine() {
        for ((character, pattern) in Code39.patterns) {
            assertEquals("$character", 9, pattern.length)
            assertEquals("$character", 3, pattern.count { it == 'w' })
        }
    }

    @Test
    fun digitsAreFramedByStartAndStop() {
        val elements = Code39.elements("1234567")!!
        // 9 characters (start, 7 digits, stop) of 9 elements each, and 8 spaces between them.
        assertEquals(9 * 9 + 8, elements.size)
        assertTrue(elements.first().isBar)
        assertTrue(elements.last().isBar)
        val star = Code39.patterns.getValue('*').map { it == 'w' }
        assertEquals(star, elements.take(9).map { it.isWide })
        assertEquals(star, elements.takeLast(9).map { it.isWide })
        // Digit "1" right after the start and its space: w n n w n n n n w.
        assertEquals(listOf(true, false, false, true, false, false, false, false, true), elements.subList(10, 19).map { it.isWide })
        assertEquals((9 * 15 + 8).toDouble(), Code39.width(elements, wideRatio = 3.0), 0.0)
    }

    @Test
    fun unsupportedCharactersAreRejected() {
        assertNull(Code39.elements("é"))
        assertNull(Code39.elements("12*3"))
        assertNotNull(Code39.elements("ab-1"))
    }
}

class ReminderPlannerTest {
    @Test
    fun remindersBeforeAndOnTheDueDayInTheFutureOnly() {
        val reminders = ReminderPlanner.loanReminders(SampleData.loans, daysBefore = 3, now = Paris.instant(2026, 10, 30, 9))
        // "Le Petit Prince" (10/28) has already passed; "Sway" (11/5) and "Chien pourri" (11/19) give 2 reminders each.
        assertEquals(4, reminders.size)
        assertEquals(Paris.instant(2026, 11, 2, 10), reminders.first().fireAt)
        assertEquals(listOf("Sway"), reminders.first().documentTitles)
        assertEquals(Reminder.Kind.LOAN_DUE_IN_DAYS, reminders.first().kind)
        assertEquals(3, reminders.first().daysBefore)
        assertTrue(reminders.all { it.isLoanReminder })
    }

    @Test
    fun loansDueTheSameDayShareOneReminder() {
        val due = Paris.instant(2026, 11, 20)
        val loans = (1..3).map {
            Loan("1_$it", "Livre $it", "", due, Library.PULP_NORD, "PULP Nord", recordId = null, coverUrl = null)
        }
        val reminders = ReminderPlanner.loanReminders(loans, daysBefore = 3, now = Paris.instant(2026, 10, 30))
        assertEquals(2, reminders.size)
        assertEquals(3, reminders[0].documentTitles.size)
        assertEquals(Reminder.Kind.LOAN_DUE_TODAY, reminders[1].kind)
        assertEquals(Paris.instant(2026, 11, 20, 10), reminders[1].fireAt)
    }

    @Test
    fun aReadyHoldGivesItsPickupPlace() {
        val reminder = ReminderPlanner.holdReadyReminder(SampleData.holds[0], now = Paris.instant(2026, 11, 2))
        assertEquals("hold-ready-100001_10", reminder.id)
        assertEquals("Elsa-Triolet", reminder.place)
        assertEquals(listOf("Murmuration"), reminder.documentTitles)
    }
}

class InboxPlannerTest {
    private val snapshot = AccountSnapshot(SampleData.loans, SampleData.holds, Paris.instant(2026, 1, 1))

    @Test
    fun lateDueSoonAndReadyHoldsBecomeItems() {
        // On 11/2: "Le Petit Prince" overdue since 10/28, "Sway" due on 11/5 (3 days left).
        val now = Paris.instant(2026, 11, 2)
        val items = InboxPlanner.items(snapshot, daysBefore = 3, now = now)
        assertEquals(listOf(InboxItem.Kind.LOAN_LATE, InboxItem.Kind.LOAN_DUE_SOON, InboxItem.Kind.HOLD_READY), items.map { it.kind })
        assertEquals("100001_1", items[0].loanId)
        assertEquals(Paris.instant(2026, 10, 29), items[0].date)
        assertEquals("Sway", items[1].documentTitle)
        assertEquals(3, items[1].daysLeft)
        assertEquals("Elsa-Triolet", items[1].place)
        assertTrue(items[1].date <= now)
        assertEquals("Murmuration", items[2].documentTitle)
    }

    @Test
    fun idsAreStableBetweenSyncs() {
        val first = InboxPlanner.items(snapshot, daysBefore = 3, now = Paris.instant(2026, 11, 2, 9))
        val second = InboxPlanner.items(snapshot, daysBefore = 3, now = Paris.instant(2026, 11, 2, 18))
        assertEquals(first.map { it.id }, second.map { it.id })
        val now = Paris.instant(2026, 11, 2)
        assertEquals(InboxPlanner.libraryMessage("Fermé le 11/11", now).id, InboxPlanner.libraryMessage("Fermé le 11/11", now).id)
    }

    @Test
    fun theMessageIsStrippedOfHtml() {
        val item = InboxPlanner.libraryMessage("<p>Fermeture <b>exceptionnelle</b></p>", Paris.instant(2026, 11, 2), isRead = true)
        assertEquals("Fermeture exceptionnelle", item.message)
        assertTrue(item.isRead)
    }

    @Test
    fun theHashIsFnv1a() {
        // Reference values of 64-bit FNV-1a.
        assertEquals("cbf29ce484222325", InboxPlanner.stableHash(""))
        assertEquals("af63dc4c8601ec8c", InboxPlanner.stableHash("a"))
    }
}

class ReturnsSummaryTest {
    @Test
    fun aLateLoanComesFirstThenTheNextReturn() {
        val now = Paris.instant(2026, 10, 31, 9)
        val summary = ReturnsSummary.of(AccountSnapshot(SampleData.loans, emptyList(), now), now) as ReturnsSummary.Late
        assertEquals(1, summary.lateCount)
        assertEquals("Le Petit Prince", summary.firstLateTitle)
        assertEquals("Sway", summary.next?.firstTitle)
        assertEquals(5, summary.next?.daysLeft)
    }

    @Test
    fun theNextReturnAndTheRemainingCount() {
        val snapshot = AccountSnapshot(SampleData.loans.drop(1), emptyList(), Paris.instant(2026, 11, 1))
        assertEquals(
            ReturnsSummary.Upcoming(
                sameDayCount = 1,
                firstTitle = "Sway",
                dueDate = Paris.instant(2026, 11, 5),
                daysLeft = 4,
                laterCount = 1
            ),
            ReturnsSummary.of(snapshot, Paris.instant(2026, 11, 1, 9))
        )
        assertEquals(0, (ReturnsSummary.of(snapshot, Paris.instant(2026, 11, 5, 9)) as ReturnsSummary.Upcoming).daysLeft)
    }

    @Test
    fun noSnapshotOrNoLoans() {
        val now = Paris.instant(2026, 10, 31)
        assertEquals(ReturnsSummary.SignedOut, ReturnsSummary.of(null, now))
        assertEquals(ReturnsSummary.NoLoans, ReturnsSummary.of(AccountSnapshot(emptyList(), emptyList(), now), now))
    }
}

class SchoolHolidaysTest {
    @Test
    fun theHolidaysInProgressOrTheNextOnes() {
        assertEquals(SchoolHoliday.Kind.ALL_SAINTS, SchoolHolidays.currentOrNext(Paris.instant(2026, 10, 20))?.kind)
        // School resumes on 11/2: the next holidays are Christmas.
        assertEquals(SchoolHoliday.Kind.CHRISTMAS, SchoolHolidays.currentOrNext(Paris.instant(2026, 11, 2))?.kind)
        assertNull(SchoolHolidays.currentOrNext(Paris.instant(2028, 9, 1)))
    }
}
