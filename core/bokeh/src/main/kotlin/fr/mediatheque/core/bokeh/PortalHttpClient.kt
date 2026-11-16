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
 * Résout le défi BunkerWeb à [url] et rend les cookies à garder. [userAgent] doit être celui des requêtes :
 * BunkerWeb lie sa session au User-Agent. [cookies] amorcent la résolution (une session ouverte survit ainsi).
 */
fun interface ChallengeSolver {
    suspend fun solve(url: HttpUrl, userAgent: String, cookies: List<Cookie>): List<Cookie>

    companion object {
        /** Pour les tests et les modules sans WebView : le défi échoue. */
        val Unavailable = ChallengeSolver { _, _, _ -> throw BokehError.ChallengeFailed }
    }
}

/**
 * Transport vers le portail. Il porte la session web (cookie `PHPSESSID`), qui suffit à l'API JSON
 * `/api/user/…` comme aux pages du compte. Les cookies ne vivent qu'en mémoire.
 *
 * Toutes les fonctions sont sûres depuis le thread principal : OkHttp exécute et lit les réponses sur ses threads.
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

    /** Partagé par les requêtes qui tombent en même temps sur le défi : il n'est résolu qu'une fois. */
    private val challengeMutex = Mutex()

    /** Incrémenté à chaque défi résolu : une requête envoyée avant la dernière résolution n'a qu'à être rejouée. */
    @Volatile
    private var challengeGeneration = 0

    // Requêtes

    suspend fun bytes(endpoint: Endpoint): ByteArray {
        val generation = challengeGeneration
        var response = execute(endpoint)
        challengeUrl(response)?.let { url ->
            // BunkerWeb veut vérifier qu'on est un navigateur : on le résout (sauf si une autre requête
            // l'a déjà fait pendant que celle-ci était en vol), puis on rejoue la requête une fois.
            passChallenge(url, generation)
            response = execute(endpoint)
            if (challengeUrl(response) != null) throw BokehError.ChallengeFailed
        }
        if (response.finalUrl.encodedPath.startsWith("/auth/login") && endpoint.pathSegments.firstOrNull() != "auth") {
            // Une page protégée nous a renvoyés vers la connexion.
            throw BokehError.Unauthorized
        }
        if (response.status !in 200..399) throw BokehError.Http(response.status)
        if (response.isJson || endpoint.isAjax) {
            BokehError.fromJson(response.body.decodeToString())?.let { throw it }
        }
        return response.body
    }

    suspend fun string(endpoint: Endpoint): String = bytes(endpoint).decodeToString()

    /** Lit la réponse avec [parse] ; une erreur de lecture devient [BokehError.UnexpectedFormat]. */
    suspend fun <T> decode(endpoint: Endpoint, parse: (String) -> T): T {
        val text = string(endpoint)
        return try {
            parse(text)
        } catch (error: BokehError) {
            throw error
        } catch (error: RuntimeException) {
            throw BokehError.UnexpectedFormat("${endpoint.pathSegments.take(3).joinToString("/")} : ${error.message}")
        }
    }

    // Session

    /** Cookies de la session web, à injecter dans une WebView pour la connexion aux services numériques. */
    fun sessionCookies(): List<Cookie> = cookieJar.loadForRequest(baseUrl)

    val hasSessionCookie: Boolean get() = sessionCookies().any { it.name == "PHPSESSID" }

    fun clearSession() = cookieJar.clear()

    // Défi BunkerWeb

    private fun challengeUrl(response: PortalResponse): HttpUrl? = response.finalUrl.takeIf { it.encodedPath == CHALLENGE_PATH }

    private suspend fun passChallenge(url: HttpUrl, generationSeen: Int) {
        challengeMutex.withLock {
            if (challengeGeneration != generationSeen) return
            val cookies = challengeSolver.solve(url, userAgent, cookieJar.loadForRequest(baseUrl))
            cookieJar.saveFromResponse(baseUrl, cookies)
            challengeGeneration++
        }
    }

    // Construction et exécution

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

        /** Corps de formulaire : espaces en `+`, tout sauf les lettres et chiffres ASCII et `-._*` encodé en UTF-8. */
        internal fun formEncoded(fields: List<Endpoint.FormField>): String = fields.joinToString("&") {
            "${URLEncoder.encode(it.name, Charsets.UTF_8)}=${URLEncoder.encode(it.value, Charsets.UTF_8)}"
        }
    }
}

/** Cookies en mémoire seulement, comme une session éphémère : rien n'est écrit sur l'appareil. */
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
