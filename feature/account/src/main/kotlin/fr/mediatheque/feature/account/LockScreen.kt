// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.content.Context
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.EmptyState

/** Fingerprint, face, or the device's screen lock as a fallback (as `deviceOwnerAuthentication` on iOS). */
internal const val ALLOWED_AUTHENTICATORS = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

/** Whether the device can lock the account (a biometric or at least a screen lock is set up). */
internal fun canLockAccount(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

/** Asks for the fingerprint, the face or the screen lock; [onSuccess] once it passed. */
internal fun authenticate(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        }
    )
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(ALLOWED_AUTHENTICATORS).build())
}

/** The account is locked: the check is asked for at once, and the button asks again after a cancel. */
@Composable
internal fun LockScreen(onUnlocked: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val activity = LocalActivity.current as? FragmentActivity
    val title = stringResource(R.string.lock_prompt_title)
    val ask = { activity?.let { authenticate(it, title, onUnlocked) } }
    LaunchedEffect(Unit) { ask() }
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EmptyState(
            icon = DesignR.drawable.ic_lock,
            title = stringResource(R.string.lock_title),
            message = stringResource(R.string.lock_message),
            modifier = Modifier.widthIn(max = 480.dp)
        )
        Button(onClick = { ask() }, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.lock_unlock)) }
        TextButton(onClick = onSignOut) { Text(stringResource(R.string.lock_sign_out)) }
    }
}
