// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.IconTile
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.designsystem.component.StateAction
import fr.mediatheque.core.designsystem.component.StatusPill
import fr.mediatheque.core.designsystem.component.StatusStyle
import fr.mediatheque.core.designsystem.component.isLargeText
import fr.mediatheque.core.designsystem.component.segmentShape
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.Availability
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.ui.CoverImage
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.LoadErrorState

@Composable
internal fun SearchScreen(viewModel: SearchViewModel, onOpen: (Notice) -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val title = state.browsing?.let { stringResource(it.title) } ?: stringResource(R.string.search_title)
    DestinationScaffold(
        title = title,
        modifier = modifier,
        navigationIcon = {
            if (state.browsing != null) {
                IconButton(onClick = viewModel::reset) {
                    Icon(painterResource(DesignR.drawable.ic_arrow_back), contentDescription = stringResource(R.string.search_back))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
            // Result rows are a segmented list, 2 dp apart; the other items add their own space above.
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                SearchField(
                    text = state.text,
                    onTextChange = viewModel::onTextChange,
                    onSubmit = {
                        viewModel.submit()
                        focus.clearFocus()
                    },
                    onClear = viewModel::reset
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
            item { Filters(state, onSelectScope = viewModel::selectScope, onSelectLibrary = viewModel::selectLibrary) }
            val results = state.results
            when {
                results == null -> landing(state, viewModel)

                results == LoadState.Loading -> items(5) { SkeletonRow(Modifier.readable().padding(top = 10.dp)) }

                results is LoadState.Failed -> item {
                    LoadErrorState(results.error, viewModel::retry, Modifier.readable().padding(top = 12.dp))
                }

                results is LoadState.Loaded && results.value.isEmpty() -> item {
                    EmptyState(
                        icon = DesignR.drawable.ic_search,
                        title = stringResource(R.string.search_no_results_title),
                        message = stringResource(R.string.search_no_results_message),
                        primary = if (state.hasActiveFilters) {
                            StateAction(
                                stringResource(R.string.search_clear_filters),
                                viewModel::clearFilters
                            )
                        } else {
                            null
                        },
                        modifier = Modifier.readable().padding(top = 12.dp)
                    )
                }

                results is LoadState.Loaded -> {
                    item {
                        Text(
                            pluralStringResource(R.plurals.search_results, state.total, state.total),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .readable()
                                .padding(top = 14.dp, bottom = 6.dp)
                                .semantics { liveRegion = LiveRegionMode.Polite }
                        )
                    }
                    val documents = results.value
                    itemsIndexed(documents, key = { _, notice -> notice.id }) { index, notice ->
                        LaunchedEffect(notice.id) { viewModel.onRowShown(notice) }
                        DocumentRow(notice, state.availability[notice.id], segmentShape(index, documents.size), onOpen)
                    }
                    if (state.isLoadingMore) {
                        item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                    }
                }
            }
        }
    }
}

/** Search results no wider than reads well on a tablet. */
private fun Modifier.readable() = padding(horizontal = 16.dp).widthIn(max = 720.dp).fillMaxWidth()

/**
 * The search field with the look of Material 3's search bar (56 dp, surfaceContainerHigh): a filled pill, the search
 * icon leading, clear trailing.
 */
@Composable
private fun SearchField(text: String, onTextChange: (String) -> Unit, onSubmit: () -> Unit, onClear: () -> Unit) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(painterResource(DesignR.drawable.ic_search), contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier.readable().heightIn(min = 56.dp)
    )
}

@Composable
private fun Filters(state: SearchUiState, onSelectScope: (SearchScope) -> Unit, onSelectLibrary: (Library?) -> Unit) {
    var showsLibraries by remember { mutableStateOf(false) }
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Box {
                FilterChip(
                    selected = state.library != null,
                    onClick = { showsLibraries = true },
                    label = { Text(state.library?.shortName ?: stringResource(R.string.search_library_filter)) },
                    trailingIcon = {
                        Icon(painterResource(DesignR.drawable.ic_expand_more), contentDescription = null, Modifier.size(18.dp))
                    }
                )
                DropdownMenu(expanded = showsLibraries, onDismissRequest = { showsLibraries = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.search_all_libraries)) },
                        onClick = {
                            onSelectLibrary(null)
                            showsLibraries = false
                        }
                    )
                    Library.entries.forEach { library ->
                        DropdownMenuItem(
                            text = { Text(library.displayName) },
                            onClick = {
                                onSelectLibrary(library)
                                showsLibraries = false
                            }
                        )
                    }
                }
            }
        }
        items(SearchScope.entries) { scope ->
            FilterChip(selected = scope == state.scope, onClick = { onSelectScope(scope) }, label = { Text(stringResource(scope.title)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.landing(state: SearchUiState, viewModel: SearchViewModel) {
    if (state.recentSearches.isNotEmpty()) {
        item {
            SectionHeader(stringResource(R.string.search_recent), Modifier.readable().padding(top = 14.dp)) {
                TextButton(onClick = viewModel::clearRecentSearches) { Text(stringResource(R.string.search_clear)) }
            }
        }
        items(state.recentSearches, key = { "recent-$it" }) { recent ->
            Row(
                Modifier
                    .readable()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { viewModel.searchRecent(recent) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painterResource(DesignR.drawable.ic_history),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(recent, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    painterResource(DesignR.drawable.ic_north_west),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    item { SectionHeader(stringResource(R.string.search_explore), Modifier.readable().padding(top = 14.dp, bottom = 8.dp)) }
    item {
        FlowRow(
            Modifier.readable(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // One tile per row at large text sizes: "Livres audio" stays on one line.
            maxItemsInEachRow = if (isLargeText()) 1 else 2
        ) {
            ExploreShortcut.entries.forEach { shortcut ->
                Surface(
                    onClick = { viewModel.open(shortcut) },
                    shape = RoundedCornerShape(22.dp),
                    color = MediathequeColors.extended.card,
                    modifier = Modifier.weight(1f).heightIn(min = 88.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(shortcut.icon, shortcut.tint, size = 40.dp)
                        Text(stringResource(shortcut.title), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DocumentRow(notice: Notice, availability: Availability?, shape: Shape, onOpen: (Notice) -> Unit) {
    Surface(
        onClick = { onOpen(notice) },
        shape = shape,
        color = MediathequeColors.extended.card,
        modifier = Modifier.readable()
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverImage(notice.coverUrl, notice.title, notice.id, Modifier.size(width = 56.dp, height = 80.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(notice.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val byline = listOfNotNull(notice.authors.firstOrNull(), notice.kind.label, notice.year).joinToString(" · ")
                Text(
                    byline,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val libraries = notice.harvestedCopies.mapNotNull { it.library?.shortName }.distinct().joinToString(", ")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    when {
                        notice.kind.isDigital -> StatusPill(stringResource(R.string.search_online_included), StatusStyle.ACCENT)

                        availability != null -> StatusPill(
                            availability.label,
                            if (availability.isAvailable) StatusStyle.SUCCESS else StatusStyle.NEUTRAL
                        )
                    }
                    if (libraries.isNotEmpty() && !notice.kind.isDigital) {
                        Text(libraries, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
