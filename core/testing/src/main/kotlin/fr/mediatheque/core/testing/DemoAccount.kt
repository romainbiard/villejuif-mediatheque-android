// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.testing

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.data.demo.DemoSessionRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.domain.Reminder
import java.io.File
import java.time.Clock
import kotlinx.coroutines.CoroutineScope

/** Notifications that go nowhere. */
object SilentSyncNotifier : SyncNotifier {
    override suspend fun scheduleLoanReminders(reminders: List<Reminder>) = Unit

    override suspend fun setAgendaDigest(enabled: Boolean) = Unit

    override suspend fun notifyHoldReady(reminder: Reminder) = Unit

    override suspend fun accountChanged() = Unit

    override suspend fun clearAll() = Unit
}

/** The patron's settings in two files of [directory], closed with [scope]. */
fun settingsStore(scope: CoroutineScope, directory: File) = SettingsStore(
    settings = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "settings.preferences_pb") },
    device = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "device.preferences_pb") }
)

/** The demo patron, signed in once [SessionStore.restore] ran. */
fun demoSessionStore(settings: SettingsStore, clock: Clock) = SessionStore(
    DemoSessionRepository(),
    InMemoryValueStore(),
    settings,
    AccountSync(DemoAccountRepository(), InMemoryValueStore(), InMemoryValueStore(), SilentSyncNotifier, settings, clock),
    clock
)
