// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import java.time.Instant

/**
 * What the app hands to other apps, without any permission: web pages in a Custom Tab, the dialer, the maps app,
 * the calendar, the share sheet. Each returns `false` when no app can handle it.
 */
object ExternalActions {
    /** A web page in a Custom Tab: the patron stays in the app, with their own browser's sign-ins. */
    fun openWebPage(context: Context, url: String): Boolean = attempt {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, url.toUri())
    }

    /** The dialer with the number filled in: the patron places the call (no CALL_PHONE permission). */
    fun dial(context: Context, phone: String): Boolean =
        start(context, Intent(Intent.ACTION_DIAL, "tel:${phone.filter { it.isDigit() || it == '+' }}".toUri()))

    /** Directions in the patron's maps app, to a place named on the map. */
    fun directions(context: Context, latitude: Double, longitude: Double, label: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, "geo:0,0?q=$latitude,$longitude(${Uri.encode(label)})".toUri()))

    /** The calendar app's new event form, prefilled: the patron confirms there (no calendar permission). */
    fun addToCalendar(
        context: Context,
        title: String,
        start: Instant?,
        end: Instant?,
        allDay: Boolean,
        location: String?,
        url: String
    ): Boolean {
        val intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, title)
            start?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it.toEpochMilli()) }
            (end ?: start)?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it.toEpochMilli()) }
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            location?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
            putExtra(CalendarContract.Events.DESCRIPTION, url)
        }
        return start(context, intent)
    }

    /** The system share sheet with a title and a link. */
    fun share(context: Context, title: String, url: String): Boolean {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        return start(context, Intent.createChooser(send, null))
    }

    private fun start(context: Context, intent: Intent): Boolean = attempt { context.startActivity(intent) }

    private inline fun attempt(block: () -> Unit): Boolean = try {
        block()
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
