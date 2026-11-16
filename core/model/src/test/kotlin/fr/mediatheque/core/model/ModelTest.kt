// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    @Test
    fun librariesAreFoundFromTheSiteLabels() {
        assertEquals(Library.PULP_NORD, Library.fromLabel("Pulp Nord"))
        assertEquals(Library.PULP_SUD, Library.fromLabel("PULP Sud"))
        assertEquals(Library.ELSA_TRIOLET, Library.fromLabel("Médiathèque Elsa-Triolet"))
        assertNull(Library.fromLabel("Théâtre Romain Rolland"))
    }

    @Test
    fun codesAndSlugsRoundTrip() {
        assertEquals(Library.PULP_NORD, Library.fromCode("3"))
        assertEquals(Library.ELSA_TRIOLET, Library.fromSlug("elsa_triolet"))
        assertEquals("Y2", Library.PULP_SUD.searchFacet)
    }
}

class OpeningHoursTest {
    private val elsa = OpeningHours.parse(listOf("Mardi : 13h - 19h", "Mercredi : 10h - 19h", "Samedi : 10h - 12h30 / 14h - 18h"))

    @Test
    fun openDuringASlot() {
        // Wednesday, November 4, 2026, 11 a.m.
        assertEquals(
            OpeningHours.Status.Open(closesAt = Paris.instant(2026, 11, 4, 19)),
            elsa.status(Paris.instant(2026, 11, 4, 11))
        )
    }

    @Test
    fun closedBeforeOpeningTheSameDay() {
        assertEquals(
            OpeningHours.Status.Closed(reopensAt = Paris.instant(2026, 11, 3, 13)),
            elsa.status(Paris.instant(2026, 11, 3, 9))
        )
    }

