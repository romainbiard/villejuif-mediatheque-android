// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.info

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the practical info destination. */
@Serializable
data object InfoRoute : NavKey

/** The destination's screens, for the app's navigation. */
@Suppress("UNUSED_PARAMETER") // A single screen: nothing to open from it yet.
fun EntryProviderScope<NavKey>.infoEntries(navigator: Navigator) {
    entry<InfoRoute> { InfoScreen(hiltViewModel()) }
}
