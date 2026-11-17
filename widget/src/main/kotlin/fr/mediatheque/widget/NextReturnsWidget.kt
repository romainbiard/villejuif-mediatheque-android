// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.datastore.ValueStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.theme.MediathequeColorSchemes
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.AppLink
import fr.mediatheque.core.model.Loan
import java.time.Clock
import java.time.Instant

/** What the widget needs from the app's graph: it runs in the app's process, so no shared storage is needed. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun snapshots(): ValueStore<AccountSnapshot>

    fun clock(): Clock
}

/** The widget's content, worded before it is drawn. */
internal sealed interface WidgetContent {
    data class Message(val text: String) : WidgetContent

    data class Returns(val readyHold: String?, val loans: List<Row>) : WidgetContent

    enum class Tone { LATE, SOON, ON_TIME }

    data class Row(val title: String, val due: String, val tone: Tone)
}

/** "Prochains retours": the next loans to return and a hold waiting, from the last account snapshot. */
class NextReturnsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val content = content(context, entryPoint.snapshots().load(), entryPoint.clock().instant())
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.putExtra(AppLink.EXTRA, AppLink.LOANS.key)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        provideContent {
            GlanceTheme(colors = colors) {
                Widget(content, header = context.getString(R.string.widget_header), open = open)
            }
        }
    }

    private fun content(context: Context, snapshot: AccountSnapshot?, now: Instant): WidgetContent {
        if (snapshot == null) return WidgetContent.Message(context.getString(R.string.widget_signed_out))
        val loans = snapshot.upcomingLoans
        val ready = snapshot.holds.filter { it.isReadyForPickup }
        if (loans.isEmpty() && ready.isEmpty()) return WidgetContent.Message(context.getString(R.string.widget_nothing))
        val readyText = when (ready.size) {
            0 -> null
            1 -> context.getString(R.string.widget_hold_ready, ready.single().title)
            else -> context.resources.getQuantityString(R.plurals.widget_holds_ready, ready.size, ready.size)
        }
        return WidgetContent.Returns(readyText, loans.take(MAX_LOANS).map { row(context, it, now) })
    }

    private fun row(context: Context, loan: Loan, now: Instant): WidgetContent.Row {
        val resources = context.resources
        return when (val state = loan.dueState(now)) {
            is Loan.DueState.Late -> WidgetContent.Row(
                loan.title,
                resources.getQuantityString(R.plurals.widget_late, state.days, state.days),
                WidgetContent.Tone.LATE
            )

            else -> WidgetContent.Row(
                loan.title,
                when (state.days) {
                    0 -> context.getString(R.string.widget_today)
                    1 -> context.getString(R.string.widget_tomorrow)
                    else -> resources.getQuantityString(R.plurals.widget_in_days, state.days, state.days)
                },
                if (state is Loan.DueState.DueSoon) WidgetContent.Tone.SOON else WidgetContent.Tone.ON_TIME
            )
        }
    }

    companion object {
        private const val MAX_LOANS = 4

        private val colors = ColorProviders(light = MediathequeColorSchemes.light, dark = MediathequeColorSchemes.dark)

        /** Redraws every placed widget, after a sync or a sign-out. */
        suspend fun updateAll(context: Context) = NextReturnsWidget().updateAll(context)
    }
}

class NextReturnsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextReturnsWidget()
}

// The same light and dark pairs as the app's status colors, readable on the widget's background in both themes.
private val late = ColorProvider(day = Color(0xFFA60A3D), night = Color(0xFFFF6B8E))
private val soon = ColorProvider(day = Color(0xFF7A4F00), night = Color(0xFFF5C451))
private val onTime = ColorProvider(day = Color(0xFF006E43), night = Color(0xFF4CC38A))

@Composable
private fun Widget(content: WidgetContent, header: String, open: Intent?) {
    val base = GlanceModifier
        .fillMaxSize()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(16.dp)
        .padding(12.dp)
    Column(if (open != null) base.clickable(actionStartActivity(open)) else base) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(DesignR.drawable.ic_book_2),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                modifier = GlanceModifier.size(18.dp)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(header, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(6.dp))
        when (content) {
            is WidgetContent.Message -> Text(content.text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            is WidgetContent.Returns -> Returns(content)
        }
    }
}

@Composable
private fun Returns(content: WidgetContent.Returns) {
    content.readyHold?.let {
        Text(it, maxLines = 1, style = TextStyle(color = onTime, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        Spacer(GlanceModifier.height(4.dp))
    }
    // The widget's height clips the list: a taller widget shows more loans.
    content.loans.forEach { row ->
        Column(GlanceModifier.padding(bottom = 6.dp).semantics { contentDescription = "${row.title}, ${row.due}" }) {
            Text(
                row.title,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                row.due,
                maxLines = 1,
                style = TextStyle(
                    color = when (row.tone) {
                        WidgetContent.Tone.LATE -> late
                        WidgetContent.Tone.SOON -> soon
                        WidgetContent.Tone.ON_TIME -> onTime
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            )
        }
    }
}
