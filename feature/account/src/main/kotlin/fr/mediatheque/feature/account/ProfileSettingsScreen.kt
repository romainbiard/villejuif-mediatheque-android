// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.component.SkeletonRow
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.ui.LoadErrorState

/** Days before the due date the patron can choose for the reminder. */
private val DAYS_BEFORE_CHOICES = listOf(1, 2, 3, 5, 7)

private fun notificationsGranted(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
internal fun ProfileSettingsScreen(viewModel: ProfileSettingsViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val reminders by viewModel.reminderSettings.collectAsStateWithLifecycle()
    val device by viewModel.deviceSettings.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val newsletter by viewModel.newsletter.collectAsStateWithLifecycle()
    val updating by viewModel.updating.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var granted by remember { mutableStateOf(notificationsGranted(context)) }
    // Back from the system settings: the permission may have changed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = notificationsGranted(context) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        if (result) pending?.invoke()
        pending = null
    }

    /** Turning a notification on asks for the permission first, once; turning it off never does. */
    fun enabling(isOn: Boolean, apply: () -> Unit) {
        if (!isOn || granted || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            apply()
        } else {
            pending = apply
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    ProfileScaffold(
        title = stringResource(R.string.home_settings),
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(
                padding
            ).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(stringResource(R.string.welcome_notifications), Modifier.readable())
            if (!granted) {
                GroupCard(Modifier.readable()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.welcome_notifications_off), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.welcome_notifications_off_detail), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            )
                        }) { Text(stringResource(R.string.welcome_open_settings)) }
                    }
                }
            }
            GroupCard(Modifier.readable()) {
                SettingSwitchRow(
                    title = stringResource(R.string.welcome_loan_reminders),
                    detail = pluralStringResource(R.plurals.settings_days_before, reminders.daysBefore, reminders.daysBefore),
                    checked = reminders.loanReminders,
                    onChange = { isOn -> enabling(isOn) { viewModel.setLoanReminders(isOn) } }
                )
                if (reminders.loanReminders) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                        DAYS_BEFORE_CHOICES.forEachIndexed { index, days ->
                            SegmentedButton(
                                selected = reminders.daysBefore == days,
                                onClick = { viewModel.setDaysBefore(days) },
                                shape = SegmentedButtonDefaults.itemShape(index, DAYS_BEFORE_CHOICES.size),
                                label = { Text(pluralStringResource(R.plurals.settings_days_short, days, days)) }
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 16.dp))
                SettingSwitchRow(
                    title = stringResource(R.string.welcome_hold_alerts),
                    detail = stringResource(R.string.welcome_hold_alerts_detail),
                    checked = reminders.holdAlerts,
                    onChange = { isOn -> enabling(isOn) { viewModel.setHoldAlerts(isOn) } }
                )
                HorizontalDivider(Modifier.padding(start = 16.dp))
                SettingSwitchRow(
                    title = stringResource(R.string.welcome_agenda_digest),
                    detail = stringResource(R.string.welcome_agenda_digest_detail),
                    checked = reminders.agendaDigest,
                    onChange = { isOn -> enabling(isOn) { viewModel.setAgendaDigest(isOn) } }
                )
            }

            SectionHeader(stringResource(R.string.settings_libraries), Modifier.readable())
            when (val state = bookmarks) {
                LoadState.Loading -> SkeletonRow(Modifier.readable())

                is LoadState.Failed -> LoadErrorState(state.error, viewModel::load, Modifier.readable())

                is LoadState.Loaded -> GroupCard(Modifier.readable()) {
                    Library.entries.forEachIndexed { index, library ->
                        if (index > 0) HorizontalDivider(Modifier.padding(start = 16.dp))
                        SettingSwitchRow(
                            title = library.displayName,
                            detail = if (library == viewModel.homeLibrary) stringResource(R.string.settings_home_library) else null,
                            checked = library in state.value,
                            onChange = { viewModel.setBookmarked(library, it) },
                            busy = library.code in updating
                        )
                    }
                }
            }
            Footer(stringResource(R.string.settings_libraries_footer))

            SectionHeader(stringResource(R.string.settings_newsletter), Modifier.readable())
            GroupCard(Modifier.readable()) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_newsletter_receive),
                    detail = viewModel.patronEmail ?: stringResource(R.string.settings_newsletter_no_email),
                    checked = newsletter,
                    onChange = viewModel::setNewsletter,
                    busy = ProfileSettingsViewModel.NEWSLETTER in updating
                )
            }
            Footer(stringResource(R.string.settings_newsletter_footer))

            if (canLockAccount(context)) {
                SectionHeader(stringResource(R.string.welcome_security), Modifier.readable())
                GroupCard(Modifier.readable()) {
                    SettingSwitchRow(
                        title = stringResource(R.string.welcome_biometric_lock),
                        detail = stringResource(R.string.welcome_biometric_lock_detail),
                        checked = device.biometricLock,
                        onChange = viewModel::setBiometricLock
                    )
                }
                Footer(stringResource(R.string.settings_security_footer))
            }
        }
    }
}

@Composable
private fun Footer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.readable().padding(horizontal = 16.dp)
    )
}
