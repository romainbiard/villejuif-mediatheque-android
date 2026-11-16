// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.ui.ComingSoonDestination
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the digital destination. */
@Serializable
data object DigitalRoute : NavKey

/** The destination's screens, for the app's navigation. */
fun EntryProviderScope<NavKey>.digitalEntries(navigator: Navigator) {
    entry<DigitalRoute> { DigitalScreen() }
}

@Composable
internal fun DigitalScreen(modifier: Modifier = Modifier) {
    ComingSoonDestination(stringResource(R.string.digital_title), DesignR.drawable.ic_play_circle, modifier)
}
