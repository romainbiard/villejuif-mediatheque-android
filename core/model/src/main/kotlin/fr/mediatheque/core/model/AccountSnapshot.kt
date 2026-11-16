// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * Last known account state, read by the widget and the background sync.
 * The account's only persistence: a JSON file, no database.
 */
@Serializable
data class AccountSnapshot(
    val loans: List<Loan>,
    val holds: List<Hold>,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
) {
    /** Upcoming returns, most urgent first. */
    val upcomingLoans: List<Loan> get() = loans.sortedBy { it.dueDate }

    /** Holds that became available since [previous] (for the "waiting for you" notification). */
    fun newlyReadyHolds(previous: AccountSnapshot?): List<Hold> {
        val wasReady = previous?.holds.orEmpty().filter { it.isReadyForPickup }.map { it.id }.toSet()
        return holds.filter { it.isReadyForPickup && it.id !in wasReady }
    }
}
