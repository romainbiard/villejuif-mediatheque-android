// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.parser

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.PortalHttpClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// Data extracted from HTML

/** An event from the calendar (an "Article" card). */
data class CalendarEventDto(
    val articleId: Int,
    val title: String,
    val imageUrl: String?,
    val location: String?,
    /** Raw badge text, "31/10/2026 à 10:30" or "26/09/2026". */
    val startText: String?,
    val endText: String?,
    val eventType: String?,
    val audience: String?,
    /** Full sentence from the site, for example "Samedi 31 octobre 2026 de 10:30 à 11:00". */
    val dateSentence: String?
)

/** A copy from `/noticeajax/exemplaires/id/{id}`. */
data class CopyDto(
    val libraryName: String,
    val availability: String,
    val section: String?,
    val shelfMark: String?,
    /** "17/11/2026". */
    val dueDateText: String?,
    /** "Déjà 2 réservations". */
    val holdsText: String?
)

/** Availability badge of a record. */
data class AvailabilityDto(val label: String, val isAvailable: Boolean)

/** A library from the Practical Information page. */
data class LibraryDto(
    val slug: String,
    val name: String,
    val addressLines: List<String>,
    val phone: String?,
    val contactUrl: String?,
    /** Lines like "Mardi : 13h - 19h". */
    val openingHours: List<String>,
    val bookmarkLibraryId: Int?
)

/** A pickup location offered when placing a hold (`code_annexe`). */
data class PickupOptionDto(val code: String, val label: String, val isSelected: Boolean)

/** Parsing of portal pages. Everything that depends on the site's HTML lives here. */
object HtmlParsers {
    private val baseUri = PortalHttpClient.VILLEJUIF_BASE_URL.toString()

    internal fun parse(html: String): Document = Jsoup.parse(html, baseUri)

    /** CSRF token of the forms (`auth/login`, `auth/lostpass`). */
    fun csrfToken(html: String): String = parse(html).selectFirst("input[name=csrftoken]")?.attr("value")
        ?.takeIf { it.isNotEmpty() }
        ?: throw BokehError.UnexpectedFormat("csrftoken not found")

    /**
     * True only if the page carries the portal's explicit rejection of the credentials, displayed under the
     * card number field: "L'identifiant ou le mot de passe est invalide." Everything else (another page, another
     * error) is not a rejection: the saved password must survive it.
     */
    fun loginRejected(html: String): Boolean = parse(html)
        .select("li.element_error_message, ul.element_error_messages li")
        .any { item ->
            item.text().lowercase().replace('’', '\'').contains("identifiant ou le mot de passe est invalide")
        }

    fun calendarEvents(html: String): List<CalendarEventDto> {
        val seen = mutableSetOf<Int>()
        return parse(html).select("div.card_Intonation_Library_View_Wrapper_Article").mapNotNull { card ->
            val link = card.selectFirst(".card-title a[href*=articleview]") ?: return@mapNotNull null
            val articleId = link.attr("href").substringAfterLast('/').toIntOrNull() ?: return@mapNotNull null
            if (!seen.add(articleId)) return@mapNotNull null

            link.select(".sr-only").remove()
            CalendarEventDto(
                articleId = articleId,
                title = link.text(),
                imageUrl = card.selectFirst("img.card-img")?.attr("abs:src")?.urlOrNull(),
                location = badgeText(card, ".badge_article_location"),
                startText = badgeText(card, ".badge_event_start"),
                endText = badgeText(card, ".badge_event_end"),
                eventType = badgeText(card, ".custom_field_meta_2"),
                audience = badgeText(card, ".custom_field_meta_3"),
                dateSentence = card.selectFirst("span.calendar_event_date")?.text()
            )
        }
    }

    fun copies(html: String): List<CopyDto> = parse(html).select("div.card_Intonation_Library_View_Wrapper_Item").map { card ->
        CopyDto(
            libraryName = card.selectFirst(".card-title a")?.ownText()?.trim().orEmpty(),
            availability = card.selectFirst(".item_availability")?.text().orEmpty(),
            section = badgeText(card, ".badge_section"),
            shelfMark = badgeText(card, ".badge_cote"),
            dueDateText = badgeText(card, ".badge_retour"),
            holdsText = badgeText(card, ".item_hold_rank")
        )
    }

