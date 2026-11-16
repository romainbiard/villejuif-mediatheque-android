// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.Endpoint
import fr.mediatheque.core.bokeh.PortalHttpClient
import fr.mediatheque.core.bokeh.dto.AccountResponseDto
import fr.mediatheque.core.bokeh.dto.SearchResponseDto
import fr.mediatheque.core.bokeh.dto.SelectionToggleDto
import fr.mediatheque.core.bokeh.parser.HtmlParsers
import fr.mediatheque.core.bokeh.parser.bookmarkedLibraryIds
import fr.mediatheque.core.bokeh.parser.checkedValue
import fr.mediatheque.core.bokeh.parser.defaultSelectionsHolding
import fr.mediatheque.core.bokeh.parser.fieldValue
import fr.mediatheque.core.bokeh.parser.formErrors
import fr.mediatheque.core.bokeh.parser.isSignedIn
import fr.mediatheque.core.bokeh.parser.recordsToRate
import fr.mediatheque.core.bokeh.parser.reviews
import fr.mediatheque.core.bokeh.parser.selections
import fr.mediatheque.core.bokeh.parser.suggestions
import fr.mediatheque.core.model.ContactDetails
import fr.mediatheque.core.model.DefaultSelection
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.RecordToRate
import fr.mediatheque.core.model.Review
import fr.mediatheque.core.model.SearchPage
import fr.mediatheque.core.model.Selection
import fr.mediatheque.core.model.SendingChannel
import fr.mediatheque.core.model.Suggestion
import fr.mediatheque.core.model.SuggestionDraft
import fr.mediatheque.core.model.SuggestionKind
import javax.inject.Inject

/**
 * The patron space besides loans and holds: selections, reviews, suggestions, contact details, preferences.
 * These sections only exist as HTML pages (`/abonne/...`), read with the web session.
 */
interface ProfileRepository {
    suspend fun selections(): List<Selection>

    suspend fun selectionNotices(selection: Selection, page: Int): SearchPage

    suspend fun createSelection(name: String)

    suspend fun removeFromSelection(selection: Selection, recordId: Int)

    /** Default selections holding the record (one page read). */
    suspend fun defaultSelections(recordId: Int): Set<DefaultSelection>

    /** Puts the record in a default selection, or takes it out. */
    suspend fun setInDefaultSelection(selection: DefaultSelection, recordId: Int, isIn: Boolean)

    suspend fun recordsToRate(): List<RecordToRate>

    suspend fun reviews(): List<Review>

    suspend fun submitReview(record: RecordToRate, rating: Int, title: String, text: String)

    suspend fun suggestions(): List<Suggestion>

    suspend fun sendSuggestion(draft: SuggestionDraft)

    suspend fun contactDetails(): ContactDetails

    suspend fun updateContactDetails(details: ContactDetails)

    suspend fun changePassword(newPassword: String)

    suspend fun bookmarkedLibraries(): Set<Library>

    suspend fun setLibraryBookmarked(library: Library, isBookmarked: Boolean)

    suspend fun setNewsletterSubscribed(isSubscribed: Boolean)
}

