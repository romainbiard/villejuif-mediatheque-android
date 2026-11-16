// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import fr.mediatheque.core.bokeh.dto.AccountResponseDto
import fr.mediatheque.core.bokeh.testing.Fixture
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cookie
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Le client contre un faux portail : défi BunkerWeb, session expirée, erreurs de l'API, cookies. */
class PortalHttpClientTest {
    private val server = MockWebServer()

    @Before
    fun start() = server.start()

    @After
    fun stop() = server.close()

    private fun client(solver: ChallengeSolver = ChallengeSolver.Unavailable) =
        PortalHttpClient(baseUrl = server.url("/"), challengeSolver = solver)

    private fun json(body: String) = MockResponse.Builder().addHeader("Content-Type", "application/json").body(body).build()

    private fun html(body: String) = MockResponse.Builder().addHeader("Content-Type", "text/html; charset=utf-8").body(body).build()

    private fun redirect(to: String) = MockResponse.Builder().code(302).addHeader("Location", to).build()

    private fun route(handler: (RecordedRequest) -> MockResponse) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = handler(request)
        }
    }

    @Test
    fun theChallengeIsSolvedOnceForConcurrentRequestsThenReplayed() = runTest {
        val solves = AtomicInteger()
        route { request ->
            val cleared = request.headers["Cookie"].orEmpty().contains("bunkerweb_clearance=ok")
            when {
                request.url.encodedPath == "/challenge" -> html("<html>défi</html>")
                !cleared -> redirect("/challenge")
                else -> json(Fixture.string("account.json"))
            }
        }
        val portal = client { url, userAgent, _ ->
            solves.incrementAndGet()
            assertEquals(PortalHttpClient.DEFAULT_USER_AGENT, userAgent)
            listOf(Cookie.Builder().name("bunkerweb_clearance").value("ok").domain(url.host).path("/").build())
        }

        val accounts = List(3) { async { portal.decode(Endpoint.ACCOUNT, AccountResponseDto::parse) } }.awaitAll()

        assertEquals(1, solves.get())
        assertTrue(accounts.all { it.account.login == "0012345" })
    }

    @Test
    fun aChallengeThatComesBackFails() = runTest {
        route { request -> if (request.url.encodedPath == "/challenge") html("défi") else redirect("/challenge") }
        val portal = client { url, _, _ -> listOf(Cookie.Builder().name("x").value("y").domain(url.host).build()) }
        val error = runCatching { portal.string(Endpoint.ACCOUNT) }.exceptionOrNull()
        assertEquals(BokehError.ChallengeFailed, error)
    }

    @Test
    fun aRedirectToSignInMeansTheSessionExpired() = runTest {
        route { request -> if (request.url.encodedPath.startsWith("/auth/login")) html("connexion") else redirect("/auth/login") }
        val error = runCatching { client().string(Endpoint.holdsPage("100001")) }.exceptionOrNull()
        assertEquals(BokehError.Unauthorized, error)
        // La page de connexion elle-même n'est pas une session expirée.
        assertEquals("connexion", client().string(Endpoint.LOGIN_PAGE))
    }

    @Test
    fun apiAndHttpErrorsAreTyped() = runTest {
        route { request ->
            when (request.url.encodedPath) {
                "/api/user/loans" -> json(Fixture.string("api-error.json"))
                "/recherche/reserver/record_id/1/render/popup" -> html("""{"error":"not_holdable","message":"Non réservable"}""")
                else -> MockResponse.Builder().code(500).build()
            }
        }
        val portal = client()
        assertEquals(BokehError.Unauthorized, runCatching { portal.string(Endpoint.LOANS) }.exceptionOrNull())
        // Un popup AJAX répond en JSON même quand le site annonce du HTML.
        assertEquals(
            BokehError.Api("not_holdable", "Non réservable"),
            runCatching {
                portal.string(Endpoint.holdOptions(1))
            }.exceptionOrNull()
        )
        assertEquals(BokehError.Http(500), runCatching { portal.string(Endpoint.HOLDS) }.exceptionOrNull())
    }

    @Test
    fun anUnreadableAnswerIsAnUnexpectedFormat() = runTest {
        route { json("""{"account":{"login":null}}""") }
        val error = runCatching { client().decode(Endpoint.ACCOUNT, AccountResponseDto::parse) }.exceptionOrNull()
        assertTrue(error is BokehError.UnexpectedFormat)
    }

    @Test
    fun theSessionCookieIsKeptInMemoryAndCleared() = runTest {
        route { html("ok").newBuilder().addHeader("Set-Cookie", "PHPSESSID=abc; Path=/").build() }
        val portal = client()
        assertFalse(portal.hasSessionCookie)
        portal.string(Endpoint.LOGIN_PAGE)
        assertTrue(portal.hasSessionCookie)
        portal.clearSession()
        assertFalse(portal.hasSessionCookie)
    }
}