    /** Badge from `/noticeajax/availability/id/{id}`: label and availability. */
    fun availability(html: String): AvailabilityDto {
        val badge = parse(html).selectFirst(".badge_record_availability")
            ?: throw BokehError.UnexpectedFormat("availability badge not found")
        return AvailabilityDto(badge.text(), isAvailable = !badge.hasClass("record_not_available"))
    }

    fun libraries(html: String): List<LibraryDto> {
        val seen = mutableSetOf<String>()
        return parse(html).select("div.card_Intonation_Library_View_Wrapper_Library").mapNotNull { card ->
            val link = card.selectFirst(".card-title a") ?: return@mapNotNull null
            val slug = link.attr("href").trimStart('/')
            if (!seen.add(slug)) return@mapNotNull null

            // The site separates lines with an invalid `</br>`: read the text nodes one by one.
            val addressLines = card.selectFirst(".badge_address .badge_text")?.textNodes().orEmpty()
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }

            LibraryDto(
                slug = slug,
                name = link.ownText(),
                addressLines = addressLines,
                phone = badgeText(card, ".badge_phone"),
                contactUrl = contactUrl(card),
                openingHours = card.select(".default_opening_hours ul li").map { it.text() },
                bookmarkLibraryId = card.selectFirst("a[href*=add-library-to-bookmarks]")
                    ?.attr("href")?.substringAfterLast('/')?.toIntOrNull()
            )
        }
    }

    /** Pickup locations from the `reservation-pickup-ajax` form (popup content). */
    fun pickupOptions(html: String): List<PickupOptionDto> {
        val document = parse(html)
        return document.select("input[name=code_annexe]").map { input ->
            val id = input.attr("id")
            val label = (if (id.isEmpty()) null else document.selectFirst("label[for=$id]")) ?: input.parent()
            PickupOptionDto(code = input.attr("value"), label = label?.text().orEmpty(), isSelected = input.hasAttr("checked"))
        }
    }

    /** Per-copy hold links in `recherche/reserver/.../render/popup`. */
    fun holdPickupPaths(html: String): List<String> = parse(html).select("a[href*=reservation-pickup-ajax]")
        .map { it.attr("href").trimStart('/') }
        .distinct()

    /**
     * "Rang N" ranks displayed on the "Mes réservations" page, in hold order.
     * The page has only been seen with a single hold: the rank is matched to the hold by order of appearance.
     */
    fun holdRanks(html: String): List<Int> = Regex("""Rang\s+(\d+)""").findAll(parse(html).text())
        .mapNotNull { it.groupValues[1].toIntOrNull() }
        .toList()

    /** Provider URL carried by a `/modules/{service}` page. */
    fun ssoTarget(html: String): HttpUrl? = Regex("""location\.href\s*=\s*"([^"]+)"""").find(html)?.groupValues?.get(1)?.toHttpUrlOrNull()

    // Utilities

    /**
     * The site nests a link inside the badge's `mailto:` (`mailto:<a href="https://...">`):
     * the contact form URL is extracted directly from the attribute.
     */
    private fun contactUrl(card: Element): String? {
        val href = card.selectFirst("a.badge_mail")?.attr("href") ?: return null
        return Regex("""https?://[^"'<>\s]+""").find(href)?.value?.urlOrNull()
    }

    internal fun badgeText(element: Element, selector: String): String? {
        val badge = element.selectFirst("$selector .badge_text") ?: return null
        badge.select(".sr-only").remove()
        return badge.text().trim().ifEmpty { null }
    }
}

/** The URL if it is valid (`http` or `https`), otherwise `null`. */
internal fun String.urlOrNull(): String? = takeIf { it.toHttpUrlOrNull() != null }

/** `null` if the text is empty or blank, otherwise the text trimmed of surrounding whitespace. */
internal fun String.nullIfBlank(): String? = trim().ifEmpty { null }
