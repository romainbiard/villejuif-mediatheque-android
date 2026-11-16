// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.CachePolicy
import fr.mediatheque.core.data.LibraryRepository
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.model.LibraryInfo
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InfoUiState(
    val libraries: LoadState<List<LibraryInfo>> = LoadState.Loading,
    /** The portal did not answer: the hours on screen are the last ones saved. */
    val staleError: LoadError? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class InfoViewModel @Inject constructor(private val repository: LibraryRepository) : ViewModel() {
    private val _state = MutableStateFlow(InfoUiState())
    val state: StateFlow<InfoUiState> = _state.asStateFlow()

    init {
        load(CachePolicy.USE_CACHE)
    }

    /** Pull to refresh: past the 12-hour cache, without losing the list if it fails. */
    fun refresh() = load(CachePolicy.RELOAD)

    fun retry() = load(CachePolicy.USE_CACHE)

    private fun load(policy: CachePolicy) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = policy == CachePolicy.RELOAD) }
            try {
                val cached = repository.libraries(policy)
                // The three libraries in network order.
                val libraries = cached.value.sortedBy { it.library?.code ?: "9" }
                _state.update { InfoUiState(LoadState.Loaded(libraries), cached.refreshError) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update { current ->
                    if (current.libraries is LoadState.Loaded) {
                        current.copy(staleError = LoadError.from(error), isRefreshing = false)
                    } else {
                        InfoUiState(LoadState.Failed(LoadError.from(error)))
                    }
                }
            }
        }
    }
}
