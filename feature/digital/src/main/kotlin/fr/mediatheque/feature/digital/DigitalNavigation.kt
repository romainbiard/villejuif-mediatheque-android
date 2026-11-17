// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the digital destination. */
@Serializable
data object DigitalRoute : NavKey

/**
 * The destination's screens, for the app's navigation. The portal sign-in is not a route: its URL is single-use and
 * must never reach a saved state.
 */
fun EntryProviderScope<NavKey>.digitalEntries(navigator: Navigator, onOpenAccount: () -> Unit) {
    entry<DigitalRoute> { DigitalScreen(hiltViewModel(), onOpenAccount) }
}
