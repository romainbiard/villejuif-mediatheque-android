// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.info

import fr.mediatheque.core.data.CachePolicy
import fr.mediatheque.core.data.Cached
import fr.mediatheque.core.data.LibraryRepository
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.LibraryInfo
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class InfoViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun librariesComeInNetworkOrderAndTheRefreshErrorIsKept() {
        val repository = object : LibraryRepository {
            override suspend fun libraries(policy: CachePolicy): Cached<List<LibraryInfo>> =
                Cached(SampleData.libraries.reversed(), refreshError = LoadError.SiteUnavailable.takeIf { policy == CachePolicy.RELOAD })
        }
        val viewModel = InfoViewModel(repository)
        val libraries = (viewModel.state.value.libraries as LoadState.Loaded).value
        assertEquals(listOf(Library.ELSA_TRIOLET, Library.PULP_SUD, Library.PULP_NORD), libraries.map { it.library })

        viewModel.refresh()
        assertEquals(LoadError.SiteUnavailable, viewModel.state.value.staleError)
    }
}