class PortalProfileRepository @Inject constructor(private val http: PortalHttpClient, private val session: PortalSession) :
    ProfileRepository {
    override suspend fun selections(): List<Selection> = HtmlParsers.selections(page("selections")).map { it.toSelection() }

    override suspend fun selectionNotices(selection: Selection, page: Int): SearchPage {
        val basketId = selection.basketId ?: return SearchPage(0, emptyList())
        return session.perform { http.decode(Endpoint.selectionRecords(basketId, page), SearchResponseDto::parse).toSearchPage() }
    }

    override suspend fun createSelection(name: String) = submit("creer-selection", listOf(field("libelle", name)))

    override suspend fun removeFromSelection(selection: Selection, recordId: Int) {
        session.perform { http.bytes(Endpoint.removeFromSelection(patronId(), selection.id, recordId)) }
    }

    override suspend fun defaultSelections(recordId: Int): Set<DefaultSelection> = session.perform {
        val html = http.string(Endpoint.noticePage(recordId))
        // A public page shows every selection link as "add": signed out, that would empty the heart.
        if (!HtmlParsers.isSignedIn(html)) throw BokehError.Unauthorized
        HtmlParsers.defaultSelectionsHolding(html).mapNotNull(DefaultSelection::fromSiteName).toSet()
    }

    override suspend fun setInDefaultSelection(selection: DefaultSelection, recordId: Int, isIn: Boolean) {
        val endpoint = Endpoint.toggleDefaultSelection(selection.siteName, selection.siteImage, selection.siteRevertImage, recordId, isIn)
        val toggle = session.perform { http.decode(endpoint, SelectionToggleDto::parse) }
        if (toggle.holdsRecord != isIn) throw ProfileError.SelectionNotChanged
    }

    override suspend fun recordsToRate(): List<RecordToRate> = HtmlParsers.recordsToRate(page("donner-des-avis")).map {
        it.toRecordToRate()
    }

    override suspend fun reviews(): List<Review> = HtmlParsers.reviews(page("mes-avis")).mapIndexed { index, dto ->
        Review(id = "${dto.recordId ?: 0}-$index", title = dto.title, text = dto.text, rating = dto.rating)
    }

    override suspend fun submitReview(record: RecordToRate, rating: Int, title: String, text: String) {
        session.perform { http.bytes(Endpoint.submitReview(record.recordId, record.recordKey, rating, title, text)) }
    }

    override suspend fun suggestions(): List<Suggestion> = HtmlParsers.suggestions(page("suggestions")).mapIndexed { index, dto ->
        Suggestion(id = "$index-${dto.title}", title = dto.title, detail = dto.detail)
    }

    override suspend fun sendSuggestion(draft: SuggestionDraft) {
        // The site prefills its library field with the patron's library: we send that value back.
        val library = HtmlParsers.fieldValue("Library", page("suggestion-achat-add")).orEmpty()
        // Comics have no type of their own on the site: they go as a book, with the site's word in the comment.
        val comment = if (draft.kind == SuggestionKind.COMIC) {
            listOf("Bande dessinée", draft.comment).filter { it.isNotEmpty() }.joinToString(". ")
        } else {
            draft.comment
        }
        submit(
            "suggestion-achat-add",
            listOf(
                field("DocType", draft.kind.docTypeCode),
                field("Title", draft.title),
                field("Author", draft.author),
                field("Isbn", draft.isbn),
                field("Library", library),
                field("Url", ""),
                field("Comment", comment),
                field("submit", "Envoyer")
            )
        )
    }

    override suspend fun contactDetails(): ContactDetails {
        val html = page("modifier")
        return ContactDetails(
            nickname = HtmlParsers.fieldValue("pseudo", html).orEmpty(),
            email = HtmlParsers.fieldValue("mail", html).orEmpty(),
            phone = HtmlParsers.fieldValue("telephone", html).orEmpty(),
            channel = SendingChannel.fromCode(HtmlParsers.checkedValue("favoriteSendingChannel", html)) ?: SendingChannel.ANY
        )
    }

    override suspend fun updateContactDetails(details: ContactDetails) = submit(
        "modifier",
        listOf(
            field("pseudo", details.nickname),
            field("mail", details.email),
            field("telephone", details.phone),
            field("favoriteSendingChannel", details.channel.code)
        )
    )

    override suspend fun changePassword(newPassword: String) = submit(
        "changer-mon-mot-de-passe",
        listOf(field("new_password", newPassword), field("confirm_new_password", newPassword))
    )

    override suspend fun bookmarkedLibraries(): Set<Library> =
        HtmlParsers.bookmarkedLibraryIds(page("configurations")).mapNotNull { Library.fromCode(it.toString()) }.toSet()

    override suspend fun setLibraryBookmarked(library: Library, isBookmarked: Boolean) {
        val id = library.code.toInt()
        session.perform {
            http.bytes(if (isBookmarked) Endpoint.bookmarkLibrary(id) else Endpoint.removeLibraryBookmark(id))
        }
    }

    override suspend fun setNewsletterSubscribed(isSubscribed: Boolean) {
        session.perform {
            http.bytes(
                if (isSubscribed) Endpoint.subscribeNewsletter(NEWSLETTER_ID) else Endpoint.patronPage("disable-newsletter", patronId())
            )
        }
    }

    // Patron pages and forms

    private suspend fun patronId(): String {
        val account = http.decode(Endpoint.ACCOUNT, AccountResponseDto::parse).account
        return account.cards?.firstOrNull()?.id?.toString() ?: throw ProfileError.MissingPatronId
    }

    private suspend fun page(name: String): String = session.perform { http.string(Endpoint.patronPage(name, patronId())) }

    /** Submits a form, and surfaces the first error message the site shows. */
    private suspend fun submit(name: String, fields: List<Endpoint.FormField>) {
        session.perform {
            val html = http.string(Endpoint.patronForm(name, patronId(), fields))
            HtmlParsers.formErrors(html).firstOrNull()?.let { throw ProfileError.Rejected(it) }
        }
    }

    private fun field(name: String, value: String) = Endpoint.FormField(name, value)

    private companion object {
        /** The network's newsletter, the only one the site offers (checked on 10/31/2026). */
        const val NEWSLETTER_ID = 3
    }
}
