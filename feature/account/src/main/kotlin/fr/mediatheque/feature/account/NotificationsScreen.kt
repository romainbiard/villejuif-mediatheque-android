// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.StateAction
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.BokehDates
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.InboxItem
import fr.mediatheque.core.ui.parisFormat
import java.time.Instant

@Composable
internal fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inbox by viewModel.inbox.collectAsStateWithLifecycle()
    val renewing by viewModel.renewing.collectAsStateWithLifecycle()
    val renewed by viewModel.renewed.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val now = viewModel.now
    ProfileScaffold(
        title = stringResource(R.string.home_notifications),
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier,
        actions = {
            IconButton(onClick = viewModel::markAllRead, enabled = inbox.unreadCount > 0) {
                Icon(painterResource(DesignR.drawable.ic_done_all), contentDescription = stringResource(R.string.inbox_mark_all_read))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(DesignR.drawable.ic_settings), contentDescription = stringResource(R.string.inbox_settings))
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (inbox.items.isEmpty()) {
                item {
                    EmptyState(
                        icon = DesignR.drawable.ic_notifications,
                        title = stringResource(R.string.inbox_empty_title),
                        message = stringResource(R.string.inbox_empty_message),
                        modifier = Modifier.readable(),
                        primary = StateAction(stringResource(R.string.inbox_settings), onOpenSettings)
                    )
                }
            } else {
                viewModel.sections(inbox).forEach { section ->
                    item(key = section.kind) { SectionHeader(stringResource(section.kind.title), Modifier.readable()) }
                    item(key = "${section.kind}-items") {
                        GroupCard(Modifier.readable()) {
                            section.items.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider(Modifier.padding(start = 66.dp))
                                NotificationRow(
                                    item = item,
                                    now = now,
                                    canRenew = item.loanId != null && item.loanId !in renewed,
                                    renewing = item.loanId in renewing,
                                    onRead = { viewModel.markRead(item) },
                                    onRenew = { viewModel.renew(item) }
                                )
                            }
                        }
                    }
                }
                item {
                    Text(
                        pluralStringResource(R.plurals.inbox_retention, Inbox.RETENTION_DAYS.toInt(), Inbox.RETENTION_DAYS),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.readable()
                    )
                }
            }
        }
    }
}

private val InboxSection.Kind.title: Int
    get() = when (this) {
        InboxSection.Kind.TODAY -> R.string.inbox_today
        InboxSection.Kind.THIS_WEEK -> R.string.inbox_this_week
        InboxSection.Kind.EARLIER -> R.string.inbox_earlier
    }

@Composable
private fun NotificationRow(item: InboxItem, now: Instant, canRenew: Boolean, renewing: Boolean, onRead: () -> Unit, onRenew: () -> Unit) {
    val (title, body) = item.texts()
    val (icon, tint) = item.style()
    val date = DateUtils.getRelativeTimeSpanString(item.date.toEpochMilli(), now.toEpochMilli(), DateUtils.DAY_IN_MILLIS).toString()
    val unreadLabel = stringResource(R.string.inbox_unread)
    val readAction = stringResource(R.string.inbox_mark_read)
    val renewAction = stringResource(R.string.loans_renew)
    val description = listOfNotNull(unreadLabel.takeUnless { item.isRead }, title, body, date).joinToString(". ")
    // One element read in full (state, title, body, date); Renew is offered as an action.
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onRead)
            .clearAndSetSemantics {
                contentDescription = description
                customActions = listOfNotNull(
                    CustomAccessibilityAction(readAction) {
                        onRead()
                        true
                    }.takeUnless { item.isRead },
                    CustomAccessibilityAction(renewAction) {
                        onRenew()
                        true
                    }.takeIf { canRenew && !renewing }
                )
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box {
            Box(
                Modifier.size(42.dp).background(tint.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(icon), contentDescription = null, tint = tint)
            }
            if (!item.isRead) {
                Box(Modifier.size(10.dp).align(Alignment.TopStart).background(MediathequeColors.extended.magenta, CircleShape))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (body.isNotBlank()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (canRenew) {
                if (renewing) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    FilledTonalButton(onClick = onRenew) { Text(renewAction) }
                }
            }
        }
    }
}

/** The entry worded in the device's language: its facts are kept, not sentences. */
@Composable
private fun InboxItem.texts(): Pair<String, String> {
    val format = parisFormat()
    val document = documentTitle.orEmpty()
    val due = dueDate?.let { format.dayMonth(it) }.orEmpty()
    val where = place.orEmpty()
    return when (kind) {
        InboxItem.Kind.LOAN_LATE -> stringResource(R.string.inbox_late_title, document) to stringResource(R.string.inbox_late_body, due)

        InboxItem.Kind.LOAN_DUE_SOON -> {
            val title = when (val days = daysLeft ?: 0) {
                0 -> stringResource(R.string.inbox_due_today, document)
                1 -> stringResource(R.string.inbox_due_tomorrow, document)
                else -> pluralStringResource(R.plurals.inbox_due_in_days, days, document, days)
            }
            title to stringResource(R.string.inbox_due_body, due, where)
        }

        InboxItem.Kind.HOLD_READY -> {
            // The site's status ("Disponible jusqu'au …") is worded again from its date.
            val until = statusText?.let { BokehDates.firstDate(it) }
            stringResource(R.string.inbox_hold_title, document) to if (until != null) {
                stringResource(R.string.inbox_hold_body_until, where, format.dayMonth(until))
            } else {
                stringResource(R.string.inbox_hold_body, where)
            }
        }

        InboxItem.Kind.LIBRARY_MESSAGE -> stringResource(R.string.inbox_message_title) to message.orEmpty()
    }
}

@Composable
private fun InboxItem.style(): Pair<Int, Color> {
    val extended = MediathequeColors.extended
    return when (kind) {
        InboxItem.Kind.LOAN_LATE -> DesignR.drawable.ic_error to MaterialTheme.colorScheme.error
        InboxItem.Kind.LOAN_DUE_SOON -> DesignR.drawable.ic_schedule to extended.warning
        InboxItem.Kind.HOLD_READY -> DesignR.drawable.ic_inventory_2 to extended.success
        InboxItem.Kind.LIBRARY_MESSAGE -> DesignR.drawable.ic_chat to MaterialTheme.colorScheme.primary
    }
}
