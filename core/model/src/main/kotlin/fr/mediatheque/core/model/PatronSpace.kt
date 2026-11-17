// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

// Patron space other than loans and holds. Displayed labels live in the UI resources
// (one language per device), not in the model.

/** A patron's wish list ("panier" on the Bokeh side). Serializable: a navigation route carries it. */
@Serializable
data class Selection(
    val id: Int,
    val basketId: Int?,
    val name: String,
    val count: Int,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant?,
    val coverUrls: List<String>
)

/** A borrowed document that the patron can rate. */
data class RecordToRate(val recordId: Int, val recordKey: String, val title: String, val author: String?, val coverUrl: String?)

data class Review(val id: String, val title: String, val text: String, val rating: Int?)

data class Suggestion(val id: String, val title: String, val detail: String?)

/** Purchase suggestion to send (form `suggestion-achat-add`). */
data class SuggestionDraft(
    val kind: SuggestionKind = SuggestionKind.BOOK,
    val title: String = "",
    val author: String = "",
    val isbn: String = "",
    val comment: String = ""
) {
    /** The site requires the title and the author. */
    val isValid: Boolean get() = title.isNotBlank() && author.isNotBlank()
}

/** Types offered by the form: the site's `DocType` codes (the most common ones). */
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

    /** Comics have no type on the site: we send it as a book, specified in the comment. */
    val docTypeCode: String get() = if (this == COMIC) BOOK.code else code
}

/** Contact details editable online (form `modifier`). Name and address are changed at the front desk. */
data class ContactDetails(val nickname: String, val email: String, val phone: String, val channel: SendingChannel)

/** The site's `favoriteSendingChannel`. */
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
 * The three selections the site creates for each patron. A document goes in and out with one tap
 * (the site's toggle links); the patron's other selections are baskets.
 */
enum class DefaultSelection(
    /** Name on the site, also the one the patron sees in "Mes sélections". */
    val siteName: String,
    /** The site's icons for the toggle, which are part of its links. */
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
