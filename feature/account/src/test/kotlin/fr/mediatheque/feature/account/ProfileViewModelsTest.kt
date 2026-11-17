// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.ProfileError
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.data.demo.DemoProfileRepository
import fr.mediatheque.core.data.demo.DemoSessionRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.ContactDetails
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.InboxItem
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.model.SendingChannel
import fr.mediatheque.core.model.SuggestionDraft
import fr.mediatheque.core.testing.MainDispatcherRule
import fr.mediatheque.core.testing.TestClock
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelsTest {
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

    /** The demo profile, made to fail on demand: offline, or rejected by the site. */
    private class FlakyProfile(private val demo: ProfileRepository = DemoProfileRepository()) : ProfileRepository by demo {
        var failure: Exception? = null

        override suspend fun sendSuggestion(draft: SuggestionDraft) = failure?.let { throw it } ?: demo.sendSuggestion(draft)

        override suspend fun setLibraryBookmarked(library: Library, isBookmarked: Boolean) =
            failure?.let { throw it } ?: demo.setLibraryBookmarked(library, isBookmarked)
    }

    private fun TestScope.settings() = SettingsStore(
        settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
        device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
    )

    private fun TestScope.sync(settings: SettingsStore, inbox: InMemoryValueStore<Inbox> = InMemoryValueStore()) =
        AccountSync(DemoAccountRepository(), InMemoryValueStore(), inbox, SilentNotifier, settings, clock)

    private suspend fun TestScope.signedIn(settings: SettingsStore): SessionStore {
        val store = SessionStore(DemoSessionRepository(), InMemoryValueStore(), settings, sync(settings), clock)
        store.restore()
        return store
    }

    @Test
    fun selectionsLoadAndANewOneIsCreated() = runTest {
        val viewModel = SelectionsViewModel(DemoProfileRepository())
        advanceUntilIdle()
        assertEquals(SampleData.selections, viewModel.selections.value.valueOrNull)

        viewModel.create("  mes préférés ")
        assertEquals(ProfileMessage.DuplicateSelection, viewModel.message.value)

        viewModel.create("Vacances")
        advanceUntilIdle()
        assertEquals(ProfileMessage.SelectionCreated("Vacances"), viewModel.message.value)
        assertFalse(viewModel.isCreating.value)
    }

    @Test
    fun aDocumentIsRemovedFromASelection() = runTest {
        val selection = SampleData.selections.first()
        val viewModel = SelectionDetailViewModel(selection, DemoProfileRepository())
        advanceUntilIdle()
        val notices = viewModel.notices.value.valueOrNull.orEmpty()
        assertEquals(selection.count, notices.size)

        viewModel.remove(notices.first())
        advanceUntilIdle()
        assertEquals(selection.count - 1, viewModel.notices.value.valueOrNull?.size)
        assertEquals(ProfileMessage.RemovedFrom(selection.name), viewModel.message.value)
        assertTrue(viewModel.removing.value.isEmpty())
    }

    @Test
    fun aReviewNeedsARatingAndLeavesTheList() = runTest {
        val viewModel = ReviewsViewModel(DemoProfileRepository())
        advanceUntilIdle()
        val record = viewModel.toRate.value.valueOrNull!!.first()
        var closed = false

        viewModel.submit(record, rating = 0, title = "", text = "") { closed = true }
        advanceUntilIdle()
        assertFalse(closed)

        viewModel.submit(record, rating = 4, title = " Super ", text = "") { closed = true }
        advanceUntilIdle()
        assertTrue(closed)
        assertEquals(ProfileMessage.ReviewSent, viewModel.message.value)
        assertFalse(viewModel.toRate.value.valueOrNull.orEmpty().any { it.recordId == record.recordId })
    }

    @Test
    fun aSuggestionIsSentOnlyWhenValid() = runTest {
        val profile = FlakyProfile()
        val viewModel = SuggestionsViewModel(profile)
        advanceUntilIdle()

        viewModel.updateDraft { it.copy(title = "Le Petit Prince") }
        viewModel.send()
        advanceUntilIdle()
        assertNull(viewModel.message.value)

        // The site's own reason shows as it is.
        profile.failure = ProfileError.Rejected("Ce document est déjà au catalogue.")
        viewModel.updateDraft { it.copy(author = "Antoine de Saint-Exupéry") }
        viewModel.send()
        advanceUntilIdle()
        assertEquals(ProfileMessage.Rejected("Ce document est déjà au catalogue."), viewModel.message.value)
        assertEquals("Le Petit Prince", viewModel.draft.value.title)

        profile.failure = null
        viewModel.send()
        advanceUntilIdle()
        assertEquals(ProfileMessage.SuggestionSent, viewModel.message.value)
        assertEquals(SuggestionDraft(), viewModel.draft.value)
    }

    @Test
    fun contactDetailsAreCheckedThenSaved() = runTest {
        val credentials = InMemoryValueStore(Credentials(SampleData.patron.cardNumber, "ancien"))
        val viewModel = InformationViewModel(DemoProfileRepository(), credentials, signedIn(settings()))
        advanceUntilIdle()
        val details = viewModel.contact.value.valueOrNull!!
        var saved = false

        viewModel.save(details.copy(email = "camille.martin"), onSaved = { saved = true })
        assertEquals(ProfileMessage.InvalidEmail, viewModel.message.value)

        val updated = ContactDetails("Camille", " camille.martin@exemple.fr ", "", SendingChannel.SMS)
        viewModel.save(updated, onSaved = { saved = true })
        advanceUntilIdle()
        assertTrue(saved)
        assertEquals(updated.copy(email = "camille.martin@exemple.fr"), viewModel.contact.value.valueOrNull)
        assertEquals(ProfileMessage.ContactSaved, viewModel.message.value)
    }

    @Test
    fun aNewPasswordIsKeptForTheAutomaticSignIn() = runTest {
        val credentials = InMemoryValueStore(Credentials(SampleData.patron.cardNumber, "ancien"))
        val viewModel = InformationViewModel(DemoProfileRepository(), credentials, signedIn(settings()))
        advanceUntilIdle()
        var offered: Credentials? = null

        viewModel.changePassword("nouveau", "nouvel", onChanged = { offered = it })
        assertEquals(ProfileMessage.PasswordMismatch, viewModel.message.value)
        assertEquals(ProfileMessage.PasswordEmpty, InformationViewModel.passwordProblem("", ""))

        viewModel.changePassword("nouveau", "nouveau", onChanged = { offered = it })
        advanceUntilIdle()
        assertEquals("nouveau", credentials.value?.password)
        assertEquals(credentials.value, offered)
        assertEquals(ProfileMessage.PasswordChanged, viewModel.message.value)
    }

    @Test
    fun librariesAreBookmarkedAndFailuresReported() = runTest {
        val profile = FlakyProfile()
        val settings = settings()
        val viewModel = ProfileSettingsViewModel(profile, settings, signedIn(settings), sync(settings))
        advanceUntilIdle()
        assertEquals(setOf(Library.PULP_NORD), viewModel.bookmarks.value.valueOrNull)

        viewModel.setBookmarked(Library.ELSA_TRIOLET, true)
        advanceUntilIdle()
        assertEquals(setOf(Library.PULP_NORD, Library.ELSA_TRIOLET), viewModel.bookmarks.value.valueOrNull)

        profile.failure = UnknownHostException()
        viewModel.setBookmarked(Library.PULP_SUD, true)
        advanceUntilIdle()
        assertEquals(ProfileMessage.Failed(ProfileMessage.Action.MODIFICATION, offline = true), viewModel.message.value)
        assertTrue(viewModel.updating.value.isEmpty())

        // DataStore writes off the test dispatcher: wait for the clamped value to arrive.
        viewModel.setDaysBefore(30)
        advanceUntilIdle()
        settings.reminderSettings.first { it.daysBefore == ProfileSettingsViewModel.MAX_DAYS_BEFORE }
    }

    @Test
    fun theInboxIsGroupedAndMarkedRead() = runTest {
        val store = InMemoryValueStore(SampleData.inbox)
        val settings = settings()
        val viewModel = NotificationsViewModel(store, DemoAccountRepository(), sync(settings, store), clock)
        advanceUntilIdle()
        // The sample entries are all more than a week old.
        val sections = viewModel.sections(viewModel.inbox.value)
        assertEquals(listOf(InboxSection.Kind.EARLIER), sections.map { it.kind })
        assertEquals(SampleData.inbox.items.sortedByDescending { it.date }, sections.single().items)

        viewModel.markAllRead()
        advanceUntilIdle()
        assertEquals(0, store.value?.unreadCount)
    }

    @Test
    fun aLateLoanCannotBeRenewedFromTheInbox() = runTest {
        val store = InMemoryValueStore(SampleData.inbox)
        val viewModel = NotificationsViewModel(store, DemoAccountRepository(), sync(settings(), store), clock)
        advanceUntilIdle()
        val late = viewModel.inbox.value.items.single { it.kind == InboxItem.Kind.LOAN_LATE }
        assertTrue(viewModel.canRenew(late))

        viewModel.renew(late)
        advanceUntilIdle()
        assertEquals(ProfileMessage.RenewRefused, viewModel.message.value)
        assertTrue(viewModel.canRenew(late))
        assertTrue(viewModel.renewing.value.isEmpty())
        assertTrue(viewModel.renewed.value.isEmpty())
    }
}
