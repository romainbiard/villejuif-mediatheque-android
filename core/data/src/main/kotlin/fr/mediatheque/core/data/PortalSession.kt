// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.Endpoint
import fr.mediatheque.core.bokeh.PortalHttpClient
import fr.mediatheque.core.bokeh.dto.AccountResponseDto
import fr.mediatheque.core.bokeh.parser.HtmlParsers
import fr.mediatheque.core.datastore.ValueStore
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.Patron
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Sign-in, as the screens see it. */
interface SessionRepository {
    /** Explicit sign-in (sign-in screen). The credentials are kept only on success. */
    suspend fun login(credentials: Credentials): Patron

    /** Reopens the session with the stored credentials; `null` if there are none. */
    suspend fun restore(): Patron?

    suspend fun logout()

    /** Asks the portal to email a password reset link. */
    suspend fun requestPasswordReset(cardNumber: String)

    /** Whether credentials are stored on this device. */
    suspend fun hasCredentials(): Boolean
}

/**
 * Opens the web session through the portal's sign-in form, and opens it again when it expires.
 * The session expires quickly: every authenticated call goes through [perform].
 */
@Singleton
class PortalSession @Inject constructor(private val http: PortalHttpClient, private val credentials: ValueStore<Credentials>) :
    SessionRepository {
    /** Only one sign-in at a time, even when several screens notice the expiry together. */
    private val signInMutex = Mutex()

    /** Bumped by each sign-in: a call that failed before the last one only needs to be replayed. */
    @Volatile
    private var sessionGeneration = 0

    override suspend fun login(credentials: Credentials): Patron = signInMutex.withLock {
        val patron = openSession(credentials)
        this.credentials.save(credentials)
        sessionGeneration++
        patron
    }

    override suspend fun restore(): Patron? = signInMutex.withLock {
        val stored = credentials.load() ?: return@withLock null
        reopen(stored).also { sessionGeneration++ }
    }

    override suspend fun logout() {
        signInMutex.withLock {
            runCatching { http.bytes(Endpoint.LOGOUT) }
            http.clearSession()
            credentials.clear()
            sessionGeneration++
        }
    }

    /** The form refuses a submission less than 2 s after it was shown (`minimum_time`), hence the wait. */
    override suspend fun requestPasswordReset(cardNumber: String) {
        val token = HtmlParsers.csrfToken(http.string(Endpoint.LOST_PASSWORD_PAGE))
        delay(2_200.milliseconds)
        http.bytes(Endpoint.lostPassword(cardNumber, token))
    }

    override suspend fun hasCredentials(): Boolean = credentials.load() != null

    /** Runs [operation]; if the session has expired, signs in again once and retries. */
    suspend fun <T> perform(operation: suspend () -> T): T {
        val generation = sessionGeneration
        return try {
            operation()
        } catch (_: BokehError.Unauthorized) {
            signInAgain(generation)
            operation()
        }
    }

    private suspend fun signInAgain(generationSeen: Int) = signInMutex.withLock {
        // Another call already signed in again while this one was waiting: just replay.
        if (sessionGeneration != generationSeen) return@withLock
        val stored = credentials.load() ?: throw AuthError.NotLoggedIn
        reopen(stored)
        sessionGeneration++
    }

    private suspend fun reopen(stored: Credentials): Patron = try {
        openSession(stored)
    } catch (error: AuthError.InvalidCredentials) {
        // The portal explicitly refused the stored password (changed on the website): forget it, so background
        // refreshes stop signing in with it. The patron signs in again.
        credentials.clear()
        throw error
    }

    private suspend fun openSession(credentials: Credentials): Patron {
        val token = HtmlParsers.csrfToken(http.string(Endpoint.LOGIN_PAGE))
        val answer = http.string(Endpoint.login(credentials.cardNumber, credentials.password, token))
        // Only the portal's explicit message is a refusal. No answer, a timeout or a server error
        // throws before this point and keeps the stored password.
        if (HtmlParsers.loginRejected(answer)) throw AuthError.InvalidCredentials
        return try {
            http.decode(Endpoint.ACCOUNT, AccountResponseDto::parse).account.toPatron()
        } catch (_: BokehError.Unauthorized) {
            throw AuthError.SessionNotOpened
        }
    }
}
