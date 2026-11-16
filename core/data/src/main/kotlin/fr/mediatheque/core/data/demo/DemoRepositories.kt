// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data.demo

import fr.mediatheque.core.data.AccountRepository
import fr.mediatheque.core.data.AgendaRepository
import fr.mediatheque.core.data.AuthError
import fr.mediatheque.core.data.CachePolicy
import fr.mediatheque.core.data.Cached
import fr.mediatheque.core.data.CatalogRepository
import fr.mediatheque.core.data.DigitalRepository
import fr.mediatheque.core.data.DigitalService
import fr.mediatheque.core.data.HoldRequest
import fr.mediatheque.core.data.LibraryRepository
import fr.mediatheque.core.data.ProfileRepository
import fr.mediatheque.core.data.SessionRepository
import fr.mediatheque.core.model.Availability
import fr.mediatheque.core.model.ContactDetails
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.DefaultSelection
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.EventDetail
import fr.mediatheque.core.model.Hold
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.LibraryInfo
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.model.Loan
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.RecordToRate
import fr.mediatheque.core.model.RenewalResult
import fr.mediatheque.core.model.Review
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.model.SearchPage
import fr.mediatheque.core.model.SearchQuery
import fr.mediatheque.core.model.Selection
import fr.mediatheque.core.model.SendingChannel
import fr.mediatheque.core.model.Suggestion
import fr.mediatheque.core.model.SuggestionDraft
import fr.mediatheque.core.model.folded
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

// The demo build: the fictitious patron "Camille MARTIN" and the sample data, no network and no account.
// Used for the screenshots, the UI tests and trying the app without a library card. Actions change the data
// in memory, so renewing or placing a hold behaves as on the real portal while the app runs.

@Singleton
class DemoSessionRepository @Inject constructor() : SessionRepository {
    @Volatile
    private var signedIn = true

    override suspend fun login(credentials: Credentials): Patron {
        if (credentials.password.isEmpty()) throw AuthError.InvalidCredentials
        signedIn = true
        return SampleData.patron
    }

    override suspend fun restore(): Patron? = SampleData.patron.takeIf { signedIn }

    override suspend fun logout() {
        signedIn = false
    }

    override suspend fun requestPasswordReset(cardNumber: String) = Unit

    override suspend fun hasCredentials(): Boolean = signedIn
}

@Singleton
class DemoAccountRepository @Inject constructor() : AccountRepository {
    private val mutex = Mutex()
    private val loans = SampleData.loans.toMutableList()
    private val holds = SampleData.holds.toMutableList()

    override suspend fun patron(): Patron = SampleData.patron

    override suspend fun loans(): List<Loan> = mutex.withLock { loans.toList() }

    override suspend fun holds(): List<Hold> = mutex.withLock { holds.toList() }

    /** Late loans cannot be renewed, the others gain three weeks: the network's rule. */
    override suspend fun renew(loanIds: List<String>): List<RenewalResult> = mutex.withLock {
        loanIds.map { id ->
            val index = loans.indexOfFirst { it.id == id }
            val loan = loans.getOrNull(index)
            if (loan == null || loan.dueState(Instant.now()) is Loan.DueState.Late) {
                RenewalResult.Refused(id)
            } else {
                val renewed = loan.copy(dueDate = loan.dueDate.plus(RENEWAL_DAYS, ChronoUnit.DAYS))
                loans[index] = renewed
                RenewalResult.Renewed(id, renewed.dueDate)
            }
        }
    }

    override suspend fun cancelHold(holdId: String) {
        mutex.withLock { holds.removeAll { it.id == holdId } }
    }

    override suspend fun prepareHold(recordId: Int) = HoldRequest("demo/hold/$recordId", Library.entries, SampleData.patron.homeLibrary)

    override suspend fun placeHold(request: HoldRequest, pickup: Library): String = mutex.withLock {
        val recordId = request.pickupPath.substringAfterLast('/').toIntOrNull()
        val title = SampleData.documents.firstOrNull { it.id == recordId }?.title ?: "Document"
        holds += Hold(
            id = "100001_${holds.size + 20}",
            title = title,
            statusText = "En attente",
            pickupLibrary = pickup,
            pickupLibraryName = pickup.displayName,
            recordId = recordId,
            coverUrl = null,
            rank = 1
        )
        // The site's own confirmation, in French as the portal writes it.
        "Votre réservation est enregistrée."
    }

