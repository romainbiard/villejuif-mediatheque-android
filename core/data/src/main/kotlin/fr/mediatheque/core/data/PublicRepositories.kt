// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.Endpoint
import fr.mediatheque.core.bokeh.PortalHttpClient
import fr.mediatheque.core.bokeh.dto.SearchResponseDto
import fr.mediatheque.core.bokeh.parser.HtmlParsers
import fr.mediatheque.core.bokeh.parser.ICalParser
import fr.mediatheque.core.model.Availability
import fr.mediatheque.core.model.Event
import fr.mediatheque.core.model.EventDetail
import fr.mediatheque.core.model.LibraryInfo
import fr.mediatheque.core.model.LiveCopy
import fr.mediatheque.core.model.SearchPage
import fr.mediatheque.core.model.SearchQuery
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.builtins.ListSerializer

// The portal's public data: no account needed.

/** Public catalog: JSON search and real-time availability. */
interface CatalogRepository {
    suspend fun search(query: SearchQuery): SearchPage

    /** Real-time badge for a record (the status in the search JSON dates from the night before). */
    suspend fun availability(recordId: Int): Availability

    /** Real-time copies: library, call number, due date, holds. */
    suspend fun copies(recordId: Int): List<LiveCopy>
}

class PortalCatalogRepository @Inject constructor(private val http: PortalHttpClient, private val cache: DataCache) : CatalogRepository {
    override suspend fun search(query: SearchQuery): SearchPage {
        val endpoint = Endpoint.search(query.text, query.facets, query.sort.value, query.page)
        // Kept in memory for 5 minutes: switching filters back and forth, or reopening a recent search, is instant.
        return cache.value("search-${endpoint.cacheKey}", 5.minutes, SearchPage.serializer(), persist = false) {
            http.decode(endpoint, SearchResponseDto::parse).toSearchPage()
        }
    }

    override suspend fun availability(recordId: Int): Availability =
        // Must stay close to real time: 2 minutes, just enough not to reload it while scrolling.
        cache.value("availability-$recordId", 2.minutes, Availability.serializer(), persist = false) {
            val badge = HtmlParsers.availability(http.string(Endpoint.availability(recordId)))
            Availability(badge.label, badge.isAvailable)
        }

    override suspend fun copies(recordId: Int): List<LiveCopy> =
        HtmlParsers.copies(http.string(Endpoint.copies(recordId))).map { it.toLiveCopy() }
}

/** Agenda: the list comes from the agenda page (the `/api/agenda` API has no dates), the details from each event's iCal. */
interface AgendaRepository {
    suspend fun events(policy: CachePolicy = CachePolicy.USE_CACHE): Cached<List<Event>>

    suspend fun detail(event: Event): EventDetail
}

class PortalAgendaRepository @Inject constructor(private val http: PortalHttpClient, private val cache: DataCache) : AgendaRepository {
    override suspend fun events(policy: CachePolicy): Cached<List<Event>> =
        // The site publishes the agenda once and for all: 30 minutes is enough, and it stays readable offline.
        cache.cached("agenda-events", 30.minutes, ListSerializer(Event.serializer()), policy) {
            HtmlParsers.calendarEvents(http.string(Endpoint.calendar()))
                .map { it.toEvent() }
                .sortedBy { it.start ?: java.time.Instant.MAX }
        }

    override suspend fun detail(event: Event): EventDetail {
        val vevent = ICalParser.events(http.string(Endpoint.eventICal(event.id))).firstOrNull()
        return EventDetail(vevent?.description, vevent?.latitude, vevent?.longitude)
    }
}

/** Practical information about the three libraries. */
interface LibraryRepository {
    suspend fun libraries(policy: CachePolicy = CachePolicy.USE_CACHE): Cached<List<LibraryInfo>>
}

class PortalLibraryRepository @Inject constructor(private val http: PortalHttpClient, private val cache: DataCache) : LibraryRepository {
    override suspend fun libraries(policy: CachePolicy): Cached<List<LibraryInfo>> =
        // Opening hours rarely change (holidays, works): 12 hours, and always viewable offline.
        cache.cached("libraries", 12.hours, ListSerializer(LibraryInfo.serializer()), policy) {
            HtmlParsers.libraries(http.string(Endpoint.PRACTICAL_INFO)).map { it.toLibraryInfo() }
        }
}
