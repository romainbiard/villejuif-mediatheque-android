// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the agenda destination. */
@Serializable
data object AgendaRoute : NavKey

/** An event's page. The event travels whole: the agenda already has it, no request needed. */
@Serializable
data class EventRoute(val event: Event) : NavKey

/** The agenda's screens: the list, and an event beside it on a wide window. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.agendaEntries(navigator: Navigator) {
    entry<AgendaRoute>(metadata = ListDetailSceneStrategy.listPane(sceneKey = AgendaRoute)) {
        AgendaScreen(hiltViewModel(), onOpenEvent = { navigator.navigate(EventRoute(it)) })
    }
    entry<EventRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AgendaRoute)) { route ->
        EventDetailScreen(
            viewModel = hiltViewModel<EventDetailViewModel, EventDetailViewModel.Factory>(key = "event-${route.event.id}") {
                it.create(route.event)
            },
            onBack = navigator::back
        )
    }
}
