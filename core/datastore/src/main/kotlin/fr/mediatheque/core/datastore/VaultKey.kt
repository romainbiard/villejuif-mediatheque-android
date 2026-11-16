// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.datastore

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The key that encrypts the vault: an AES-256-GCM Tink keyset, itself encrypted by a key that never leaves
 * the Android Keystore (hardware-backed when the device has it).
 *
 * The keyset lives in its own SharedPreferences file, outside the backup rules: a restored device has no key,
 * and the vault files restored with it (none are, by the same rules) could not be read anyway.
 *
 * Not tied to biometrics on purpose: the background sync must sign back in without the patron. The biometric
 * lock guards the screens, like on iOS.
 */
class VaultKey(private val context: Context) {
    private val mutex = Mutex()
    private var aead: Aead? = null

    /** The cipher, created on first use off the main thread (the Keystore can take a few hundred milliseconds). */
    suspend fun aead(): Aead = mutex.withLock {
        aead ?: withContext(Dispatchers.IO) {
            AeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, PREFERENCES_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }.also { aead = it }
    }

    private companion object {
        const val KEYSET_NAME = "vault_keyset"
        const val PREFERENCES_FILE = "vault_key"
        const val MASTER_KEY_URI = "android-keystore://mediatheque_vault_master_key"
    }
}
