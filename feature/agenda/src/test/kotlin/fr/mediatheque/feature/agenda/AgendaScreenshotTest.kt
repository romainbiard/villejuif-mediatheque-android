// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.agenda

import fr.mediatheque.core.data.LoadState
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.screenshottesting.ScreenshotTest
import fr.mediatheque.core.screenshottesting.ScreenshotVariant
import org.junit.Test

class AgendaScreenshotTest : ScreenshotTest() {
    private val actions = AgendaActions(
        onRefresh = {},
        onRetry = {},
        onSelectDay = {},
        onSelectPeriod = {},
        onApplyFilters = {},
        onClearFilters = {},
        onOpenEvent = {}
    )

    @Test
    fun agenda() = capture("agenda", ScreenshotVariant.PHONE + ScreenshotVariant.TABLET) {
        AgendaContent(
            AgendaUiState(events = LoadState.Loaded(SampleData.events), today = Paris.dayOf(Paris.instant(2026, 10, 28))),
            actions
        )
    }
}
