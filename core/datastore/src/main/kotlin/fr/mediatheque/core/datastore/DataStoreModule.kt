// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.Patron
import java.io.File
import javax.inject.Singleton

/**
 * Where each value lives. Only `datastore/settings.preferences_pb` is backed up
 * (app/src/main/res/xml/data_extraction_rules.xml): everything else stays on this device.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context) = SettingsStore(
        settings = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsStore.SETTINGS_FILE) },
        device = PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(SettingsStore.DEVICE_FILE) }
    )

    @Provides
    @Singleton
    fun vaultKey(@ApplicationContext context: Context) = VaultKey(context)

    /** Card number and password, encrypted. */
    @Provides
    @Singleton
    fun credentialStore(@ApplicationContext context: Context, key: VaultKey): ValueStore<Credentials> =
        FileValueStore(File(context.filesDir, "vault/credentials.bin"), Credentials.serializer(), key::aead)

    /** Last account read from the portal, shown at once on launch while the session reopens. Encrypted. */
    @Provides
    @Singleton
    fun patronCache(@ApplicationContext context: Context, key: VaultKey): ValueStore<Patron> =
        FileValueStore(File(context.filesDir, "vault/patron.bin"), Patron.serializer(), key::aead)

    /** Loans and holds for the widget and the background sync. */
    @Provides
    @Singleton
    fun snapshotStore(@ApplicationContext context: Context): ValueStore<AccountSnapshot> =
        FileValueStore(File(context.filesDir, "account/snapshot.json"), AccountSnapshot.serializer())

    /** Notification center history. */
    @Provides
    @Singleton
    fun inboxStore(@ApplicationContext context: Context): ValueStore<Inbox> =
        FileValueStore(File(context.filesDir, "account/inbox.json"), Inbox.serializer())
}
