// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.model.Selection
import fr.mediatheque.core.ui.DetailPlaceholder
import fr.mediatheque.core.ui.LoadingState
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the account destination. */
@Serializable
data object AccountRoute : NavKey

/** The library card full screen, opened from the app shortcut or the Quick Settings tile. */
@Serializable
data object CardRoute : NavKey

@Serializable
data object LoansRoute : NavKey

@Serializable
data object HoldsRoute : NavKey

@Serializable
data object NotificationsRoute : NavKey

@Serializable
data object SelectionsRoute : NavKey

@Serializable
data class SelectionRoute(val selection: Selection) : NavKey

@Serializable
data object ReviewsRoute : NavKey

@Serializable
data object SuggestionsRoute : NavKey

@Serializable
data object InformationRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

/**
 * The account's screens: the home and, on a wide window, the section beside it. [documentRoute] opens a record from a
 * selection: the record page belongs to the search feature.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.accountEntries(navigator: Navigator, documentRoute: (Notice) -> NavKey) {
    entry<AccountRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            sceneKey = AccountRoute,
            detailPlaceholder = {
                DetailPlaceholder(
                    DesignR.drawable.ic_account_circle,
                    stringResource(R.string.account_detail_placeholder_title),
                    stringResource(R.string.account_detail_placeholder_message)
                )
            }
        )
    ) {
        AccountScreen(
            viewModel = hiltViewModel(),
            onOpenLoans = { navigator.navigate(LoansRoute) },
            onOpenHolds = { navigator.navigate(HoldsRoute) },
            onOpenNotifications = { navigator.navigate(NotificationsRoute) },
            onOpen = navigator::navigate
        )
    }
    entry<CardRoute> {
        CardScreen(hiltViewModel(), onDismiss = navigator::back)
    }
    entry<LoansRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        LoansScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<HoldsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        HoldsScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<NotificationsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        NotificationsScreen(hiltViewModel(), onOpenSettings = { navigator.navigate(SettingsRoute) }, onBack = navigator::back)
    }
    entry<SelectionsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        SelectionsScreen(hiltViewModel(), onOpen = { navigator.navigate(SelectionRoute(it)) }, onBack = navigator::back)
    }
    entry<SelectionRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) { route ->
        SelectionDetailScreen(
            viewModel = hiltViewModel<SelectionDetailViewModel, SelectionDetailViewModel.Factory>(key = "selection-${route.selection.id}") {
                it.create(route.selection)
            },
            onOpen = { navigator.navigate(documentRoute(it)) },
            onBack = navigator::back
        )
    }
    entry<ReviewsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        ReviewsScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<SuggestionsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        SuggestionsScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<InformationRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        InformationScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<SettingsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        ProfileSettingsScreen(hiltViewModel(), onBack = navigator::back)
    }
}

/** The card alone, for the signed-in patron; otherwise straight back to the account. */
@Composable
internal fun CardScreen(viewModel: AccountViewModel, onDismiss: () -> Unit) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    when (val state = session) {
        is SessionStore.State.LoggedIn -> FullScreenCard(state.patron, onDismiss = onDismiss)
        SessionStore.State.Restoring -> LoadingState()
        SessionStore.State.Locked, SessionStore.State.LoggedOut -> LaunchedEffect(Unit) { onDismiss() }
    }
}

/** The patron's lists and profile, under loans and holds. */
internal fun accountLinkGroups(onOpen: (NavKey) -> Unit) = listOf(
    AccountLinkGroup(
        R.string.home_my_lists,
        listOf(
            AccountLink(R.string.home_selections, DesignR.drawable.ic_favorite) { onOpen(SelectionsRoute) },
            AccountLink(R.string.home_reviews, DesignR.drawable.ic_star) { onOpen(ReviewsRoute) },
            AccountLink(R.string.home_suggestions, DesignR.drawable.ic_lightbulb) { onOpen(SuggestionsRoute) }
        )
    ),
    AccountLinkGroup(
        R.string.home_my_profile,
        listOf(
            AccountLink(R.string.home_information, DesignR.drawable.ic_badge) { onOpen(InformationRoute) },
            AccountLink(R.string.home_settings, DesignR.drawable.ic_settings) { onOpen(SettingsRoute) }
        )
    )
)

/** What the account shows, by sign-in state: loading, locked, the sign-in form, the welcome setup, or the account. */
@Composable
internal fun AccountScreen(
    viewModel: AccountViewModel,
    onOpenLoans: () -> Unit,
    onOpenHolds: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpen: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val deviceSettings by viewModel.deviceSettings.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val refreshError by viewModel.refreshError.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val unread by viewModel.unreadNotifications.collectAsStateWithLifecycle()
    when (val state = session) {
        SessionStore.State.Restoring -> LoadingState(modifier)

        SessionStore.State.Locked -> LockScreen(onUnlocked = viewModel::unlock, onSignOut = viewModel::logout, modifier = modifier)

        SessionStore.State.LoggedOut -> LoginScreen(hiltViewModel(), modifier)

        is SessionStore.State.LoggedIn -> when (deviceSettings?.onboardingDone) {
            null -> LoadingState(modifier)

            false -> WelcomeSetupScreen(hiltViewModel(), modifier)

            true -> AccountHome(
                patron = state.patron,
                snapshot = snapshot,
                refreshError = refreshError,
                isRefreshing = isRefreshing,
                unread = unread,
                now = viewModel.now,
                onRefresh = viewModel::refresh,
                onSignOut = viewModel::logout,
                onOpenLoans = onOpenLoans,
                onOpenHolds = onOpenHolds,
                onOpenNotifications = onOpenNotifications,
                linkGroups = remember(onOpen) { accountLinkGroups(onOpen) },
                modifier = modifier
            )
        }
    }
}
