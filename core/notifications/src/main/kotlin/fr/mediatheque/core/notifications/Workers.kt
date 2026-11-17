// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.SessionRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** Shows a due-date reminder at its time. */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifications: LocalNotifications
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ReminderWork.reminder(inputData)?.let(notifications::showLoanReminder)
        return Result.success()
    }
}

/** The Monday agenda reminder, then the next Monday's. */
@HiltWorker
class AgendaDigestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val notifications: LocalNotifications
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        notifications.showAgendaDigest()
        notifications.scheduleNextAgendaDigest()
        return Result.success()
    }
}

/**
 * The account read again in the background: new snapshot, reminders, widget, and a notification for holds that just
 * arrived. The stored credentials sign in again when the portal's session has expired.
 */
@HiltWorker
class AccountSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val accountSync: AccountSync,
    private val session: SessionRepository
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!session.hasCredentials()) return Result.success()
        return try {
            accountSync.run()
            Result.success()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Offline or site down: try again later. Anything else waits for the next period.
            when (LoadError.from(error)) {
                LoadError.Offline, LoadError.SiteUnavailable -> Result.retry()
                else -> Result.failure()
            }
        }
    }
}

/** The background sync, every six hours while signed in, on a network and with enough battery. */
@Singleton
class BackgroundSync @Inject constructor(@ApplicationContext private val context: Context) {
    fun start() {
        val request = PeriodicWorkRequestBuilder<AccountSyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            // The account was just read: the first background sync can wait a period.
            .setInitialDelay(INTERVAL_HOURS, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun stop() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "account-sync"
        const val INTERVAL_HOURS = 6L
        const val BACKOFF_MINUTES = 30L
    }
}
