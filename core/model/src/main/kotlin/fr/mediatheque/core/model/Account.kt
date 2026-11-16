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
    /** Message de la médiathèque, s'il y en a un. */
    val message: String?,
    val hasUnreadMessage: Boolean,
    val nickname: String? = null,
    /** Adresse postale sur une ligne. Elle ne se modifie qu'à l'accueil. */
    val postalAddress: String? = null,
    val newsletterSubscribed: Boolean = false
) {
    @Serializable
    data class Permissions(val holdForbidden: Boolean, val digitalResourcesForbidden: Boolean)

    @Serializable
    data class Counters(
        val loans: Int,
        val lateLoans: Int,
        /** Réservations qui attendent d'être retirées. */
        val waitingToBePulled: Int
    )

    /** « Camille Martin » : le site écrit le nom en capitales (« MARTIN-DUPONT » → « Martin-Dupont »). */
    val displayName: String get() = "$firstName ${capitalizedWords(lastName)}"
}

/** Majuscule en tête de chaque mot, après une espace, un trait d'union ou une apostrophe. */
internal fun capitalizedWords(text: String): String {
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
    /** `{idAbonné}_{idPrêt}`. */
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
    /** Identifiant de l'abonné, préfixe de l'identifiant du prêt (utile pour les routes HTML). */
    val patronId: String get() = id.substringBefore('_')

    sealed interface DueState {
        val days: Int

        data class Late(override val days: Int) : DueState

        data class DueSoon(override val days: Int) : DueState

        data class OnTime(override val days: Int) : DueState
    }

    /** État par rapport à [now], « bientôt » à 7 jours ou moins, en jours calendaires à Paris. */
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
    /** Texte du site, par exemple « Pas disponible avant le 14/11/2026 ». */
    val statusText: String,
    val pickupLibrary: Library?,
    val pickupLibraryName: String,
    val recordId: Int?,
    val coverUrl: String?,
    /** Position dans la file, lue dans le HTML du compte (absente de l'API). */
    val rank: Int? = null
) {
    /** Date lue dans le statut « Pas disponible avant le … ». */
    val expectedDate: Instant? get() = BokehDates.firstDate(statusText)

    /** Vrai quand le document attend l'abonné. */
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
