// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.parser

import java.net.URLDecoder

// Pages `/abonne/*` de l'espace abonné, vérifiées le 31/10/2026.

/** Une carte de `/abonne/selections`. */
data class SelectionDto(
    val id: Int,
    /** Panier de recherche, présent dès que la sélection contient un document. */
    val basketId: Int?,
    val title: String,
    val count: Int,
    /** « 30/10/2026 ». */
    val updatedText: String?,
    val coverUrls: List<String>,
    val previewTitles: List<String>
)

/** Un document emprunté à noter (`/abonne/donner-des-avis`). */
data class RecordToRateDto(val recordId: Int, val recordKey: String, val title: String, val author: String?, val coverUrl: String?)

/**
 * Un avis publié (`/abonne/mes-avis`).
 * ⚠️ Lu sur un compte sans avis : la structure d'une carte est supposée identique aux autres listes
 * Intonation (titre lié à la notice, texte, étoiles).
 */
data class ReviewDto(val recordId: Int?, val title: String, val text: String, val rating: Int?)

/**
 * Une suggestion d'achat envoyée (`/abonne/suggestions`).
 * ⚠️ Lue sur un compte sans suggestion : la structure est supposée.
 */
data class SuggestionDto(val title: String, val detail: String?)

fun HtmlParsers.selections(html: String): List<SelectionDto> =
    parse(html).select("div.card_title_Intonation_Library_View_Wrapper_Selection").mapNotNull { title ->
        val card = title.parents().firstOrNull { it.hasClass("card") } ?: return@mapNotNull null
        val hrefs = card.select("a[href]").map { it.attr("href") }
        val id = hrefs.firstNotNullOfOrNull { firstInt("selection_id/", it) } ?: return@mapNotNull null

        val link = title.selectFirst("a")
        link?.select(".sr-only")?.remove()
        SelectionDto(
            id = id,
            basketId = hrefs.firstNotNullOfOrNull { firstInt("id_panier/", it) },
            title = link?.text().orEmpty(),
            count = card.selectFirst(".selection_count_records .badge")?.text()?.toIntOrNull() ?: 0,
            updatedText = card.selectFirst(".selection_date_maj .badge")?.text(),
            coverUrls = card.select(".cardify_horizontal_img img").mapNotNull { it.attr("abs:src").urlOrNull() },
            previewTitles = card.select("p.model_description_Class_PanierNotice").map { it.text() }
        )
    }

