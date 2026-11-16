// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.parser

import java.net.URLDecoder

// `/abonne/*` pages of the patron area, verified on 10/31/2026.

/** A card from `/abonne/selections`. */
data class SelectionDto(
    val id: Int,
    /** Search basket, present as soon as the selection contains an item. */
    val basketId: Int?,
    val title: String,
    val count: Int,
    /** "30/10/2026". */
    val updatedText: String?,
    val coverUrls: List<String>,
    val previewTitles: List<String>
)

/** A borrowed item to rate (`/abonne/donner-des-avis`). */
data class RecordToRateDto(val recordId: Int, val recordKey: String, val title: String, val author: String?, val coverUrl: String?)

/**
 * A published review (`/abonne/mes-avis`).
 * ⚠️ Read from an account with no reviews: a card's structure is assumed identical to the other Intonation
 * lists (title linked to the record, text, stars).
 */
data class ReviewDto(val recordId: Int?, val title: String, val text: String, val rating: Int?)

/**
 * A submitted purchase suggestion (`/abonne/suggestions`).
 * ⚠️ Read from an account with no suggestions: the structure is assumed.
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

/** Borrowed items offered for rating, with their `ajax-review` form. */
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

/** Favorite libraries: those that offer the "retirer des favoris" link. */
fun HtmlParsers.bookmarkedLibraryIds(html: String): Set<Int> =
    parse(html).select("a[href*=remove-library-from-bookmarks]").mapNotNull { firstInt("library_id/", it.attr("href")) }.toSet()

/** Prefilled value of a form field (for example `Library` in the purchase suggestion). */
fun HtmlParsers.fieldValue(name: String, html: String): String? = parse(html).selectFirst("[name=$name]")?.attr("value")?.nullIfBlank()

/** Value of the checked radio button (for example `favoriteSendingChannel`). */
fun HtmlParsers.checkedValue(name: String, html: String): String? = parse(html).selectFirst("input[name=$name][checked]")?.attr("value")

/**
 * True if the page was served to a signed-in patron: it then offers the logout link. A public page
 * (expired session) shows the same selection links, all in the "ajouter" state.
 */
fun HtmlParsers.isSignedIn(html: String): Boolean = parse(html).select("a[href*=/auth/logout]").isNotEmpty()

/** Default selections that contain the record, read from its page: their link is "supprimer-de-la-selection". */
fun HtmlParsers.defaultSelectionsHolding(html: String): Set<String> =
    parse(html).select("a[href*=/supprimer-de-la-selection/selection_label/]").mapNotNull { link ->
        val label = link.attr("href").substringAfterLast("selection_label/").substringBefore('/')
        label.takeIf { it.isNotEmpty() }?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8) }.getOrNull() }
    }.toSet()

/** Error messages of a form returned by the site (invalid fields, rejected password, etc.). */
fun HtmlParsers.formErrors(html: String): List<String> =
    parse(html).select(".jumbotron_rich_content ul.errors li, .jumbotron_rich_content .alert-danger, .jumbotron_rich_content .error")
        .map { it.text() }
        .filter { it.isNotEmpty() }

/** Segment that follows [name] in a Bokeh `/key/value` path. */
private fun segmentAfter(name: String, path: String): String? {
    val segments = path.split('/').filter { it.isNotEmpty() }
    val index = segments.indexOf(name)
    return segments.getOrNull(index + 1)?.takeIf { index >= 0 }
}

/** Number that immediately follows [marker] in [text]. */
private fun firstInt(marker: String, text: String): Int? {
    val start = text.indexOf(marker).takeIf { it >= 0 } ?: return null
    return text.substring(start + marker.length).takeWhile { it.isDigit() }.toIntOrNull()
}
