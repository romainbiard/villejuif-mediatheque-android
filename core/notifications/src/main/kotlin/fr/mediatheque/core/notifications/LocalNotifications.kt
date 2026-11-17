// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.AppLink
import fr.mediatheque.core.model.BokehDates
import fr.mediatheque.core.model.Paris
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's notifications: one channel per kind (so the patron can turn each off in the system settings), due-date
 * reminders scheduled with WorkManager, holds ready shown at once, and the Monday agenda reminder.
 *
 * Nothing is shown while POST_NOTIFICATIONS is denied (Android 13+) or the channel is off: the reminders stay
 * scheduled and show again as soon as the patron allows them.
 */
@Singleton
class LocalNotifications @Inject constructor(@ApplicationContext private val context: Context, private val clock: Clock) {
    private val manager = NotificationManagerCompat.from(context)
    private val workManager get() = WorkManager.getInstance(context)

    /** At launch: creating a channel again only updates its name and description. */
    fun createChannels() {
        manager.createNotificationChannelsCompat(
            listOf(
                channel(
                    CHANNEL_LOANS,
                    R.string.channel_loans_name,
                    R.string.channel_loans_description,
                    NotificationManagerCompat.IMPORTANCE_DEFAULT
                ),
                channel(
                    CHANNEL_HOLDS,
                    R.string.channel_holds_name,
                    R.string.channel_holds_description,
                    NotificationManagerCompat.IMPORTANCE_HIGH
                ),
                channel(
                    CHANNEL_AGENDA,
                    R.string.channel_agenda_name,
                    R.string.channel_agenda_description,
                    NotificationManagerCompat.IMPORTANCE_LOW
                )
            )
        )
    }

    private fun channel(id: String, name: Int, description: Int, importance: Int) = NotificationChannelCompat.Builder(id, importance)
        .setName(context.getString(name))
        .setDescription(context.getString(description))
        .build()

    /** Replaces every scheduled due-date reminder. WorkManager runs these operations in order. */
    fun scheduleLoanReminders(reminders: List<Reminder>) {
        workManager.cancelAllWorkByTag(ReminderWork.LOAN_TAG)
        val now = clock.instant()
        reminders.forEach { reminder ->
            workManager.enqueueUniqueWork(reminder.id, ExistingWorkPolicy.REPLACE, ReminderWork.loanRequest(reminder, now))
        }
    }

    /** Turns the Monday reminder on (keeping the one already scheduled) or off. */
    fun setAgendaDigest(enabled: Boolean) {
        if (enabled) {
            workManager.enqueueUniqueWork(ReminderWork.AGENDA_WORK, ExistingWorkPolicy.KEEP, ReminderWork.agendaRequest(clock.instant()))
        } else {
            workManager.cancelUniqueWork(ReminderWork.AGENDA_WORK)
        }
    }

    /** After the agenda reminder showed: the next Monday's, once this one has finished. */
    internal fun scheduleNextAgendaDigest() {
        workManager.enqueueUniqueWork(
            ReminderWork.AGENDA_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            ReminderWork.agendaRequest(clock.instant())
        )
    }

    /** On sign-out: nothing scheduled, nothing left in the notification shade. */
    fun clearAll() {
        workManager.cancelAllWorkByTag(ReminderWork.LOAN_TAG)
        workManager.cancelUniqueWork(ReminderWork.AGENDA_WORK)
        manager.cancelAll()
    }

    /** A hold just became ready: the title stays generic, the document only in the text, hidden on a locked screen. */
    fun notifyHoldReady(reminder: Reminder) {
        val title = reminder.documentTitles.firstOrNull().orEmpty()
        val place = reminder.place.orEmpty()
        val until = reminder.statusText?.let(BokehDates::firstDate)
        val body = if (until != null) {
            context.getString(R.string.hold_ready_body_until, title, place, dayMonth(until))
        } else {
            context.getString(R.string.hold_ready_body, title, place)
        }
        post(reminder.id, CHANNEL_HOLDS, context.getString(R.string.hold_ready_title), body, AppLink.HOLDS)
    }

    /** A due-date reminder whose time has come, worded in the device's current language. */
    internal fun showLoanReminder(reminder: Reminder) {
        val titles = reminder.documentTitles
        val resources = context.resources
        val (title, body) = when (reminder.kind) {
            Reminder.Kind.LOAN_DUE_IN_DAYS -> {
                val days = reminder.daysBefore ?: 0
                val day = reminder.dueDate?.let(::weekdayDayMonth).orEmpty()
                resources.getQuantityString(R.plurals.loan_due_in_days_title, days, days) to if (titles.size == 1) {
                    context.getString(R.string.loan_due_in_days_body, titles.single(), day)
                } else {
                    resources.getQuantityString(R.plurals.loan_due_in_days_body_several, titles.size, titles.size, day)
                }
            }

            Reminder.Kind.LOAN_DUE_TODAY -> context.getString(R.string.loan_due_today_title) to if (titles.size == 1) {
                context.getString(R.string.loan_due_today_body, titles.single())
            } else {
                resources.getQuantityString(R.plurals.loan_due_today_body_several, titles.size, titles.size)
            }

            Reminder.Kind.HOLD_READY -> return notifyHoldReady(reminder)
        }
        post(reminder.id, CHANNEL_LOANS, title, body, AppLink.LOANS)
    }

    internal fun showAgendaDigest() {
        post(
            AGENDA_NOTIFICATION,
            CHANNEL_AGENDA,
            context.getString(R.string.agenda_title),
            context.getString(R.string.agenda_body),
            AppLink.AGENDA
        )
    }

    private fun post(tag: String, channel: String, title: String, body: String, link: AppLink) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openApp(link))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // On a locked screen with contents hidden, only the generic title shows.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        manager.notify(tag, NOTIFICATION_ID, notification)
    }

    /** Opens the app on [link]'s screen, in the running task when there is one. */
    private fun openApp(link: AppLink): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .setPackage(context.packageName)
            .putExtra(AppLink.EXTRA, link.key)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, link.ordinal, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private val locale: Locale get() = context.resources.configuration.locales[0] ?: Locale.FRANCE

    private fun dayMonth(instant: java.time.Instant): String =
        DateTimeFormatter.ofPattern(if (locale.language == "en") "MMMM d" else "d MMMM", locale).withZone(Paris.zone).format(instant)

    private fun weekdayDayMonth(instant: java.time.Instant): String = DateTimeFormatter.ofPattern(
        if (locale.language ==
            "en"
        ) {
            "EEEE, MMMM d"
        } else {
            "EEEE d MMMM"
        },
        locale
    ).withZone(Paris.zone).format(instant)

    companion object {
        const val CHANNEL_LOANS = "loan-reminders"
        const val CHANNEL_HOLDS = "holds-ready"
        const val CHANNEL_AGENDA = "agenda-digest"

        /** Notifications are told apart by their tag (the reminder's id); the id is the same for all. */
        private const val NOTIFICATION_ID = 1
        private const val AGENDA_NOTIFICATION = "agenda"

        /** The brand violet, as on the app icon. */
        private const val BRAND_COLOR = 0xFF773DBD.toInt()
    }
}
