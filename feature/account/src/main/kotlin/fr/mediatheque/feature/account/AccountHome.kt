// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.IconTile
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.StaleBanner
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.component.TileTint
import fr.mediatheque.core.designsystem.component.isLargeText
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.RefreshErrorBanner
import fr.mediatheque.core.ui.parisFormat
import java.time.Instant

/** A link in the account's menu: its title, and its icon on a colored tile. */
internal data class AccountLink(val title: Int, @param:DrawableRes val icon: Int, val tint: TileTint, val onClick: () -> Unit)

/**
 * Loans, late loans and holds to pick up. Counted from the loans and holds when they are read, so the counters always
 * agree with the pills of the lists below them; the portal's counters until then.
 */
internal data class AccountCounts(val loans: Int, val late: Int, val toPickUp: Int) {
    companion object {
        fun of(patron: Patron, snapshot: AccountSnapshot?, now: Instant): AccountCounts = if (snapshot != null) {
            AccountCounts(
                loans = snapshot.loans.size,
                late = snapshot.loans.count { it.dueState(now) is Loan.DueState.Late },
                toPickUp = snapshot.holds.count { it.isReadyForPickup }
            )
        } else {
            with(patron.counters) { AccountCounts(loans, lateLoans, waitingToBePulled) }
        }
    }
}

/** A titled group of links under the loans and holds: the patron's lists, the profile. */
internal data class AccountLinkGroup(val title: Int, val links: List<AccountLink>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountHome(
    patron: Patron,
    snapshot: AccountSnapshot?,
    refreshError: LoadError?,
    isRefreshing: Boolean,
    unread: Int,
    now: Instant,
    onShown: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpenLoans: () -> Unit,
    onOpenHolds: () -> Unit,
    onOpenNotifications: () -> Unit,
    linkGroups: List<AccountLinkGroup>,
    modifier: Modifier = Modifier
) {
    var showsCard by rememberSaveable { mutableStateOf(false) }
    var confirmsSignOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { onShown() }
    val warning = cardWarning(patron)
    val counts = AccountCounts.of(patron, snapshot, now)
    val isWide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= WIDE_WINDOW

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
                item { Counters(counts, onOpenLoans, onOpenHolds, Modifier.readable()) }
                // On a wide window the home has room for the next returns, as the iPad's dashboard.
                if (isWide) {
                    item { LoansSummary(snapshot?.upcomingLoans, now, onOpenLoans, Modifier.readable()) }
                }
                item {
                    Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(stringResource(R.string.home_my_borrowings))
                        Menu(
                            listOf(
                                AccountLink(R.string.home_my_loans, DesignR.drawable.ic_book_2, TileTint.VIOLET, onOpenLoans),
                                AccountLink(R.string.home_my_holds, DesignR.drawable.ic_bookmark, TileTint.SUCCESS, onOpenHolds)
                            ),
                            accessory = { link ->
                                when {
                                    link.title == R.string.home_my_loans && counts.late > 0 -> StatusPill(
                                        pluralStringResource(R.plurals.home_late, counts.late, counts.late),
                                        StatusStyle.DANGER
                                    )

                                    link.title == R.string.home_my_holds && counts.toPickUp > 0 -> StatusPill(
                                        pluralStringResource(R.plurals.home_to_pick_up, counts.toPickUp, counts.toPickUp),
                                        StatusStyle.SUCCESS
                                    )
                                }
                            }
                        )
                    }
                }
                linkGroups.forEach { group ->
                    item(key = group.title) {
                        Column(Modifier.readable(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionHeader(stringResource(group.title))
                            Menu(group.links)
                        }
                    }
                }
                item {
                    TextButton(
                        onClick = { confirmsSignOut = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
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
private fun LoansSummary(loans: List<Loan>?, now: Instant, onOpenLoans: () -> Unit, modifier: Modifier = Modifier) {
    val largeText = isLargeText()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.home_to_return))
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
                        val shown = loans.take(SUMMARY_LOANS)
                        shown.forEachIndexed { index, loan ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    loan.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = if (largeText) 3 else 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                DueStatePill(loan, now)
                            }
                            if (index < shown.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

/** Loans, late and to pick up as three tiles, stacked at large text sizes so their labels are not cut. */
@Composable
private fun Counters(counts: AccountCounts, onOpenLoans: () -> Unit, onOpenHolds: () -> Unit, modifier: Modifier = Modifier) {
    val tiles = @Composable { tileModifier: Modifier ->
        CounterTile(
            counts.loans,
            pluralStringResource(R.plurals.home_counter_loans, counts.loans),
            MaterialTheme.colorScheme.onSurface,
            onOpenLoans,
            tileModifier
        )
        CounterTile(
            counts.late,
            stringResource(R.string.home_counter_late),
            MaterialTheme.colorScheme.error,
            onOpenLoans,
            tileModifier
        )
        CounterTile(
            counts.toPickUp,
            stringResource(R.string.home_counter_to_pick_up),
            MediathequeColors.extended.success,
            onOpenHolds,
            tileModifier
        )
    }
    if (isLargeText()) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) { tiles(Modifier.fillMaxWidth()) }
    } else {
        Row(modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tiles(Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun CounterTile(value: Int, label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MediathequeColors.extended.card,
        modifier = modifier.semantics(mergeDescendants = true) {}
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text("$value", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold), color = color)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val WIDE_WINDOW = 600.dp

private const val SUMMARY_LOANS = 3

@Composable
private fun Menu(links: List<AccountLink>, modifier: Modifier = Modifier, accessory: @Composable (AccountLink) -> Unit = {}) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Column {
            links.forEachIndexed { index, link ->
                MenuRow(link, accessory = { accessory(link) })
                if (index < links.lastIndex) {
                    HorizontalDivider(Modifier.padding(start = 62.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** An icon on its colored tile, the title, a status pill (beside it, or under it at large text sizes), a chevron. */
@Composable
private fun MenuRow(link: AccountLink, accessory: @Composable () -> Unit) {
    Surface(onClick = link.onClick, color = MediathequeColors.extended.card) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconTile(link.icon, link.tint)
            val title = @Composable { titleModifier: Modifier ->
                Text(stringResource(link.title), style = MaterialTheme.typography.titleMedium, modifier = titleModifier)
            }
            if (isLargeText()) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    title(Modifier)
                    accessory()
                }
            } else {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    title(Modifier.weight(1f).padding(end = 8.dp))
                    accessory()
                }
            }
            Icon(
                painterResource(DesignR.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
