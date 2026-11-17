// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.datastore.SettingsStore
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The app's launch: the session reopens once, whatever screen is shown first. */
@HiltViewModel
class MainViewModel @Inject constructor(private val sessionStore: SessionStore, settings: SettingsStore) : ViewModel() {
    /** With the biometric lock on, the app switcher shows no snapshot of the account. */
    val biometricLock: Flow<Boolean> = settings.deviceSettings.map { it.biometricLock }.distinctUntilChanged()

    /** For links from outside (notifications, widget, shortcuts): account screens open only once signed in. */
    val session: StateFlow<SessionStore.State> = sessionStore.state

    fun restoreSession() {
        viewModelScope.launch { sessionStore.restore() }
    }
}
