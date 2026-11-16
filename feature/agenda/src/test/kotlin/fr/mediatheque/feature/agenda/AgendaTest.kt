// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import fr.mediatheque.core.data.AgendaRepository
import fr.mediatheque.core.data.CachePolicy
import fr.mediatheque.core.data.Cached
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.EventDetail
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.testing.MainDispatcherRule
import fr.mediatheque.core.testing.TestClock
import java.net.UnknownHostException
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AgendaLogicTest {
    private val events = SampleData.events

    // Saturday 31 October 2026: "Ronde des histoires", during the prize that runs until March.
    private val saturday = LocalDate.of(2026, 10, 31)

    @Test
    fun eventsOfTheDayAndOngoing() {
        val logic = AgendaLogic(events, AgendaFilters(), saturday)
        assertEquals(listOf(990), logic.eventsOn(saturday).map { it.id })
        assertEquals(listOf(983), logic.ongoingOn(saturday).map { it.id })
        assertEquals(14, logic.days.size)
        assertTrue(LocalDate.of(2026, 11, 4) in logic.daysWithEvents)
    }

    @Test
    fun anEmptyDaySuggestsTheNextDayWithEvents() {
        val monday = LocalDate.of(2026, 11, 2)
        val logic = AgendaLogic(events, AgendaFilters(), saturday)
        assertTrue(logic.eventsOn(monday).isEmpty())
        assertTrue(AgendaLogic.isLibraryClosingDay(monday))
        assertEquals(LocalDate.of(2026, 11, 4), logic.nextDayWithEvents(monday))
    }

    @Test
    fun filtersAreAppliedLocally() {
        val filters = AgendaFilters(types = setOf("Rencontre"))
        val logic = AgendaLogic(events, filters, saturday)
        assertEquals(1, logic.upcomingCount(filters))
        assertTrue(logic.eventsOn(saturday).isEmpty())
        assertEquals(listOf("Lectures", "Rencontre"), logic.availableTypes)
        // The libraries come first among the places.
        assertEquals(listOf("Médiathèque Elsa-Triolet", "Pulp Nord"), logic.availablePlaces)
    }

    @Test
    fun weekendDays() {
        val thursday = LocalDate.of(2026, 10, 29)
        assertEquals(listOf(saturday, LocalDate.of(2026, 11, 1)), AgendaPeriod.WEEKEND.days(thursday))
        assertEquals(listOf(saturday, LocalDate.of(2026, 11, 1)), AgendaPeriod.WEEKEND.days(saturday))
        assertEquals(listOf(LocalDate.of(2026, 11, 1)), AgendaPeriod.WEEKEND.days(LocalDate.of(2026, 11, 1)))
        assertTrue(AgendaPeriod.ALL.days(thursday).isEmpty())
    }

    @Test
    fun wednesdaysStartAtTheNextOne() {
        val days = AgendaPeriod.WEDNESDAYS.days(LocalDate.of(2026, 10, 29))
        assertEquals(AgendaPeriod.WEDNESDAY_WEEKS, days.size)
        assertEquals(LocalDate.of(2026, 11, 4), days.first())
        assertEquals(LocalDate.of(2026, 11, 4), AgendaPeriod.WEDNESDAYS.days(LocalDate.of(2026, 11, 4)).first())
    }

    @Test
    fun schoolHolidaysAreTheCurrentOrNextOnes() {
        val allSaints = AgendaPeriod.SCHOOL_HOLIDAYS.days(LocalDate.of(2026, 10, 29))
        assertEquals((29..31).map { LocalDate.of(2026, 10, it) } + LocalDate.of(2026, 11, 1), allSaints)
        val christmas = AgendaPeriod.SCHOOL_HOLIDAYS.days(LocalDate.of(2026, 11, 10))
        assertEquals(LocalDate.of(2026, 12, 19), christmas.first())
        assertEquals(LocalDate.of(2027, 1, 3), christmas.last())
    }

    @Test
    fun theWeekendGroupsEventsByDay() {
        val filters = AgendaFilters(period = AgendaPeriod.WEEKEND)
        val logic = AgendaLogic(events, filters, LocalDate.of(2026, 10, 29))
        assertEquals(listOf(saturday), logic.periodDays.map { it.day })
        assertEquals(listOf(990), logic.periodDays.first().events.map { it.id })
        assertEquals(listOf(983), logic.periodOngoing.map { it.id })
        assertEquals(1, filters.count)
    }
}

class AgendaViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private class FakeAgenda(var result: () -> Cached<List<Event>>) : AgendaRepository {
        override suspend fun events(policy: CachePolicy) = result()

        override suspend fun detail(event: Event) = EventDetail(null, null, null)
    }

    private val clock = TestClock(Paris.instant(2026, 10, 31, 9))

    @Test
    fun theSavedAgendaSaysItCouldNotUpdate() {
        val repository = FakeAgenda { Cached(SampleData.events) }
        val viewModel = AgendaViewModel(repository, clock)
        assertEquals(LoadState.Loaded(SampleData.events), viewModel.state.value.events)
        assertNull(viewModel.state.value.staleError)

        repository.result = { Cached(SampleData.events, refreshError = LoadError.Offline) }
        viewModel.refresh()
        assertEquals(LoadError.Offline, viewModel.state.value.staleError)
        assertFalse(viewModel.state.value.isRefreshing)
    }

    @Test
    fun aFailureWithoutSavedAgendaIsShownAndAFailedRefreshKeepsTheList() {
        val repository = FakeAgenda { throw UnknownHostException() }
        val viewModel = AgendaViewModel(repository, clock)
        assertEquals(LoadState.Failed(LoadError.Offline), viewModel.state.value.events)

        repository.result = { Cached(SampleData.events) }
        viewModel.retry()
        repository.result = { throw UnknownHostException() }
        viewModel.refresh()
        assertEquals(LoadState.Loaded(SampleData.events), viewModel.state.value.events)
        assertEquals(LoadError.Offline, viewModel.state.value.staleError)
    }

    @Test
    fun selectingADayAndAPeriod() {
        val viewModel = AgendaViewModel(FakeAgenda { Cached(SampleData.events) }, clock)
        assertEquals(LocalDate.of(2026, 10, 31), viewModel.state.value.selectedDay)
        viewModel.selectDay(LocalDate.of(2026, 11, 4))
        assertEquals(listOf(1019), viewModel.state.value.logic.eventsOn(viewModel.state.value.selectedDay).map { it.id })
        viewModel.selectPeriod(AgendaPeriod.WEEKEND)
        viewModel.clearFilters()
        assertEquals(AgendaPeriod.ALL, viewModel.state.value.filters.period)
    }
}
