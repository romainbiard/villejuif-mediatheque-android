// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.system

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.notifications.LocalNotifications
import fr.mediatheque.widget.NextReturnsWidget
import javax.inject.Inject

/** What an account sync changes outside the app: the notifications and the home screen widget. */
class AppSyncNotifier @Inject constructor(@ApplicationContext private val context: Context, private val notifications: LocalNotifications) :
    SyncNotifier {
    override suspend fun scheduleLoanReminders(reminders: List<Reminder>) = notifications.scheduleLoanReminders(reminders)

    override suspend fun setAgendaDigest(enabled: Boolean) = notifications.setAgendaDigest(enabled)

    override suspend fun notifyHoldReady(reminder: Reminder) = notifications.notifyHoldReady(reminder)

    override suspend fun accountChanged() = NextReturnsWidget.updateAll(context)

    override suspend fun clearAll() {
        notifications.clearAll()
        NextReturnsWidget.updateAll(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncNotifierModule {
    @Binds
    abstract fun syncNotifier(notifier: AppSyncNotifier): SyncNotifier
}
