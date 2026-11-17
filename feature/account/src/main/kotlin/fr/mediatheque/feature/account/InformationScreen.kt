// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.BusyButton
import fr.mediatheque.core.designsystem.component.SectionHeader
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.ContactDetails
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.SendingChannel
import fr.mediatheque.core.ui.LoadErrorState
import fr.mediatheque.core.ui.LoadingState
import fr.mediatheque.core.ui.parisFormat
import kotlinx.coroutines.launch

@Composable
internal fun InformationScreen(viewModel: InformationViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val contact by viewModel.contact.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val patron = (session as? SessionStore.State.LoggedIn)?.patron
    var editsContact by rememberSaveable { mutableStateOf(false) }
    var changesPassword by rememberSaveable { mutableStateOf(false) }
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    ProfileScaffold(
        title = stringResource(R.string.home_information),
        message = message,
        onMessageShown = viewModel::messageShown,
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        if (patron == null) {
            LoadingState(Modifier.padding(padding))
            return@ProfileScaffold
        }
        Column(
            Modifier.fillMaxSize().padding(
                padding
            ).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(stringResource(R.string.information_subscription), Modifier.readable())
            GroupCard(Modifier.readable()) {
                InfoRow(stringResource(R.string.information_card_number), patron.cardNumber)
                HorizontalDivider(Modifier.padding(start = 16.dp))
                InfoRow(stringResource(R.string.information_valid_until), patron.cardExpiry?.let { parisFormat().dayMonthYear(it) } ?: DASH)
                HorizontalDivider(Modifier.padding(start = 16.dp))
                InfoRow(stringResource(R.string.information_home_library), patron.homeLibrary?.displayName ?: DASH)
            }
            SectionHeader(stringResource(R.string.information_contact), Modifier.readable()) {
                TextButton(onClick = { editsContact = true }, enabled = contact is LoadState.Loaded) {
                    Text(stringResource(R.string.information_edit))
                }
            }
            when (val state = contact) {
                is LoadState.Failed -> LoadErrorState(state.error, viewModel::load, Modifier.readable())
                else -> ContactCard(patron, state.valueOrNull, Modifier.readable())
            }
            Text(
                stringResource(R.string.information_front_desk),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.readable()
            )
            SectionHeader(stringResource(R.string.information_security), Modifier.readable())
            Surface(
                onClick = { changesPassword = true },
                shape = MaterialTheme.shapes.extraLarge,
                color = MediathequeColors.extended.card,
                modifier = Modifier.readable()
            ) {
                Row(
                    Modifier.heightIn(min = 56.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(painterResource(DesignR.drawable.ic_key), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        stringResource(R.string.password_change),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(painterResource(DesignR.drawable.ic_chevron_right), contentDescription = null)
                }
            }
        }
    }
    val details = contact.valueOrNull
    if (editsContact && details != null) {
        ContactSheet(details, isSaving, onSave = { viewModel.save(it, onSaved = { editsContact = false }) }, onDismiss = {
            editsContact =
                false
        })
    }
    if (changesPassword && patron != null) {
        PasswordSheet(
            cardNumber = patron.cardNumber,
            isSaving = isSaving,
            onChange = { password, confirmation ->
                viewModel.changePassword(password, confirmation) { credentials ->
                    changesPassword = false
                    // The password manager keeps the new one too, if the patron agrees.
                    activity?.let { scope.launch { offerToSave(it, credentials) } }
                }
            },
            onDismiss = { changesPassword = false }
        )
    }
}

@Composable
private fun ContactCard(patron: Patron, details: ContactDetails?, modifier: Modifier = Modifier) {
    GroupCard(modifier) {
        InfoRow(stringResource(R.string.information_name), patron.displayName)
        HorizontalDivider(Modifier.padding(start = 16.dp))
        InfoRow(stringResource(R.string.information_nickname), (details?.nickname ?: patron.nickname).orEmpty().ifBlank { DASH })
        HorizontalDivider(Modifier.padding(start = 16.dp))
        InfoRow(stringResource(R.string.information_email), (details?.email ?: patron.email).orEmpty().ifBlank { DASH })
        HorizontalDivider(Modifier.padding(start = 16.dp))
        InfoRow(stringResource(R.string.information_phone), (details?.phone ?: patron.phone).orEmpty().ifBlank { DASH })
        HorizontalDivider(Modifier.padding(start = 16.dp))
        InfoRow(stringResource(R.string.information_address), patron.postalAddress ?: DASH)
        details?.let {
            HorizontalDivider(Modifier.padding(start = 16.dp))
            InfoRow(stringResource(R.string.information_channel), stringResource(it.channel.label))
        }
    }
}

private const val DASH = "—"

internal val SendingChannel.label: Int
    get() = when (this) {
        SendingChannel.ANY -> R.string.channel_any
        SendingChannel.EMAIL -> R.string.channel_email
        SendingChannel.SMS -> R.string.channel_sms
        SendingChannel.LETTER -> R.string.channel_letter
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactSheet(initial: ContactDetails, isSaving: Boolean, onSave: (ContactDetails) -> Unit, onDismiss: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(initial.nickname) }
    var email by rememberSaveable { mutableStateOf(initial.email) }
    var phone by rememberSaveable { mutableStateOf(initial.phone) }
    var channel by rememberSaveable { mutableStateOf(initial.channel) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.information_contact),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text(stringResource(R.string.information_nickname)) },
                supportingText = { Text(stringResource(R.string.information_nickname_detail)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.information_email)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.EmailAddress }
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text(stringResource(R.string.information_phone)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.PhoneNumber }
            )
            Text(stringResource(R.string.information_channel), style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                SendingChannel.entries.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .selectable(selected = channel == option, role = Role.RadioButton, onClick = { channel = option }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = channel == option, onClick = null)
                        Text(stringResource(option.label), Modifier.padding(start = 16.dp))
                    }
                }
            }
            Text(
                stringResource(R.string.information_channel_detail),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            BusyButton(
                stringResource(R.string.information_save),
                onClick = { onSave(ContactDetails(nickname, email, phone, channel)) },
                modifier = Modifier.fillMaxWidth(),
                busy = isSaving
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordSheet(cardNumber: String, isSaving: Boolean, onChange: (String, String) -> Unit, onDismiss: () -> Unit) {
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var reveals by rememberSaveable { mutableStateOf(false) }
    val mismatch = confirmation.isNotEmpty() && password != confirmation
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.password_change),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            // The card number, for the password manager to pair it with the new password.
            OutlinedTextField(
                value = cardNumber,
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.information_card_number)) },
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username }
            )
            val transformation = if (reveals) VisualTransformation.None else PasswordVisualTransformation()
            val toggle: @Composable () -> Unit = {
                IconButton(onClick = { reveals = !reveals }) {
                    Icon(
                        painterResource(if (reveals) DesignR.drawable.ic_visibility_off else DesignR.drawable.ic_visibility),
                        contentDescription = stringResource(if (reveals) R.string.password_hide else R.string.password_show)
                    )
                }
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.password_new)) },
                singleLine = true,
                visualTransformation = transformation,
                trailingIcon = toggle,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword }
            )
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it },
                label = { Text(stringResource(R.string.password_confirm)) },
                singleLine = true,
                isError = mismatch,
                supportingText = {
                    Text(stringResource(if (mismatch) R.string.password_mismatch else R.string.password_detail))
                },
                visualTransformation = transformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.NewPassword }
            )
            BusyButton(
                stringResource(R.string.password_submit),
                onClick = { onChange(password, confirmation) },
                modifier = Modifier.fillMaxWidth(),
                busy = isSaving,
                enabled = password.isNotEmpty() && !mismatch
            )
        }
    }
}
