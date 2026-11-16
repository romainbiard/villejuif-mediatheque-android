// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.testing

/**
 * Captures of the real portal, anonymized (fictional patron "Camille MARTIN", card 0012345, domain exemple.fr).
 * Every new capture is anonymized before entering the repository (docs/PLAN-PORTAGE.md § 7).
 */
object Fixture {
    fun bytes(name: String): ByteArray = checkNotNull(Fixture::class.java.getResourceAsStream("/fixtures/$name")) {
        "Fixture not found: $name"
    }.use { it.readBytes() }

    fun string(name: String): String = bytes(name).decodeToString()
}
