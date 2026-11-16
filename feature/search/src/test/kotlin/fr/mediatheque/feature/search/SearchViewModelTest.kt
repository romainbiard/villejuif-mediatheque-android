// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.CatalogRepository
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.model.Availability
import fr.mediatheque.core.model.CopyStatus
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.model.SearchPage
import fr.mediatheque.core.model.SearchQuery
import fr.mediatheque.core.testing.MainDispatcherRule
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeCatalog(var pages: (SearchQuery) -> SearchPage = { SearchPage(SampleData.documents.size, SampleData.documents) }) :
        CatalogRepository {
        val queries = mutableListOf<SearchQuery>()

        override suspend fun search(query: SearchQuery): SearchPage {
            queries += query
            return pages(query)
        }

        override suspend fun availability(recordId: Int) = Availability("Disponible", isAvailable = true)

        override suspend fun copies(recordId: Int): List<LiveCopy> = emptyList()
    }

    private fun TestScope.settings() = SettingsStore(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
    )

    @Test
    fun typingIsDebounced() = runTest(main.dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = SearchViewModel(catalog, settings())
        viewModel.onTextChange("har")
        viewModel.onTextChange("harry")
        advanceTimeBy(200)
        assertTrue(catalog.queries.isEmpty())
        advanceUntilIdle()
        assertEquals(listOf("harry"), catalog.queries.map { it.text })
        assertEquals(LoadState.Loaded(SampleData.documents), viewModel.state.value.results)
    }

    @Test
    fun aScopeSearchesAtOnceWithItsFacet() = runTest(main.dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = SearchViewModel(catalog, settings())
        viewModel.onTextChange("dune")
        advanceUntilIdle()
        viewModel.selectScope(SearchScope.BOOKS)
        viewModel.selectLibrary(Library.PULP_NORD)
        advanceUntilIdle()
        assertEquals(listOf("T1", "Y3"), catalog.queries.last().facets)
        assertTrue(viewModel.state.value.hasActiveFilters)
    }

    @Test
    fun submittingRemembersTheSearch() = runTest(main.dispatcher) {
        val viewModel = SearchViewModel(FakeCatalog(), settings())
        listOf("dune", "astérix", "DUNE").forEach {
            viewModel.onTextChange(it)
            viewModel.submit()
            advanceUntilIdle()
        }
        assertEquals(listOf("DUNE", "astérix"), viewModel.state.value.recentSearches)
        viewModel.clearRecentSearches()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.recentSearches.isEmpty())
    }

    @Test
    fun anExploreTileBrowsesNewestWithoutText() = runTest(main.dispatcher) {
        val catalog = FakeCatalog()
        val viewModel = SearchViewModel(catalog, settings())
        viewModel.open(ExploreShortcut.FILMS)
        advanceUntilIdle()
        val query = catalog.queries.single()
        assertEquals("", query.text)
        assertEquals(SearchQuery.Sort.NEWEST, query.sort)
        assertEquals(listOf("T4"), query.facets)
        viewModel.reset()
        assertNull(viewModel.state.value.results)
    }

    @Test
    fun theNextPageIsAppendedWithoutDuplicates() = runTest(main.dispatcher) {
        val first = SampleData.documents.take(2)
        val second = SampleData.documents.drop(1)
        val catalog = FakeCatalog { query -> SearchPage(3, if (query.page == 1) first else second) }
        val viewModel = SearchViewModel(catalog, settings())
        viewModel.onTextChange("harry")
        advanceUntilIdle()
        viewModel.onRowShown(first.last())
        advanceUntilIdle()
        assertEquals(SampleData.documents.map { it.id }, viewModel.state.value.results?.valueOrNull?.map { it.id })
        assertEquals(2, catalog.queries.last().page)
    }

    @Test
    fun aFailureIsReported() = runTest(main.dispatcher) {
        val viewModel = SearchViewModel(FakeCatalog { throw UnknownHostException() }, settings())
        viewModel.onTextChange("dune")
        advanceUntilIdle()
        assertEquals(LoadState.Failed(LoadError.Offline), viewModel.state.value.results)
    }

    @Test
    fun copiesAreGroupedAvailableFirst() {
        fun copy(library: String, status: CopyStatus) = LiveCopy(null, library, status, "", null, null, 0)
        val groups = DocumentDetailViewModel.group(
            listOf(
                copy("PULP Sud", CopyStatus.OnLoan(null)),
                copy("PULP Nord", CopyStatus.Available),
                copy("Elsa", CopyStatus.OnLoan(null))
            )
        )
        assertEquals(listOf("PULP Nord", "Elsa", "PULP Sud"), groups.map { it.name })
    }
}
