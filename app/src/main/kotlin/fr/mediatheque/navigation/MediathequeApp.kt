// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import fr.mediatheque.feature.account.accountEntries
import fr.mediatheque.feature.agenda.agendaEntries
import fr.mediatheque.feature.digital.digitalEntries
import fr.mediatheque.feature.info.infoEntries
import fr.mediatheque.feature.search.searchEntries
import kotlinx.coroutines.flow.Flow

/**
 * The app's frame: the navigation suite adapts to the window (bar on a phone, rail on a tablet, a foldable or a
 * desktop window), and each destination keeps its own back stack, as Material asks of a navigation bar.
 */
@Composable
fun MediathequeApp(shortcuts: Flow<TopLevelDestination>, modifier: Modifier = Modifier) {
    var current by rememberSaveable { mutableStateOf(TopLevelDestination.START) }
    val backStacks: Map<TopLevelDestination, NavBackStack<NavKey>> =
        TopLevelDestination.entries.associateWith { rememberNavBackStack(it.route) }
    val backStack = backStacks.getValue(current)

    fun select(destination: TopLevelDestination) {
        if (destination == current) {
            // Tapping the current destination again returns to its root. The stacks are remembered, so the
            // shortcut collector below, started once, still reaches the right one.
            backStacks.getValue(destination).retainAll { it == destination.route }
        } else {
            current = destination
        }
    }

    // At the root of a destination other than the start, Back returns to the start rather than leaving the app.
    BackHandler(enabled = current != TopLevelDestination.START && backStack.size == 1) {
        current = TopLevelDestination.START
    }

    // Ctrl+1…5 and Ctrl+F, received by MainActivity.onKeyShortcut.
    LaunchedEffect(shortcuts) {
        shortcuts.collect { select(it) }
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val selected = destination == current
                item(
                    selected = selected,
                    onClick = { select(destination) },
                    icon = {
                        Icon(painterResource(if (selected) destination.selectedIcon else destination.icon), contentDescription = null)
                    },
                    label = { Text(stringResource(destination.label)) }
                )
            }
        }
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider {
                accountEntries()
                searchEntries()
                agendaEntries()
                digitalEntries()
                infoEntries()
            }
        )
    }
}
