// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.navigation

import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.feature.account.AccountRoute
import fr.mediatheque.feature.account.CardRoute
import fr.mediatheque.feature.account.LoansRoute
import fr.mediatheque.feature.account.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionGuardTest {
    @Test
    fun lockAndSignOutHideTheAccountScreens() {
        assertTrue(SessionGuard.hidesAccountScreens(SessionStore.State.Locked))
        assertTrue(SessionGuard.hidesAccountScreens(SessionStore.State.LoggedOut))
        assertFalse(SessionGuard.hidesAccountScreens(SessionStore.State.Restoring))
        assertFalse(SessionGuard.hidesAccountScreens(SessionStore.State.LoggedIn(SampleData.patron)))
    }

    @Test
    fun theAccountStackReturnsToItsRoot() {
        val stack = mutableListOf<NavKey>(AccountRoute, LoansRoute, SettingsRoute, CardRoute)
        assertTrue(SessionGuard.closeAccountScreens(stack))
        assertEquals(listOf<NavKey>(AccountRoute), stack)
        assertFalse(SessionGuard.closeAccountScreens(stack))
    }
}
