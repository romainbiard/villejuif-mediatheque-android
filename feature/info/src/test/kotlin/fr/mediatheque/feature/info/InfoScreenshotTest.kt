// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.info

import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.screenshottesting.ScreenshotTest
import fr.mediatheque.core.screenshottesting.ScreenshotVariant
import org.junit.Test

class InfoScreenshotTest : ScreenshotTest() {
    /** A Wednesday at 11 am: Elsa-Triolet open, the PULPs not yet. */
    private val now = Paris.instant(2026, 11, 18, 11)

    @Test
    fun libraries() = capture("info", ScreenshotVariant.PHONE + ScreenshotVariant.TABLET) {
        InfoContent(InfoUiState(LoadState.Loaded(SampleData.libraries)), onRefresh = {}, onRetry = {}, now = now)
    }
}
