// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.AgendaRepository
import fr.mediatheque.core.data.CachePolicy
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.EventDetail
import fr.mediatheque.core.model.Paris
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AgendaUiState(
    val events: LoadState<List<Event>> = LoadState.Loading,
    /** The portal did not answer: the agenda on screen is the last one saved. */
    val staleError: LoadError? = null,
    val isRefreshing: Boolean = false,
    val today: LocalDate,
    val selectedDay: LocalDate = today,
    val filters: AgendaFilters = AgendaFilters()
) {
    val logic: AgendaLogic get() = AgendaLogic(events.valueOrNull.orEmpty(), filters, today)
}

@HiltViewModel
class AgendaViewModel @Inject constructor(private val repository: AgendaRepository, clock: Clock) : ViewModel() {
    private val _state = MutableStateFlow(AgendaUiState(today = Paris.dayOf(clock.instant())))
    val state: StateFlow<AgendaUiState> = _state.asStateFlow()

    init {
        load(CachePolicy.USE_CACHE)
    }

    fun refresh() = load(CachePolicy.RELOAD)

    fun retry() = load(CachePolicy.USE_CACHE)

    fun selectDay(day: LocalDate) = _state.update { it.copy(selectedDay = day) }

    fun selectPeriod(period: AgendaPeriod) = _state.update { it.copy(filters = it.filters.copy(period = period)) }

    fun applyFilters(filters: AgendaFilters) = _state.update { it.copy(filters = filters) }

    fun clearFilters() = _state.update { it.copy(filters = AgendaFilters()) }

    private fun load(policy: CachePolicy) {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = policy == CachePolicy.RELOAD) }
            try {
                val cached = repository.events(policy)
                _state.update { it.copy(events = LoadState.Loaded(cached.value), staleError = cached.refreshError, isRefreshing = false) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update {
                    if (it.events is LoadState.Loaded) {
                        it.copy(staleError = LoadError.from(error), isRefreshing = false)
                    } else {
                        it.copy(events = LoadState.Failed(LoadError.from(error)), isRefreshing = false)
                    }
                }
            }
        }
    }
}

/** An event's page: what the agenda knows, and its iCal for the description and the map. */
@HiltViewModel(assistedFactory = EventDetailViewModel.Factory::class)
class EventDetailViewModel @AssistedInject constructor(@Assisted val event: Event, private val repository: AgendaRepository) :
    ViewModel() {
    private val _detail = MutableStateFlow<LoadState<EventDetail>>(LoadState.Loading)
    val detail: StateFlow<LoadState<EventDetail>> = _detail.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _detail.value = LoadState.Loading
            _detail.value = try {
                LoadState.Loaded(repository.detail(event))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                LoadState.Failed(LoadError.from(error))
            }
        }
    }

    /** The event's page on the portal, to share. */
    val pageUrl: String get() = "https://mediatheque.villejuif.fr/cms/articleview/id/${event.id}"

    @AssistedFactory
    interface Factory {
        fun create(event: Event): EventDetailViewModel
    }
}
