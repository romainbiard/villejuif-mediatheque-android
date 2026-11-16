// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import okhttp3.HttpUrl

/**
 * Description of a request to the portal. Bokeh routes are path segments (`/key/value`),
 * hence they are built from [pathSegments].
 */
data class Endpoint(
    val pathSegments: List<String>,
    val method: Method = Method.GET,
    val query: List<FormField> = emptyList(),
    val form: List<FormField> = emptyList(),
    /** Adds `X-Requested-With: XMLHttpRequest`, so Bokeh popups respond with JSON. */
    val isAjax: Boolean = false
) {
    enum class Method { GET, POST }

    data class FormField(val name: String, val value: String)

    /** Unique key of a GET request (path and parameters), for caches. */
    val cacheKey: String get() = (listOf(encodedPath) + query.map { "${it.name}=${it.value}" }).joinToString("&")

    /** Path encoded segment by segment (spaces become `%20`; `/` and `*` are encoded). */
    val encodedPath: String get() = "/" + pathSegments.joinToString("/") { encodePathSegment(it) }

    fun url(base: HttpUrl): HttpUrl = base.newBuilder()
        .encodedPath(encodedPath)
        .apply { query.forEach { addQueryParameter(it.name, it.value) } }
        .build()

    companion object {
        // Catalog

        /**
         * Simple search in JSON format. Facets combine (AND) through `facettes/T1-Y2`.
         * `*` browses the whole catalog; `sort = "date_creation desc"` gives new arrivals.
         */
        fun search(text: String, facets: List<String> = emptyList(), sort: String = "*", page: Int = 1): Endpoint {
            val segments = mutableListOf("recherche", "simple", "expressionRecherche", text.ifEmpty { "*" }, "tri", sort)
            if (facets.isNotEmpty()) segments += listOf("facettes", facets.joinToString("-"))
            segments += listOf("format", "json")
            if (page > 1) segments += listOf("page", page.toString())
            return Endpoint(segments)
        }

        /** Real-time availability badge (HTML). */
        fun availability(recordId: Int) = Endpoint(listOf("noticeajax", "availability", "id", recordId.toString()))

        /** Real-time copies (HTML): status, shelf mark, due date, holds. */
        fun copies(recordId: Int) = Endpoint(listOf("noticeajax", "exemplaires", "id", recordId.toString()))

        /** Search autocomplete. */
        fun suggest(prefix: String) = Endpoint(listOf("recherche", "suggestajax"), query = listOf(FormField("startsWith", prefix)))

        // Calendar and practical information

        /**
         * Calendar page (server-rendered, unlike `/cms/calendar/...` which is loaded via AJAX).
         * Filters are the same segments as on the site.
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

        // Login

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
         * `website` is an anti-spam honeypot that must stay empty; the form must be submitted
         * at least `minimum_time` seconds after it is displayed.
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

        // Patron account (JSON, with the session cookie)

        val ACCOUNT = Endpoint(listOf("api", "user", "account"))
        val LOANS = Endpoint(listOf("api", "user", "loans"))
        val HOLDS = Endpoint(listOf("api", "user", "holds"))

        /** Renews one or more loans (IDs separated by `;`). */
        fun renewLoans(patronId: String, loanIds: List<String>) = Endpoint(
            listOf("abonne", "prolongerPret", "id_profil", "1", "id", patronId, "id_pret", loanIds.joinToString(";"))
        )

        fun cancelHold(holdId: String) = Endpoint(listOf("abonne", "reservations", "id_delete", holdId))

        /** HTML holds page: the only source of the queue position. */
        fun holdsPage(patronId: String) = Endpoint(listOf("abonne", "reservations", "id_profil", "1", "id", patronId))

        fun bookmarkLibrary(libraryId: Int) = Endpoint(listOf("abonne", "add-library-to-bookmarks", "library_id", libraryId.toString()))

        // Holds (AJAX popups)

        fun holdOptions(recordId: Int) = Endpoint(
            listOf("recherche", "reserver", "record_id", recordId.toString(), "render", "popup"),
            isAjax = true
        )

        /** [path] is the `reservation-pickup-ajax/...` link read from [holdOptions]. */
        fun pickupForm(path: String) = Endpoint(path.split('/').filter { it.isNotEmpty() }, isAjax = true)

        fun submitPickup(path: String, pickupLibraryCode: String) = Endpoint(
            path.split('/').filter { it.isNotEmpty() },
            method = Method.POST,
            form = listOf(FormField("code_annexe", pickupLibraryCode)),
            isAjax = true
        )

        // Digital library

        /** Tiny page `<script>document.location.href="..."</script>` that carries the sign-in (SSO) URL. */
        fun digitalModule(slug: String) = Endpoint(listOf("modules", slug))

        // Patron area (HTML pages and forms, verified on 10/31/2026)

        /** Page of an account section: `/abonne/{page}/id_profil/1/id/{patronId}`. */
        fun patronPage(page: String, patronId: String) = Endpoint(listOf("abonne", page, "id_profil", "1", "id", patronId))

        /** Form of an account section, without a CSRF token. */
        fun patronForm(page: String, patronId: String, fields: List<FormField>) = Endpoint(
            listOf("abonne", page, "id_profil", "1", "id", patronId),
            method = Method.POST,
            form = fields
        )

        /** Contents of a selection: it is a JSON search on the basket. */
        fun selectionRecords(basketId: Int, page: Int = 1): Endpoint {
            val segments = mutableListOf("recherche", "simple", "id_panier", basketId.toString(), "format", "json")
            if (page > 1) segments += listOf("page", page.toString())
            return Endpoint(segments)
        }

        /** Page of a record by its ID alone. When signed in, it says which default selections contain it. */
        fun noticePage(recordId: Int) = Endpoint(listOf("recherche", "viewnotice", "id", recordId.toString()))

        /**
         * Adds a record to one of the site's default selections ("Mes préférés", "Déjà lu", "À lire") or removes it from there.
         * [image] and [revertImage] are the site's icons for the toggle; it responds with the new link (JSON).
         * The label is sent the way the site writes it, with spaces as `+` (`Mes+préférés`).
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

        /** Review of a borrowed item: rating from 1 to 5, title, and text. */
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
 * Characters kept as-is in a segment: those of a URL path (RFC 3986), except `/` and `*`,
 * which Bokeh would read as a separator or wildcard. The rest is UTF-8 encoded (`é` -> `%C3%A9`).
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
