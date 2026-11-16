// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.info

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.ui.ComingSoonDestination
import kotlinx.serialization.Serializable

/** Root of the info destination. */
@Serializable
data object InfoRoute : NavKey

/** The destination's screens, for the app's navigation. */
fun EntryProviderScope<NavKey>.infoEntries() {
    entry<InfoRoute> { InfoScreen() }
}

@Composable
internal fun InfoScreen(modifier: Modifier = Modifier) {
    ComingSoonDestination(stringResource(R.string.info_title), DesignR.drawable.ic_location_on, modifier)
}
