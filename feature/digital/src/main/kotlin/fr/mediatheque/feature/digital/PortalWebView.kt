// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.net.URISyntaxException
import java.util.UUID

/** What the portal WebView tells its screen. */
internal interface PortalWebViewListener {
    fun onProgress(percent: Int)

    fun onNavigationChanged(canGoBack: Boolean)

    /** A partner app took over: the screen says so and offers to close. */
    fun onHandedOffToApp()

    fun onDownloadStarted()

    /** The renderer crashed or was killed for memory: the screen closes, the app keeps running. */
    fun onCrashed()
}

/**
 * The WebView that runs a provider's sign-in with the portal session, and hands off to the partner app as soon as
 * the flow allows.
 *
 * Isolation: with WebView's multi-profile support (WebView 124+), each opening gets its own profile, deleted when it
 * closes, so the provider's session never outlives the screen and never mixes with other WebViews. Without it, the
 * default profile is used and its cookies and storage are cleared on close.
 */
internal class PortalWebViewController private constructor(
    val webView: WebView,
    private val cookieManager: CookieManager,
    private val profileName: String?
) {
    fun load(session: PortalSession) {
        // Each portal cookie is set for its own domain only: none is offered to the provider's hosts.
        for (cookie in session.cookies) {
            val scheme = if (cookie.secure) "https" else "http"
            cookieManager.setCookie("$scheme://${cookie.domain}${cookie.path}", cookie.toString())
        }
        cookieManager.flush()
        webView.loadUrl(session.url)
    }

    fun destroy() {
        webView.stopLoading()
        webView.destroy()
        val profile = profileName
        if (profile != null && WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            // Refused while the WebView is still being torn down: the profile is then left empty and never reused.
            runCatching { ProfileStore.getInstance().deleteProfile(profile) }
        } else {
            cookieManager.removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    private class Client(private val listener: PortalWebViewListener) : WebViewClient() {
        /** App links already offered to their app: if no app took them, they load here without looping. */
        private val triedInApp = mutableSetOf<Uri>()

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url
            val decision = PortalLinkPolicy.decide(url.scheme, url.host, url.path, request.isForMainFrame, request.hasGesture())
            return when (decision) {
                PortalLinkPolicy.Decision.LOAD -> false

                PortalLinkPolicy.Decision.BLOCK -> true

                PortalLinkPolicy.Decision.EXTERNAL -> {
                    start(view.context, Intent(Intent.ACTION_VIEW, url).addCategory(Intent.CATEGORY_BROWSABLE))
                    true
                }

                PortalLinkPolicy.Decision.TRY_PARTNER_APP_LINK -> {
                    if (!triedInApp.add(url) || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
                    // Only an app that verified the link, never a browser: otherwise the page loads here.
                    val intent = Intent(Intent.ACTION_VIEW, url)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_REQUIRE_NON_BROWSER)
                    if (start(view.context, intent)) {
                        listener.onHandedOffToApp()
                        true
                    } else {
                        false
                    }
                }

                PortalLinkPolicy.Decision.PARTNER_APP_ONLY -> {
                    openPartnerApp(view, url.toString())
                    true
                }
            }
        }

        /**
         * An `intent:` or custom-scheme link, to a partner app only. The intent is rebuilt so a page cannot aim it
         * at a component of its choosing: no component, no selector, no flags (no URI permission grants), the
         * browsable category, and a target resolved among the partner packages, exported, never this app.
         */
        private fun openPartnerApp(view: WebView, url: String) {
            val context = view.context
            val intent = try {
                if (url.startsWith("intent:", ignoreCase = true)) {
                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                } else {
                    Intent(Intent.ACTION_VIEW, url.toUri())
                }
            } catch (_: URISyntaxException) {
                return
            }
            val fallback = intent.getStringExtra(BROWSER_FALLBACK_URL)
            intent.component = null
            intent.selector = null
            intent.flags = 0
            intent.clipData = null
            intent.addCategory(Intent.CATEGORY_BROWSABLE)
            val target = context.packageManager.resolveActivity(intent, 0)?.activityInfo
            val allowed = target != null &&
                target.exported &&
                target.packageName in DigitalCatalog.partnerPackages &&
                target.packageName != context.packageName
            if (allowed) {
                intent.setPackage(target.packageName)
                if (start(context, intent)) {
                    listener.onHandedOffToApp()
                    return
                }
            }
            // No partner app for it: the page's own web fallback, if it is one.
            if (fallback != null && URLUtil.isNetworkUrl(fallback)) view.loadUrl(fallback)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
            listener.onNavigationChanged(view.canGoBack())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame) listener.onProgress(DONE)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            listener.onCrashed()
            return true
        }

        private fun start(context: Context, intent: Intent): Boolean = try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    companion object {
        private const val DONE = 100
        private const val BROWSER_FALLBACK_URL = "browser_fallback_url"

        @SuppressLint("SetJavaScriptEnabled") // The providers' sign-in pages need JavaScript; no JavaScript interface.
        fun create(context: Context, listener: PortalWebViewListener): PortalWebViewController {
            val webView = WebView(context)
            val profileName: String?
            val cookieManager: CookieManager
            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                profileName = "portal-${UUID.randomUUID()}"
                WebViewCompat.setProfile(webView, profileName)
                cookieManager = ProfileStore.getInstance().getOrCreateProfile(profileName).cookieManager
            } else {
                profileName = null
                cookieManager = CookieManager.getInstance()
            }
            cookieManager.setAcceptCookie(true)
            // The sign-in goes from the portal to the provider and back: third-party cookies, in this WebView only.
            cookieManager.setAcceptThirdPartyCookies(webView, true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
                javaScriptCanOpenWindowsAutomatically = false
                // Links opening a new window load in this one.
                setSupportMultipleWindows(false)
            }
            webView.webViewClient = Client(listener)
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) = listener.onProgress(newProgress)
            }
            webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                // A borrowed e-book (LCP license, PDF): to the system's downloads, with the provider's session.
                if (!URLUtil.isNetworkUrl(url)) return@setDownloadListener
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(url.toUri())
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                cookieManager.getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                context.getSystemService<DownloadManager>()?.enqueue(request)
                listener.onDownloadStarted()
            }
            return PortalWebViewController(webView, cookieManager, profileName)
        }
    }
}
