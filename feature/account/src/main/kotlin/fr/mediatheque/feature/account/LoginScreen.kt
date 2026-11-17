// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.app.Activity
import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode as ScannedBarcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.BusyButton
import fr.mediatheque.core.designsystem.component.VillejuifCrest
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.model.Credentials
import kotlinx.coroutines.launch

@Composable
internal fun LoginScreen(viewModel: LoginViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showsLostPassword by rememberSaveable { mutableStateOf(false) }
    var showsPassword by rememberSaveable { mutableStateOf(false) }
    val submit = {
        viewModel.submit { credentials -> activity?.let { scope.launch { offerToSave(it, credentials) } } }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val extended = MediathequeColors.extended
        Surface(
            color = extended.brandSurface,
            contentColor = extended.onBrandSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                Modifier.drawBehind {
                    // The City's shapes, as on its posters and the iOS app: a magenta disc, a deep violet one, a yellow
                    // dot and a cyan ring.
                    drawCircle(extended.magenta, radius = 150.dp.toPx(), center = Offset(size.width - 20.dp.toPx(), 30.dp.toPx()))
                    drawCircle(LoginDeepViolet, radius = 90.dp.toPx(), center = Offset(-10.dp.toPx(), size.height - 20.dp.toPx()))
                    drawCircle(extended.yellow, radius = 12.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.52f))
                    drawCircle(
                        extended.cyan,
                        radius = 30.dp.toPx(),
                        center = Offset(size.width * 0.85f, size.height - 10.dp.toPx()),
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
            ) {
                Column(
                    Modifier.statusBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp).widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        VillejuifCrest(Modifier.size(width = 64.dp, height = 48.dp), tint = Color.White)
                        Text(stringResource(R.string.login_city), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        stringResource(R.string.account_title),
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 12.dp).semantics { heading() }
                    )
                    Text(stringResource(R.string.login_tagline), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = state.cardNumber,
                onValueChange = viewModel::onCardNumberChange,
                label = { Text(stringResource(R.string.login_card_number)) },
                supportingText = { Text(stringResource(R.string.login_card_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = { scanCard(context) { viewModel.onCardNumberChange(it) } }) {
                        Icon(
                            painterResource(DesignR.drawable.ic_qr_code_scanner),
                            contentDescription = stringResource(R.string.login_scan_card)
                        )
                    }
                },
                // The password manager recognizes the field and fills it.
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username }
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation = if (showsPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { showsPassword = !showsPassword }) {
                        Icon(
                            painterResource(if (showsPassword) DesignR.drawable.ic_visibility_off else DesignR.drawable.ic_visibility),
                            contentDescription = stringResource(
                                if (showsPassword) R.string.login_hide_password else R.string.login_show_password
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Password }
            )
            state.error?.let { error ->
                Text(
                    stringResource(
                        when (error) {
                            LoginError.INVALID -> R.string.login_invalid
                            LoginError.OFFLINE -> R.string.login_offline
                            LoginError.SITE_DOWN -> R.string.login_site_down
                        }
                    ),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )
            }
            BusyButton(
                text = stringResource(R.string.login_submit),
                onClick = { submit() },
                busy = state.isSubmitting,
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { scanCard(context) { viewModel.onCardNumberChange(it) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(DesignR.drawable.ic_qr_code_scanner), contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.login_scan_card), Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = { showsLostPassword = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.login_forgot_password))
            }
            Text(
                stringResource(R.string.login_not_registered),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    if (showsLostPassword) {
        LostPasswordSheet(initialCardNumber = state.cardNumber, onDismiss = { showsLostPassword = false })
    }
}

/**
 * The card's barcode through Google's code scanner: the camera runs in Google Play services, so the app needs no
 * camera permission. Without Play services, the patron types the number.
 */
private fun scanCard(context: Context, onScanned: (String) -> Unit) {
    val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(
        ScannedBarcode.FORMAT_CODE_39,
        ScannedBarcode.FORMAT_CODE_128
    ).build()
    GmsBarcodeScanning.getClient(context, options).startScan().addOnSuccessListener { barcode ->
        barcode.rawValue?.filter { it.isLetterOrDigit() }?.takeIf { it.isNotEmpty() }?.let(onScanned)
    }
}

/** After a sign-in, offers to keep the card number and password in the patron's password manager. */
internal suspend fun offerToSave(activity: Activity, credentials: Credentials) {
    try {
        CredentialManager.create(activity).createCredential(activity, CreatePasswordRequest(credentials.cardNumber, credentials.password))
    } catch (_: CreateCredentialException) {
        // Declined, or no password manager: nothing to do.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LostPasswordSheet(initialCardNumber: String, onDismiss: () -> Unit, viewModel: LostPasswordViewModel = hiltViewModel()) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    var cardNumber by rememberSaveable { mutableStateOf(initialCardNumber) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                stringResource(R.string.lost_password_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics {
                    heading()
                }
            )
            Text(stringResource(R.string.lost_password_message), style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = cardNumber,
                onValueChange = { cardNumber = it },
                label = { Text(stringResource(R.string.login_card_number)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().semantics { contentType = ContentType.Username },
                shape = RoundedCornerShape(12.dp)
            )
            when (status) {
                LostPasswordViewModel.Status.SENT -> Text(
                    stringResource(R.string.lost_password_sent),
                    color = MediathequeColors.extended.success,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )

                LostPasswordViewModel.Status.FAILED -> Text(
                    stringResource(R.string.lost_password_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                )

                else -> Unit
            }
            BusyButton(
                text = stringResource(R.string.lost_password_send),
                onClick = { viewModel.send(cardNumber) },
                busy = status == LostPasswordViewModel.Status.SENDING,
                enabled = cardNumber.isNotBlank() && status != LostPasswordViewModel.Status.SENT,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.lost_password_no_email),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// The login header keeps the card's colors in both themes.
private val LoginDeepViolet = Color(0xFF5714A4)
