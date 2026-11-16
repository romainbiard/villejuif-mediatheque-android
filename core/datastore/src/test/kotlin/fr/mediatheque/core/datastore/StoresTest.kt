// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.DeviceSettings
import fr.mediatheque.core.model.ReminderSettings
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileValueStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val credentials = Credentials("0012345", "secret-password")

    private fun newAead(): Aead {
        AeadConfig.register()
        return KeysetHandle.newBuilder()
            .addEntry(KeysetHandle.generateEntryFromParameters(PredefinedAeadParameters.AES256_GCM).withRandomId().makePrimary())
            .build().getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    @Test
    fun theVaultRoundTripsAndNeverWritesThePasswordInTheClear() = runTest {
        val aead = newAead()
        val file = File(folder.root, "vault/credentials.bin")
        val store = FileValueStore(file, Credentials.serializer(), aead = { aead }, ioDispatcher = StandardTestDispatcher(testScheduler))

        assertNull(store.load())
        store.save(credentials)
        assertEquals(credentials, store.load())
        assertFalse(file.readBytes().decodeToString().contains("secret-password"))
        assertFalse(File(file.parentFile, "credentials.bin.tmp").exists())

        store.clear()
        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test
    fun aVaultFileFromAnotherKeyIsForgotten() = runTest {
        val file = File(folder.root, "vault/credentials.bin")
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = newAead()
        FileValueStore(file, Credentials.serializer(), aead = { first }, ioDispatcher = dispatcher).save(credentials)

        // Restored on another device, or the Keystore key was lost: the value cannot be read and is removed.
        val other = newAead()
        assertNull(FileValueStore(file, Credentials.serializer(), aead = { other }, ioDispatcher = dispatcher).load())
        assertFalse(file.exists())
    }

    @Test
    fun anUnreadableJsonFileIsForgotten() = runTest {
        val file = File(folder.root, "account/snapshot.json").apply {
            parentFile!!.mkdirs()
            writeText("{not json")
        }
        val store = FileValueStore(file, Credentials.serializer(), ioDispatcher = StandardTestDispatcher(testScheduler))
        assertNull(store.load())
        assertFalse(file.exists())
    }

    @Test
    fun theCredentialsNeverPrintThePassword() {
        assertFalse(credentials.toString().contains("secret-password"))
    }
}

class SettingsStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun TestScope.store() = SettingsStore(
        settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
        device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
    )

    @Test
    fun defaultsThenUpdates() = runTest {
        val store = store()
        assertEquals(ReminderSettings(), store.currentReminderSettings())
        assertEquals(DeviceSettings(), store.currentDeviceSettings())

        store.updateReminderSettings { it.copy(daysBefore = 5, agendaDigest = true) }
        store.setBiometricLock(true)
        store.setOnboardingDone(true)

        assertEquals(ReminderSettings(daysBefore = 5, agendaDigest = true), store.currentReminderSettings())
        assertTrue(store.currentDeviceSettings().biometricLock)
    }

    @Test
    fun signingOutResetsOnlyTheDeviceSettings() = runTest {
        val store = store()
        store.updateReminderSettings { it.copy(loanReminders = false) }
        store.setBiometricLock(true)

        store.resetDeviceSettings()

        assertEquals(DeviceSettings(), store.currentDeviceSettings())
        assertFalse(store.currentReminderSettings().loanReminders)
    }
}
