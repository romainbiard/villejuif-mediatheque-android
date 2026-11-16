// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.navigation

import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import fr.mediatheque.R
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.feature.account.AccountRoute
import fr.mediatheque.feature.agenda.AgendaRoute
import fr.mediatheque.feature.digital.DigitalRoute
import fr.mediatheque.feature.info.InfoRoute
import fr.mediatheque.feature.search.SearchRoute

/**
 * The five destinations of the navigation bar (rail on wider screens), in their order on screen and in the
 * Ctrl+1…5 shortcuts. Material allows five at most: the app uses them all.
 */
enum class TopLevelDestination(
    val route: NavKey,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
    val shortcutKey: Int
) {
    ACCOUNT(
        AccountRoute,
        R.string.tab_account,
        DesignR.drawable.ic_account_circle,
        DesignR.drawable.ic_account_circle_filled,
        KeyEvent.KEYCODE_1
    ),
    SEARCH(SearchRoute, R.string.tab_search, DesignR.drawable.ic_search, DesignR.drawable.ic_search_filled, KeyEvent.KEYCODE_2),
    AGENDA(
        AgendaRoute,
        R.string.tab_agenda,
        DesignR.drawable.ic_calendar_month,
        DesignR.drawable.ic_calendar_month_filled,
        KeyEvent.KEYCODE_3
    ),
    DIGITAL(
        DigitalRoute,
        R.string.tab_digital,
        DesignR.drawable.ic_play_circle,
        DesignR.drawable.ic_play_circle_filled,
        KeyEvent.KEYCODE_4
    ),
    INFO(InfoRoute, R.string.tab_info, DesignR.drawable.ic_location_on, DesignR.drawable.ic_location_on_filled, KeyEvent.KEYCODE_5)
    ;

    companion object {
        /** Where the app opens, and where Back leads from the root of another destination. */
        val START = ACCOUNT
    }
}
