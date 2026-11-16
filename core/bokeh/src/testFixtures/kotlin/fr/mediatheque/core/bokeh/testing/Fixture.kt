// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.testing

/**
 * Captures du portail réel, anonymisées (abonnée fictive « Camille MARTIN », carte 0012345, domaine exemple.fr).
 * Toute nouvelle capture est anonymisée avant d'entrer dans le dépôt (docs/PLAN-PORTAGE.md § 7).
 */
object Fixture {
    fun bytes(name: String): ByteArray = checkNotNull(Fixture::class.java.getResourceAsStream("/fixtures/$name")) {
        "Fixture introuvable : $name"
    }.use { it.readBytes() }

    fun string(name: String): String = bytes(name).decodeToString()
}
