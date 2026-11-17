// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.PillRow
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.component.isLargeText
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.LoadErrorState
import fr.mediatheque.core.ui.RefreshErrorBanner
import fr.mediatheque.core.ui.parisFormat

@Composable
internal fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.close))
    }
}

/** The text of an action's result, for the snackbar. */
@Composable
private fun AccountMessage.text(): String = when (this) {
    is AccountMessage.Renewed -> when {
        refused == 0 -> pluralStringResource(R.plurals.loans_renewed, renewed, renewed)
        renewed == 0 -> pluralStringResource(R.plurals.loans_refused, refused, refused)
        else -> stringResource(R.string.loans_partly_renewed, renewed, refused)
    }

    AccountMessage.RenewOffline -> stringResource(R.string.loans_renew_offline)

    AccountMessage.RenewFailed -> stringResource(R.string.loans_renew_failed)

    AccountMessage.HoldCancelled -> stringResource(R.string.holds_cancelled)

    AccountMessage.CancelFailed -> stringResource(R.string.holds_cancel_failed)
}

@Composable
internal fun LoansScreen(viewModel: LoansViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.text()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, modifier = modifier) { outer ->
        DestinationScaffold(
            title = stringResource(R.string.home_my_loans),
            modifier = Modifier.padding(outer),
            navigationIcon = { BackButton(onBack) },
            actions = {
                val loans = state.loans.valueOrNull.orEmpty()
                if (loans.any { it.id !in state.refused }) {
                    TextButton(onClick = viewModel::renewAll, enabled = state.renewing.isEmpty()) {
                        Text(stringResource(R.string.home_renew_all))
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.staleError?.let { item { RefreshErrorBanner(it, Modifier.readable()) } }
                when (val loans = state.loans) {
                    LoadState.Loading -> items(3) { SkeletonRow(Modifier.readable()) }

                    is LoadState.Failed -> item { LoadErrorState(loans.error, viewModel::load, Modifier.readable()) }

                    is LoadState.Loaded -> if (loans.value.isEmpty()) {
                        item {
                            EmptyState(
                                DesignR.drawable.ic_book_2,
                                stringResource(R.string.loans_empty_title),
                                stringResource(R.string.loans_empty_message),
                                Modifier.readable()
                            )
                        }
                    } else {
                        state.sections.forEach { section ->
                            item(key = section.kind) { SectionHeader(stringResource(section.kind.title), Modifier.readable()) }
                            items(section.loans, key = { it.id }) { loan ->
                                LoanRow(
                                    loan = loan,
                                    state = state,
                                    onRenew = { viewModel.renew(loan) },
                                    modifier = Modifier.readable()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LoanSection.Kind.title: Int
    get() = when (this) {
        LoanSection.Kind.LATE -> R.string.loans_late
        LoanSection.Kind.THIS_WEEK -> R.string.loans_this_week
        LoanSection.Kind.LATER -> R.string.loans_later
    }

@Composable
private fun LoanRow(loan: Loan, state: LoansUiState, onRenew: () -> Unit, modifier: Modifier = Modifier) {
    val largeText = isLargeText()
    val action = @Composable {
        when {
            loan.id in state.renewing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)

            loan.id in state.refused -> Text(
                stringResource(R.string.loans_not_renewable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            else -> FilledTonalButton(onClick = onRenew) { Text(stringResource(R.string.loans_renew)) }
        }
    }
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CoverImage(loan.coverUrl, loan.title, loan.recordId ?: loan.id.hashCode(), Modifier.size(width = 52.dp, height = 76.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        loan.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = if (largeText) 4 else 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (loan.author.isNotBlank()) {
                        Text(
                            loan.author,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (largeText) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DueStatePill(loan, state.now)
                }
                if (!largeText) action()
            }
            // At large text sizes the button goes under the text, which keeps the whole width.
            if (largeText) Box(Modifier.align(Alignment.End)) { action() }
        }
    }
}

@Composable
internal fun HoldsScreen(viewModel: HoldsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.text()
    var pendingCancel by remember { mutableStateOf<Hold?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, modifier = modifier) { outer ->
        DestinationScaffold(
            title = stringResource(R.string.home_my_holds),
            modifier = Modifier.padding(outer),
            navigationIcon = { BackButton(onBack) }
        ) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                state.staleError?.let { item { RefreshErrorBanner(it, Modifier.readable()) } }
                when (val holds = state.holds) {
                    LoadState.Loading -> items(2) { SkeletonRow(Modifier.readable()) }

                    is LoadState.Failed -> item { LoadErrorState(holds.error, viewModel::load, Modifier.readable()) }

                    is LoadState.Loaded -> if (holds.value.isEmpty()) {
                        item {
                            EmptyState(
                                DesignR.drawable.ic_bookmark,
                                stringResource(R.string.holds_empty_title),
                                stringResource(R.string.holds_empty_message),
                                Modifier.readable()
                            )
                        }
                    } else {
                        listOf(
                            R.string.holds_ready to state.ready,
                            R.string.holds_waiting to state.waiting
                        ).forEach { (title, holdsInSection) ->
                            if (holdsInSection.isNotEmpty()) {
                                item(key = title) { SectionHeader(stringResource(title), Modifier.readable()) }
                                items(holdsInSection, key = { it.id }) { hold ->
                                    HoldRow(hold, cancelling = hold.id in state.cancelling, onCancel = {
                                        pendingCancel = hold
                                    }, Modifier.readable())
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingCancel?.let { hold ->
        AlertDialog(
            onDismissRequest = { pendingCancel = null },
            title = { Text(stringResource(R.string.holds_cancel_title, hold.title)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingCancel = null
                    viewModel.cancel(hold)
                }) { Text(stringResource(R.string.holds_cancel), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingCancel = null }) { Text(stringResource(R.string.holds_keep)) } }
        )
    }
}

@Composable
private fun HoldRow(hold: Hold, cancelling: Boolean, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val format = parisFormat()
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverImage(hold.coverUrl, hold.title, hold.recordId ?: hold.id.hashCode(), Modifier.size(width = 52.dp, height = 76.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hold.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.holds_pickup_at, hold.pickupLibrary?.shortName ?: hold.pickupLibraryName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PillRow {
                        when {
                            hold.isReadyForPickup -> StatusPill(
                                hold.expectedDate?.let { stringResource(R.string.holds_ready_until, format.dayMonth(it)) }
                                    ?: stringResource(R.string.holds_ready_pill),
                                StatusStyle.SUCCESS
                            )

                            hold.expectedDate != null -> StatusPill(
                                stringResource(R.string.holds_not_before, format.dayMonth(hold.expectedDate!!)),
                                StatusStyle.NEUTRAL
                            )

                            hold.statusText.isNotBlank() -> StatusPill(hold.statusText, StatusStyle.NEUTRAL)
                        }
                        hold.rank?.let { StatusPill(stringResource(R.string.holds_rank, it), StatusStyle.ACCENT) }
                    }
                }
            }
            if (cancelling) {
                CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.End), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.holds_cancel), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
