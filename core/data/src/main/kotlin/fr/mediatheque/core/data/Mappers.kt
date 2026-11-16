// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.dto.AccountDto
import fr.mediatheque.core.bokeh.dto.HoldDto
import fr.mediatheque.core.bokeh.dto.LoanDto
import fr.mediatheque.core.bokeh.dto.RecordDto
import fr.mediatheque.core.bokeh.dto.SearchResponseDto
import fr.mediatheque.core.bokeh.parser.CalendarEventDto
import fr.mediatheque.core.bokeh.parser.CopyDto
import fr.mediatheque.core.bokeh.parser.LibraryDto
import fr.mediatheque.core.bokeh.parser.RecordToRateDto
import fr.mediatheque.core.bokeh.parser.SelectionDto
import fr.mediatheque.core.model.BokehDates
import fr.mediatheque.core.model.CopyStatus
import fr.mediatheque.core.model.DocumentKind
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.HarvestedCopy
import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.LibraryInfo
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Notice
import fr.mediatheque.core.model.OpeningHours
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.RecordToRate
import fr.mediatheque.core.model.SearchPage
import fr.mediatheque.core.model.Selection
import fr.mediatheque.core.model.capitalizedWords
import fr.mediatheque.core.model.folded

// From the portal's formats (core:bokeh) to the app's model (core:model).

internal fun RecordDto.toNotice(): Notice? {
    val id = noticeId ?: return null
    return Notice(
        id = id,
        title = titles.lastOrNull() ?: "",
        authors = creators.map(::creatorDisplayName),
        year = date.ifEmpty { null },
        publisher = publishers.firstOrNull { it.isNotEmpty() }?.let(::withoutPlace),
        kind = DocumentKind(type.code, type.label),
        coverUrl = thumbnail?.ifEmpty { null },
        series = serial,
        volume = volume,
        permalink = permalink,
        harvestedCopies = items.map { item ->
            HarvestedCopy(item.subfield("a")?.let(Library::fromLabel) ?: item.annex?.let(Library::fromCode))
        }
    )
}

/** "Rowling|Joanne Kathleen" → "Joanne Kathleen Rowling"; "Toto La Momposina" is left as is. */
internal fun creatorDisplayName(creator: String): String {
    val parts = creator.split('|', limit = 2).map { it.trim() }
    return if (parts.size == 2) "${parts[1]} ${parts[0]}" else creator.trim()
}

/** "Gallimard jeunesse (Paris)" → "Gallimard jeunesse". */
internal fun withoutPlace(publisher: String): String = publisher.replace(Regex("""\s*\([^)]*\)\s*$"""), "")

internal fun SearchResponseDto.toSearchPage() = SearchPage(total, records.mapNotNull { it.toNotice() })

internal fun CopyDto.toLiveCopy(): LiveCopy {
    val label = availability
    val text = label.folded()
    val status = when {
        // "Bientôt disponible": returned, but not back on the shelf yet.
        "bientot" in text -> CopyStatus.Other(label)

        "pret" in text -> CopyStatus.OnLoan(dueDateText?.let(BokehDates::firstDate))

        "disponible" in text || "en rayon" in text -> CopyStatus.Available

        "reserve" in text || "demander" in text -> CopyStatus.OnRequest

        else -> CopyStatus.Other(label)
    }
    return LiveCopy(
        library = Library.fromLabel(libraryName),
        libraryName = libraryName,
        status = status,
        statusLabel = label,
        section = section,
        shelfMark = shelfMark,
        holdCount = holdCount(holdsText)
    )
}

/** "Déjà 2 réservations" → 2, "Déjà une réservation" → 1. */
internal fun holdCount(text: String?): Int {
    text ?: return 0
    Regex("""(\d+)""").find(text)?.let { return it.groupValues[1].toInt() }
    return if ("une reservation" in text.folded()) 1 else 0
}

internal fun AccountDto.toPatron(): Patron {
    val cityLine = "${contact.zipCode} ${contact.city.capitalizedWords()}"
    return Patron(
        cardNumber = login,
        firstName = identity.firstName,
        lastName = identity.lastName,
        email = contact.email.ifEmpty { null },
        phone = contact.mobile.ifEmpty { null } ?: contact.phone.ifEmpty { null },
        homeLibrary = Library.fromCode(libraryDetails.id.toString()) ?: Library.fromLabel(libraryDetails.label),
        cardExpiry = BokehDates.isoDay(card.expireAt),
        cardExpired = card.expired,
        cardAboutToExpire = card.aboutToExpire,
        cardExpiresInDays = card.expiresIn,
        permissions = Patron.Permissions(permissions.holdForbidden, permissions.digitalResourcesForbidden),
        counters = Patron.Counters(counters.loans, counters.lateLoans, counters.waitingToBePulled),
        message = message.content.ifEmpty { null },
        hasUnreadMessage = message.unread,
        nickname = identity.nickname?.ifEmpty { null },
        postalAddress = listOf(contact.address, cityLine).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { null },
        newsletterSubscribed = (preferences?.newsletterCount ?: 0) > 0
    )
}

internal fun LoanDto.toLoan(): Loan? {
    val due = BokehDates.isoDay(dateDue) ?: return null
    return Loan(
        id = id,
        title = title,
        author = author,
        dueDate = due,
        library = Library.fromLabel(library),
        libraryName = library,
        recordId = record?.id,
        coverUrl = record?.thumbnail?.ifEmpty { null }
    )
}

internal fun HoldDto.toHold(rank: Int? = null) = Hold(
    id = id,
    title = cleanHoldTitle(title),
    statusText = status.orEmpty(),
    pickupLibrary = Library.fromLabel(library),
    pickupLibraryName = library,
    recordId = record?.id,
    coverUrl = record?.thumbnail?.ifEmpty { null },
    rank = rank
)

/** Bokeh glues the series and the title together ("Astérix n° 39Astérix et le griffon"). */
internal fun cleanHoldTitle(title: String): String = title.replaceFirst(Regex("""^(.+? n° \d+)(?=\p{Lu})"""), "$1 · ")

internal fun CalendarEventDto.toEvent(): Event {
    val start = startText?.let(BokehDates::frenchDate)
    val end = endText?.let(BokehDates::frenchDate)
    return Event(
        id = articleId,
        title = title,
        imageUrl = imageUrl,
        place = location,
        library = location?.let(Library::fromLabel),
        start = start?.instant,
        end = end?.instant,
        isAllDay = start?.hasTime?.not() ?: true,
        type = eventType,
        audience = audience,
        dateDescription = dateSentence
    )
}

internal fun LibraryDto.toLibraryInfo() = LibraryInfo(
    library = Library.fromSlug(slug),
    slug = slug,
    name = name,
    addressLines = addressLines,
    phone = phone,
    contactUrl = contactUrl,
    hours = OpeningHours.parse(openingHours),
    hourLines = openingHours
)

internal fun SelectionDto.toSelection() = Selection(
    id = id,
    basketId = basketId,
    name = title,
    count = count,
    updatedAt = updatedText?.let(BokehDates::firstDate),
    coverUrls = coverUrls
)

internal fun RecordToRateDto.toRecordToRate() = RecordToRate(recordId, recordKey, title, author, coverUrl)
