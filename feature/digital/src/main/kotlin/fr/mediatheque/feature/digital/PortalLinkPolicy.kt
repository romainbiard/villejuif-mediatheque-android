// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

/**
 * What the portal WebView does with a navigation. The rule follows the iOS app: a provider page, an ad or an iframe
 * must not be able to open any app on the device with parameters of its choosing.
 * - Web pages load in the WebView, except the providers' app links, offered once to their app.
 * - `intent:` and custom schemes reach a partner app only, from the main frame only.
 * - `mailto:` and `tel:` only after the patron tapped a link.
 * - Anything else is blocked.
 */
object PortalLinkPolicy {
    enum class Decision {
        LOAD,

        /** A provider's app link: try its app (never a browser), and load it here if no app takes it. */
        TRY_PARTNER_APP_LINK,

        /** An `intent:` or custom-scheme URL: to a partner app only, after sanitizing. */
        PARTNER_APP_ONLY,

        /** `mailto:` or `tel:` after a tap: to the system. */
        EXTERNAL,
        BLOCK
    }

    private val webSchemes = setOf("http", "https", "about", "blob", "data")

    fun decide(scheme: String?, host: String?, path: String?, isMainFrame: Boolean, hasGesture: Boolean): Decision {
        val lowerScheme = scheme?.lowercase() ?: return Decision.BLOCK
        if (lowerScheme in webSchemes) {
            return if (isMainFrame && isProviderAppLink(host, path)) Decision.TRY_PARTNER_APP_LINK else Decision.LOAD
        }
        if (!isMainFrame) return Decision.BLOCK
        return when (lowerScheme) {
            "mailto", "tel" -> if (hasGesture) Decision.EXTERNAL else Decision.BLOCK
            "javascript", "file", "content" -> Decision.BLOCK
            else -> Decision.PARTNER_APP_ONLY
        }
    }

    /** The providers' known app links: they open the app already signed in. */
    fun isProviderAppLink(host: String?, path: String?): Boolean {
        val lowerHost = host?.lowercase() ?: return false
        val lowerPath = path?.lowercase().orEmpty()
        return when {
            lowerHost == "cafeyn.co" || lowerHost.endsWith(".cafeyn.co") -> "/externallogin/" in lowerPath
            lowerHost == "app.mozaik.co" -> lowerPath.startsWith("/autologin.php")
            else -> false
        }
    }
}
