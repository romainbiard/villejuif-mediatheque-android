// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.data.demo.DemoCatalogRepository
import fr.mediatheque.core.data.demo.DemoProfileRepository
import fr.mediatheque.core.data.demo.DemoSessionRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.DefaultSelection
import fr.mediatheque.core.model.Paris
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentDetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = TestClock(Paris.instant(2026, 11, 17, 12))

    private object SilentNotifier : SyncNotifier {
        override suspend fun scheduleLoanReminders(reminders: List<Reminder>) = Unit

        override suspend fun setAgendaDigest(enabled: Boolean) = Unit

        override suspend fun notifyHoldReady(reminder: Reminder) = Unit

        override suspend fun accountChanged() = Unit

        override suspend fun clearAll() = Unit
    }

    private class FlakyProfile(private val demo: ProfileRepository = DemoProfileRepository()) : ProfileRepository by demo {
        var offline = false

        override suspend fun setInDefaultSelection(selection: DefaultSelection, recordId: Int, isIn: Boolean) =
            if (offline) throw UnknownHostException() else demo.setInDefaultSelection(selection, recordId, isIn)
    }

    private fun TestScope.sessionStore(): SessionStore {
        val settings = SettingsStore(
            settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
            device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
        )
        val sync = AccountSync(DemoAccountRepository(), InMemoryValueStore(), InMemoryValueStore(), SilentNotifier, settings, clock)
        return SessionStore(DemoSessionRepository(), InMemoryValueStore(), settings, sync, clock)
    }

    @Test
    fun theHeartChangesAtOnceAndComesBackIfTheSiteRefuses() = runTest {
        val store = sessionStore()
        val profile = FlakyProfile()
        val viewModel = DocumentDetailViewModel(SampleData.documents.first(), DemoCatalogRepository(), profile, store)
        advanceUntilIdle()
        // Signed out: the selections are unknown, the heart is not offered.
        assertNull(viewModel.selections.value)

        store.restore()
        advanceUntilIdle()
        assertEquals(emptySet<DefaultSelection>(), viewModel.selections.value)

        viewModel.toggle(DefaultSelection.FAVORITES)
        assertEquals(setOf(DefaultSelection.FAVORITES), viewModel.selections.value)
        advanceUntilIdle()
        assertEquals(DocumentDetailViewModel.SelectionMessage.Added(DefaultSelection.FAVORITES), viewModel.selectionMessage.value)

        profile.offline = true
        viewModel.toggle(DefaultSelection.TO_READ)
        advanceUntilIdle()
        assertEquals(setOf(DefaultSelection.FAVORITES), viewModel.selections.value)
        assertEquals(DocumentDetailViewModel.SelectionMessage.Failed, viewModel.selectionMessage.value)
    }
}
