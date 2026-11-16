// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Dernier état connu du compte, lu par le widget et la synchronisation en arrière-plan.
 * Seule persistance du compte : un fichier JSON, sans base de données.
 */
@Serializable
data class AccountSnapshot(
    val loans: List<Loan>,
    val holds: List<Hold>,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
) {
    /** Prochains retours, le plus urgent d'abord. */
    val upcomingLoans: List<Loan> get() = loans.sortedBy { it.dueDate }

    /** Réservations devenues disponibles depuis [previous] (pour la notification « vous attend »). */
    fun newlyReadyHolds(previous: AccountSnapshot?): List<Hold> {
        val wasReady = previous?.holds.orEmpty().filter { it.isReadyForPickup }.map { it.id }.toSet()
        return holds.filter { it.isReadyForPickup && it.id !in wasReady }
    }
}
