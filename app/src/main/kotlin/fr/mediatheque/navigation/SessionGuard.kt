// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.navigation

import androidx.navigation3.runtime.NavKey
import fr.mediatheque.core.data.SessionStore

/**
 * What the biometric lock and the sign-out hide: every account screen above the account's root, which itself shows
 * the lock screen or the sign-in form. Loans, holds, the card, selections and the profile would otherwise stay on
 * screen, readable, behind a lock that only covers the root.
 */
internal object SessionGuard {
    /** The states in which no account screen may stay open. */
    fun hidesAccountScreens(state: SessionStore.State): Boolean =
        state == SessionStore.State.Locked || state == SessionStore.State.LoggedOut

    /** Back to the account's root. Returns whether a screen was closed. */
    fun closeAccountScreens(accountStack: MutableList<NavKey>): Boolean = accountStack.retainAll { it == TopLevelDestination.ACCOUNT.route }
}
