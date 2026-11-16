// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState
import fr.mediatheque.core.designsystem.component.StateAction
import fr.mediatheque.core.designsystem.theme.MediathequeColors

/**
 * After sign-in, once. First why the app wants notifications, before Android asks (the system prompt only comes
 * once); then the reminders and the biometric lock.
 */
@Composable
internal fun WelcomeSetupScreen(viewModel: WelcomeSetupViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun notificationsGranted() = !needsPermission ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var step by rememberSaveable { mutableStateOf(if (notificationsGranted()) Step.SETTINGS else Step.PRIMER) }
    var granted by rememberSaveable { mutableStateOf(notificationsGranted()) }
    var biometricLock by rememberSaveable { mutableStateOf(false) }
    val reminders by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        step = Step.SETTINGS
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (step) {
            Step.PRIMER -> EmptyState(
                icon = DesignR.drawable.ic_notifications,
                title = stringResource(R.string.welcome_primer_title),
                message = stringResource(R.string.welcome_primer_message),
                primary = StateAction(stringResource(R.string.welcome_enable_notifications)) {
                    if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else step = Step.SETTINGS
                },
                secondary = StateAction(stringResource(R.string.welcome_not_now)) { step = Step.SETTINGS },
                modifier = Modifier.widthIn(max = 480.dp)
            )

            Step.SETTINGS -> Column(Modifier.widthIn(max = 480.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(R.string.welcome_settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                Text(
                    stringResource(R.string.welcome_notifications),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics {
                        heading()
                    }
                )
                if (granted) {
                    Card {
                        SwitchRow(
                            R.string.welcome_loan_reminders,
                            R.string.welcome_loan_reminders_detail,
                            reminders.loanReminders,
                            viewModel::setLoanReminders
                        )
                        SwitchRow(
                            R.string.welcome_hold_alerts,
                            R.string.welcome_hold_alerts_detail,
                            reminders.holdAlerts,
                            viewModel::setHoldAlerts
                        )
                        SwitchRow(
                            R.string.welcome_agenda_digest,
                            R.string.welcome_agenda_digest_detail,
                            reminders.agendaDigest,
                            viewModel::setAgendaDigest
                        )
                    }
                } else {
                    Card {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.welcome_notifications_off), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.welcome_notifications_off_detail), style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    ).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }) { Text(stringResource(R.string.welcome_open_settings)) }
                        }
                    }
                }
                if (canLockAccount(context)) {
                    Text(
                        stringResource(R.string.welcome_security),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics {
                            heading()
                        }
                    )
                    Card {
                        SwitchRow(R.string.welcome_biometric_lock, R.string.welcome_biometric_lock_detail, biometricLock) {
                            biometricLock =
                                it
                        }
                    }
                }
                Text(
                    stringResource(R.string.welcome_settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    viewModel.finish(biometricLock)
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.welcome_finish)) }
            }
        }
    }
}

private enum class Step { PRIMER, SETTINGS }

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = MediathequeColors.extended.card, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun SwitchRow(title: Int, detail: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(detail), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
