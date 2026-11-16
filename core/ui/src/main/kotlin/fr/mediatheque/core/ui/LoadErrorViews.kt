// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.StaleBanner
import fr.mediatheque.core.designsystem.component.StateAction

/** What an error says to the patron, and so what they can do. */
private data class ErrorText(@StringRes val title: Int, @StringRes val message: Int, @DrawableRes val icon: Int)

private val LoadError.text: ErrorText
    get() = when (this) {
        LoadError.Offline -> ErrorText(R.string.error_offline_title, R.string.error_offline_message, DesignR.drawable.ic_wifi_off)

        LoadError.NotLoggedIn -> ErrorText(
            R.string.error_not_logged_in_title,
            R.string.error_not_logged_in_message,
            DesignR.drawable.ic_lock
        )

        LoadError.UnreadableAnswer -> ErrorText(
            R.string.error_unreadable_title,
            R.string.error_unreadable_message,
            DesignR.drawable.ic_error
        )

        LoadError.SiteUnavailable, is LoadError.Other ->
            ErrorText(R.string.error_site_unavailable_title, R.string.error_site_unavailable_message, DesignR.drawable.ic_error)
    }

/** Full-screen error, when there is nothing to show yet. */
@Composable
fun LoadErrorState(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val text = error.text
    EmptyState(
        icon = text.icon,
        title = stringResource(text.title),
        message = stringResource(text.message),
        primary = StateAction(stringResource(R.string.error_retry), onRetry),
        modifier = modifier.padding(16.dp)
    )
}

/** The line above cached data when the refresh failed. */
@Composable
fun RefreshErrorBanner(error: LoadError, modifier: Modifier = Modifier) {
    if (error == LoadError.Offline) {
        StaleBanner(stringResource(R.string.stale_offline), DesignR.drawable.ic_wifi_off, modifier)
    } else {
        StaleBanner(stringResource(R.string.stale_not_updated), DesignR.drawable.ic_cloud_off, modifier)
    }
}

/** A centered spinner, announced as "Chargement" by TalkBack. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.loading)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
    }
}
