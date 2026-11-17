// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.CatalogRepository
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.TileTint
import fr.mediatheque.core.model.Availability
import fr.mediatheque.core.model.DocumentKind
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.model.SearchQuery
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kinds of document offered as search filters. */
enum class SearchScope(@param:StringRes val title: Int, val kinds: List<DocumentKind>) {
    ALL(R.string.search_scope_all, emptyList()),
    BOOKS(R.string.search_scope_books, listOf(DocumentKind.BOOK)),
    DVD(R.string.search_scope_dvd, listOf(DocumentKind.DVD)),
    CD(R.string.search_scope_cd, listOf(DocumentKind.CD)),
    AUDIOBOOKS(R.string.search_scope_audiobooks, listOf(DocumentKind.AUDIOBOOK)),
    EBOOKS(R.string.search_scope_ebooks, listOf(DocumentKind("Numilog", "Livres numériques"))),
    ONLINE_FILMS(R.string.search_scope_online_films, listOf(DocumentKind("ArteVod", "Cinéma en ligne")))
}

/** A tile that browses part of the catalog, newest first, without typing. */
enum class ExploreShortcut(@param:StringRes val title: Int, @param:DrawableRes val icon: Int, val tint: TileTint, val scope: SearchScope) {
    NEW(R.string.search_explore_new, DesignR.drawable.ic_auto_awesome, TileTint.MAGENTA, SearchScope.BOOKS),
    FILMS(R.string.search_explore_films, DesignR.drawable.ic_movie, TileTint.VIOLET, SearchScope.DVD),
    MUSIC(R.string.search_explore_music, DesignR.drawable.ic_music_note, TileTint.SUCCESS, SearchScope.CD),
    EBOOKS(R.string.search_scope_ebooks, DesignR.drawable.ic_tablet_android, TileTint.DEEP_VIOLET, SearchScope.EBOOKS),
    AUDIOBOOKS(R.string.search_scope_audiobooks, DesignR.drawable.ic_headphones, TileTint.WARNING, SearchScope.AUDIOBOOKS),
    ONLINE_FILMS(R.string.search_scope_online_films, DesignR.drawable.ic_live_tv, TileTint.INK, SearchScope.ONLINE_FILMS)
}

data class SearchUiState(
    val text: String = "",
    val scope: SearchScope = SearchScope.ALL,
    val library: Library? = null,
    val sort: SearchQuery.Sort = SearchQuery.Sort.RELEVANCE,
    /** Browsing from a tile, with no text typed. */
    val browsing: ExploreShortcut? = null,
    /** `null` on the landing (recent searches and tiles). */
    val results: LoadState<List<Notice>>? = null,
    val total: Int = 0,
    val isLoadingMore: Boolean = false,
    val availability: Map<Int, Availability> = emptyMap(),
    val recentSearches: List<String> = emptyList()
) {
    val showsLanding: Boolean get() = text.isBlank() && browsing == null
    val hasActiveFilters: Boolean get() = scope != SearchScope.ALL || library != null
}

