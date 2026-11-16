// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

@Serializable
data class Patron(
    val cardNumber: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val phone: String?,
    val homeLibrary: Library?,
    @Serializable(with = InstantSerializer::class)
    val cardExpiry: Instant?,
    val cardExpired: Boolean,
    val cardAboutToExpire: Boolean,
    val cardExpiresInDays: Int,
    val permissions: Permissions,
    val counters: Counters,
    /** Message from the library, if there is one. */
    val message: String?,
    val hasUnreadMessage: Boolean,
    val nickname: String? = null,
    /** Postal address on one line. It can only be changed at the front desk. */
    val postalAddress: String? = null,
    val newsletterSubscribed: Boolean = false
) {
    @Serializable
    data class Permissions(val holdForbidden: Boolean, val digitalResourcesForbidden: Boolean)

    @Serializable
    data class Counters(
        val loans: Int,
        val lateLoans: Int,
        /** Holds waiting to be picked up. */
        val waitingToBePulled: Int
    )

    /** "Camille Martin": the site writes the last name in capitals ("MARTIN-DUPONT" → "Martin-Dupont"). */
    val displayName: String get() = "$firstName ${lastName.capitalizedWords()}"
}

/** Capital letter at the start of each word, after a space, a hyphen, or an apostrophe. */
fun String.capitalizedWords(): String {
    val text = this
    val builder = StringBuilder(text.length)
    var startOfWord = true
    for (character in text.lowercase(Paris.locale)) {
        builder.append(if (startOfWord) character.titlecaseChar() else character)
        startOfWord = !character.isLetterOrDigit()
    }
    return builder.toString()
}

@Serializable
data class Loan(
    /** `{patronId}_{loanId}`. */
    val id: String,
    val title: String,
    val author: String,
    @Serializable(with = InstantSerializer::class)
    val dueDate: Instant,
    val library: Library?,
    val libraryName: String,
    val recordId: Int?,
    val coverUrl: String?
) {
    /** Patron ID, prefix of the loan ID (useful for HTML routes). */
    val patronId: String get() = id.substringBefore('_')

    sealed interface DueState {
        val days: Int

        data class Late(override val days: Int) : DueState

        data class DueSoon(override val days: Int) : DueState

        data class OnTime(override val days: Int) : DueState
    }

    /** Status relative to [now], "soon" at 7 days or fewer, in calendar days in Paris. */
    fun dueState(now: Instant): DueState {
        val days = ChronoUnit.DAYS.between(Paris.dayOf(now), Paris.dayOf(dueDate)).toInt()
        return when {
            days < 0 -> DueState.Late(-days)
            days <= DUE_SOON_DAYS -> DueState.DueSoon(days)
            else -> DueState.OnTime(days)
        }
    }

    private companion object {
        const val DUE_SOON_DAYS = 7
    }
}

@Serializable
data class Hold(
    val id: String,
    val title: String,
    /** Text from the site, for example "Pas disponible avant le 14/11/2026". */
    val statusText: String,
    val pickupLibrary: Library?,
    val pickupLibraryName: String,
    val recordId: Int?,
    val coverUrl: String?,
    /** Position in the queue, parsed from the account HTML (absent from the API). */
    val rank: Int? = null
) {
    /** Date parsed from the status "Pas disponible avant le …". */
    val expectedDate: Instant? get() = BokehDates.firstDate(statusText)

    /** True when the document is waiting for the patron. */
    val isReadyForPickup: Boolean
        get() {
            val status = statusText.folded()
            return ("disponible" in status && "pas disponible" !in status) ||
                "a retirer" in status ||
                "en attente de retrait" in status
        }
}

sealed interface RenewalResult {
    val loanId: String

    data class Renewed(override val loanId: String, val newDueDate: Instant) : RenewalResult

    data class Refused(override val loanId: String) : RenewalResult
}
