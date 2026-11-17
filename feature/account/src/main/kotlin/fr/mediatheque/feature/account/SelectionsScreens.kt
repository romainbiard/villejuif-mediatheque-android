// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.model.Selection
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.LoadErrorState
import fr.mediatheque.core.ui.parisFormat

@Composable
internal fun SelectionsScreen(
    viewModel: SelectionsViewModel,
    onOpen: (Selection) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selections by viewModel.selections.collectAsStateWithLifecycle()
    val isCreating by viewModel.isCreating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var creates by rememberSaveable { mutableStateOf(false) }
    ProfileScaffold(
        title = stringResource(R.string.home_selections),
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier,
        actions = {
            if (isCreating) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = { creates = true }) {
                    Icon(painterResource(DesignR.drawable.ic_add), contentDescription = stringResource(R.string.selections_new))
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
            when (val state = selections) {
                LoadState.Loading -> items(3) { SkeletonRow(Modifier.readable()) }

                is LoadState.Failed -> item { LoadErrorState(state.error, viewModel::load, Modifier.readable()) }

                is LoadState.Loaded -> items(state.value, key = { it.id }) { selection ->
                    SelectionRow(selection, onClick = { onOpen(selection) }, modifier = Modifier.readable())
                }
            }
        }
    }
    if (creates) {
        NewSelectionDialog(
            onCreate = {
                creates = false
                viewModel.create(it)
            },
            onDismiss = { creates = false }
        )
    }
}

@Composable
private fun SelectionRow(selection: Selection, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val format = parisFormat()
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(painterResource(DesignR.drawable.ic_favorite), contentDescription = null, tint = MediathequeColors.extended.magenta)
            Column(Modifier.weight(1f)) {
                Text(selection.name, style = MaterialTheme.typography.titleMedium)
                val count = pluralStringResource(R.plurals.selections_count, selection.count, selection.count)
                val detail = selection.updatedAt?.let { stringResource(R.string.selections_updated, count, format.dayMonth(it)) } ?: count
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(painterResource(DesignR.drawable.ic_chevron_right), contentDescription = null)
        }
    }
}

@Composable
private fun NewSelectionDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.selections_new)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.selections_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onCreate(name) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.selections_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
internal fun SelectionDetailScreen(
    viewModel: SelectionDetailViewModel,
    onOpen: (Notice) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val removing by viewModel.removing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    ProfileScaffold(
        title = viewModel.selection.name,
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val state = notices) {
                LoadState.Loading -> items(3) { SkeletonRow(Modifier.readable()) }

                is LoadState.Failed -> item { LoadErrorState(state.error, viewModel::load, Modifier.readable()) }

                is LoadState.Loaded -> if (state.value.isEmpty()) {
                    item {
                        EmptyState(
                            DesignR.drawable.ic_favorite,
                            stringResource(R.string.selection_empty_title),
                            stringResource(R.string.selection_empty_message),
                            Modifier.readable()
                        )
                    }
                } else {
                    items(state.value, key = { it.id }) { notice ->
                        NoticeRow(
                            notice = notice,
                            removing = notice.id in removing,
                            onOpen = { onOpen(notice) },
                            onRemove = { viewModel.remove(notice) },
                            modifier = Modifier.readable()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeRow(notice: Notice, removing: Boolean, onOpen: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CoverImage(notice.coverUrl, notice.title, notice.id, Modifier.size(width = 52.dp, height = 76.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notice.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(notice.authors.firstOrNull(), notice.year).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(
                        detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            if (removing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        painterResource(DesignR.drawable.ic_delete),
                        contentDescription = stringResource(R.string.selection_remove, notice.title)
                    )
                }
            }
        }
    }
}
