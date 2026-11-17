// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.AccountRepository
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.data.demo.DemoSessionRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.RenewalResult
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.testing.MainDispatcherRule
import fr.mediatheque.core.testing.TestClock
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelsTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = TestClock(Paris.instant(2026, 11, 17, 12))
    private val snapshots = InMemoryValueStore<AccountSnapshot>()

    private object SilentNotifier : SyncNotifier {
        override suspend fun scheduleLoanReminders(reminders: List<Reminder>) = Unit

        override suspend fun setAgendaDigest(enabled: Boolean) = Unit

        override suspend fun notifyHoldReady(reminder: Reminder) = Unit

        override suspend fun accountChanged() = Unit

        override suspend fun clearAll() = Unit
    }

    /** The demo account, made to fail on demand. */
    private class FlakyAccount(private val demo: AccountRepository = DemoAccountRepository()) : AccountRepository by demo {
        var offline = false

        override suspend fun loans(): List<Loan> = if (offline) throw UnknownHostException() else demo.loans()

        override suspend fun holds(): List<Hold> = if (offline) throw UnknownHostException() else demo.holds()

        override suspend fun renew(loanIds: List<String>): List<RenewalResult> =
            if (offline) throw UnknownHostException() else demo.renew(loanIds)

        override suspend fun cancelHold(holdId: String) = if (offline) throw UnknownHostException() else demo.cancelHold(holdId)
    }

    private fun TestScope.sync(account: AccountRepository): AccountSync {
        val settings = SettingsStore(
            settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
            device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
        )
        return AccountSync(account, snapshots, InMemoryValueStore(), SilentNotifier, settings, clock)
    }

    @Test
    fun loansAreGroupedByUrgency() = runTest {
        val account = FlakyAccount()
        val viewModel = LoansViewModel(account, snapshots, sync(account), clock)
        advanceUntilIdle()
        val sections = viewModel.state.value.sections
        assertEquals(listOf(LoanSection.Kind.LATE, LoanSection.Kind.THIS_WEEK), sections.map { it.kind })
        assertEquals(listOf("Le Petit Prince", "Sway"), sections.first().loans.map { it.title })
    }

    @Test
    fun aLateLoanIsRefusedAndTheOthersRenewed() = runTest {
        val account = FlakyAccount()
        val viewModel = LoansViewModel(account, snapshots, sync(account), clock)
        advanceUntilIdle()
        viewModel.renewAll()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(AccountMessage.Renewed(1, 2), state.message)
        assertEquals(setOf("100001_1", "100001_2"), state.refused)
        assertTrue(state.renewing.isEmpty())
        // The list was read again: the renewed loan has its new due date.
        val chien = state.loans.valueOrNull.orEmpty().single { it.id == "100001_3" }
        assertTrue(chien.dueDate > SampleData.loans.single { it.id == "100001_3" }.dueDate)
    }

    @Test
    fun offlineTheSnapshotStaysWithAWarning() = runTest {
        snapshots.save(AccountSnapshot(SampleData.loans, SampleData.holds, clock.instant()))
        val account = FlakyAccount().apply { offline = true }
        val viewModel = LoansViewModel(account, snapshots, sync(account), clock)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loans is LoadState.Loaded)
        assertEquals(LoadError.Offline, viewModel.state.value.staleError)

        viewModel.renewAll()
        advanceUntilIdle()
        assertEquals(AccountMessage.RenewOffline, viewModel.state.value.message)
    }

    @Test
    fun withoutSnapshotOfflineIsAnError() = runTest {
        val account = FlakyAccount().apply { offline = true }
        val viewModel = HoldsViewModel(account, snapshots)
        advanceUntilIdle()
        assertEquals(LoadState.Failed(LoadError.Offline), viewModel.state.value.holds)
    }

    @Test
    fun holdsAreSplitAndCancelled() = runTest {
        val viewModel = HoldsViewModel(FlakyAccount(), snapshots)
        advanceUntilIdle()
        assertEquals(listOf("Murmuration"), viewModel.state.value.ready.map { it.title })
        assertEquals(1, viewModel.state.value.waiting.size)

        viewModel.cancel(viewModel.state.value.ready.single())
        advanceUntilIdle()
        assertTrue(viewModel.state.value.ready.isEmpty())
        assertEquals(AccountMessage.HoldCancelled, viewModel.state.value.message)
    }

    @Test
    fun theAccountIsReadAgainOnlyWhenStale() = runTest {
        var reads = 0
        val account = object : AccountRepository by DemoAccountRepository() {
            override suspend fun patron(): Patron {
                reads++
                return SampleData.patron
            }
        }
        val settings = SettingsStore(
            settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "s.preferences_pb") },
            device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "d.preferences_pb") }
        )
        val accountSync = AccountSync(account, snapshots, InMemoryValueStore(), SilentNotifier, settings, clock)
        val sessionStore = SessionStore(DemoSessionRepository(), InMemoryValueStore(), settings, accountSync, clock)
        sessionStore.restore()
        val viewModel = AccountViewModel(sessionStore, account, accountSync, snapshots, InMemoryValueStore(), settings, clock)

        viewModel.onShown()
        advanceUntilIdle()
        assertEquals(1, reads)
        assertFalse("an automatic read shows no indicator", viewModel.isRefreshing.value)

        // Back from another destination a minute later: nothing read.
        clock.now = clock.now.plusSeconds(60)
        viewModel.onShown()
        advanceUntilIdle()
        assertEquals(1, reads)

        // Pull to refresh always reads.
        viewModel.refresh()
        advanceUntilIdle()
        assertEquals(2, reads)

        // Shown again after the stale delay: read again.
        clock.now = clock.now.plus(AccountViewModel.STALE_AFTER)
        viewModel.onShown()
        advanceUntilIdle()
        assertEquals(3, reads)
    }
}