@HiltViewModel
class SearchViewModel @Inject constructor(private val catalog: CatalogRepository, private val settings: SettingsStore) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var page = 1
    private var searchJob: Job? = null
    private val availabilityRequests = mutableSetOf<Int>()
    private var recentSearchesChanged = false

    init {
        // Read once: from then on the screen is the reference, and each change is saved at once.
        viewModelScope.launch {
            val saved = settings.recentSearches.first()
            // A search made while the list was being read wins over it.
            if (!recentSearchesChanged) _state.update { it.copy(recentSearches = saved) }
        }
    }

    // Intents

    /** Typing: wait for a pause before searching. */
    fun onTextChange(text: String) {
        _state.update { it.copy(text = text, browsing = null, sort = SearchQuery.Sort.RELEVANCE) }
        schedule(debounce = true)
    }

    /** The keyboard's search key: search now, and remember it. */
    fun submit() {
        remember(_state.value.text)
        schedule(debounce = false)
    }

    fun selectScope(scope: SearchScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope) }
        schedule(debounce = false)
    }

    fun selectLibrary(library: Library?) {
        if (library == _state.value.library) return
        _state.update { it.copy(library = library) }
        schedule(debounce = false)
    }

    fun open(shortcut: ExploreShortcut) {
        _state.update { it.copy(text = "", browsing = shortcut, scope = shortcut.scope, sort = SearchQuery.Sort.NEWEST) }
        schedule(debounce = false)
    }

    fun searchRecent(text: String) {
        _state.update { it.copy(text = text, browsing = null, sort = SearchQuery.Sort.RELEVANCE) }
        submit()
    }

    fun clearRecentSearches() = saveRecentSearches(emptyList())

    fun clearFilters() {
        _state.update { it.copy(scope = SearchScope.ALL, library = null) }
        schedule(debounce = false)
    }

    /** Back from a tile's browsing, or clearing the field: the landing again. */
    fun reset() {
        searchJob?.cancel()
        _state.update { it.copy(text = "", browsing = null, sort = SearchQuery.Sort.RELEVANCE, results = null, total = 0) }
    }

    fun retry() = schedule(debounce = false)

    /** A row came on screen: its real-time availability, and the next page near the end of the list. */
    fun onRowShown(notice: Notice) {
        loadAvailability(notice)
        val current = _state.value
        val documents = current.results?.valueOrNull ?: return
        val nearEnd = documents.takeLast(3).any { it.id == notice.id }
        if (nearEnd && !current.isLoadingMore && documents.size < current.total) loadNextPage()
    }

    // Loading

    private fun query(state: SearchUiState) = SearchQuery(
        text = state.text.trim(),
        kinds = state.scope.kinds,
        libraries = listOfNotNull(state.library),
        sort = state.sort,
        page = page
    )

    private fun schedule(debounce: Boolean) {
        searchJob?.cancel()
        if (_state.value.showsLanding) {
            _state.update { it.copy(results = null, total = 0) }
            return
        }
        searchJob = viewModelScope.launch {
            if (debounce) delay(DEBOUNCE)
            page = 1
            availabilityRequests.clear()
            _state.update { it.copy(results = LoadState.Loading, availability = emptyMap()) }
            try {
                val result = catalog.search(query(_state.value))
                _state.update { it.copy(results = LoadState.Loaded(result.documents), total = result.total) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _state.update { it.copy(results = LoadState.Failed(LoadError.from(error))) }
            }
        }
    }

    private fun loadNextPage() {
        _state.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            page += 1
            try {
                val result = catalog.search(query(_state.value))
                _state.update { state ->
                    val documents = state.results?.valueOrNull.orEmpty()
                    val known = documents.map { it.id }.toSet()
                    state.copy(results = LoadState.Loaded(documents + result.documents.filter { it.id !in known }), isLoadingMore = false)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                page -= 1
                _state.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    private fun loadAvailability(notice: Notice) {
        // Online resources have no copy on a shelf.
        if (notice.kind.isDigital || !availabilityRequests.add(notice.id)) return
        viewModelScope.launch {
            try {
                val availability = catalog.availability(notice.id)
                _state.update { it.copy(availability = it.availability + (notice.id to availability)) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // The row simply shows no badge.
            }
        }
    }

    private fun remember(search: String) {
        val trimmed = search.trim()
        if (trimmed.isEmpty()) return
        saveRecentSearches(
            (
                listOf(trimmed) + _state.value.recentSearches.filterNot {
                    it.equals(trimmed, ignoreCase = true)
                }
                ).take(MAX_RECENT)
        )
    }

    private fun saveRecentSearches(searches: List<String>) {
        recentSearchesChanged = true
        _state.update { it.copy(recentSearches = searches) }
        viewModelScope.launch { settings.setRecentSearches(searches) }
    }

    private companion object {
        val DEBOUNCE = 350.milliseconds
        const val MAX_RECENT = 8
    }
}
