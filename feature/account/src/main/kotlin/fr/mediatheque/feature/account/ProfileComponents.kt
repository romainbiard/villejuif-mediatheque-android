// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.ui.DestinationScaffold
import fr.mediatheque.core.ui.parisFormat

/** A section of the account: a large title with Back, actions, and a snackbar for [message]. */
@Composable
internal fun ProfileScaffold(
    title: String,
    message: ProfileMessage?,
    onMessageShown: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val snackbar = remember { SnackbarHostState() }
    val text = message?.text()
    LaunchedEffect(text) {
        if (text != null) {
            snackbar.showSnackbar(text)
            onMessageShown()
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, modifier = modifier) { outer ->
        DestinationScaffold(
            title = title,
            modifier = Modifier.padding(outer),
            navigationIcon = { BackButton(onBack) },
            actions = actions,
            content = content
        )
    }
}

/** A rounded group of rows, as in the account's menu. */
@Composable
internal fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = modifier) {
        Column(content = content)
    }
}

/** A setting with its switch: the whole row toggles, and reads as one switch. */
@Composable
internal fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    busy: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, enabled = enabled && !busy, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

/** A label and its value, one under the other. */
@Composable
internal fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The text of a profile action's result. */
@Composable
internal fun ProfileMessage.text(): String = when (this) {
    is ProfileMessage.Failed -> stringResource(failureText(action, offline))
    is ProfileMessage.Rejected -> reason
    is ProfileMessage.SelectionCreated -> stringResource(R.string.selections_created, name)
    ProfileMessage.DuplicateSelection -> stringResource(R.string.selections_duplicate)
    is ProfileMessage.RemovedFrom -> stringResource(R.string.selections_removed, selection)
    ProfileMessage.ReviewSent -> stringResource(R.string.reviews_sent)
    ProfileMessage.SuggestionSent -> stringResource(R.string.suggestions_sent)
    ProfileMessage.ContactSaved -> stringResource(R.string.information_saved)
    ProfileMessage.InvalidEmail -> stringResource(R.string.information_invalid_email)
    ProfileMessage.PasswordEmpty -> stringResource(R.string.password_empty)
    ProfileMessage.PasswordMismatch -> stringResource(R.string.password_mismatch)
    ProfileMessage.PasswordChanged -> stringResource(R.string.password_changed)
    is ProfileMessage.RenewedUntil -> stringResource(R.string.inbox_renewed_until, parisFormat().dayMonth(dueDate))
    ProfileMessage.RenewRefused -> stringResource(R.string.inbox_renew_refused)
}

private fun failureText(action: ProfileMessage.Action, offline: Boolean): Int = when (action) {
    ProfileMessage.Action.CREATION -> if (offline) R.string.failure_creation_offline else R.string.failure_creation
    ProfileMessage.Action.REMOVAL -> if (offline) R.string.failure_removal_offline else R.string.failure_removal
    ProfileMessage.Action.SENDING -> if (offline) R.string.failure_sending_offline else R.string.failure_sending
    ProfileMessage.Action.SAVING -> if (offline) R.string.failure_saving_offline else R.string.failure_saving
    ProfileMessage.Action.PASSWORD_CHANGE -> if (offline) R.string.failure_password_offline else R.string.failure_password
    ProfileMessage.Action.MODIFICATION -> if (offline) R.string.failure_modification_offline else R.string.failure_modification
    ProfileMessage.Action.RENEWAL -> if (offline) R.string.loans_renew_offline else R.string.loans_renew_failed
}
