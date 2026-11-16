// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.Endpoint
import fr.mediatheque.core.bokeh.dto.LoansResponseDto
import fr.mediatheque.core.bokeh.testing.FakePortal
import fr.mediatheque.core.bokeh.testing.FakePortal.Response
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.model.Credentials
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A fake portal, the real client and session, and credentials in memory. */
class PortalHarness(signedIn: Boolean = true, respond: (FakePortal.Request) -> Response) : AutoCloseable {
    val portal = FakePortal(respond)
    val http = portal.client()
    val credentials = InMemoryValueStore(if (signedIn) CREDENTIALS else null)
    val session = PortalSession(http, credentials)

    override fun close() = portal.close()

    companion object {
        val CREDENTIALS = Credentials("0012345", "secret")
    }
}

class PortalSessionTest {
    private var harness: PortalHarness? = null

    @After
    fun close() {
        harness?.close()
    }

    private fun harness(signedIn: Boolean = true, respond: (FakePortal.Request) -> Response) =
        PortalHarness(signedIn, respond).also { harness = it }

    /** Loans answer only once the sign-in form has been posted. */
    private fun expiringLoans(rejected: Boolean = false): (FakePortal.Request) -> Response = { request ->
        FakePortal.signIn(request, rejected)
            ?: if (request.path == FakePortal.ACCOUNT_PATH) {
                Response.json("account.json")
            } else if (request.afterSignIn) {
                Response.json("loans.json")
            } else {
                Response.expiredSession
            }
    }

    @Test
    fun signInOpensTheSessionAndKeepsTheCredentials() = runTest {
        val harness = harness(signedIn = false) { FakePortal.signIn(it) ?: Response.json("account.json") }
        val patron = harness.session.login(PortalHarness.CREDENTIALS)

        assertEquals("0012345", patron.cardNumber)
        assertEquals(PortalHarness.CREDENTIALS, harness.credentials.value)
        val form = harness.portal.requests.first { it.isSignIn }.form
        assertEquals("0012345", form["username"])
        assertEquals("secret", form["password"])
        assertFalse(form["csrftoken"].isNullOrEmpty())
    }

    @Test
    fun aRefusedPasswordIsNeverKept() = runTest {
        val harness = harness(signedIn = false) { FakePortal.signIn(it, rejected = true) ?: Response.json("account.json") }
        assertEquals(AuthError.InvalidCredentials, runCatching { harness.session.login(PortalHarness.CREDENTIALS) }.exceptionOrNull())
        assertNull(harness.credentials.value)
    }

    @Test
    fun anExpiredSessionSignsInAgainThenRetries() = runTest {
        val harness = harness(respond = expiringLoans())
        val loans = harness.session.perform { harness.http.decode(Endpoint.LOANS, LoansResponseDto::parse).loans }
        assertEquals(3, loans.size)
        assertEquals(1, harness.portal.signIns)
        // The expired call, then the same call once signed in again.
        assertEquals(2, harness.portal.count("/api/user/loans"))
    }

    @Test
    fun screensExpiringTogetherShareOneSignIn() = runTest {
        val harness = harness(respond = expiringLoans())
        List(3) { async { harness.session.perform { harness.http.decode(Endpoint.LOANS, LoansResponseDto::parse) } } }.awaitAll()
        assertEquals(1, harness.portal.signIns)
    }

    @Test
    fun aStoredPasswordRefusedOnReopenIsForgotten() = runTest {
        val harness = harness(respond = expiringLoans(rejected = true))
        val error = runCatching { harness.session.perform { harness.http.bytes(Endpoint.LOANS) } }.exceptionOrNull()
        assertEquals(AuthError.InvalidCredentials, error)
        // Background refreshes stop signing in with a password the site refuses.
        assertNull(harness.credentials.value)
    }

    @Test
    fun aSessionThatDoesNotOpenKeepsThePassword() = runTest {
        // The form goes through, but the API still says the session is closed: a passing failure.
        val harness = harness { FakePortal.signIn(it) ?: Response.expiredSession }
        val error = runCatching { harness.session.perform { harness.http.bytes(Endpoint.LOANS) } }.exceptionOrNull()
        assertEquals(AuthError.SessionNotOpened, error)
        assertEquals(PortalHarness.CREDENTIALS, harness.credentials.value)
    }

    @Test
    fun withoutStoredCredentialsNothingIsSent() = runTest {
        val harness = harness(signedIn = false) { Response.expiredSession }
        assertNull(harness.session.restore())
        val error = runCatching { harness.session.perform { harness.http.bytes(Endpoint.LOANS) } }.exceptionOrNull()
        assertEquals(AuthError.NotLoggedIn, error)
        // Only the expired call: no sign-in attempt without a password.
        assertEquals(listOf("/api/user/loans"), harness.portal.requests.map { it.path })
    }

    @Test
    fun aPageRedirectingToSignInCountsAsExpired() = runTest {
        // A patron page that redirects to the sign-in form means the same as the API's error.
        val harness = harness { request ->
            FakePortal.signIn(request)
                ?: when {
                    request.path == FakePortal.ACCOUNT_PATH -> Response.json("account.json")
                    request.afterSignIn -> Response.htmlLiteral("<p>Rang 1</p>")
                    else -> Response.redirect("/auth/login")
                }
        }
        val page = harness.session.perform { harness.http.string(Endpoint.holdsPage("100001")) }
        assertTrue(page.contains("Rang 1"))
        assertEquals(1, harness.portal.signIns)
    }

    @Test
    fun signingOutForgetsTheCredentialsAndTheSession() = runTest {
        val harness = harness { Response.htmlLiteral("<html></html>") }
        assertTrue(harness.session.hasCredentials())
        harness.session.logout()
        assertNull(harness.credentials.value)
        assertFalse(harness.http.hasSessionCookie)
        assertEquals(listOf("/auth/logout"), harness.portal.requests.map { it.path })
    }

    @OptIn(ExperimentalCoroutinesApi::class) // Virtual time: the 2.2 s wait does not slow the test down.
    @Test
    fun aPasswordResetWaitsForTheFormsMinimumTime() = runTest {
        val harness = harness(signedIn = false) { request ->
            if (request.method == "GET") Response.html("login.html") else Response.htmlLiteral("<html></html>")
        }
        harness.session.requestPasswordReset("0012345")
        val form = harness.portal.requests.last().form
        assertEquals("0012345", form["lost_username"])
        assertEquals("", form["website"])
        assertTrue(testScheduler.currentTime >= 2_000)
    }
}
