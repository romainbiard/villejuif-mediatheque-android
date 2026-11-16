// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.data

import fr.mediatheque.core.bokeh.BokehError
import fr.mediatheque.core.bokeh.Endpoint
import fr.mediatheque.core.bokeh.PortalHttpClient
import fr.mediatheque.core.bokeh.parser.HtmlParsers
import javax.inject.Inject
import okhttp3.Cookie
import okhttp3.HttpUrl

/** Digital services: each `/modules/{slug}` page carries the provider's sign-in (SSO) URL. */
enum class DigitalService(val slug: String) {
    ARTE_VOD("arte-vod"),
    CAFEYN("cafeyn"),
    IZNEO("cvs2"),
    NUMILOG("numilog"),
    TOUT_APPRENDRE("tout-apprendre"),
    EUREKA("bibliondemand")
}

interface DigitalRepository {
    /**
     * The provider's sign-in URL, single-use and short-lived: resolve it when the patron taps, never in advance.
     * ⚠️ It may carry personal data (Arte): never log it.
     */
    suspend fun target(service: DigitalService): HttpUrl

    /** The web session's cookies, to inject into the WebView that runs the sign-in. */
    fun sessionCookies(): List<Cookie>
}

class PortalDigitalRepository @Inject constructor(private val http: PortalHttpClient, private val session: PortalSession) :
    DigitalRepository {
    override suspend fun target(service: DigitalService): HttpUrl = session.perform {
        HtmlParsers.ssoTarget(http.string(Endpoint.digitalModule(service.slug)))
            ?: throw BokehError.UnexpectedFormat("No SSO URL for ${service.slug}")
    }

    override fun sessionCookies(): List<Cookie> = http.sessionCookies()
}
