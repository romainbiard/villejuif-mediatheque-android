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
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.model.Notice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Copies of one library, available ones first among the libraries. */
data class LibraryCopies(val name: String, val copies: List<LiveCopy>)

/** A record's page: its real-time copies, read when the page opens. */
@HiltViewModel(assistedFactory = DocumentDetailViewModel.Factory::class)
class DocumentDetailViewModel @AssistedInject constructor(@Assisted val document: Notice, private val catalog: CatalogRepository) :
    ViewModel() {
    private val _copies = MutableStateFlow<LoadState<List<LibraryCopies>>>(LoadState.Loading)
    val copies: StateFlow<LoadState<List<LibraryCopies>>> = _copies.asStateFlow()

    init {
        load()
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