/** Documents empruntés proposés à la notation, avec leur formulaire `ajax-review`. */
fun HtmlParsers.recordsToRate(html: String): List<RecordToRateDto> = parse(html).select("form[action*=ajax-review]").mapNotNull { form ->
    val action = form.attr("action")
    val recordId = firstInt("record_id/", action) ?: return@mapNotNull null
    val key = segmentAfter("record_key", action) ?: return@mapNotNull null
    val card = form.parents().firstOrNull { it.select(".cardify_horizontal_img").isNotEmpty() } ?: return@mapNotNull null

    val title = card.selectFirst(".card-title a")
    title?.select(".sr-only")?.remove()
    val author = card.selectFirst(".card-subtitle .d-inline")
    author?.select(".sr-only")?.remove()
    RecordToRateDto(
        recordId = recordId,
        recordKey = runCatching { URLDecoder.decode(key.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(key),
        title = title?.text().orEmpty(),
        author = author?.text()?.nullIfBlank(),
        coverUrl = card.selectFirst(".cardify_horizontal_img img")?.attr("abs:src")?.urlOrNull()
    )
}

fun HtmlParsers.reviews(html: String): List<ReviewDto> {
    val content = parse(html).selectFirst(".jumbotron_rich_content") ?: return emptyList()
    return content.select(".card").mapNotNull { card ->
        val link = card.selectFirst(".card-title a, a[href*=viewnotice]") ?: return@mapNotNull null
        link.select(".sr-only").remove()
        val filledStars = card.select(".fa-star.fas, .fas.fa-star, .star_on").size
        ReviewDto(
            recordId = firstInt("/id/", link.attr("href")),
            title = link.text(),
            text = card.selectFirst(".card-text")?.text().orEmpty(),
            rating = filledStars.takeIf { it > 0 }
        )
    }
}

fun HtmlParsers.suggestions(html: String): List<SuggestionDto> {
    val content = parse(html).selectFirst(".jumbotron_rich_content") ?: return emptyList()
    val rows = content.select("table tbody tr")
    if (rows.isNotEmpty()) {
        return rows.map { row ->
            val cells = row.select("td").map { it.text() }.filter { it.isNotEmpty() }
            SuggestionDto(title = cells.firstOrNull().orEmpty(), detail = cells.drop(1).joinToString(" · ").nullIfBlank())
        }
    }
    return content.select(".card").mapNotNull { card ->
        val title = card.selectFirst(".card-title")?.text()?.nullIfBlank() ?: return@mapNotNull null
        SuggestionDto(title = title, detail = card.selectFirst(".card-text")?.text()?.nullIfBlank())
    }
}

/** Médiathèques favorites : celles qui proposent le lien « retirer des favoris ». */
fun HtmlParsers.bookmarkedLibraryIds(html: String): Set<Int> =
    parse(html).select("a[href*=remove-library-from-bookmarks]").mapNotNull { firstInt("library_id/", it.attr("href")) }.toSet()

/** Valeur préremplie d'un champ de formulaire (par exemple `Library` dans la suggestion d'achat). */
fun HtmlParsers.fieldValue(name: String, html: String): String? = parse(html).selectFirst("[name=$name]")?.attr("value")?.nullIfBlank()

/** Valeur du bouton radio coché (par exemple `favoriteSendingChannel`). */
fun HtmlParsers.checkedValue(name: String, html: String): String? = parse(html).selectFirst("input[name=$name][checked]")?.attr("value")

/**
 * Vrai si la page a été servie à un abonné connecté : elle propose alors la déconnexion. Une page publique
 * (session expirée) montre les mêmes liens de sélection, tous dans l'état « ajouter ».
 */
fun HtmlParsers.isSignedIn(html: String): Boolean = parse(html).select("a[href*=/auth/logout]").isNotEmpty()

/** Sélections par défaut qui contiennent la notice, lues sur sa page : leur lien est « supprimer-de-la-selection ». */
fun HtmlParsers.defaultSelectionsHolding(html: String): Set<String> =
    parse(html).select("a[href*=/supprimer-de-la-selection/selection_label/]").mapNotNull { link ->
        val label = link.attr("href").substringAfterLast("selection_label/").substringBefore('/')
        label.takeIf { it.isNotEmpty() }?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8) }.getOrNull() }
    }.toSet()

/** Messages d'erreur d'un formulaire renvoyé par le site (champs invalides, mot de passe refusé…). */
fun HtmlParsers.formErrors(html: String): List<String> =
    parse(html).select(".jumbotron_rich_content ul.errors li, .jumbotron_rich_content .alert-danger, .jumbotron_rich_content .error")
        .map { it.text() }
        .filter { it.isNotEmpty() }

/** Segment qui suit [name] dans un chemin Bokeh `/cle/valeur`. */
private fun segmentAfter(name: String, path: String): String? {
    val segments = path.split('/').filter { it.isNotEmpty() }
    val index = segments.indexOf(name)
    return segments.getOrNull(index + 1)?.takeIf { index >= 0 }
}

/** Nombre qui suit immédiatement [marker] dans [text]. */
private fun firstInt(marker: String, text: String): Int? {
    val start = text.indexOf(marker).takeIf { it >= 0 } ?: return null
    return text.substring(start + marker.length).takeWhile { it.isDigit() }.toIntOrNull()
}