    private companion object {
        const val RENEWAL_DAYS = 21L
    }
}

class DemoCatalogRepository @Inject constructor() : CatalogRepository {
    override suspend fun search(query: SearchQuery): SearchPage {
        val text = query.text.folded()
        val documents = SampleData.documents.filter { text.isBlank() || text == "*" || text in it.title.folded() }
        return SearchPage(documents.size, documents)
    }

    override suspend fun availability(recordId: Int) =
        if (recordId % 2 == 0) Availability("Disponible", isAvailable = true) else Availability("En prêt", isAvailable = false)

    override suspend fun copies(recordId: Int): List<LiveCopy> = SampleData.copies
}

class DemoAgendaRepository @Inject constructor() : AgendaRepository {
    override suspend fun events(policy: CachePolicy): Cached<List<Event>> = Cached(SampleData.events)

    override suspend fun detail(event: Event) = EventDetail(
        description = "Un moment de lecture partagé, en accès libre.",
        latitude = event.library?.latitude,
        longitude = event.library?.longitude
    )
}

class DemoLibraryRepository @Inject constructor() : LibraryRepository {
    override suspend fun libraries(policy: CachePolicy): Cached<List<LibraryInfo>> = Cached(SampleData.libraries)
}

@Singleton
class DemoProfileRepository @Inject constructor() : ProfileRepository {
    private val mutex = Mutex()
    private val defaultSelections = mutableMapOf<Int, Set<DefaultSelection>>()
    private var contact = ContactDetails(nickname = "", email = "camille.martin@exemple.fr", phone = "", channel = SendingChannel.EMAIL)
    private val bookmarked = mutableSetOf(Library.PULP_NORD)

    override suspend fun selections(): List<Selection> = SampleData.selections

    override suspend fun selectionNotices(selection: Selection, page: Int) =
        SearchPage(selection.count, SampleData.documents.take(selection.count))

    override suspend fun createSelection(name: String) = Unit

    override suspend fun removeFromSelection(selection: Selection, recordId: Int) = Unit

    override suspend fun defaultSelections(recordId: Int): Set<DefaultSelection> = mutex.withLock { defaultSelections[recordId].orEmpty() }

    override suspend fun setInDefaultSelection(selection: DefaultSelection, recordId: Int, isIn: Boolean) {
        mutex.withLock {
            val current = defaultSelections[recordId].orEmpty()
            defaultSelections[recordId] = if (isIn) current + selection else current - selection
        }
    }

    override suspend fun recordsToRate(): List<RecordToRate> = SampleData.recordsToRate

    override suspend fun reviews(): List<Review> = emptyList()

    override suspend fun submitReview(record: RecordToRate, rating: Int, title: String, text: String) = Unit

    override suspend fun suggestions(): List<Suggestion> = emptyList()

    override suspend fun sendSuggestion(draft: SuggestionDraft) = Unit

    override suspend fun contactDetails(): ContactDetails = mutex.withLock { contact }

    override suspend fun updateContactDetails(details: ContactDetails) {
        mutex.withLock { contact = details }
    }

    override suspend fun changePassword(newPassword: String) = Unit

    override suspend fun bookmarkedLibraries(): Set<Library> = mutex.withLock { bookmarked.toSet() }

    override suspend fun setLibraryBookmarked(library: Library, isBookmarked: Boolean) {
        mutex.withLock {
            if (isBookmarked) bookmarked += library else bookmarked -= library
        }
    }

    override suspend fun setNewsletterSubscribed(isSubscribed: Boolean) = Unit
}

class DemoDigitalRepository @Inject constructor() : DigitalRepository {
    /** The digital library's public home page: no sign-in in the demo. */
    override suspend fun target(service: DigitalService): HttpUrl = "https://vod.mediatheque-numerique.com/".toHttpUrl()

    override fun sessionCookies(): List<Cookie> = emptyList()
}
