// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.StaleBanner
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.RefreshErrorBanner
import fr.mediatheque.core.ui.parisFormat
import java.time.Instant

/** A link in the account's menu. */
internal data class AccountLink(val title: Int, @param:DrawableRes val icon: Int, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountHome(
    patron: Patron,
    snapshot: AccountSnapshot?,
    refreshError: LoadError?,
    isRefreshing: Boolean,
    unread: Int,
    now: Instant,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenHolds: () -> Unit,
    onOpenNotifications: () -> Unit,
    links: List<AccountLink>,
    modifier: Modifier = Modifier
) {
    var showsCard by rememberSaveable { mutableStateOf(false) }
    var confirmsSignOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { onRefresh() }
    val warning = cardWarning(patron)

    DestinationScaffold(
        title = stringResource(R.string.account_title),
        modifier = modifier,
        actions = {
            val label = if (unread > 0) {
                pluralStringResource(R.plurals.home_notifications_unread, unread, unread)
            } else {
                stringResource(R.string.home_notifications)
            }
            IconButton(onClick = onOpenNotifications, modifier = Modifier.semantics { contentDescription = label }) {
                BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
                    Icon(painterResource(DesignR.drawable.ic_notifications), contentDescription = null)
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(isRefreshing, onRefresh, Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                refreshError?.let { item { RefreshErrorBanner(it, Modifier.readable()) } }
                item { LibraryCardSummary(patron, onOpen = { showsCard = true }, modifier = Modifier.readable()) }
                warning?.let { item { StaleBanner(it, DesignR.drawable.ic_warning, Modifier.readable()) } }
                patron.message?.let { message ->
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MediathequeColors.extended.card,
                            modifier = Modifier.readable()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.home_library_message), style = MaterialTheme.typography.titleMedium)
                                Text(message.replace(Regex("<[^>]+>"), "").trim(), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    LoansSummary(
                        patron = patron,
                        loans = snapshot?.upcomingLoans,
                        now = now,
                        onOpenLoans = onOpenLoans,
                        modifier = Modifier.readable()
                    )
                }
                item {
                    Menu(
                        listOf(
                            AccountLink(R.string.home_my_loans, DesignR.drawable.ic_book_2, onOpenLoans),
                            AccountLink(R.string.home_my_holds, DesignR.drawable.ic_bookmark, onOpenHolds)
                        ) + links,
                        holdsBadge = patron.counters.waitingToBePulled,
                        modifier = Modifier.readable()
                    )
                }
                item {
                    TextButton(onClick = { confirmsSignOut = true }) {
                        Icon(painterResource(DesignR.drawable.ic_logout), contentDescription = null)
                        Text(stringResource(R.string.home_sign_out), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
    if (showsCard) FullScreenCard(patron, onDismiss = { showsCard = false })
    if (confirmsSignOut) {
        AlertDialog(
            onDismissRequest = { confirmsSignOut = false },
            title = { Text(stringResource(R.string.home_sign_out_title)) },
            text = { Text(stringResource(R.string.home_sign_out_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmsSignOut = false
                    onSignOut()
                }) { Text(stringResource(R.string.home_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmsSignOut = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

internal fun Modifier.readable() = widthIn(max = 640.dp).fillMaxWidth()

@Composable
private fun cardWarning(patron: Patron): String? = when {
    patron.cardExpired -> stringResource(R.string.card_expired)
    patron.cardAboutToExpire -> pluralStringResource(R.plurals.card_expires_soon, patron.cardExpiresInDays, patron.cardExpiresInDays)
    else -> null
}

@Composable
private fun LoansSummary(patron: Patron, loans: List<Loan>?, now: Instant, onOpenLoans: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(stringResource(R.string.home_to_return)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (patron.counters.lateLoans > 0) {
                    StatusPill(
                        pluralStringResource(R.plurals.home_late, patron.counters.lateLoans, patron.counters.lateLoans),
                        StatusStyle.DANGER
                    )
                }
                if (patron.counters.waitingToBePulled > 0) {
                    StatusPill(
                        pluralStringResource(
                            R.plurals.home_to_pick_up,
                            patron.counters.waitingToBePulled,
                            patron.counters.waitingToBePulled
                        ),
                        StatusStyle.SUCCESS
                    )
                }
            }
        }
        Surface(onClick = onOpenLoans, shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                when {
                    loans == null -> Text(" ", Modifier.padding(vertical = 8.dp))

                    loans.isEmpty() -> Text(
                        stringResource(R.string.home_nothing_to_return),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    else -> {
                        loans.take(SUMMARY_LOANS).forEachIndexed { index, loan ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(loan.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                DueStatePill(loan, now)
                            }
                            if (index <
                                minOf(loans.size, SUMMARY_LOANS) - 1
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        Text(
                            pluralStringResource(R.plurals.home_see_all_loans, loans.size, loans.size),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

private const val SUMMARY_LOANS = 3

@Composable
private fun Menu(links: List<AccountLink>, holdsBadge: Int, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Column {
            links.forEachIndexed { index, link ->
                Surface(onClick = link.onClick, color = MediathequeColors.extended.card) {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(painterResource(link.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(link.title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (link.title == R.string.home_my_holds && holdsBadge > 0) Badge { Text("$holdsBadge") }
                        Icon(painterResource(DesignR.drawable.ic_chevron_right), contentDescription = null)
                    }
                }
                if (index <
                    links.lastIndex
                ) {
                    HorizontalDivider(Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** "En retard de 2 jours", "À rendre aujourd'hui", "À rendre le 5 novembre". */
@Composable
internal fun DueStatePill(loan: Loan, now: Instant) {
    val format = parisFormat()
    when (val state = loan.dueState(now)) {
        is Loan.DueState.Late ->
            StatusPill(pluralStringResource(R.plurals.loans_late_by, state.days, state.days), StatusStyle.DANGER)

        is Loan.DueState.DueSoon -> StatusPill(
            if (state.days ==
                0
            ) {
                stringResource(R.string.loans_due_today)
            } else {
                stringResource(R.string.loans_due_on, format.dayMonth(loan.dueDate))
            },
            StatusStyle.WARNING
        )

        is Loan.DueState.OnTime ->
            StatusPill(stringResource(R.string.loans_due_on, format.dayMonth(loan.dueDate)), StatusStyle.NEUTRAL)
    }
}

/** A section title, for TalkBack too. */
@Composable
internal fun Heading(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}
