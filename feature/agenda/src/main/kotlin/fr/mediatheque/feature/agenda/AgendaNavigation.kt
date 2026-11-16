// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.ui.ComingSoonDestination
import kotlinx.serialization.Serializable

/** Root of the agenda destination. */
@Serializable
data object AgendaRoute : NavKey

/** The destination's screens, for the app's navigation. */
fun EntryProviderScope<NavKey>.agendaEntries() {
    entry<AgendaRoute> { AgendaScreen() }
}

@Composable
internal fun AgendaScreen(modifier: Modifier = Modifier) {
    ComingSoonDestination(stringResource(R.string.agenda_title), DesignR.drawable.ic_calendar_month, modifier)
}
