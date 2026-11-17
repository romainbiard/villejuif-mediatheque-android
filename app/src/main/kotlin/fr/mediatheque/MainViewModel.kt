// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.SessionStore
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** The app's launch: the session reopens once, whatever screen is shown first. */
@HiltViewModel
class MainViewModel @Inject constructor(private val sessionStore: SessionStore) : ViewModel() {
    /** For links from outside (notifications, widget, shortcuts): account screens open only once signed in. */
    val session: StateFlow<SessionStore.State> = sessionStore.state

    fun restoreSession() {
        viewModelScope.launch { sessionStore.restore() }
    }
}
