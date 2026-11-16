// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.mediatheque.core.model.DeviceSettings
import fr.mediatheque.core.model.ReminderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The patron's preferences, in two DataStores:
 * - `settings` (reminders) goes into the device backup, so a new phone keeps the patron's choices;
 * - `device` (biometric lock, welcome setup) stays on this device and is reset on sign-out.
 *
 * The file names match app/src/main/res/xml/data_extraction_rules.xml.
 */
class SettingsStore(private val settings: DataStore<Preferences>, private val device: DataStore<Preferences>) {
    val reminderSettings: Flow<ReminderSettings> = settings.data.map { it.reminderSettings() }

    val deviceSettings: Flow<DeviceSettings> = device.data.map { preferences ->
        DeviceSettings(
            biometricLock = preferences[Keys.biometricLock] ?: false,
            onboardingDone = preferences[Keys.onboardingDone] ?: false
        )
    }

    suspend fun currentReminderSettings(): ReminderSettings = reminderSettings.first()

    suspend fun currentDeviceSettings(): DeviceSettings = deviceSettings.first()

    suspend fun updateReminderSettings(transform: (ReminderSettings) -> ReminderSettings) {
        settings.edit { preferences ->
            val updated = transform(preferences.reminderSettings())
            preferences[Keys.loanReminders] = updated.loanReminders
            preferences[Keys.daysBefore] = updated.daysBefore
            preferences[Keys.holdAlerts] = updated.holdAlerts
            preferences[Keys.agendaDigest] = updated.agendaDigest
        }
    }

    suspend fun setBiometricLock(enabled: Boolean) {
        device.edit { it[Keys.biometricLock] = enabled }
    }

    suspend fun setOnboardingDone(done: Boolean) {
        device.edit { it[Keys.onboardingDone] = done }
    }

    /** Recent catalog searches, newest first: on this device only. */
    val recentSearches: Flow<List<String>> = device.data.map { preferences ->
        preferences[Keys.recentSearches]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
    }

    suspend fun setRecentSearches(searches: List<String>) {
        device.edit { it[Keys.recentSearches] = searches.joinToString(SEPARATOR) }
    }

    /** On sign-out: the next patron on this device starts from the defaults. */
    suspend fun resetDeviceSettings() {
        device.edit { it.clear() }
    }

    private fun Preferences.reminderSettings(): ReminderSettings {
        val defaults = ReminderSettings()
        return ReminderSettings(
            loanReminders = this[Keys.loanReminders] ?: defaults.loanReminders,
            daysBefore = this[Keys.daysBefore] ?: defaults.daysBefore,
            holdAlerts = this[Keys.holdAlerts] ?: defaults.holdAlerts,
            agendaDigest = this[Keys.agendaDigest] ?: defaults.agendaDigest
        )
    }

    private object Keys {
        val loanReminders = booleanPreferencesKey("reminders.loans")
        val daysBefore = intPreferencesKey("reminders.daysBefore")
        val holdAlerts = booleanPreferencesKey("reminders.holds")
        val agendaDigest = booleanPreferencesKey("reminders.agendaDigest")
        val biometricLock = booleanPreferencesKey("security.biometricLock")
        val onboardingDone = booleanPreferencesKey("account.onboardingDone")
        val recentSearches = stringPreferencesKey("search.recent")
    }

    companion object {
        /** Backed up: `datastore/settings.preferences_pb`. */
        const val SETTINGS_FILE = "settings"

        /** Unit separator: cannot be typed in a search. */
        private const val SEPARATOR = "\u001F"

        /** Not backed up: `datastore/device.preferences_pb`. */
        const val DEVICE_FILE = "device"
    }
}
