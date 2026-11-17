// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.AccountRepository
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.data.ProfileError
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.datastore.ValueStore
import fr.mediatheque.core.model.ContactDetails
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.DeviceSettings
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.InboxItem
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.RecordToRate
import fr.mediatheque.core.model.ReminderSettings
import fr.mediatheque.core.model.RenewalResult
import fr.mediatheque.core.model.Review
import fr.mediatheque.core.model.Selection
import fr.mediatheque.core.model.Suggestion
import fr.mediatheque.core.model.SuggestionDraft
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A short message after a profile action, shown in a snackbar. */
sealed interface ProfileMessage {
    /** What the patron tried, for the failure text. */
    enum class Action { CREATION, REMOVAL, SENDING, SAVING, PASSWORD_CHANGE, MODIFICATION, RENEWAL }

    data class Failed(val action: Action, val offline: Boolean) : ProfileMessage

    /** The site rejected the form, with its own reason. */
    data class Rejected(val reason: String) : ProfileMessage

    data class SelectionCreated(val name: String) : ProfileMessage

    data object DuplicateSelection : ProfileMessage

    data class RemovedFrom(val selection: String) : ProfileMessage

    data object ReviewSent : ProfileMessage

    data object SuggestionSent : ProfileMessage

    data object ContactSaved : ProfileMessage

    data object InvalidEmail : ProfileMessage

    data object PasswordEmpty : ProfileMessage

    data object PasswordMismatch : ProfileMessage

    data object PasswordChanged : ProfileMessage

    data class RenewedUntil(val dueDate: Instant) : ProfileMessage

    data object RenewRefused : ProfileMessage

    companion object {
        fun failure(error: Throwable, action: Action): ProfileMessage =
            if (error is ProfileError.Rejected) Rejected(error.siteMessage) else Failed(action, LoadError.from(error) == LoadError.Offline)
    }
}

/** Runs [block], turning a failure into a [ProfileMessage]; `null` when it went through. */
private suspend fun attempt(action: ProfileMessage.Action, block: suspend () -> Unit): ProfileMessage? = try {
    block()
    null
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (error: Exception) {
    ProfileMessage.failure(error, action)
}

/** Loads a list into [state]; a failure only shows when nothing was loaded yet. */
private suspend fun <T> MutableStateFlow<LoadState<T>>.loadInto(block: suspend () -> T) {
    try {
        value = LoadState.Loaded(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        if (value !is LoadState.Loaded) value = LoadState.Failed(LoadError.from(error))
    }
}

// My selections

@HiltViewModel
class SelectionsViewModel @Inject constructor(private val profile: ProfileRepository) : ViewModel() {
    private val _selections = MutableStateFlow<LoadState<List<Selection>>>(LoadState.Loading)
    val selections: StateFlow<LoadState<List<Selection>>> = _selections.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _selections.loadInto { profile.selections() } }
    }

    fun create(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || _isCreating.value) return
        if (_selections.value.valueOrNull.orEmpty().any { it.name.equals(trimmed, ignoreCase = true) }) {
            _message.value = ProfileMessage.DuplicateSelection
            return
        }
        _isCreating.value = true
        viewModelScope.launch {
            val failure = attempt(ProfileMessage.Action.CREATION) { profile.createSelection(trimmed) }
            if (failure == null) _selections.loadInto { profile.selections() }
            _message.value = failure ?: ProfileMessage.SelectionCreated(trimmed)
            _isCreating.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }
}

