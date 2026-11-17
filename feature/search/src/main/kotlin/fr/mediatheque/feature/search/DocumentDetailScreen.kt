// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.CopyStatus
import fr.mediatheque.core.model.DefaultSelection
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.ExternalActions
import fr.mediatheque.core.ui.parisFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentDetailScreen(viewModel: DocumentDetailViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val document = viewModel.document
    val copies by viewModel.copies.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showsHold by rememberSaveable { mutableStateOf(false) }
    var asksToSignIn by rememberSaveable { mutableStateOf(false) }
    val selections by viewModel.selections.collectAsStateWithLifecycle()
    val selectionMessage by viewModel.selectionMessage.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messageText = selectionMessage?.let { selectionMessageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.selectionMessageShown()
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.search_back))
                    }
                },
                actions = {
                    if (session is SessionStore.State.LoggedIn) {
                        SelectionsButtons(selections, onToggle = viewModel::toggle)
                    }
                    document.permalink?.let { link ->
                        IconButton(onClick = { ExternalActions.share(context, document.title, link) }) {
                            Icon(painterResource(DesignR.drawable.ic_share), contentDescription = stringResource(R.string.document_share))
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CoverImage(document.coverUrl, document.title, document.id, Modifier.size(width = 120.dp, height = 172.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(document.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        document.authors.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                it.joinToString(", "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(listOfNotNull(document.kind.label, document.year).joinToString(" · "), StatusStyle.ACCENT)
                    }
                }

                if (!document.kind.isDigital) {
                    val patron = (session as? SessionStore.State.LoggedIn)?.patron
                    if (patron?.permissions?.holdForbidden == true) {
                        Text(
                            stringResource(R.string.hold_forbidden),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Button(onClick = {
                            if (patron !=
                                null
                            ) {
                                showsHold = true
                            } else {
                                asksToSignIn = true
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(painterResource(DesignR.drawable.ic_bookmark), contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.hold_action), Modifier.padding(start = 8.dp))
                        }
                    }
                }

                if (document.kind.isDigital) {
                    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.document_online_title), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.document_online_message), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    SectionHeader(stringResource(R.string.document_where))
                    when (val loaded = copies) {
                        LoadState.Loading -> SkeletonRow()

                        is LoadState.Failed -> Text(
                            stringResource(R.string.document_copies_unavailable),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        is LoadState.Loaded -> if (loaded.value.isEmpty()) {
                            Text(stringResource(R.string.document_no_copy), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            loaded.value.forEach { library -> LibraryCopiesCard(library) }
                        }
                    }
                }

                SectionHeader(stringResource(R.string.document_details))
                Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        val lines = listOfNotNull(
                            stringResource(R.string.document_kind) to document.kind.label,
                            document.publisher?.let { stringResource(R.string.document_publisher) to it },
                            document.year?.let { stringResource(R.string.document_year) to it },
                            document.series?.let { series ->
                                stringResource(R.string.document_series) to listOfNotNull(
                                    series,
                                    document.volume?.let { stringResource(R.string.document_volume, it) }
                                ).joinToString(", ")
                            }
                        )
                        lines.forEachIndexed { index, (label, value) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics(mergeDescendants = true) {}) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(0.4f)
                                )
                                Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                            }
                            if (index < lines.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
    if (showsHold) {
        val homeLibrary = (session as? SessionStore.State.LoggedIn)?.patron?.homeLibrary
        HoldSheet(
            viewModel = hiltViewModel<HoldViewModel, HoldViewModel.Factory>(key = "hold-${document.id}") {
                it.create(document, homeLibrary)
            },
            onDismiss = { showsHold = false }
        )
    }
    if (asksToSignIn) {
        AlertDialog(
            onDismissRequest = { asksToSignIn = false },
            title = { Text(stringResource(R.string.hold_sign_in_title)) },
            text = { Text(stringResource(R.string.hold_sign_in_message)) },
            confirmButton = { TextButton(onClick = { asksToSignIn = false }) { Text(stringResource(R.string.hold_ok)) } }
        )
    }
}

@Composable
private fun LibraryCopiesCard(library: LibraryCopies) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(library.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            library.copies.forEach { copy -> CopyLine(copy) }
        }
    }
}

@Composable
private fun CopyLine(copy: LiveCopy) {
    val format = parisFormat()
    val status = copy.status
    val statusText = if (status is CopyStatus.OnLoan && status.dueDate != null) {
        stringResource(R.string.document_on_loan_until, format.dayMonth(status.dueDate!!))
    } else {
        copy.statusLabel
    }
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        StatusPill(statusText, if (status.isAvailable) StatusStyle.SUCCESS else StatusStyle.NEUTRAL)
        val details = listOfNotNull(copy.section, copy.shelfMark).joinToString(" · ")
        if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
        if (copy.holdCount > 0) {
            Text(
                pluralStringResource(R.plurals.document_holds, copy.holdCount, copy.holdCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The site's three default selections: the heart for "Mes préférés", the others in the overflow menu. */
@Composable
private fun SelectionsButtons(selections: Set<DefaultSelection>?, onToggle: (DefaultSelection) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        val isFavorite = selections?.contains(DefaultSelection.FAVORITES) == true
        IconToggleButton(checked = isFavorite, onCheckedChange = { onToggle(DefaultSelection.FAVORITES) }, enabled = selections != null) {
            Icon(
                painterResource(if (isFavorite) DesignR.drawable.ic_favorite_filled else DesignR.drawable.ic_favorite),
                contentDescription = stringResource(R.string.document_favorite),
                tint = if (isFavorite) MediathequeColors.extended.magenta else LocalContentColor.current
            )
        }
        var expanded by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { expanded = true }, enabled = selections != null) {
                Icon(painterResource(DesignR.drawable.ic_more_vert), contentDescription = stringResource(R.string.document_more))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(DefaultSelection.READ, DefaultSelection.TO_READ).forEach { selection ->
                    val isIn = selections?.contains(selection) == true
                    DropdownMenuItem(
                        text = { Text(stringResource(selection.label)) },
                        onClick = {
                            expanded = false
                            onToggle(selection)
                        },
                        trailingIcon = { Checkbox(checked = isIn, onCheckedChange = null) },
                        modifier = Modifier.semantics { toggleableState = ToggleableState(isIn) }
                    )
                }
            }
        }
    }
}

private val DefaultSelection.label: Int
    get() = when (this) {
        DefaultSelection.FAVORITES -> R.string.selection_favorites
        DefaultSelection.READ -> R.string.selection_read
        DefaultSelection.TO_READ -> R.string.selection_to_read
    }

@Composable
private fun selectionMessageText(message: DocumentDetailViewModel.SelectionMessage): String = when (message) {
    is DocumentDetailViewModel.SelectionMessage.Added -> stringResource(R.string.selection_added, stringResource(message.selection.label))

    is DocumentDetailViewModel.SelectionMessage.Removed -> stringResource(
        R.string.selection_removed,
        stringResource(message.selection.label)
    )

    DocumentDetailViewModel.SelectionMessage.Failed -> stringResource(R.string.selection_failed)
}
