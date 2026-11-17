// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.search

import fr.mediatheque.core.data.demo.DemoCatalogRepository
import fr.mediatheque.core.data.demo.DemoProfileRepository
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.screenshottesting.ScreenshotTest
import fr.mediatheque.core.screenshottesting.ScreenshotVariant
import fr.mediatheque.core.testing.TestClock
import fr.mediatheque.core.testing.demoSessionStore
import fr.mediatheque.core.testing.settingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SearchScreenshotTest : ScreenshotTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun closeStores() = storeScope.cancel()

    @Test
    fun landing() {
        val viewModel = SearchViewModel(DemoCatalogRepository(), settingsStore(storeScope, folder.root))
        capture("search_landing") { SearchScreen(viewModel, onOpen = {}) }
    }

    @Test
    fun results() {
        val viewModel = SearchViewModel(DemoCatalogRepository(), settingsStore(storeScope, folder.root)).apply {
            onTextChange("harry")
            submit()
        }
        capture("search_results") { SearchScreen(viewModel, onOpen = {}) }
    }

    @Test
    fun record() {
        val sessions = demoSessionStore(settingsStore(storeScope, folder.root), TestClock(Paris.instant(2026, 11, 17, 12)))
        runBlocking { sessions.restore() }
        val viewModel = DocumentDetailViewModel(SampleData.documents.first(), DemoCatalogRepository(), DemoProfileRepository(), sessions)
        capture("search_record", ScreenshotVariant.PHONE + ScreenshotVariant.TABLET) { DocumentDetailScreen(viewModel, onBack = {}) }
    }
}
