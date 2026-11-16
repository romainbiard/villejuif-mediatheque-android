// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.notifications

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.domain.Reminder
import javax.inject.Inject

/** Placeholder until notifications and the widget arrive: the sync works, nothing is shown outside the app yet. */
class SilentSyncNotifier @Inject constructor() : SyncNotifier {
    override suspend fun scheduleLoanReminders(reminders: List<Reminder>) = Unit

    override suspend fun setAgendaDigest(enabled: Boolean) = Unit

    override suspend fun notifyHoldReady(reminder: Reminder) = Unit

    override suspend fun accountChanged() = Unit

    override suspend fun clearAll() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationsModule {
    @Binds
    abstract fun syncNotifier(notifier: SilentSyncNotifier): SyncNotifier
}
