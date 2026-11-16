// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant

// Espace abonné autre que les prêts et réservations. Les libellés affichés vivent dans les ressources
// de l'interface (une langue par appareil), pas dans le modèle.

/** Une liste d'envies de l'abonné (« panier » côté Bokeh). */
data class Selection(
    val id: Int,
    val basketId: Int?,
    val name: String,
    val count: Int,
    val updatedAt: Instant?,
    val coverUrls: List<String>
)

/** Un document emprunté que l'abonné peut noter. */
data class RecordToRate(val recordId: Int, val recordKey: String, val title: String, val author: String?, val coverUrl: String?)

data class Review(val id: String, val title: String, val text: String, val rating: Int?)

data class Suggestion(val id: String, val title: String, val detail: String?)

/** Suggestion d'achat à envoyer (formulaire `suggestion-achat-add`). */
data class SuggestionDraft(
    val kind: SuggestionKind = SuggestionKind.BOOK,
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val comment: String = ""
) {
    /** Le site exige le titre et l'auteur. */
    val isValid: Boolean get() = title.isNotBlank() && author.isNotBlank()
}

/** Types proposés par le formulaire : codes `DocType` du site (les plus courants). */
enum class SuggestionKind(val code: String) {
    BOOK("1"),
    LARGE_PRINT("19"),
    AUDIOBOOK("17"),
    COMIC("BD"),
    CD("3"),
    DVD("4"),
    BOARD_GAME("15"),
    LANGUAGE_METHOD("21"),
    EBOOK("Numilog")
    ;

    /** La BD n'a pas de type sur le site : on l'envoie comme un livre, précisé dans le commentaire. */
    val docTypeCode: String get() = if (this == COMIC) BOOK.code else code
}

/** Coordonnées modifiables en ligne (formulaire `modifier`). Le nom et l'adresse se changent à l'accueil. */
data class ContactDetails(val nickname: String, val email: String, val phone: String, val channel: SendingChannel)

/** `favoriteSendingChannel` du site. */
enum class SendingChannel(val code: String) {
    ANY("0"),
    EMAIL("1"),
    SMS("2"),
    LETTER("3")
    ;

    companion object {
        fun fromCode(code: String?): SendingChannel? = entries.firstOrNull { it.code == code }
    }
}

/**
 * Les trois sélections que le site crée pour chaque abonné. Un document y entre et en sort d'un geste
 * (les liens bascule du site) ; les autres sélections de l'abonné sont des paniers.
 */
enum class DefaultSelection(
    /** Nom sur le site, aussi celui que l'abonné voit dans « Mes sélections ». */
    val siteName: String,
    /** Icônes du site pour la bascule, qui font partie de ses liens. */
    val siteImage: String,
    val siteRevertImage: String
) {
    FAVORITES("Mes préférés", "like", "dislike"),
    READ("Déjà lu", "readed", "unread"),
    TO_READ("À lire", "to_read", "to_not_read")
    ;

    companion object {
        fun fromSiteName(name: String): DefaultSelection? = entries.firstOrNull { it.siteName == name }
    }
}
