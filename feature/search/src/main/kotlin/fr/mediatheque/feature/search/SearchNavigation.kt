// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.ui.DetailPlaceholder
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the search destination. */
@Serializable
data object SearchRoute : NavKey

/** A record's page. The record travels whole from the results: only its copies are read. */
@Serializable
data class DocumentRoute(val document: Notice) : NavKey

/** The search's screens: the results, and a record beside them on a wide window. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.searchEntries(navigator: Navigator) {
    entry<SearchRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = SearchRoute,
            detailPlaceholder = {
                DetailPlaceholder(
                    DesignR.drawable.ic_book_2,
                    stringResource(R.string.search_detail_placeholder_title),
                    stringResource(R.string.search_detail_placeholder_message)
                )
            }
        )
    ) {
        SearchScreen(hiltViewModel(), onOpen = { navigator.navigate(DocumentRoute(it)) })
    }
    entry<DocumentRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = SearchRoute)) { route ->
        DocumentDetailScreen(
            viewModel = hiltViewModel<DocumentDetailViewModel, DocumentDetailViewModel.Factory>(key = "document-${route.document.id}") {
                it.create(route.document)
            },
            onBack = navigator::back
        )
    }
}
