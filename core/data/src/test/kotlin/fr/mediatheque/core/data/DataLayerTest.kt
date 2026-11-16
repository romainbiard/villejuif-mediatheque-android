// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.dto.SearchResponseDto
import fr.mediatheque.core.bokeh.parser.CopyDto
import fr.mediatheque.core.model.CopyStatus
import fr.mediatheque.core.model.Library
import fr.mediatheque.core.model.Paris
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MapperTest {
    @Test
    fun creatorNamesAreReordered() {
        assertEquals("Joanne Kathleen Rowling", creatorDisplayName("Rowling|Joanne Kathleen"))
        assertEquals("Toto La Momposina", creatorDisplayName("Toto La Momposina"))
    }

    @Test
    fun thePublishersPlaceIsStripped() {
        assertEquals("Gallimard jeunesse", withoutPlace("Gallimard jeunesse (Paris Cedex 07)"))
    }

    @Test
    fun aDocumentFromARecord() {
        val json = """{"total":1,"records":[{"identifier":"SWAY--TOTOG--IDOL-2018-3","titles":["Sway"],"serial":null,"creators":["Toto|Gérald"],
            |"date":"2018","publishers":["Idol"],"type":{"code":"3","label":"CD"},"thumbnail":"",
            |"permalink":"https://mediatheque.villejuif.fr/recherche/viewnotice/clef/SWAY--TOTOG--IDOL-2018-3/id/474169","collections":[],
            |"items":[{"id":1,"id_notice":474169,"annexe":"1","cote":"781.3 TOT","activite":"En rayon","id_bib":1,
            |"zone995":[{"code":"a","valeur":"Médiathèque Elsa-Triolet"}]}]}]}
        """.trimMargin()
        val document = SearchResponseDto.parse(json).toSearchPage().documents.single()
        assertEquals(474169, document.id)
        assertEquals(listOf("Gérald Toto"), document.authors)
        assertNull(document.coverUrl)
        assertFalse(document.kind.isDigital)
        assertEquals(listOf(Library.ELSA_TRIOLET), document.libraries)
    }

    @Test
    fun liveCopyStatus() {
        val copy = CopyDto("Médiathèque Elsa-Triolet", "En prêt", "Jeunesse", "SF ROW", "17/11/2026", "Déjà 2 réservations").toLiveCopy()
        assertEquals(Library.ELSA_TRIOLET, copy.library)
        assertEquals(2, copy.holdCount)
        assertEquals(CopyStatus.OnLoan(Paris.instant(2026, 11, 17)), copy.status)
        assertEquals(1, holdCount("Déjà une réservation"))
        val soon = CopyDto("PULP Nord", "Bientôt disponible", null, null, null, null).toLiveCopy()
        assertFalse(soon.status.isAvailable)
        assertEquals(CopyStatus.Available, CopyDto("PULP Sud", "En rayon", null, null, null, null).toLiveCopy().status)
        assertEquals(CopyStatus.OnRequest, CopyDto("PULP Sud", "En réserve", null, null, null, null).toLiveCopy().status)
    }

    @Test
    fun holdTitlesAreUnglued() {
        assertEquals("Astérix n° 39 · Astérix et le griffon", cleanHoldTitle("Astérix n° 39Astérix et le griffon"))
        assertEquals("Murmuration", cleanHoldTitle("Murmuration"))
    }
}

class LoadErrorTest {
    @Test
    fun errorsSayWhatThePatronCanDo() {
        assertEquals(LoadError.Offline, LoadError.from(UnknownHostException("mediatheque.villejuif.fr")))
        assertEquals(LoadError.SiteUnavailable, LoadError.from(SocketTimeoutException()))
        assertEquals(LoadError.SiteUnavailable, LoadError.from(IOException("reset")))
        assertEquals(LoadError.NotLoggedIn, LoadError.from(AuthError.InvalidCredentials))
        assertEquals(LoadError.NotLoggedIn, LoadError.from(BokehError.Unauthorized))
        // Temporary: never shown as signed out.
        assertEquals(LoadError.SiteUnavailable, LoadError.from(AuthError.SessionNotOpened))
        assertEquals(LoadError.SiteUnavailable, LoadError.from(BokehError.ChallengeFailed))
        assertEquals(LoadError.UnreadableAnswer, LoadError.from(BokehError.UnexpectedFormat("x")))
    }
}

class DataCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone() = ZoneOffset.UTC

        override fun withZone(zone: java.time.ZoneId?) = this

        override fun instant() = now
    }

    private val clock = MutableClock(Instant.ofEpochSecond(1_000))

    @Test
    fun aFreshValueAvoidsTheLoad() = runTest {
        val cache = DataCache(folder.newFolder(), clock, StandardTestDispatcher(testScheduler))
        assertEquals(1, cache.value("k", 60.seconds, Int.serializer()) { 1 })
        clock.now = clock.now.plusSeconds(30)
        assertEquals(1, cache.value("k", 60.seconds, Int.serializer()) { 2 })
    }

    @Test
    fun aStaleValueIsReloadedAndReloadBypassesTheCache() = runTest {
        val cache = DataCache(folder.newFolder(), clock, StandardTestDispatcher(testScheduler))
        cache.value("k", 60.seconds, Int.serializer()) { 1 }
        clock.now = clock.now.plusSeconds(61)
        assertEquals(2, cache.value("k", 60.seconds, Int.serializer()) { 2 })
        assertEquals(3, cache.value("k", 60.seconds, Int.serializer(), CachePolicy.RELOAD) { 3 })
    }

    @Test
    fun aFailedLoadFallsBackToTheStaleValueAndSaysSo() = runTest {
        val cache = DataCache(folder.newFolder(), clock, StandardTestDispatcher(testScheduler))
        val fresh = cache.cached("k", 60.seconds, String.serializer()) { "yesterday" }
        clock.now = clock.now.plusSeconds(3600)
        val offline = cache.cached("k", 60.seconds, String.serializer(), CachePolicy.RELOAD) { throw UnknownHostException() }
        assertNull(fresh.refreshError)
        assertEquals("yesterday", offline.value)
        assertEquals(LoadError.Offline, offline.refreshError)

        val error = runCatching { cache.value("absent", 60.seconds, String.serializer()) { throw IOException("down") } }.exceptionOrNull()
        assertTrue(error is IOException)
    }

    @Test
    fun valuesSurviveOnDiskButNotWhenMemoryOnly() = runTest {
        val directory = folder.newFolder()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cache = DataCache(directory, clock, dispatcher)
        val list = ListSerializer(Int.serializer())
        cache.value("disk", 60.seconds, list) { listOf(1, 2) }
        cache.value("memory", 60.seconds, list, persist = false) { listOf(3) }

        // A new cache on the same folder: as when the app launches again.
        val reopened = DataCache(directory, clock, dispatcher)
        assertEquals(listOf(1, 2), reopened.value("disk", 60.seconds, list) { emptyList() })
        assertEquals(emptyList<Int>(), reopened.value("memory", 60.seconds, list, persist = false) { emptyList() })
    }
}

class ChallengeCookiesTest {
    @Test
    fun webViewCookiesBecomeSessionCookiesOfThePortal() {
        val url = "https://mediatheque.villejuif.fr/challenge".toHttpUrl()
        val cookies = WebViewChallengeSolver.parseCookies(url, "PHPSESSID=abc; bunkerweb_clearance=ok")
        assertEquals(listOf("PHPSESSID", "bunkerweb_clearance"), cookies.map { it.name })
        assertTrue(cookies.all { it.domain == "mediatheque.villejuif.fr" && !it.persistent })
        assertTrue(WebViewChallengeSolver.parseCookies(url, null).isEmpty())
    }
}
