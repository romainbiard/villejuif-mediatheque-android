// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.dto

import fr.mediatheque.core.bokeh.BokehError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** `GET /api/user/account`, accessible avec le seul cookie de session. */
data class AccountResponseDto(val account: AccountDto) {
    companion object {
        fun parse(text: String) = AccountResponseDto(AccountDto.read(parseObject(text).requiredObject("account")))
    }
}

data class AccountDto(
    /** Le numéro de carte : le seul champ dont le compte ne peut pas se passer. */
    val login: String,
    val identity: Identity,
    val contact: Contact,
    val card: Card,
    val libraryDetails: LibraryDetails,
    val permissions: Permissions,
    val counters: Counters,
    val message: Message,
    val preferences: Preferences?,
    /** Cartes liées au compte : leur `id` est l'identifiant d'abonné des routes `/abonne/...`. `null` si absent. */
    val cards: List<LinkedCard>?
) {
    data class Identity(val firstName: String, val lastName: String, val nickname: String?)

    data class Contact(val email: String, val phone: String, val mobile: String, val address: String, val zipCode: String, val city: String)

    data class Card(val expireAt: String, val expired: Boolean, val aboutToExpire: Boolean, val expiresIn: Int)

    data class LibraryDetails(val id: Int, val label: String)

    data class Permissions(val holdForbidden: Boolean, val digitalResourcesForbidden: Boolean)

    data class Counters(val loans: Int, val lateLoans: Int, val waitingToBePulled: Int)

    data class Message(val content: String, val unread: Boolean)

    /** Lettres d'information souscrites : le format d'un élément n'a jamais été vu (liste vide), on ne lit que leur nombre. */
    data class Preferences(val newsletterCount: Int)

    data class LinkedCard(val id: Int)

    companion object {
        internal fun read(json: JsonObject): AccountDto {
            val login = json.lenientString("login")?.takeIf { it.isNotEmpty() }
                ?: throw BokehError.UnexpectedFormat("compte sans numéro de carte")
            return AccountDto(
                login = login,
                identity = json.lenientObject("identity") {
                    Identity(
                        firstName = it.lenientText("first_name"),
                        lastName = it.lenientText("last_name"),
                        nickname = it.lenientString("nickname")
                    )
                } ?: Identity("", "", null),
                contact = json.lenientObject("contact") {
                    Contact(
                        email = it.lenientText("email"),
                        phone = it.lenientText("phone"),
                        mobile = it.lenientText("mobile"),
                        address = it.lenientText("address"),
                        zipCode = it.lenientText("zip_code"),
                        city = it.lenientText("city")
                    )
                } ?: Contact("", "", "", "", "", ""),
                card = json.lenientObject("card") {
                    Card(
                        expireAt = it.lenientText("expire_at"),
                        expired = it.lenientFlag("expired"),
                        aboutToExpire = it.lenientFlag("about_to_expire"),
                        expiresIn = it.lenientCount("expires_in")
                    )
                } ?: Card("", expired = false, aboutToExpire = false, expiresIn = 0),
                libraryDetails = json.lenientObject("library_details") {
                    LibraryDetails(id = it.lenientCount("id"), label = it.lenientText("label"))
                } ?: LibraryDetails(0, ""),
                permissions = json.lenientObject("permissions") {
                    Permissions(
                        holdForbidden = it.lenientFlag("hold_forbidden"),
                        digitalResourcesForbidden = it.lenientFlag("digital_resources_forbidden")
                    )
                } ?: Permissions(holdForbidden = false, digitalResourcesForbidden = false),
                counters = json.lenientObject("counters") {
                    Counters(
                        loans = it.lenientCount("loans"),
                        lateLoans = it.lenientCount("late_loans"),
                        waitingToBePulled = it.lenientCount("waiting_to_be_pulled")
                    )
                } ?: Counters(0, 0, 0),
                message = json.lenientObject("message") {
                    Message(content = it.lenientText("content"), unread = it.lenientFlag("unread"))
                } ?: Message("", unread = false),
                preferences = json.lenientObject("preferences") {
                    Preferences(newsletterCount = (it["newsletters"] as? JsonArray)?.size ?: 0)
                },
                cards = if ("cards" in json) json.lossyList("cards") { LinkedCard(it.requiredInt("id")) } else null
            )
        }
    }
}

/** Référence à la notice d'un prêt ou d'une réservation. */
data class RecordRefDto(val id: Int, val thumbnail: String?) {
    companion object {
        internal fun read(json: JsonObject) = RecordRefDto(json.requiredInt("id"), json.optionalString("thumbnail"))
    }
}

/** `GET /api/user/loans` et `/api/user/loans/history`. */
data class LoansResponseDto(val loans: List<LoanDto>) {
    companion object {
        fun parse(text: String) = LoansResponseDto(parseObject(text).lossyList("loans", LoanDto::read))
    }
}

data class LoanDto(
    /** `{idAbonné}_{idPrêt}`, réutilisable tel quel pour `prolongerPret`. Strict : un prêt sans identifiant est écarté. */
    val id: String,
    val title: String,
    val author: String,
    val dateDue: String,
    val library: String,
    val record: RecordRefDto?
) {
    companion object {
        internal fun read(json: JsonObject) = LoanDto(
            id = json.requiredString("id"),
            title = json.lenientText("title"),
            author = json.lenientText("author"),
            dateDue = json.lenientText("date_due"),
            library = json.lenientText("library"),
            record = json.lenientObject("record", RecordRefDto::read)
        )
    }
}

/** `GET /api/user/holds`. Le rang dans la file n'y figure pas (seulement dans le HTML du compte). */
data class HoldsResponseDto(val holds: List<HoldDto>) {
    companion object {
        fun parse(text: String) = HoldsResponseDto(parseObject(text).lossyList("holds", HoldDto::read))
    }
}

data class HoldDto(
    /** Nécessaire pour annuler la réservation. Strict : une réservation sans identifiant est écartée. */
    val id: String,
    val title: String,
    /** Texte libre, par exemple « Pas disponible avant le 14/11/2026 ». `null` pour certaines réservations (vu en novembre 2026). */
    val status: String?,
    /** Lieu de retrait. */
    val library: String,
    val record: RecordRefDto?
) {
    companion object {
        internal fun read(json: JsonObject) = HoldDto(
            id = json.requiredString("id"),
            title = json.lenientText("title"),
            status = json.lenientString("status"),
            library = json.lenientText("library"),
            record = json.lenientObject("record", RecordRefDto::read)
        )
    }
}

/** Réponse à la bascule d'une sélection par défaut : le nouveau lien du site, « supprimer… » une fois ajouté, « ajouter… » une fois retiré. */
data class SelectionToggleDto(val anchor: String) {
    val holdsRecord: Boolean get() = "supprimer-de-la-selection" in anchor

    companion object {
        fun parse(text: String) = SelectionToggleDto(parseObject(text).requiredString("anchor"))
    }
}

/** Réponse JSON des popups AJAX (réservations…). */
data class PopupDto(val title: String?, val content: String) {
    companion object {
        fun parse(text: String): PopupDto {
            val json = parseObject(text)
            return PopupDto(json.optionalString("title"), json.requiredString("content"))
        }
    }
}
