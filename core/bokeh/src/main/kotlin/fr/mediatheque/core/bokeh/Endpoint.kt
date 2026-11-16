// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import okhttp3.HttpUrl

/**
 * Description d'une requête au portail. Les routes Bokeh sont des segments de chemin (`/cle/valeur`),
 * d'où leur construction par [pathSegments].
 */
data class Endpoint(
    val pathSegments: List<String>,
    val method: Method = Method.GET,
    val query: List<FormField> = emptyList(),
    val form: List<FormField> = emptyList(),
    /** Ajoute `X-Requested-With: XMLHttpRequest` : les popups Bokeh répondent alors en JSON. */
    val isAjax: Boolean = false
) {
    enum class Method { GET, POST }

    data class FormField(val name: String, val value: String)

    /** Clé unique d'une requête GET (chemin et paramètres), pour les caches. */
    val cacheKey: String get() = (listOf(encodedPath) + query.map { "${it.name}=${it.value}" }).joinToString("&")

    /** Chemin encodé segment par segment (les espaces deviennent `%20`, `/` et `*` sont encodés). */
    val encodedPath: String get() = "/" + pathSegments.joinToString("/") { encodePathSegment(it) }

    fun url(base: HttpUrl): HttpUrl = base.newBuilder()
        .encodedPath(encodedPath)
        .apply { query.forEach { addQueryParameter(it.name, it.value) } }
        .build()

    companion object {
        // Catalogue

        /**
         * Recherche simple au format JSON. Les facettes se combinent (ET) par `facettes/T1-Y2`.
         * `*` parcourt tout le catalogue ; `sort = "date_creation desc"` donne les nouveautés.
         */
        fun search(text: String, facets: List<String> = emptyList(), sort: String = "*", page: Int = 1): Endpoint {
            val segments = mutableListOf("recherche", "simple", "expressionRecherche", text.ifEmpty { "*" }, "tri", sort)
            if (facets.isNotEmpty()) segments += listOf("facettes", facets.joinToString("-"))
            segments += listOf("format", "json")
            if (page > 1) segments += listOf("page", page.toString())
            return Endpoint(segments)
        }

        /** Badge de disponibilité en temps réel (HTML). */
        fun availability(recordId: Int) = Endpoint(listOf("noticeajax", "availability", "id", recordId.toString()))

        /** Exemplaires en temps réel (HTML) : statut, cote, date de retour, réservations. */
        fun copies(recordId: Int) = Endpoint(listOf("noticeajax", "exemplaires", "id", recordId.toString()))

        /** Autocomplétion de la recherche. */
        fun suggest(prefix: String) = Endpoint(listOf("recherche", "suggestajax"), query = listOf(FormField("startsWith", prefix)))

        // Agenda et infos pratiques

        /**
         * Page de l'agenda (rendue côté serveur, contrairement à `/cms/calendar/...` chargé en AJAX).
         * Les filtres sont les mêmes segments que sur le site.
         */
        fun calendar(placeId: Int? = null, typeId: Int? = null, audienceId: Int? = null): Endpoint {
            val segments = mutableListOf("accueil", "agenda")
            placeId?.let { segments += listOf("place", it.toString()) }
            typeId?.let { segments += listOf("custom_field_2", it.toString()) }
            audienceId?.let { segments += listOf("custom_field_3", it.toString()) }
            return Endpoint(segments)
        }

        fun eventICal(articleId: Int) = Endpoint(listOf("cms", "ical", "id_article", articleId.toString()))

        val PRACTICAL_INFO = Endpoint(listOf("accueil", "infos_pratiques"))

        // Connexion

        val LOGIN_PAGE = Endpoint(listOf("auth", "login"))

        fun login(cardNumber: String, password: String, csrfToken: String) = Endpoint(
            listOf("auth", "login"),
            method = Method.POST,
            form = listOf(
                FormField("username", cardNumber),
                FormField("password", password),
                FormField("login", "Se connecter"),
                FormField("csrftoken", csrfToken)
            )
        )

        val LOST_PASSWORD_PAGE = Endpoint(listOf("auth", "lostpass"))

        /**
         * `website` est un pot de miel anti-spam qui doit rester vide ; le formulaire doit être envoyé
         * au moins `minimum_time` secondes après son affichage.
         */
        fun lostPassword(cardNumber: String, csrfToken: String) = Endpoint(
            listOf("auth", "lostpass"),
            method = Method.POST,
            form = listOf(
                FormField("lost_username", cardNumber),
                FormField("website", ""),
                FormField("minimum_time", "2"),
                FormField("csrftoken", csrfToken)
            )
        )

        val LOGOUT = Endpoint(listOf("auth", "logout"))

        // Compte de l'abonné (JSON, avec le cookie de session)

        val ACCOUNT = Endpoint(listOf("api", "user", "account"))
        val LOANS = Endpoint(listOf("api", "user", "loans"))
        val HOLDS = Endpoint(listOf("api", "user", "holds"))

        /** Prolonge un ou plusieurs prêts (identifiants séparés par `;`). */
        fun renewLoans(patronId: String, loanIds: List<String>) = Endpoint(
            listOf("abonne", "prolongerPret", "id_profil", "1", "id", patronId, "id_pret", loanIds.joinToString(";"))
        )

        fun cancelHold(holdId: String) = Endpoint(listOf("abonne", "reservations", "id_delete", holdId))

        /** Page HTML des réservations : seule source du rang dans la file. */
        fun holdsPage(patronId: String) = Endpoint(listOf("abonne", "reservations", "id_profil", "1", "id", patronId))

        fun bookmarkLibrary(libraryId: Int) = Endpoint(listOf("abonne", "add-library-to-bookmarks", "library_id", libraryId.toString()))

        // Réservations (popups AJAX)

        fun holdOptions(recordId: Int) = Endpoint(
            listOf("recherche", "reserver", "record_id", recordId.toString(), "render", "popup"),
            isAjax = true
        )

        /** [path] est le lien `reservation-pickup-ajax/...` lu dans [holdOptions]. */
        fun pickupForm(path: String) = Endpoint(path.split('/').filter { it.isNotEmpty() }, isAjax = true)

        fun submitPickup(path: String, pickupLibraryCode: String) = Endpoint(
            path.split('/').filter { it.isNotEmpty() },
            method = Method.POST,
            form = listOf(FormField("code_annexe", pickupLibraryCode)),
            isAjax = true
        )

        // Médiathèque numérique

        /** Petite page `<script>document.location.href="…"</script>` qui porte l'URL de connexion (SSO). */
        fun digitalModule(slug: String) = Endpoint(listOf("modules", slug))

        // Espace abonné (pages et formulaires HTML, vérifiés le 31/10/2026)

        /** Page d'une rubrique du compte : `/abonne/{page}/id_profil/1/id/{idAbonné}`. */
        fun patronPage(page: String, patronId: String) = Endpoint(listOf("abonne", page, "id_profil", "1", "id", patronId))

        /** Formulaire d'une rubrique du compte, sans jeton CSRF. */
        fun patronForm(page: String, patronId: String, fields: List<FormField>) = Endpoint(
            listOf("abonne", page, "id_profil", "1", "id", patronId),
            method = Method.POST,
            form = fields
        )

        /** Contenu d'une sélection : c'est une recherche JSON sur le panier. */
        fun selectionRecords(basketId: Int, page: Int = 1): Endpoint {
            val segments = mutableListOf("recherche", "simple", "id_panier", basketId.toString(), "format", "json")
            if (page > 1) segments += listOf("page", page.toString())
            return Endpoint(segments)
        }

        /** Page d'une notice par son seul identifiant. Connecté, elle dit quelles sélections par défaut la contiennent. */
        fun noticePage(recordId: Int) = Endpoint(listOf("recherche", "viewnotice", "id", recordId.toString()))

        /**
         * Ajoute une notice à une sélection par défaut du site (« Mes préférés », « Déjà lu », « À lire ») ou l'en retire.
         * [image] et [revertImage] sont les icônes du site pour la bascule ; il répond par le nouveau lien (JSON).
         * Le libellé part comme le site l'écrit, espaces en `+` (`Mes+préférés`).
         */
        fun toggleDefaultSelection(label: String, image: String, revertImage: String, recordId: Int, adding: Boolean): Endpoint {
            val selection = listOf("selection_label", label.replace(' ', '+'), "image", image, "revert-image", revertImage)
            return if (adding) {
                Endpoint(
                    listOf("abonne", "ajouter-le-document-a-la-selection") + selection + listOf("record_id", recordId.toString()),
                    isAjax = true
                )
            } else {
                Endpoint(
                    listOf("abonne", "supprimer-de-la-selection") + selection + listOf("record_id", recordId.toString(), "ajax", "true"),
                    isAjax = true
                )
            }
        }

        fun removeFromSelection(patronId: String, selectionId: Int, recordId: Int) = Endpoint(
            listOf(
                "abonne", "supprimer-de-la-selection", "id_profil", "1", "id", patronId,
                "selection_id", selectionId.toString(), "record_id", recordId.toString(), "delete", "1"
            )
        )

        /** Avis sur un document emprunté : note de 1 à 5, titre et texte. */
        fun submitReview(recordId: Int, recordKey: String, rating: Int, title: String, text: String) = Endpoint(
            listOf("abonne", "ajax-review", "record_id", recordId.toString(), "record_key", recordKey),
            method = Method.POST,
            form = listOf(FormField("note", rating.toString()), FormField("entete", title), FormField("avis", text))
        )

        fun removeLibraryBookmark(libraryId: Int) =
            Endpoint(listOf("abonne", "remove-library-from-bookmarks", "library_id", libraryId.toString()))

        fun subscribeNewsletter(id: Int) = Endpoint(listOf("abonne", "subscribe-newsletter", "id", id.toString()))
    }
}

/**
 * Caractères gardés tels quels dans un segment : ceux d'un chemin d'URL (RFC 3986), sauf `/` et `*`
 * que Bokeh lirait comme séparateur ou joker. Le reste est encodé en UTF-8 (`é` → `%C3%A9`).
 */
private const val PATH_SEGMENT_ALLOWED = "-._~!$&'()+,;=:@"

internal fun encodePathSegment(segment: String): String {
    val builder = StringBuilder()
    for (byte in segment.toByteArray(Charsets.UTF_8)) {
        val character = (byte.toInt() and 0xFF).toChar()
        val isAsciiAlphanumeric = character in 'A'..'Z' || character in 'a'..'z' || character in '0'..'9'
        if (isAsciiAlphanumeric || character in PATH_SEGMENT_ALLOWED) {
            builder.append(character)
        } else {
            builder.append('%').append("%02X".format(byte.toInt() and 0xFF))
        }
    }
    return builder.toString()
}
