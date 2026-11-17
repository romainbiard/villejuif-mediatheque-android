// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.datastore.ValueStore
import fr.mediatheque.core.model.Patron
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The sign-in state shared across the app (the account, a hold from a record page, the digital services).
 *
 * The biometric check itself is the UI's: when the state is [State.Locked], the account screen asks for it and calls
 * [unlock] once it passed.
 */
@Singleton
class SessionStore @Inject constructor(
    private val session: SessionRepository,
    private val patronCache: ValueStore<Patron>,
    private val settings: SettingsStore,
    private val accountSync: AccountSync,
    private val clock: Clock
) {
    sealed interface State {
        data object Restoring : State

        /** The biometric lock is on and has not been passed yet. */
        data object Locked : State

        data object LoggedOut : State

        data class LoggedIn(val patron: Patron) : State
    }

    private val _state = MutableStateFlow<State>(State.Restoring)
    val state: StateFlow<State> = _state.asStateFlow()

    val patron: Patron? get() = (_state.value as? State.LoggedIn)?.patron

    private val mutex = Mutex()
    private var restored = false
    private var backgroundedAt: Instant? = null

    /** The account kept while locked after a stay in the background: the biometric check reopens it without signing in. */
    private var lockedPatron: Patron? = null

    /**
     * At launch, once: reopens the session if credentials are stored. The last known account shows at once.
     *
     * Only a restore that ran to the end counts: the caller's scope can be cancelled midway (the activity finished
     * while the process lives on), and the next launch must try again rather than find [State.Restoring] for good.
     */
    suspend fun restore() {
        mutex.withLock {
            if (restored) return
            val hasCredentials = session.hasCredentials()
            if (hasCredentials && settings.currentDeviceSettings().biometricLock) {
                _state.value = State.Locked
            } else {
                reopen(hasCredentials)
            }
            restored = true
        }
    }

    /** The biometric check passed. */
    suspend fun unlock() {
        if (_state.value != State.Locked) return
        lockedPatron?.let {
            lockedPatron = null
            _state.value = State.LoggedIn(it)
            return
        }
        _state.value = State.Restoring
        try {
            reopen(hasCredentials = true)
        } catch (cancellation: CancellationException) {
            // Interrupted: locked again, so the next check starts over instead of a spinner that never ends.
            if (_state.value == State.Restoring) _state.value = State.Locked
            throw cancellation
        }
    }

    private suspend fun reopen(hasCredentials: Boolean) {
        if (hasCredentials) patronCache.load()?.let { _state.value = State.LoggedIn(it) }
        try {
            val patron = session.restore()
            if (patron != null) {
                didLogIn(patron)
            } else {
                patronCache.clear()
                _state.value = State.LoggedOut
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // Offline or site down: keep the last known account, the account screen says it could not refresh.
            // Without one, signed out: the next action will try again.
            if (patron != null && LoadError.from(error) != LoadError.NotLoggedIn) return
            patronCache.clear()
            _state.value = State.LoggedOut
        }
    }

    suspend fun didLogIn(patron: Patron) {
        _state.value = State.LoggedIn(patron)
        patronCache.save(patron)
    }

    /** The account was read again (counters, card). */
    suspend fun update(patron: Patron) {
        if (_state.value !is State.LoggedIn) return
        _state.value = State.LoggedIn(patron)
        patronCache.save(patron)
    }

    /** The app went to the background (all its screens stopped). */
    fun onBackground() {
        if (_state.value is State.LoggedIn) backgroundedAt = clock.instant()
    }

    /** Back in the foreground: past [RELOCK_DELAY] with the biometric lock on, the account locks again. */
    suspend fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        val current = _state.value as? State.LoggedIn ?: return
        if (!settings.currentDeviceSettings().biometricLock) return
        if (Duration.between(since, clock.instant()) < RELOCK_DELAY) return
        lockedPatron = current.patron
        _state.value = State.Locked
    }

    /** Signs out and erases the account from the device: credentials, cache, snapshot, reminders, device settings. */
    suspend fun logout() {
        lockedPatron = null
        backgroundedAt = null
        session.logout()
        patronCache.clear()
        accountSync.reset()
        // The next patron to sign in on this device starts from the defaults (biometric lock off, welcome setup again).
        settings.resetDeviceSettings()
        _state.value = State.LoggedOut
    }

    companion object {
        /** With the biometric lock on, the account locks again after this long in the background. */
        val RELOCK_DELAY: Duration = Duration.ofMinutes(2)
    }
}
