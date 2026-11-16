// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.ui.ComingSoonDestination
import fr.mediatheque.core.ui.LoadingState
import fr.mediatheque.core.ui.Navigator
import kotlinx.serialization.Serializable

/** Root of the account destination. */
@Serializable
data object AccountRoute : NavKey

@Serializable
data object LoansRoute : NavKey

@Serializable
data object HoldsRoute : NavKey

@Serializable
data object NotificationsRoute : NavKey

/** The account's screens: the home and, on a wide window, the section beside it. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.accountEntries(navigator: Navigator) {
    entry<AccountRoute>(metadata = ListDetailSceneStrategy.listPane(sceneKey = AccountRoute)) {
        AccountScreen(
            viewModel = hiltViewModel(),
            onOpenLoans = { navigator.navigate(LoansRoute) },
            onOpenHolds = { navigator.navigate(HoldsRoute) },
            onOpenNotifications = { navigator.navigate(NotificationsRoute) }
        )
    }
    entry<LoansRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        LoansScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<HoldsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        HoldsScreen(hiltViewModel(), onBack = navigator::back)
    }
    entry<NotificationsRoute>(metadata = ListDetailSceneStrategy.detailPane(sceneKey = AccountRoute)) {
        ComingSoonDestination(stringResource(R.string.home_notifications), DesignR.drawable.ic_notifications)
    }
}

/** What the account shows, by sign-in state: loading, locked, the sign-in form, the welcome setup, or the account. */
@Composable
internal fun AccountScreen(
    viewModel: AccountViewModel,
    onOpenLoans: () -> Unit,
    onOpenHolds: () -> Unit,
    onOpenNotifications: () -> Unit,
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
                links = emptyList(),
                modifier = modifier
            )
        }
    }
}
