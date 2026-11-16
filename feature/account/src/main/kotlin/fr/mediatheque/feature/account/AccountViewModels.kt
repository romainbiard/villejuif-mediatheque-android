// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.AccountRepository
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.AuthError
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.data.SessionRepository
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.datastore.ValueStore
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.DeviceSettings
import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.ReminderSettings
import fr.mediatheque.core.model.RenewalResult
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A short message after an action, shown in a snackbar. */
sealed interface AccountMessage {
    data class Renewed(val renewed: Int, val refused: Int) : AccountMessage

    data object RenewOffline : AccountMessage

    data object RenewFailed : AccountMessage

    data object HoldCancelled : AccountMessage

    data object CancelFailed : AccountMessage
}

/** The account's root: who is signed in, and the summary that refreshes when the account opens. */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val account: AccountRepository,
    private val accountSync: AccountSync,
    private val snapshots: ValueStore<AccountSnapshot>,
    private val inbox: ValueStore<Inbox>,
    settings: SettingsStore,
    private val clock: Clock
) : ViewModel() {
    val session: StateFlow<SessionStore.State> = sessionStore.state
    val deviceSettings: StateFlow<DeviceSettings?> = settings.deviceSettings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _snapshot = MutableStateFlow<AccountSnapshot?>(null)

    /** Loans and holds for the summary: the last snapshot at once, then the portal's. */
    val snapshot: StateFlow<AccountSnapshot?> = _snapshot.asStateFlow()

    private val _refreshError = MutableStateFlow<LoadError?>(null)

    /** The refresh failed: what is on screen is the last account read. */
    val refreshError: StateFlow<LoadError?> = _refreshError.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _unreadNotifications = MutableStateFlow(0)
    val unreadNotifications: StateFlow<Int> = _unreadNotifications.asStateFlow()

    val now: Instant get() = clock.instant()

    /** The account opened: read again, with the sync (snapshot, reminders, notification center) alongside. */
    fun refresh() {
        if (sessionStore.patron == null || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            if (_snapshot.value == null) _snapshot.value = snapshots.load()
            val sync = async { runCatching { accountSync.run() }.getOrNull() }
            try {
                val patron = account.patron()
                sessionStore.update(patron)
                accountSync.recordLibraryMessage(patron.message, patron.hasUnreadMessage)
                _refreshError.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val loadError = LoadError.from(error)
                if (loadError == LoadError.NotLoggedIn) sessionStore.logout()
                _refreshError.value = loadError
            }
            sync.await()?.let { _snapshot.value = it }
            _unreadNotifications.value = inbox.load()?.unreadCount ?: 0
            _isRefreshing.value = false
        }
    }

    fun unlock() {
        viewModelScope.launch { sessionStore.unlock() }
    }

    fun logout() {
        viewModelScope.launch { sessionStore.logout() }
    }
}

data class LoginUiState(
    val cardNumber: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    val error: LoginError? = null
) {
    val canSubmit: Boolean get() = cardNumber.isNotBlank() && password.isNotEmpty() && !isSubmitting
}

enum class LoginError { INVALID, OFFLINE, SITE_DOWN }

