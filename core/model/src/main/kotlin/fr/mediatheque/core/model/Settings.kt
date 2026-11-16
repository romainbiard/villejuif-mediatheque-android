// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import kotlinx.serialization.Serializable

/** The patron's card number and password, kept to sign back in when the web session expires. */
@Serializable
data class Credentials(val cardNumber: String, val password: String) {
    /** Never prints the password, in logs or crash reports. */
    override fun toString(): String = "Credentials(cardNumber=$cardNumber, password=•••)"
}

/** Reminder settings, chosen after sign-in and then in the settings screen. Backed up with the device. */
data class ReminderSettings(
    val loanReminders: Boolean = true,
    val daysBefore: Int = 3,
    val holdAlerts: Boolean = true,
    /** Off by default: only returns and holds matter to everyone. */
    val agendaDigest: Boolean = false
)

/** Settings that belong to this device and this patron: never backed up, reset on sign-out. */
data class DeviceSettings(
    /** Biometric (or screen lock) check when the app opens and after two minutes in the background. */
    val biometricLock: Boolean = false,
    /** The welcome setup (reminders, biometrics) was shown after sign-in. */
    val onboardingDone: Boolean = false
)
