// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.CatalogRepository
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.model.DefaultSelection
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.model.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/** Copies of one library, available ones first among the libraries. */
data class LibraryCopies(val name: String, val copies: List<LiveCopy>)

/** A record's page: its real-time copies, read when the page opens. */
@HiltViewModel(assistedFactory = DocumentDetailViewModel.Factory::class)
class DocumentDetailViewModel @AssistedInject constructor(
    @Assisted val document: Notice,
    private val catalog: CatalogRepository,
    private val profile: ProfileRepository,
    sessionStore: SessionStore
) : ViewModel() {
    /** The outcome of a selection change, for the snackbar. */
    sealed interface SelectionMessage {
        data class Added(val selection: DefaultSelection) : SelectionMessage

        data class Removed(val selection: DefaultSelection) : SelectionMessage

        data object Failed : SelectionMessage
    }

    /** Signed in or not, for the hold button. */
    val session: StateFlow<SessionStore.State> = sessionStore.state

    private val _copies = MutableStateFlow<LoadState<List<LibraryCopies>>>(LoadState.Loading)
    val copies: StateFlow<LoadState<List<LibraryCopies>>> = _copies.asStateFlow()

    private val _selections = MutableStateFlow<Set<DefaultSelection>?>(null)

    /** Default selections holding the record; `null` until read (signed out, or the page did not load). */
    val selections: StateFlow<Set<DefaultSelection>?> = _selections.asStateFlow()

    private val _selectionMessage = MutableStateFlow<SelectionMessage?>(null)
    val selectionMessage: StateFlow<SelectionMessage?> = _selectionMessage.asStateFlow()

    init {
        load()
        // The site answers for the signed-in patron only: read once signed in, forget on sign-out.
        viewModelScope.launch {
            sessionStore.state.distinctUntilChangedBy { it is SessionStore.State.LoggedIn }.collect { state ->
                if (state is SessionStore.State.LoggedIn) loadSelections() else _selections.value = null
            }
        }
    }

    private suspend fun loadSelections() {
        _selections.value = try {
            profile.defaultSelections(document.id)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    /** Puts the record in the selection or takes it out: the heart changes at once, and comes back if the site refuses. */
    fun toggle(selection: DefaultSelection) {
        val before = _selections.value ?: return
        val isIn = selection !in before
        _selections.value = if (isIn) before + selection else before - selection
        viewModelScope.launch {
            _selectionMessage.value = try {
                profile.setInDefaultSelection(selection, document.id, isIn)
                if (isIn) SelectionMessage.Added(selection) else SelectionMessage.Removed(selection)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _selections.value = before
                SelectionMessage.Failed
            }
        }
    }

    fun selectionMessageShown() {
        _selectionMessage.value = null
    }

    fun load() {
        if (document.kind.isDigital) {
            _copies.value = LoadState.Loaded(emptyList())
            return
        }
        viewModelScope.launch {
            _copies.value = try {
                LoadState.Loaded(group(catalog.copies(document.id)))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LoadState.Failed(LoadError.from(error))
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(document: Notice): DocumentDetailViewModel
    }

    internal companion object {
        /** By library, those with an available copy first, then by name. */
        fun group(copies: List<LiveCopy>): List<LibraryCopies> = copies
            .groupBy { it.libraryName }
            .map { (name, copies) -> LibraryCopies(name, copies) }
            .sortedWith(compareBy<LibraryCopies> { library -> library.copies.none { it.status.isAvailable } }.thenBy { it.name })
    }
}