@HiltViewModel
class LoginViewModel @Inject constructor(private val session: SessionRepository, private val sessionStore: SessionStore) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onCardNumberChange(value: String) = _state.update { it.copy(cardNumber = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    /** Signs in; [onSuccess] gets the credentials, to offer saving them in the password manager. */
    fun submit(onSuccess: (Credentials) -> Unit) {
        val current = _state.value
        if (!current.canSubmit) return
        val credentials = Credentials(current.cardNumber.trim(), current.password)
        _state.update { it.copy(isSubmitting = true, error = null) }
        viewModelScope.launch {
            try {
                val patron = session.login(credentials)
                _state.update { it.copy(password = "", isSubmitting = false) }
                sessionStore.didLogIn(patron)
                onSuccess(credentials)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val loginError = when {
                    error is AuthError.InvalidCredentials -> LoginError.INVALID
                    LoadError.from(error) == LoadError.Offline -> LoginError.OFFLINE
                    else -> LoginError.SITE_DOWN
                }
                _state.update { it.copy(isSubmitting = false, error = loginError) }
            }
        }
    }
}

@HiltViewModel
class LostPasswordViewModel @Inject constructor(private val session: SessionRepository) : ViewModel() {
    enum class Status { IDLE, SENDING, SENT, FAILED }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun send(cardNumber: String) {
        val card = cardNumber.trim()
        if (card.isEmpty() || _status.value == Status.SENDING) return
        _status.value = Status.SENDING
        viewModelScope.launch {
            _status.value = try {
                session.requestPasswordReset(card)
                Status.SENT
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Status.FAILED
            }
        }
    }
}

/** Loans grouped by due date. */
data class LoanSection(val kind: Kind, val loans: List<Loan>) {
    enum class Kind { LATE, THIS_WEEK, LATER }
}

data class LoansUiState(
    val loans: LoadState<List<Loan>> = LoadState.Loading,
    val staleError: LoadError? = null,
    val renewing: Set<String> = emptySet(),
    /** Loans the site refused to renew while this screen is open. */
    val refused: Set<String> = emptySet(),
    val message: AccountMessage? = null,
    val now: Instant
) {
    val sections: List<LoanSection>
        get() {
            val grouped = loans.valueOrNull.orEmpty().groupBy { loan ->
                when (loan.dueState(now)) {
                    is Loan.DueState.Late -> LoanSection.Kind.LATE
                    is Loan.DueState.DueSoon -> LoanSection.Kind.THIS_WEEK
                    is Loan.DueState.OnTime -> LoanSection.Kind.LATER
                }
            }
            return LoanSection.Kind.entries.mapNotNull { kind -> grouped[kind]?.let { LoanSection(kind, it) } }
        }
}

@HiltViewModel
class LoansViewModel @Inject constructor(
    private val account: AccountRepository,
    private val snapshots: ValueStore<AccountSnapshot>,
    private val accountSync: AccountSync,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(LoansUiState(now = clock.instant()))
    val state: StateFlow<LoansUiState> = _state.asStateFlow()

    init {
        load()
    }

    /** The last snapshot shows at once, then the portal's list replaces it. */
    fun load() {
        viewModelScope.launch {
            if (_state.value.loans !is LoadState.Loaded) {
                snapshots.load()?.let { snapshot -> _state.update { it.copy(loans = LoadState.Loaded(snapshot.upcomingLoans)) } }
            }
            try {
                val loans = account.loans().sortedBy { it.dueDate }
                _state.update { it.copy(loans = LoadState.Loaded(loans), staleError = null, now = clock.instant()) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    if (it.loans is LoadState.Loaded) {
                        it.copy(
                            staleError = LoadError.from(error)
                        )
                    } else {
                        it.copy(loans = LoadState.Failed(LoadError.from(error)))
                    }
                }
            }
        }
    }

    fun renew(loan: Loan) = renew(listOf(loan.id))

    fun renewAll() = renew(_state.value.loans.valueOrNull.orEmpty().map { it.id }.filterNot { it in _state.value.refused })

    fun messageShown() = _state.update { it.copy(message = null) }

    private fun renew(ids: List<String>) {
        if (ids.isEmpty()) return
        _state.update { it.copy(renewing = it.renewing + ids) }
        viewModelScope.launch {
            try {
                val results = account.renew(ids)
                val refused = results.filterIsInstance<RenewalResult.Refused>().map { it.loanId }
                _state.update {
                    it.copy(refused = it.refused + refused, message = AccountMessage.Renewed(results.size - refused.size, refused.size))
                }
                load()
                // The due dates changed: the reminders are scheduled again, without holding the renew buttons.
                viewModelScope.launch { runCatching { accountSync.run() } }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val message = if (LoadError.from(error) == LoadError.Offline) AccountMessage.RenewOffline else AccountMessage.RenewFailed
                _state.update { it.copy(message = message) }
            } finally {
                _state.update { it.copy(renewing = it.renewing - ids.toSet()) }
            }
        }
    }
}

data class HoldsUiState(
    val holds: LoadState<List<Hold>> = LoadState.Loading,
    val staleError: LoadError? = null,
    val cancelling: Set<String> = emptySet(),
    val message: AccountMessage? = null
) {
    val ready: List<Hold> get() = holds.valueOrNull.orEmpty().filter { it.isReadyForPickup }
    val waiting: List<Hold> get() = holds.valueOrNull.orEmpty().filterNot { it.isReadyForPickup }
}

@HiltViewModel
class HoldsViewModel @Inject constructor(private val account: AccountRepository, private val snapshots: ValueStore<AccountSnapshot>) :
    ViewModel() {
    private val _state = MutableStateFlow(HoldsUiState())
    val state: StateFlow<HoldsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_state.value.holds !is LoadState.Loaded) {
                snapshots.load()?.let { snapshot -> _state.update { it.copy(holds = LoadState.Loaded(snapshot.holds)) } }
            }
            try {
                val holds = account.holds()
                _state.update { it.copy(holds = LoadState.Loaded(holds), staleError = null) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    if (it.holds is LoadState.Loaded) {
                        it.copy(
                            staleError = LoadError.from(error)
                        )
                    } else {
                        it.copy(holds = LoadState.Failed(LoadError.from(error)))
                    }
                }
            }
        }
    }

    fun cancel(hold: Hold) {
        _state.update { it.copy(cancelling = it.cancelling + hold.id) }
        viewModelScope.launch {
            try {
                account.cancelHold(hold.id)
                _state.update { state ->
                    state.copy(
                        holds = LoadState.Loaded(
                            state.holds.valueOrNull.orEmpty().filter {
                                it.id != hold.id
                            }
                        ),
                        message = AccountMessage.HoldCancelled
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _state.update { it.copy(message = AccountMessage.CancelFailed) }
            } finally {
                _state.update { it.copy(cancelling = it.cancelling - hold.id) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

/** After sign-in, once: notifications (explained before the system asks), reminders, and the biometric lock. */
@HiltViewModel
class WelcomeSetupViewModel @Inject constructor(private val settings: SettingsStore, private val accountSync: AccountSync) : ViewModel() {
    val reminderSettings = settings.reminderSettings.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings())

    fun setLoanReminders(enabled: Boolean) = updateReminders { it.copy(loanReminders = enabled) }

    fun setHoldAlerts(enabled: Boolean) = updateReminders { it.copy(holdAlerts = enabled) }

    fun setAgendaDigest(enabled: Boolean) = updateReminders { it.copy(agendaDigest = enabled) }

    fun finish(biometricLock: Boolean) {
        viewModelScope.launch {
            settings.setBiometricLock(biometricLock)
            settings.setOnboardingDone(true)
            runCatching { accountSync.run() }
        }
    }

    private fun updateReminders(transform: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch { settings.updateReminderSettings(transform) }
    }
}
