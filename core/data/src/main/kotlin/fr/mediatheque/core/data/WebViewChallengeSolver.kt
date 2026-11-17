// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.ChallengeSolver
import fr.mediatheque.core.bokeh.PortalHttpClient
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The portal sits behind BunkerWeb, whose anti-bot redirects unknown clients to `/challenge`: a JavaScript proof of
 * work that submits itself and, once solved, sets a clearance cookie. OkHttp cannot run it, so an offscreen WebView
 * solves it and hands its cookies back.
 *
 * The WebView runs in a profile of its own, deleted afterwards, so its cookies never mix with the digital services'
 * WebView. On a WebView too old for profiles, the shared cookie store is used and cleared after.
 */
class WebViewChallengeSolver @Inject constructor(@param:ApplicationContext private val context: Context) : ChallengeSolver {
    override suspend fun solve(url: HttpUrl, userAgent: String, cookies: List<Cookie>): List<Cookie> = withContext(Dispatchers.Main) {
        val session = ChallengeSession.open(context, userAgent)
        try {
            withTimeout(TIMEOUT) { session.run(url, cookies) }
        } catch (_: TimeoutCancellationException) {
            throw BokehError.ChallengeFailed
        } finally {
            session.close()
        }
    }

    private class ChallengeSession(
        private val webView: WebView,
        private val cookieManager: CookieManager,
        private val profileName: String?
    ) {
        suspend fun run(url: HttpUrl, cookies: List<Cookie>): List<Cookie> {
            // Seed the web view so an open PHPSESSID survives the round trip.
            for (cookie in cookies) cookieManager.setCookie(url.toString(), cookie.toString())
            val leftChallenge = suspendCancellableCoroutine { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView, pageUrl: String, favicon: Bitmap?) {
                        // The clearance cookie came with the redirect out of /challenge:
                        // no need to load the page the challenge was guarding.
                        val path = pageUrl.toHttpUrlOrNull()?.encodedPath
                        if (path != PortalHttpClient.CHALLENGE_PATH && continuation.isActive) {
                            view.stopLoading()
                            continuation.resume(true)
                        }
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame && continuation.isActive) continuation.resume(false)
                    }

                    // The renderer crashed or was killed for memory: the challenge fails, the app keeps running.
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        if (continuation.isActive) continuation.resume(false)
                        return true
                    }
                }
                webView.loadUrl(url.toString())
            }
            if (!leftChallenge) throw BokehError.ChallengeFailed
            cookieManager.flush()
            return parseCookies(url, cookieManager.getCookie(url.toString()))
        }

        fun close() {
            webView.stopLoading()
            webView.destroy()
            val profile = profileName
            if (profile != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                // Refused while the WebView is still being torn down: the profile is then left empty, never reused.
                runCatching { ProfileStore.getInstance().deleteProfile(profile) }
            } else {
                cookieManager.removeAllCookies(null)
            }
        }

        companion object {
            @SuppressLint("SetJavaScriptEnabled") // The challenge is a JavaScript proof of work, and the page is the portal's.
            fun open(context: Context, userAgent: String): ChallengeSession {
                val webView = WebView(context)
                val profileName: String?
                val cookieManager: CookieManager
                if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                    profileName = "challenge-${UUID.randomUUID()}"
                    WebViewCompat.setProfile(webView, profileName)
                    cookieManager = ProfileStore.getInstance().getOrCreateProfile(profileName).cookieManager
                } else {
                    profileName = null
                    cookieManager = CookieManager.getInstance()
                }
                cookieManager.setAcceptCookie(true)
                webView.settings.apply {
                    javaScriptEnabled = true
                    // BunkerWeb ties its session to the User-Agent: it must be the one OkHttp sends.
                    userAgentString = userAgent
                    allowFileAccess = false
                    allowContentAccess = false
                    // The challenge page is the portal's, over HTTPS: nothing else may load or open.
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    safeBrowsingEnabled = true
                }
                return ChallengeSession(webView, cookieManager, profileName)
            }
        }
    }

    companion object {
        private val TIMEOUT: Duration = 30.seconds

        /** `CookieManager` only gives `name=value` pairs: they become session cookies of the portal's host. */
        internal fun parseCookies(url: HttpUrl, header: String?): List<Cookie> = header.orEmpty()
            .split(';')
            .mapNotNull { pair ->
                val name = pair.substringBefore('=').trim()
                val value = pair.substringAfter('=', missingDelimiterValue = "").trim()
                if (name.isEmpty()) null else Cookie.Builder().name(name).value(value).domain(url.host).path("/").build()
            }
    }
}