    @Test
    fun closedOverTheWeekendReopensTuesday() {
        // Sunday, November 1, 2026.
        assertEquals(
            OpeningHours.Status.Closed(reopensAt = Paris.instant(2026, 11, 3, 13)),
            elsa.status(Paris.instant(2026, 11, 1, 15))
        )
        assertTrue(elsa.isClosedAllDay(LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun lunchBreakIsHandled() {
        // Saturday, October 31, 2026, 1 p.m.: between the two slots.
        assertEquals(
            OpeningHours.Status.Closed(reopensAt = Paris.instant(2026, 10, 31, 14)),
            elsa.status(Paris.instant(2026, 10, 31, 13))
        )
        assertEquals(listOf(OpeningHours.Slot(600, 750), OpeningHours.Slot(840, 1080)), elsa.slotsByDay[java.time.DayOfWeek.SATURDAY])
    }

    @Test
    fun theDaylightSavingSwitchKeepsLocalTimes() {
        // Sunday, October 25, 2026: switch to winter time; on Tuesday the 27th, it does open at 1 p.m., Paris time.
        assertEquals(
            OpeningHours.Status.Closed(reopensAt = Paris.instant(2026, 10, 27, 13)),
            elsa.status(Paris.instant(2026, 10, 25, 12))
        )
    }
}

class LoanAndHoldTest {
    private val now = Paris.instant(2026, 10, 30, 21)

    private fun loan(id: String, dueDate: java.time.Instant) = Loan(
        id = id,
        title = "Titre",
        author = "Auteur",
        dueDate = dueDate,
        library = Library.ELSA_TRIOLET,
        libraryName = "Médiathèque Elsa-Triolet",
        recordId = null,
        coverUrl = null
    )

    private fun hold(id: String, status: String) = Hold(
        id = id,
        title = "Titre",
        statusText = status,
        pickupLibrary = Library.PULP_NORD,
        pickupLibraryName = "PULP Nord",
        recordId = null,
        coverUrl = null
    )

    @Test
    fun dueStatesCountCalendarDaysInParis() {
        assertEquals(Loan.DueState.Late(2), loan("100001_1", Paris.instant(2026, 10, 28)).dueState(now))
        assertEquals(Loan.DueState.DueSoon(6), loan("100001_2", Paris.instant(2026, 11, 5)).dueState(now))
        assertEquals(Loan.DueState.OnTime(20), loan("100001_3", Paris.instant(2026, 11, 19)).dueState(now))
    }

    @Test
    fun patronIdComesFromTheLoanId() {
        assertEquals("100001", loan("100001_36674", now).patronId)
    }

    @Test
    fun holdReadinessAndExpectedDate() {
        val waiting = hold("100001_2", "Pas disponible avant le 14/11/2026")
        assertEquals(Paris.instant(2026, 11, 14), waiting.expectedDate)
        assertFalse(waiting.isReadyForPickup)
        assertTrue(hold("100001_1", "Disponible").isReadyForPickup)
        assertTrue(hold("100001_3", "À retirer avant le 20/11/2026").isReadyForPickup)
        assertTrue(hold("100001_4", "En attente de retrait").isReadyForPickup)
    }

    @Test
    fun snapshotTellsWhichHoldsJustBecameReady() {
        val before = AccountSnapshot(emptyList(), listOf(hold("a", "Pas disponible avant le 14/11/2026"), hold("b", "Disponible")), now)
        val after = before.copy(holds = listOf(hold("a", "Disponible"), hold("b", "Disponible")))
        assertEquals(listOf("a"), after.newlyReadyHolds(before).map { it.id })
        assertEquals(listOf("a", "b"), after.newlyReadyHolds(null).map { it.id })
    }

    @Test
    fun displayNameCapitalizesTheLastName() {
        val patron = Patron(
            cardNumber = "0012345", firstName = "Camille", lastName = "MARTIN-DUPONT", email = null, phone = null,
            homeLibrary = null, cardExpiry = null, cardExpired = false, cardAboutToExpire = false, cardExpiresInDays = 0,
            permissions = Patron.Permissions(holdForbidden = false, digitalResourcesForbidden = false),
            counters = Patron.Counters(loans = 0, lateLoans = 0, waitingToBePulled = 0),
            message = null, hasUnreadMessage = false
        )
        assertEquals("Camille Martin-Dupont", patron.displayName)
    }
}

class EventAndNoticeTest {
    @Test
    fun aMultiDayEventOccursOnEachOfItsDays() {
        val prize = Event(
            id = 983, title = "Prix", imageUrl = null, place = null, library = null,
            start = Paris.instant(2026, 9, 26), end = Paris.instant(2027, 3, 27), isAllDay = true,
            type = null, audience = null, dateDescription = null
        )
        assertTrue(prize.occursOn(LocalDate.of(2026, 12, 1)))
        assertFalse(prize.occursOn(LocalDate.of(2027, 3, 28)))
        assertFalse(prize.copy(start = null).occursOn(LocalDate.of(2026, 12, 1)))
    }

    @Test
    fun noticeLibrariesAreUniqueAndInNetworkOrder() {
        val notice = Notice(
            id = 1, title = "Titre", authors = emptyList(), year = null, publisher = null, kind = DocumentKind.BOOK,
            coverUrl = null, series = null, volume = null, permalink = null,
            harvestedCopies = listOf(
                HarvestedCopy(Library.PULP_NORD),
                HarvestedCopy(Library.ELSA_TRIOLET),
                HarvestedCopy(Library.PULP_NORD)
            )
        )
        assertEquals(listOf(Library.ELSA_TRIOLET, Library.PULP_NORD), notice.libraries)
        assertEquals(listOf("T1", "Y3"), SearchQuery("dune", listOf(DocumentKind.BOOK), listOf(Library.PULP_NORD)).facets)
        assertTrue(DocumentKind("ArteVod", "Vidéo").isDigital)
    }
}

class InboxTest {
    private val now = Paris.instant(2026, 11, 16, 9)

    private fun item(id: String, date: java.time.Instant, isRead: Boolean = false) =
        InboxItem(id = id, kind = InboxItem.Kind.LOAN_DUE_SOON, title = id, body = "", date = date, isRead = isRead)

    @Test
    fun mergeAddsOnlyNewEntriesKeepsReadStateAndDropsOldOnes() {
        val inbox = Inbox(listOf(item("old", Paris.instant(2026, 10, 1)), item("kept", Paris.instant(2026, 11, 10), isRead = true)))
        val result = inbox.merge(listOf(item("kept", now), item("new", now)), now)

        assertEquals(listOf("new"), result.added.map { it.id })
        assertEquals(listOf("new", "kept"), result.inbox.items.map { it.id })
        assertTrue(result.inbox.items.first { it.id == "kept" }.isRead)
        assertEquals(1, result.inbox.unreadCount)
        assertEquals(0, result.inbox.markAllRead().unreadCount)
    }

    @Test
    fun theSnapshotSurvivesAJsonRoundTrip() {
        val snapshot = AccountSnapshot(emptyList(), emptyList(), now)
        assertEquals(snapshot, Json.decodeFromString<AccountSnapshot>(Json.encodeToString(snapshot)))
    }
}

class BokehDatesTest {
    @Test
    fun datesFromTheApiAndTheHtml() {
        assertEquals(Paris.instant(2026, 11, 20), BokehDates.isoDay("2026-11-20"))
        assertNull(BokehDates.isoDay("0000-00-00"))
        assertEquals(
            BokehDates.FrenchDate(Paris.instant(2026, 10, 31, 10, 30), hasTime = true),
            BokehDates.frenchDate("31/10/2026 à 10:30")
        )
        assertEquals(BokehDates.FrenchDate(Paris.instant(2026, 9, 26), hasTime = false), BokehDates.frenchDate("26/09/2026"))
        assertNull(BokehDates.frenchDate("31/02/2026"))
    }
}