@HiltViewModel(assistedFactory = SelectionDetailViewModel.Factory::class)
class SelectionDetailViewModel @AssistedInject constructor(@Assisted val selection: Selection, private val profile: ProfileRepository) :
    ViewModel() {
    private val _notices = MutableStateFlow<LoadState<List<Notice>>>(LoadState.Loading)
    val notices: StateFlow<LoadState<List<Notice>>> = _notices.asStateFlow()

    private val _removing = MutableStateFlow<Set<Int>>(emptySet())
    val removing: StateFlow<Set<Int>> = _removing.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    init {
        load()
    }

    /** Selections stay short: every page at once. */
    fun load() {
        viewModelScope.launch {
            _notices.loadInto {
                val notices = mutableListOf<Notice>()
                var page = 1
                while (true) {
                    val result = profile.selectionNotices(selection, page)
                    notices += result.documents
                    if (result.documents.isEmpty() || notices.size >= result.total || page >= MAX_PAGES) break
                    page++
                }
                notices
            }
        }
    }

    fun remove(notice: Notice) {
        if (notice.id in _removing.value) return
        _removing.update { it + notice.id }
        viewModelScope.launch {
            val failure = attempt(ProfileMessage.Action.REMOVAL) { profile.removeFromSelection(selection, notice.id) }
            if (failure == null) {
                _notices.update { state -> LoadState.Loaded(state.valueOrNull.orEmpty().filter { it.id != notice.id }) }
            }
            _message.value = failure ?: ProfileMessage.RemovedFrom(selection.name)
            _removing.update { it - notice.id }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    @AssistedFactory
    interface Factory {
        fun create(selection: Selection): SelectionDetailViewModel
    }

    private companion object {
        const val MAX_PAGES = 10
    }
}

// My reviews

@HiltViewModel
class ReviewsViewModel @Inject constructor(private val profile: ProfileRepository) : ViewModel() {
    private val _toRate = MutableStateFlow<LoadState<List<RecordToRate>>>(LoadState.Loading)
    val toRate: StateFlow<LoadState<List<RecordToRate>>> = _toRate.asStateFlow()

    private val _published = MutableStateFlow<LoadState<List<Review>>>(LoadState.Loading)
    val published: StateFlow<LoadState<List<Review>>> = _published.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _toRate.loadInto { profile.recordsToRate() }
            _published.loadInto { profile.reviews() }
        }
    }

    /** Sends the review; [onSent] closes the sheet. */
    fun submit(record: RecordToRate, rating: Int, title: String, text: String, onSent: () -> Unit) {
        if (rating !in 1..MAX_RATING || _isSubmitting.value) return
        _isSubmitting.value = true
        viewModelScope.launch {
            val failure = attempt(ProfileMessage.Action.SENDING) { profile.submitReview(record, rating, title.trim(), text.trim()) }
            if (failure == null) {
                _toRate.update { state -> LoadState.Loaded(state.valueOrNull.orEmpty().filter { it.recordId != record.recordId }) }
                onSent()
                _published.loadInto { profile.reviews() }
            }
            _message.value = failure ?: ProfileMessage.ReviewSent
            _isSubmitting.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }

    companion object {
        const val MAX_RATING = 5
    }
}

// My purchase suggestions

@HiltViewModel
class SuggestionsViewModel @Inject constructor(private val profile: ProfileRepository) : ViewModel() {
    private val _draft = MutableStateFlow(SuggestionDraft())
    val draft: StateFlow<SuggestionDraft> = _draft.asStateFlow()

    private val _sent = MutableStateFlow<LoadState<List<Suggestion>>>(LoadState.Loading)
    val sent: StateFlow<LoadState<List<Suggestion>>> = _sent.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _sent.loadInto { profile.suggestions() } }
    }

    fun updateDraft(transform: (SuggestionDraft) -> SuggestionDraft) = _draft.update(transform)

    fun send() {
        val draft = _draft.value
        if (!draft.isValid || _isSending.value) return
        _isSending.value = true
        viewModelScope.launch {
            val failure = attempt(ProfileMessage.Action.SENDING) { profile.sendSuggestion(draft) }
            if (failure == null) {
                _draft.value = SuggestionDraft()
                _sent.loadInto { profile.suggestions() }
            }
            _message.value = failure ?: ProfileMessage.SuggestionSent
            _isSending.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }
}

// My information

