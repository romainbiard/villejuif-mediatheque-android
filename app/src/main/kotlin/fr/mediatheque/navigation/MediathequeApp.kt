// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.model.AppLink
import fr.mediatheque.core.ui.Navigator
import fr.mediatheque.feature.account.CardRoute
import fr.mediatheque.feature.account.HoldsRoute
import fr.mediatheque.feature.account.LoansRoute
import fr.mediatheque.feature.account.NotificationsRoute
import fr.mediatheque.feature.account.accountEntries
import fr.mediatheque.feature.agenda.agendaEntries
import fr.mediatheque.feature.digital.digitalEntries
import fr.mediatheque.feature.info.infoEntries
import fr.mediatheque.feature.search.DocumentRoute
import fr.mediatheque.feature.search.searchEntries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

/**
 * The app's frame: the navigation suite adapts to the window (bar on a phone, rail on a tablet, a foldable or a
 * desktop window), and each destination keeps its own back stack, as Material asks of a navigation bar.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class) // The list-detail scene strategy for Navigation 3.
@Composable
fun MediathequeApp(
    shortcuts: Flow<TopLevelDestination>,
    links: Flow<AppLink>,
    session: StateFlow<SessionStore.State>,
    modifier: Modifier = Modifier
) {
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

    // Notifications, the widget, app shortcuts and the Quick Settings tile open a destination at its root, then, for
    // the account's screens, the screen itself once the session is open (never past the biometric lock).
    LaunchedEffect(links) {
        links.collect { link ->
            val (destination, screen) = when (link) {
                AppLink.SEARCH -> TopLevelDestination.SEARCH to null
                AppLink.AGENDA -> TopLevelDestination.AGENDA to null
                AppLink.CARD -> TopLevelDestination.ACCOUNT to CardRoute
                AppLink.LOANS -> TopLevelDestination.ACCOUNT to LoansRoute
                AppLink.HOLDS -> TopLevelDestination.ACCOUNT to HoldsRoute
                AppLink.NOTIFICATIONS -> TopLevelDestination.ACCOUNT to NotificationsRoute
            }
            val stack = backStacks.getValue(destination)
            stack.retainAll { it == destination.route }
            current = destination
            if (screen != null && session.first { it != SessionStore.State.Restoring } is SessionStore.State.LoggedIn) {
                stack.add(screen)
            }
        }
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
        val navigator = remember(backStack) {
            object : Navigator {
                override fun navigate(key: NavKey) {
                    backStack.add(key)
                }

                override fun back() {
                    if (backStack.size > 1) backStack.removeLastOrNull()
                }
            }
        }
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            // List and detail side by side on a wide window (search, agenda), one after the other on a phone.
            sceneStrategies = listOf(rememberListDetailSceneStrategy<NavKey>()),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
                // Each screen's ViewModel lives as long as the screen stays in the back stack.
                rememberViewModelStoreNavEntryDecorator<NavKey>()
            ),
            entryProvider = entryProvider {
                accountEntries(navigator, documentRoute = ::DocumentRoute)
                searchEntries(navigator)
                agendaEntries(navigator)
                digitalEntries(navigator, onOpenAccount = { select(TopLevelDestination.ACCOUNT) })
                infoEntries(navigator)
            }
        )
    }
}
