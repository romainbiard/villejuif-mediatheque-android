// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.SampleData
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = Paris.zone

    override fun withZone(zone: ZoneId?): Clock = this

    override fun instant(): Instant = now
}

private class RecordingNotifier : SyncNotifier {
    var reminders: List<Reminder> = emptyList()
    val holdsReady = mutableListOf<Reminder>()
    var agendaDigest = false
    var cleared = 0

    override suspend fun scheduleLoanReminders(reminders: List<Reminder>) {
        this.reminders = reminders
    }

    override suspend fun setAgendaDigest(enabled: Boolean) {
        agendaDigest = enabled
    }

    override suspend fun notifyHoldReady(reminder: Reminder) {
        holdsReady += reminder
    }

    override suspend fun accountChanged() = Unit

    override suspend fun clearAll() {
        cleared++
    }
}

private class FakeSession(var hasCredentials: Boolean = true, var restore: () -> Patron? = { SampleData.patron }) : SessionRepository {
    var loggedOut = false

    override suspend fun login(credentials: Credentials): Patron = SampleData.patron

    override suspend fun restore(): Patron? = restore.invoke()

    override suspend fun logout() {
        loggedOut = true
        hasCredentials = false
    }

    override suspend fun requestPasswordReset(cardNumber: String) = Unit

    override suspend fun hasCredentials(): Boolean = hasCredentials
}

abstract class StoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    protected fun TestScope.settings() = SettingsStore(
        settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
        device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
    )
}

class AccountSyncTest : StoreTest() {
    private val clock = MutableClock(Paris.instant(2026, 11, 17, 12))
    private val notifier = RecordingNotifier()
    private val snapshots = InMemoryValueStore<AccountSnapshot>()
    private val inbox = InMemoryValueStore<Inbox>()

    private fun TestScope.sync(account: AccountRepository = DemoAccountRepository()) =
        AccountSync(account, snapshots, inbox, notifier, settings(), clock)

    @Test
    fun aSyncSavesTheSnapshotAndSchedulesReminders() = runTest {
        val snapshot = sync().run()
        assertEquals(SampleData.loans.size, snapshot.loans.size)
        assertEquals(snapshot, snapshots.value)
        // "Chien pourri" is due on November 19: reminders for the days before and the day itself.
        assertTrue(notifier.reminders.isNotEmpty())
        assertTrue(inbox.value!!.items.isNotEmpty())
        // The first snapshot announces nothing the patron already sees.
        assertTrue(notifier.holdsReady.isEmpty())
    }

    @Test
    fun aHoldThatBecomesReadyIsAnnouncedOnce() = runTest {
        val waiting = SampleData.holds.map { it.copy(statusText = "En attente") }
        snapshots.save(AccountSnapshot(SampleData.loans, waiting, clock.instant()))
        val sync = sync()
        sync.run()
        assertEquals(1, notifier.holdsReady.size)
        sync.run()
        assertEquals(1, notifier.holdsReady.size)
    }

    @Test
    fun resetErasesEverything() = runTest {
        val sync = sync()
        sync.run()
        sync.reset()
        assertNull(snapshots.value)
        assertNull(inbox.value)
        assertEquals(1, notifier.cleared)
    }
}

class SessionStoreTest : StoreTest() {
    private val clock = MutableClock(Paris.instant(2026, 11, 17, 12))
    private val notifier = RecordingNotifier()
    private val patronCache = InMemoryValueStore<Patron>()

    private fun TestScope.store(session: SessionRepository, settings: SettingsStore = settings()) = SessionStore(
        session,
        patronCache,
        settings,
        AccountSync(DemoAccountRepository(), InMemoryValueStore(), InMemoryValueStore(), notifier, settings, clock),
        clock
    )

    @Test
    fun restoreSignsIn() = runTest {
        val store = store(FakeSession())
        store.restore()
        assertEquals(SessionStore.State.LoggedIn(SampleData.patron), store.state.value)
        assertEquals(SampleData.patron, patronCache.value)
    }

    @Test
    fun withoutCredentialsRestoreSignsOut() = runTest {
        val store = store(FakeSession(hasCredentials = false, restore = { null }))
        store.restore()
        assertEquals(SessionStore.State.LoggedOut, store.state.value)
    }

    @Test
    fun offlineTheLastKnownAccountStays() = runTest {
        patronCache.save(SampleData.patron)
        val store = store(FakeSession(restore = { throw IOException("offline") }))
        store.restore()
        assertEquals(SessionStore.State.LoggedIn(SampleData.patron), store.state.value)
    }

    @Test
    fun theBiometricLockHoldsTheAccountUntilUnlocked() = runTest {
        val settings = settings()
        settings.setBiometricLock(true)
        val store = store(FakeSession(), settings)
        store.restore()
        assertEquals(SessionStore.State.Locked, store.state.value)
        store.unlock()
        assertEquals(SessionStore.State.LoggedIn(SampleData.patron), store.state.value)
    }

    @Test
    fun theAccountLocksAgainAfterTwoMinutesInTheBackground() = runTest {
        val settings = settings()
        val store = store(FakeSession(), settings)
        store.restore()
        settings.setBiometricLock(true)

        store.onBackground()
        clock.now = clock.now.plus(Duration.ofMinutes(1))
        store.onForeground()
        assertTrue(store.state.value is SessionStore.State.LoggedIn)

        store.onBackground()
        clock.now = clock.now.plus(SessionStore.RELOCK_DELAY)
        store.onForeground()
        assertEquals(SessionStore.State.Locked, store.state.value)
        store.unlock()
        assertTrue(store.state.value is SessionStore.State.LoggedIn)
    }

    @Test
    fun logoutErasesTheAccountAndDeviceSettings() = runTest {
        val settings = settings()
        val session = FakeSession()
        val store = store(session, settings)
        store.restore()
        settings.setBiometricLock(true)
        settings.setOnboardingDone(true)

        store.logout()

        assertEquals(SessionStore.State.LoggedOut, store.state.value)
        assertTrue(session.loggedOut)
        assertNull(patronCache.value)
        assertEquals(1, notifier.cleared)
        assertFalse(settings.currentDeviceSettings().biometricLock)
        assertFalse(settings.currentDeviceSettings().onboardingDone)
    }
}
