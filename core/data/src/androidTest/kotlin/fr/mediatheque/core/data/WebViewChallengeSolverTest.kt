// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.PortalHttpClient
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Cookie
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The solver in a real WebView, against a local stand-in for BunkerWeb: a page whose JavaScript submits a form,
 * answered with a clearance cookie and a redirect out of /challenge, like the real anti-bot.
 */
@RunWith(AndroidJUnit4::class)
class WebViewChallengeSolverTest {
    private val server = MockWebServer()
    private val userAgent = PortalHttpClient.DEFAULT_USER_AGENT
    private val seenUserAgents = mutableListOf<String>()

    private val solver = WebViewChallengeSolver(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun stop() = server.close()

    private fun start(clearance: Boolean) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                synchronized(seenUserAgents) { seenUserAgents += request.headers["User-Agent"].orEmpty() }
                return when {
                    request.method == "GET" && request.url.encodedPath == "/challenge" -> MockResponse.Builder()
                        .addHeader("Content-Type", "text/html")
                        .body(
                            """<html><body><form method="post" action="/challenge"></form><script>document.forms[0].submit()</script></body></html>"""
                        )
                        .build()

                    request.method == "POST" && clearance -> MockResponse.Builder()
                        .code(302)
                        .addHeader("Set-Cookie", "bunkerweb_clearance=ok; Path=/")
                        .addHeader("Location", "/api/user/account")
                        .build()

                    request.method == "POST" -> MockResponse.Builder().code(500).build()

                    else -> MockResponse.Builder().addHeader("Content-Type", "text/html").body("<html>account</html>").build()
                }
            }
        }
        server.start()
    }

    @Test
    fun theClearanceCookieComesBackWithTheSessionCookie() = runBlocking {
        start(clearance = true)
        val url = server.url("/challenge")
        val session = Cookie.Builder().name("PHPSESSID").value("abc").domain(url.host).path("/").build()

        val cookies = solver.solve(url, userAgent, listOf(session))

        assertEquals(setOf("PHPSESSID", "bunkerweb_clearance"), cookies.map { it.name }.toSet())
        assertEquals("ok", cookies.first { it.name == "bunkerweb_clearance" }.value)
        // BunkerWeb ties its session to the User-Agent: the WebView must send OkHttp's.
        assertTrue(synchronized(seenUserAgents) { seenUserAgents.isNotEmpty() && seenUserAgents.all { it == userAgent } })
    }

    @Test
    fun aChallengeThatFailsIsReportedAsSuch() = runBlocking {
        start(clearance = false)
        val error = runCatching { solver.solve(server.url("/challenge"), userAgent, emptyList()) }.exceptionOrNull()
        assertEquals(BokehError.ChallengeFailed, error)
    }
}
