// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import fr.mediatheque.core.data.demo.DemoDigitalRepository
import fr.mediatheque.core.model.Paris
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

class DigitalScreenshotTest : ScreenshotTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @After
    fun closeStores() = storeScope.cancel()

    /** No partner app installed. */
    private object NoApps : InstalledApps {
        override fun isInstalled(packageName: String) = false

        override fun launch(packageName: String) = false

        override fun openStore(packageName: String) = false
    }

    @Test
    fun services() {
        val sessions = demoSessionStore(settingsStore(storeScope, folder.root), TestClock(Paris.instant(2026, 11, 17, 12)))
        runBlocking { sessions.restore() }
        val viewModel = DigitalViewModel(DemoDigitalRepository(), NoApps, sessions)
        capture("digital", ScreenshotVariant.PHONE + ScreenshotVariant.TABLET) { DigitalScreen(viewModel, onOpenAccount = {}) }
    }
}
