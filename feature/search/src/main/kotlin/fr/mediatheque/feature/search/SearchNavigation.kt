// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.ui.ComingSoonDestination
import kotlinx.serialization.Serializable

/** Root of the search destination. */
@Serializable
data object SearchRoute : NavKey

/** The destination's screens, for the app's navigation. */
fun EntryProviderScope<NavKey>.searchEntries() {
    entry<SearchRoute> { SearchScreen() }
}

@Composable
internal fun SearchScreen(modifier: Modifier = Modifier) {
    ComingSoonDestination(stringResource(R.string.search_title), DesignR.drawable.ic_search, modifier)
}
