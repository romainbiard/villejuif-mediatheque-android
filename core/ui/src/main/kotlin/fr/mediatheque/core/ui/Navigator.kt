// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.navigation3.runtime.NavKey
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay

/** How a screen opens another one, in the current destination's back stack. */
interface Navigator {
    fun navigate(key: NavKey)

    fun back()
}

/** The current time, updated every minute: "Ouvert" turns into "Fermé" while the screen is open. */
@Composable
fun rememberNow(): Instant {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1.minutes)
            value = Instant.now()
        }
    }
    return now
}