@HiltViewModel
class InformationViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val credentials: ValueStore<Credentials>,
    sessionStore: SessionStore
) : ViewModel() {
    val session: StateFlow<SessionStore.State> = sessionStore.state

    private val _contact = MutableStateFlow<LoadState<ContactDetails>>(LoadState.Loading)
    val contact: StateFlow<LoadState<ContactDetails>> = _contact.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _contact.loadInto { profile.contactDetails() } }
    }

    /** Saves the contact details; [onSaved] closes the sheet. */
    fun save(details: ContactDetails, onSaved: () -> Unit) {
        val email = details.email.trim()
        if (email.isNotEmpty() && !EMAIL.matches(email)) {
            _message.value = ProfileMessage.InvalidEmail
            return
        }
        if (_isSaving.value) return
        val cleaned = details.copy(nickname = details.nickname.trim(), email = email, phone = details.phone.trim())
        _isSaving.value = true
        viewModelScope.launch {
            val failure = attempt(ProfileMessage.Action.SAVING) { profile.updateContactDetails(cleaned) }
            if (failure == null) {
                _contact.value = LoadState.Loaded(cleaned)
                onSaved()
            }
            _message.value = failure ?: ProfileMessage.ContactSaved
            _isSaving.value = false
        }
    }

    /**
     * Changes the password on the portal, then in the app's vault (the automatic sign-in uses it). [onChanged] gets
     * the new credentials, to offer updating them in the patron's password manager.
     */
    fun changePassword(password: String, confirmation: String, onChanged: (Credentials) -> Unit) {
        passwordProblem(password, confirmation)?.let {
            _message.value = it
            return
        }
        if (_isSaving.value) return
        _isSaving.value = true
        viewModelScope.launch {
            val failure = attempt(ProfileMessage.Action.PASSWORD_CHANGE) { profile.changePassword(password) }
            if (failure == null) {
                val updated = credentials.load()?.copy(password = password)
                updated?.let { credentials.save(it) }
                updated?.let(onChanged)
            }
            _message.value = failure ?: ProfileMessage.PasswordChanged
            _isSaving.value = false
        }
    }

    fun messageShown() {
        _message.value = null
    }

    companion object {
        private val EMAIL = Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")

        /** Local check before sending: `null` when the new password can go. */
        fun passwordProblem(password: String, confirmation: String): ProfileMessage? = when {
            password.isEmpty() -> ProfileMessage.PasswordEmpty
            password != confirmation -> ProfileMessage.PasswordMismatch
            else -> null
        }
    }
}

