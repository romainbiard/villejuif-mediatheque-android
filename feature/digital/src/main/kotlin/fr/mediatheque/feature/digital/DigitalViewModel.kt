// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.mediatheque.core.data.DigitalRepository
import fr.mediatheque.core.data.LoadError
import fr.mediatheque.core.data.SessionStore
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Cookie

/**
 * A session opened with a provider: the sign-in URL and the portal's cookies, for the WebView.
 * Kept in memory only, never in a route or a saved state: the URL is single-use and may carry personal data (Arte).
 */
class PortalSession(val offer: DigitalOffer, val url: String, val cookies: List<Cookie>) {
    override fun toString(): String = "PortalSession(${offer.id})"
}

/** Where a service is used: the line under its name. */
sealed interface ServiceAccess {
    data object AppInstalled : ServiceAccess

    /** The app is required and not installed yet. */
    data object InApp : ServiceAccess

    /** Borrowed on the web, read in the named app. */
    data class Reader(val appName: String) : ServiceAccess

    data object Web : ServiceAccess
}

sealed interface DigitalMessage {
    data object NotSignedIn : DigitalMessage

    data object Suspended : DigitalMessage

    data object Offline : DigitalMessage

    data class Failed(val offer: DigitalOffer) : DigitalMessage

    data object StoreUnavailable : DigitalMessage
}

data class DigitalUiState(
    val installedPackages: Set<String> = emptySet(),
    /** The offer whose sign-in URL is being resolved. */
    val resolving: String? = null,
    val portal: PortalSession? = null,
    /** The "install the app" sheet. */
    val installOffer: DigitalOffer? = null,
    /** The app was just installed from here: "the app is installed" message. */
    val justInstalled: DigitalOffer? = null,
    val message: DigitalMessage? = null
) {
    fun isInstalled(app: PartnerApp): Boolean = app.packageName in installedPackages

    fun access(offer: DigitalOffer): ServiceAccess {
        val app = offer.app ?: return ServiceAccess.Web
        return when (app.role) {
            PartnerApp.Role.REQUIRED -> if (isInstalled(app)) ServiceAccess.AppInstalled else ServiceAccess.InApp
            PartnerApp.Role.READER -> ServiceAccess.Reader(app.name)
            PartnerApp.Role.COMPANION -> ServiceAccess.Web
        }
    }

    /** The primary button offers to install rather than open. */
    fun needsInstall(offer: DigitalOffer): Boolean {
        val app = offer.app ?: return false
        return app.role == PartnerApp.Role.REQUIRED && !isInstalled(app)
    }

    /** "Connect the app with my card" only makes sense for a required app already installed. */
    fun canConnectApp(offer: DigitalOffer): Boolean = access(offer) == ServiceAccess.AppInstalled
}

/** The digital tab: a launcher for the providers' apps and sites, not a copy of their catalogs. */
@HiltViewModel
class DigitalViewModel @Inject constructor(
    private val digital: DigitalRepository,
    private val apps: InstalledApps,
    sessionStore: SessionStore
) : ViewModel() {
    val offers: List<DigitalOffer> = DigitalCatalog.offers

    /** Signed in or not, and whether digital access is suspended on the account. */
    val session: StateFlow<SessionStore.State> = sessionStore.state

    private val _state = MutableStateFlow(DigitalUiState())
    val state: StateFlow<DigitalUiState> = _state.asStateFlow()

    /** The service whose app the patron went to get: opened as soon as it is there. */
    private var pendingInstall: String? = null

    init {
        refreshInstalledApps()
        // Signed out or locked again: the provider's page, opened with the patron's session, closes at once.
        viewModelScope.launch {
            session.collect { if (it !is SessionStore.State.LoggedIn) portalClosed() }
        }
    }

    /** On resume (back from the Play Store or the settings): apps may have come or gone. */
    fun refreshInstalledApps() {
        val installed = offers.mapNotNull { it.app?.packageName }.filter(apps::isInstalled).toSet()
        _state.update { it.copy(installedPackages = installed) }
        val pending = pendingInstall?.let(DigitalCatalog::offer) ?: return
        val app = pending.app ?: return
        if (app.packageName !in installed) return
        pendingInstall = null
        _state.update { it.copy(justInstalled = pending) }
        // First launch: through the portal, so the app receives the patron's session.
        open(pending)
    }

    /** A card's main button: install the app if it is missing, open it if it is there, otherwise the web. */
    fun primaryAction(offer: DigitalOffer) {
        val state = _state.value
        val app = offer.app
        when {
            state.needsInstall(offer) -> _state.update { it.copy(installOffer = offer) }

            // The portal's sign-in does not always open the app (Arte): launch the app itself, which keeps its session.
            app != null && app.role == PartnerApp.Role.REQUIRED && state.isInstalled(app) && apps.launch(app.packageName) -> Unit

            else -> open(offer)
        }
    }

    /** "Connect the app with my card": the sign-in through the portal, for a first time or a lost session. */
    fun connectThroughPortal(offer: DigitalOffer) = open(offer)

    /** "Get the app": its Play Store page. */
    fun getApp(offer: DigitalOffer) {
        val app = offer.app ?: return
        _state.update { it.copy(installOffer = null) }
        if (apps.openStore(app.packageName)) {
            pendingInstall = offer.id
        } else {
            _state.update { it.copy(message = DigitalMessage.StoreUnavailable) }
        }
    }

    /** "Continue in the browser": the service on the web, without the app. */
    fun continueOnWeb(offer: DigitalOffer) {
        _state.update { it.copy(installOffer = null) }
        open(offer)
    }

    fun dismissInstallSheet() = _state.update { it.copy(installOffer = null) }

    fun installedMessageShown() = _state.update { it.copy(justInstalled = null) }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun portalClosed() = _state.update { it.copy(portal = null) }

    /** The sign-in URL is single-use and short-lived: resolved when the patron taps, never in advance. */
    fun open(offer: DigitalOffer) {
        if (_state.value.resolving != null) return
        val patron = (session.value as? SessionStore.State.LoggedIn)?.patron
        if (patron == null) {
            _state.update { it.copy(message = DigitalMessage.NotSignedIn) }
            return
        }
        if (patron.permissions.digitalResourcesForbidden) {
            _state.update { it.copy(message = DigitalMessage.Suspended) }
            return
        }
        _state.update { it.copy(resolving = offer.id, message = null) }
        viewModelScope.launch {
            try {
                val url = digital.target(offer.service)
                val portal = PortalSession(offer, url.toString(), digital.sessionCookies())
                _state.update { it.copy(portal = portal) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                val message = when (LoadError.from(error)) {
                    LoadError.NotLoggedIn -> DigitalMessage.NotSignedIn
                    LoadError.Offline -> DigitalMessage.Offline
                    else -> DigitalMessage.Failed(offer)
                }
                _state.update { it.copy(message = message) }
            } finally {
                _state.update { it.copy(resolving = null) }
            }
        }
    }
}
