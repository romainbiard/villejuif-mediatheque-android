// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.datastore.ValueStore
import fr.mediatheque.core.domain.InboxPlanner
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.domain.ReminderPlanner
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Inbox
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the account sync tells the rest of the system: notifications and the widget (core:notifications). */
interface SyncNotifier {
    /** Replaces every scheduled due-date reminder. */
    suspend fun scheduleLoanReminders(reminders: List<Reminder>)

    /** The Monday agenda reminder, on or off. */
    suspend fun setAgendaDigest(enabled: Boolean)

    /** A hold that just became ready for pickup. */
    suspend fun notifyHoldReady(reminder: Reminder)

    /** The snapshot changed: the widget redraws. */
    suspend fun accountChanged()

    /** On sign-out: no reminder, no notification, an empty widget. */
    suspend fun clearAll()
}

/**
 * Account sync: a new snapshot, the reminders scheduled again, the notification center updated, and a notification for
 * holds that just arrived. Run after sign-in, each time the account opens, after a renewal, and in the background.
 */
@Singleton
class AccountSync @Inject constructor(
    private val account: AccountRepository,
    private val snapshots: ValueStore<AccountSnapshot>,
    private val inbox: ValueStore<Inbox>,
    private val notifier: SyncNotifier,
    private val settings: SettingsStore,
    private val clock: Clock
) {
    /** One sync at a time: the account screen and the background worker can start one together. */
    private val mutex = Mutex()

    suspend fun run(): AccountSnapshot = mutex.withLock {
        val now = clock.instant()
        val reminderSettings = settings.currentReminderSettings()
        // Side by side: each list is one or two requests to the portal.
        val snapshot = coroutineScope {
            val loans = async { account.loans() }
            val holds = async { account.holds() }
            AccountSnapshot(loans.await(), holds.await(), now)
        }
        val previous = snapshots.load()
        snapshots.save(snapshot)
        notifier.accountChanged()

        notifier.scheduleLoanReminders(
            if (reminderSettings.loanReminders) {
                ReminderPlanner.loanReminders(
                    snapshot.loans,
                    reminderSettings.daysBefore,
                    now
                )
            } else {
                emptyList()
            }
        )
        notifier.setAgendaDigest(reminderSettings.agendaDigest)

        // The notification center keeps a trace of what the reminders announced.
        val merged = (inbox.load() ?: Inbox()).merge(InboxPlanner.items(snapshot, reminderSettings.daysBefore, now), now).inbox
        inbox.save(merged)

        // On the very first snapshot, no notification for what the patron already sees on screen.
        if (reminderSettings.holdAlerts && previous != null) {
            snapshot.newlyReadyHolds(previous).forEach { notifier.notifyHoldReady(ReminderPlanner.holdReadyReminder(it, now)) }
        }
        snapshot
    }

    /** The library's message on the account, added to the notification center. Already read on the website: it arrives read. */
    suspend fun recordLibraryMessage(message: String?, unread: Boolean) {
        if (message.isNullOrBlank()) return
        val now = clock.instant()
        mutex.withLock {
            val merged = (inbox.load() ?: Inbox()).merge(listOf(InboxPlanner.libraryMessage(message, now, isRead = !unread)), now).inbox
            inbox.save(merged)
        }
    }

    /** On sign-out: no snapshot, no reminder, no history left on the device. */
    suspend fun reset() = mutex.withLock {
        snapshots.clear()
        inbox.clear()
        notifier.clearAll()
    }
}
