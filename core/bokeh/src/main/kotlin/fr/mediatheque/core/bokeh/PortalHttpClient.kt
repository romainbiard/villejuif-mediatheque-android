// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.bokeh

import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Solves the BunkerWeb challenge at [url] and returns the cookies to keep. [userAgent] must be the one used by requests:
 * BunkerWeb ties its session to the User-Agent. [cookies] seed the solving (so an open session survives).
 */
fun interface ChallengeSolver {
    suspend fun solve(url: HttpUrl, userAgent: String, cookies: List<Cookie>): List<Cookie>

    companion object {
        /** For tests and modules without a WebView: the challenge fails. */
        val Unavailable = ChallengeSolver { _, _, _ -> throw BokehError.ChallengeFailed }
    }
}

/**
 * Transport to the portal. It carries the web session (`PHPSESSID` cookie), which is enough for the `/api/user/...`
 * JSON API as well as for the account pages. Cookies live in memory only.
 *
 * All functions are safe to call from the main thread: OkHttp executes requests and reads responses on its own threads.
 */
class PortalHttpClient(
    val baseUrl: HttpUrl = VILLEJUIF_BASE_URL,
    val userAgent: String = DEFAULT_USER_AGENT,
    private val challengeSolver: ChallengeSolver = ChallengeSolver.Unavailable,
    okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val cookieJar = MemoryCookieJar()
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .cookieJar(cookieJar)
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Shared by requests that hit the challenge at the same time: it is solved only once. */
    private val challengeMutex = Mutex()

    /** Incremented each time a challenge is solved: a request sent before the last solve only needs to be replayed. */
    @Volatile
    private var challengeGeneration = 0

    // Requests

    suspend fun bytes(endpoint: Endpoint): ByteArray {
        val generation = challengeGeneration
        var response = execute(endpoint)
        challengeUrl(response)?.let { url ->
            // BunkerWeb wants to verify that we are a browser: solve it (unless another request
            // already did so while this one was in flight), then replay the request once.
            passChallenge(url, generation)
            response = execute(endpoint)
            if (challengeUrl(response) != null) throw BokehError.ChallengeFailed
        }
        if (response.finalUrl.encodedPath.startsWith("/auth/login") && endpoint.pathSegments.firstOrNull() != "auth") {
            // A protected page redirected us to the login.
            throw BokehError.Unauthorized
        }
        if (response.status !in 200..399) throw BokehError.Http(response.status)
        if (response.isJson || endpoint.isAjax) {
            BokehError.fromJson(response.body.decodeToString())?.let { throw it }
        }
        return response.body
    }

    suspend fun string(endpoint: Endpoint): String = bytes(endpoint).decodeToString()

    /** Reads the response with [parse]; a read error becomes [BokehError.UnexpectedFormat]. */
    suspend fun <T> decode(endpoint: Endpoint, parse: (String) -> T): T {
        val text = string(endpoint)
        return try {
            parse(text)
        } catch (error: BokehError) {
            throw error
        } catch (error: RuntimeException) {
            throw BokehError.UnexpectedFormat("${endpoint.pathSegments.take(3).joinToString("/")}: ${error.message}")
        }
    }

    // Session

    /** Web session cookies, to inject into a WebView for signing in to digital services. */
    fun sessionCookies(): List<Cookie> = cookieJar.loadForRequest(baseUrl)

    val hasSessionCookie: Boolean get() = sessionCookies().any { it.name == "PHPSESSID" }

    fun clearSession() = cookieJar.clear()

    // BunkerWeb challenge

    private fun challengeUrl(response: PortalResponse): HttpUrl? = response.finalUrl.takeIf { it.encodedPath == CHALLENGE_PATH }

    private suspend fun passChallenge(url: HttpUrl, generationSeen: Int) {
        challengeMutex.withLock {
            if (challengeGeneration != generationSeen) return
            val cookies = challengeSolver.solve(url, userAgent, cookieJar.loadForRequest(baseUrl))
            cookieJar.saveFromResponse(baseUrl, cookies)
            challengeGeneration++
        }
    }

    // Building and execution

    internal fun request(endpoint: Endpoint): Request {
        val builder = Request.Builder()
            .url(endpoint.url(baseUrl))
            .header("User-Agent", userAgent)
            .header("Accept-Language", "fr-FR,fr;q=0.9")
        if (endpoint.isAjax) builder.header("X-Requested-With", "XMLHttpRequest")
        if (endpoint.method == Endpoint.Method.POST) {
            builder
                .header("Origin", baseUrl.toString().removeSuffix("/"))
                .post(formEncoded(endpoint.form).toRequestBody(FORM_MEDIA_TYPE))
        }
        return builder.build()
    }

    private suspend fun execute(endpoint: Endpoint): PortalResponse = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request(endpoint))
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = continuation.resumeWithException(e)

                override fun onResponse(call: Call, response: Response) {
                    val result = try {
                        response.use { PortalResponse.from(it) }
                    } catch (error: IOException) {
                        continuation.resumeWithException(error)
                        return
                    }
                    continuation.resume(result)
                }
            }
        )
    }

    private class PortalResponse(val status: Int, val finalUrl: HttpUrl, val isJson: Boolean, val body: ByteArray) {
        companion object {
            fun from(response: Response) = PortalResponse(
                status = response.code,
                finalUrl = response.request.url,
                isJson = response.body.contentType()?.subtype == "json",
                body = response.body.bytes()
            )
        }
    }

    companion object {
        val VILLEJUIF_BASE_URL: HttpUrl = "https://mediatheque.villejuif.fr/".toHttpUrl()
        const val DEFAULT_USER_AGENT = "MediathequesVillejuif-Android/1.0"
        const val CHALLENGE_PATH = "/challenge"
        private const val TIMEOUT_SECONDS = 20L
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()

        /** Form body: spaces as `+`, everything except ASCII letters and digits and `-._*` encoded in UTF-8. */
        internal fun formEncoded(fields: List<Endpoint.FormField>): String = fields.joinToString("&") {
            "${URLEncoder.encode(it.name, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
        }
    }
}

/** In-memory cookies only, like an ephemeral session: nothing is written to the device. */
private class MemoryCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        for (cookie in cookies) {
            this.cookies.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
            this.cookies += cookie
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt < now }
        return cookies.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() = cookies.clear()
}
