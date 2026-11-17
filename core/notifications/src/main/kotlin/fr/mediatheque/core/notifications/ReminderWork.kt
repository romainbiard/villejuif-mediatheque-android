// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.notifications

import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.Paris
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * How reminders become WorkManager work: one-time work delayed until the reminder's time. WorkManager is the
 * recommended way to schedule deferrable work: it survives reboots and needs no alarm permission. A reminder may
 * arrive a few minutes late in Doze, which a due-date reminder can afford.
 */
internal object ReminderWork {
    /** Every loan reminder carries this tag: rescheduling cancels them all first. */
    const val LOAN_TAG = "loan-reminder"

    /** The Monday agenda reminder, one unique chain that schedules the next Monday each time it runs. */
    const val AGENDA_WORK = "agenda-digest"

    private const val KEY_ID = "id"
    private const val KEY_KIND = "kind"
    private const val KEY_FIRE_AT = "fireAt"
    private const val KEY_TITLES = "titles"
    private const val KEY_DUE_DATE = "dueDate"
    private const val KEY_DAYS_BEFORE = "daysBefore"

    /** The agenda reminder's time: Mondays at 9 am, Paris time. */
    private val agendaTime: LocalTime = LocalTime.of(9, 0)

    /** The wait before [fireAt]; zero when it is already past. */
    fun delay(fireAt: Instant, now: Instant): Duration = Duration.between(now, fireAt).coerceAtLeast(Duration.ZERO)

    fun loanRequest(reminder: Reminder, now: Instant): OneTimeWorkRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(delay(reminder.fireAt, now).toMillis(), TimeUnit.MILLISECONDS)
        .setInputData(inputData(reminder))
        .addTag(LOAN_TAG)
        .build()

    /** The next Monday at 9 am in Paris, strictly after [now]. */
    fun nextAgendaDigest(now: Instant): Instant {
        val local = now.atZone(Paris.zone)
        val thisWeek = local.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).with(agendaTime)
        return (if (thisWeek.toInstant() > now) thisWeek else thisWeek.plusWeeks(1)).toInstant()
    }

    fun agendaRequest(now: Instant): OneTimeWorkRequest = OneTimeWorkRequestBuilder<AgendaDigestWorker>()
        .setInitialDelay(delay(nextAgendaDigest(now), now).toMillis(), TimeUnit.MILLISECONDS)
        .build()

    /** A due-date reminder's facts, for the worker that shows it. The wording is chosen when it is shown. */
    fun inputData(reminder: Reminder): Data = Data.Builder()
        .putString(KEY_ID, reminder.id)
        .putString(KEY_KIND, reminder.kind.name)
        .putLong(KEY_FIRE_AT, reminder.fireAt.toEpochMilli())
        .putStringArray(KEY_TITLES, reminder.documentTitles.toTypedArray())
        .putLong(KEY_DUE_DATE, reminder.dueDate?.toEpochMilli() ?: -1)
        .putInt(KEY_DAYS_BEFORE, reminder.daysBefore ?: -1)
        .build()

    fun reminder(data: Data): Reminder? {
        val id = data.getString(KEY_ID) ?: return null
        val kind = Reminder.Kind.entries.firstOrNull { it.name == data.getString(KEY_KIND) } ?: return null
        return Reminder(
            id = id,
            kind = kind,
            fireAt = Instant.ofEpochMilli(data.getLong(KEY_FIRE_AT, 0)),
            documentTitles = data.getNullableStringArray(KEY_TITLES)?.filterNotNull().orEmpty(),
            dueDate = data.getLong(KEY_DUE_DATE, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli),
            daysBefore = data.getInt(KEY_DAYS_BEFORE, -1).takeIf { it >= 0 }
        )
    }
}
