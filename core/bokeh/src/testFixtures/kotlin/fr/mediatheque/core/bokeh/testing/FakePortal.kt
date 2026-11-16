// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh.testing

import fr.mediatheque.core.bokeh.ChallengeSolver
import fr.mediatheque.core.bokeh.PortalHttpClient
import java.net.URLDecoder
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest

/**
 * A fake portal on a local server: the real client talks to it as to the real site, and it answers with the
 * captured pages. Each test describes the portal's behavior in [respond].
 */
class FakePortal(private val respond: (Request) -> Response) : AutoCloseable {
    /** A request as the portal received it: method, path and form fields. */
    data class Request(
        val method: String,
        val path: String,
        val form: Map<String, String>,
        /** The requests received before this one, oldest first: lets a page change after a sign-in. */
        val earlier: List<Request>
    ) {
        val isSignIn: Boolean get() = method == "POST" && path == SIGN_IN_PATH

        /** Whether the sign-in form was posted before this request: the session is open. */
        val afterSignIn: Boolean get() = earlier.any { it.isSignIn }
    }

    /** What the fake portal answers. [redirectTo] sends a 302 to that path instead (an expired session). */
    class Response(val status: Int = 200, val body: String = "", val contentType: String = "text/html", val redirectTo: String? = null) {
        companion object {
            fun json(fixture: String) = Response(body = Fixture.string(fixture), contentType = "application/json")

            fun jsonLiteral(text: String) = Response(body = text, contentType = "application/json")

            fun html(fixture: String) = Response(body = Fixture.string(fixture))

            fun htmlLiteral(text: String) = Response(body = text)

            fun calendar(fixture: String) = Response(body = Fixture.string(fixture), contentType = "text/calendar")

            /** The JSON API's answer when the web session has expired. */
            val expiredSession: Response get() = json("api-error.json")

            fun redirect(path: String) = Response(status = 302, redirectTo = path)
        }
    }

    private val server = MockWebServer()
    private val received = mutableListOf<Request>()

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val portalRequest = synchronized(received) {
                    Request(
                        method = request.method,
                        path = request.url.encodedPath,
                        form = form(request.body?.utf8().orEmpty()),
                        earlier = received.toList()
                    ).also { received += it }
                }
                val response = respond(portalRequest)
                return MockResponse.Builder()
                    .code(response.status)
                    .addHeader("Content-Type", "${response.contentType}; charset=utf-8")
                    .apply { response.redirectTo?.let { addHeader("Location", it) } }
                    .body(response.body)
                    .build()
            }
        }
        server.start()
    }

    /** Requests received so far, in order. */
    val requests: List<Request> get() = synchronized(received) { received.toList() }

    /** How many requests went to [path]. */
    fun count(path: String): Int = requests.count { it.path == path }

    val signIns: Int get() = requests.count { it.isSignIn }

    /** The real transport, wired to this fake portal. */
    fun client(challengeSolver: ChallengeSolver = ChallengeSolver.Unavailable) =
        PortalHttpClient(baseUrl = server.url("/"), challengeSolver = challengeSolver)

    override fun close() = server.close()

    companion object {
        const val SIGN_IN_PATH = "/auth/login"
        const val ACCOUNT_PATH = "/api/user/account"

        /** Answers the sign-in form like the portal; `null` when the request is something else. */
        fun signIn(request: Request, rejected: Boolean = false): Response? {
            if (request.path != SIGN_IN_PATH) return null
            if (request.method == "GET") return Response.html("login.html")
            return if (rejected) {
                Response.html(
                    "login-rejected.html"
                )
            } else {
                Response.htmlLiteral("<html><body><h1>Mon compte</h1></body></html>")
            }
        }

        private fun form(body: String): Map<String, String> = body.split('&')
            .mapNotNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2) URLDecoder.decode(parts[0], Charsets.UTF_8) to URLDecoder.decode(parts[1], Charsets.UTF_8) else null
            }
            .toMap()
    }
}