// My settings

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val profile: ProfileRepository,
    private val settings: SettingsStore,
    private val sessionStore: SessionStore,
    private val accountSync: AccountSync
) : ViewModel() {
    val reminderSettings: StateFlow<ReminderSettings> =
        settings.reminderSettings.stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettings())
    val deviceSettings: StateFlow<DeviceSettings> = settings.deviceSettings.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        DeviceSettings()
    )

    private val _bookmarks = MutableStateFlow<LoadState<Set<Library>>>(LoadState.Loading)
    val bookmarks: StateFlow<LoadState<Set<Library>>> = _bookmarks.asStateFlow()

    private val _newsletter = MutableStateFlow(sessionStore.patron?.newsletterSubscribed == true)
    val newsletter: StateFlow<Boolean> = _newsletter.asStateFlow()

    /** Switches waiting for the portal: a library's code, or [NEWSLETTER]. */
    private val _updating = MutableStateFlow<Set<String>>(emptySet())
    val updating: StateFlow<Set<String>> = _updating.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    val patronEmail: String? get() = sessionStore.patron?.email?.takeIf { it.isNotBlank() }
    val homeLibrary: Library? get() = sessionStore.patron?.homeLibrary

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _bookmarks.loadInto { profile.bookmarkedLibraries() } }
    }

    fun setLoanReminders(enabled: Boolean) = updateReminders { it.copy(loanReminders = enabled) }

    fun setDaysBefore(days: Int) = updateReminders { it.copy(daysBefore = days.coerceIn(1, MAX_DAYS_BEFORE)) }

    fun setHoldAlerts(enabled: Boolean) = updateReminders { it.copy(holdAlerts = enabled) }

    fun setAgendaDigest(enabled: Boolean) = updateReminders { it.copy(agendaDigest = enabled) }

    fun setBiometricLock(enabled: Boolean) {
        viewModelScope.launch { settings.setBiometricLock(enabled) }
    }

    /** The reminders are scheduled again with the new settings. */
    private fun updateReminders(transform: (ReminderSettings) -> ReminderSettings) {
        viewModelScope.launch {
            settings.updateReminderSettings(transform)
            runCatching { accountSync.run() }
        }
    }

    fun setBookmarked(library: Library, isOn: Boolean) {
        val current = _bookmarks.value.valueOrNull ?: return
        if ((library in current) == isOn || library.code in _updating.value) return
        runUpdate(library.code) {
            profile.setLibraryBookmarked(library, isOn)
            _bookmarks.update { state ->
                val set = state.valueOrNull.orEmpty()
                LoadState.Loaded(if (isOn) set + library else set - library)
            }
        }
    }

    fun setNewsletter(isOn: Boolean) {
        if (_newsletter.value == isOn || NEWSLETTER in _updating.value) return
        runUpdate(NEWSLETTER) {
            profile.setNewsletterSubscribed(isOn)
            _newsletter.value = isOn
        }
    }

    private fun runUpdate(key: String, block: suspend () -> Unit) {
        _updating.update { it + key }
        viewModelScope.launch {
            _message.value = attempt(ProfileMessage.Action.MODIFICATION, block)
            _updating.update { it - key }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    companion object {
        const val NEWSLETTER = "newsletter"
        const val MAX_DAYS_BEFORE = 7
    }
}

// Notification center

data class InboxSection(val kind: Kind, val items: List<InboxItem>) {
    enum class Kind { TODAY, THIS_WEEK, EARLIER }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val inboxStore: ValueStore<Inbox>,
    private val account: AccountRepository,
    private val accountSync: AccountSync,
    private val clock: Clock
) : ViewModel() {
    private val _inbox = MutableStateFlow(Inbox())
    val inbox: StateFlow<Inbox> = _inbox.asStateFlow()

    private val _renewing = MutableStateFlow<Set<String>>(emptySet())
    val renewing: StateFlow<Set<String>> = _renewing.asStateFlow()

    /** Loans renewed from this screen: their button goes away. */
    private val _renewed = MutableStateFlow<Set<String>>(emptySet())
    val renewed: StateFlow<Set<String>> = _renewed.asStateFlow()

    private val _message = MutableStateFlow<ProfileMessage?>(null)
    val message: StateFlow<ProfileMessage?> = _message.asStateFlow()

    val now: Instant get() = clock.instant()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch { _inbox.value = inboxStore.load() ?: Inbox() }
    }

    /** Today, the last seven days, then earlier; the newest first. */
    fun sections(inbox: Inbox): List<InboxSection> {
        val today = Paris.startOf(Paris.dayOf(clock.instant()))
        val weekStart = Paris.startOf(Paris.dayOf(clock.instant()).minusDays(WEEK_DAYS))
        val grouped = inbox.items.groupBy { item ->
            when {
                item.date >= today -> InboxSection.Kind.TODAY
                item.date >= weekStart -> InboxSection.Kind.THIS_WEEK
                else -> InboxSection.Kind.EARLIER
            }
        }
        return InboxSection.Kind.entries.mapNotNull { kind ->
            grouped[kind]?.let { items -> InboxSection(kind, items.sortedByDescending { it.date }) }
        }
    }

    fun markRead(item: InboxItem) {
        if (item.isRead) return
        save(_inbox.value.markRead(item.id))
    }

    fun markAllRead() {
        if (_inbox.value.unreadCount == 0) return
        save(_inbox.value.markAllRead())
    }

    private fun save(inbox: Inbox) {
        _inbox.value = inbox
        viewModelScope.launch { inboxStore.save(inbox) }
    }

    fun canRenew(item: InboxItem): Boolean = item.loanId != null && item.loanId !in _renewed.value

    fun renew(item: InboxItem) {
        val loanId = item.loanId ?: return
        if (loanId in _renewing.value) return
        _renewing.update { it + loanId }
        viewModelScope.launch {
            try {
                when (val result = account.renew(listOf(loanId)).firstOrNull()) {
                    is RenewalResult.Renewed -> {
                        _renewed.update { it + loanId }
                        markRead(item)
                        _message.value = ProfileMessage.RenewedUntil(result.newDueDate)
                        // A new due date: reminders and history brought up to date.
                        runCatching { accountSync.run() }
                        _inbox.value = inboxStore.load() ?: _inbox.value
                    }

                    is RenewalResult.Refused, null -> _message.value = ProfileMessage.RenewRefused
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _message.value = ProfileMessage.failure(error, ProfileMessage.Action.RENEWAL)
            } finally {
                _renewing.update { it - loanId }
            }
        }
    }

    fun messageShown() {
        _message.value = null
    }

    private companion object {
        const val WEEK_DAYS = 6L
    }
}
