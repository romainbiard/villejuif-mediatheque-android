// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.digital

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.DigitalRepository
import fr.mediatheque.core.data.DigitalService
import fr.mediatheque.core.data.SessionRepository
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.data.SyncNotifier
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.datastore.SettingsStore
import fr.mediatheque.core.domain.Reminder
import fr.mediatheque.core.model.Credentials
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.testing.MainDispatcherRule
import fr.mediatheque.core.testing.TestClock
import java.io.File
import java.net.UnknownHostException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DigitalViewModelTest {
    @get:Rule
    val main = MainDispatcherRule(StandardTestDispatcher())

    @get:Rule
    val folder = TemporaryFolder()

    private val clock = TestClock(Paris.instant(2026, 11, 17, 12))

    private object SilentNotifier : SyncNotifier {
        override suspend fun scheduleLoanReminders(reminders: List<Reminder>) = Unit

        override suspend fun setAgendaDigest(enabled: Boolean) = Unit

        override suspend fun notifyHoldReady(reminder: Reminder) = Unit

        override suspend fun accountChanged() = Unit

        override suspend fun clearAll() = Unit
    }

    private class FakeSession(private val patron: Patron?) : SessionRepository {
        override suspend fun login(credentials: Credentials): Patron = patron ?: SampleData.patron

        override suspend fun restore(): Patron? = patron

        override suspend fun logout() = Unit

        override suspend fun requestPasswordReset(cardNumber: String) = Unit

        override suspend fun hasCredentials(): Boolean = patron != null
    }

    private class FakeApps(val installed: MutableSet<String> = mutableSetOf()) : InstalledApps {
        val launched = mutableListOf<String>()
        val storePages = mutableListOf<String>()

        override fun isInstalled(packageName: String) = packageName in installed

        override fun launch(packageName: String): Boolean {
            launched += packageName
            return packageName in installed
        }

        override fun openStore(packageName: String): Boolean {
            storePages += packageName
            return true
        }
    }

    private class FakeDigital : DigitalRepository {
        var offline = false
        val resolved = mutableListOf<DigitalService>()

        override suspend fun target(service: DigitalService): HttpUrl {
            if (offline) throw UnknownHostException()
            resolved += service
            return "https://provider.example/sso?token=abc".toHttpUrl()
        }

        override fun sessionCookies(): List<Cookie> = emptyList()
    }

    private suspend fun TestScope.viewModel(
        patron: Patron? = SampleData.patron,
        apps: FakeApps = FakeApps(),
        digital: FakeDigital = FakeDigital()
    ): DigitalViewModel {
        val settings = SettingsStore(
            settings = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
            device = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "device.preferences_pb") }
        )
        val sync = AccountSync(DemoAccountRepository(), InMemoryValueStore(), InMemoryValueStore(), SilentNotifier, settings, clock)
        val store = SessionStore(FakeSession(patron), InMemoryValueStore(), settings, sync, clock)
        store.restore()
        return DigitalViewModel(digital, apps, store)
    }

    private val films = DigitalCatalog.offer("films")!!
    private val filmsPackage = films.app!!.packageName
    private val comics = DigitalCatalog.offer("bd")!!
    private val ebooks = DigitalCatalog.offer("ebooks")!!

    @Test
    fun aRequiredAppMissingIsOfferedForInstall() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.state.value
        assertTrue(state.needsInstall(films))
        assertEquals(ServiceAccess.InApp, state.access(films))
        assertEquals(ServiceAccess.Reader("Louise Reader"), state.access(ebooks))
        assertEquals(ServiceAccess.Web, state.access(comics))

        viewModel.primaryAction(films)
        assertEquals(films, viewModel.state.value.installOffer)
    }

    @Test
    fun anInstalledAppOpensDirectly() = runTest {
        val apps = FakeApps(mutableSetOf(filmsPackage))
        val digital = FakeDigital()
        val viewModel = viewModel(apps = apps, digital = digital)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.canConnectApp(films))
        viewModel.primaryAction(films)
        advanceUntilIdle()
        assertEquals(listOf(filmsPackage), apps.launched)
        assertTrue(digital.resolved.isEmpty())
    }

    @Test
    fun aWebServiceOpensThePortalSession() = runTest {
        val digital = FakeDigital()
        val viewModel = viewModel(digital = digital)
        advanceUntilIdle()
        viewModel.primaryAction(comics)
        advanceUntilIdle()
        val portal = viewModel.state.value.portal!!
        assertEquals(comics, portal.offer)
        assertEquals(listOf(DigitalService.IZNEO), digital.resolved)
        // The single-use URL never shows in logs.
        assertFalse("token" in portal.toString())
        assertNull(viewModel.state.value.resolving)

        viewModel.portalClosed()
        assertNull(viewModel.state.value.portal)
    }

    @Test
    fun afterInstallingFromTheStoreTheServiceOpensThroughThePortal() = runTest {
        val apps = FakeApps()
        val digital = FakeDigital()
        val viewModel = viewModel(apps = apps, digital = digital)
        advanceUntilIdle()
        viewModel.getApp(films)
        assertEquals(listOf(filmsPackage), apps.storePages)

        apps.installed += filmsPackage
        viewModel.refreshInstalledApps()
        advanceUntilIdle()
        assertEquals(films, viewModel.state.value.justInstalled)
        assertEquals(films, viewModel.state.value.portal?.offer)
    }

    @Test
    fun signedOutTheServicesAskToSignIn() = runTest {
        val digital = FakeDigital()
        val viewModel = viewModel(patron = null, digital = digital)
        advanceUntilIdle()
        viewModel.primaryAction(comics)
        assertEquals(DigitalMessage.NotSignedIn, viewModel.state.value.message)
        assertTrue(digital.resolved.isEmpty())
    }

    @Test
    fun suspendedAccessIsExplained() = runTest {
        val suspended = SampleData.patron.copy(permissions = Patron.Permissions(holdForbidden = false, digitalResourcesForbidden = true))
        val viewModel = viewModel(patron = suspended)
        advanceUntilIdle()
        viewModel.primaryAction(comics)
        assertEquals(DigitalMessage.Suspended, viewModel.state.value.message)
    }

    @Test
    fun offlineIsSaid() = runTest {
        val viewModel = viewModel(digital = FakeDigital().apply { offline = true })
        advanceUntilIdle()
        viewModel.primaryAction(comics)
        advanceUntilIdle()
        assertEquals(DigitalMessage.Offline, viewModel.state.value.message)
        assertNull(viewModel.state.value.portal)
    }
}

class PortalLinkPolicyTest {
    private fun decide(url: String, mainFrame: Boolean = true, gesture: Boolean = false): PortalLinkPolicy.Decision {
        val scheme = url.substringBefore(':')
        val rest = url.substringAfter("://", "")
        val host = rest.substringBefore('/').takeIf { it.isNotEmpty() }
        val path = if (host != null) "/" + rest.substringAfter('/', "") else null
        return PortalLinkPolicy.decide(scheme, host, path, mainFrame, gesture)
    }

    @Test
    fun webPagesLoad() {
        assertEquals(PortalLinkPolicy.Decision.LOAD, decide("https://www.izneo.com/fr/"))
        assertEquals(PortalLinkPolicy.Decision.LOAD, decide("https://cafeyn.co/fr/externallogin/x", mainFrame = false))
    }

    @Test
    fun providerAppLinksGoToTheirApp() {
        assertEquals(PortalLinkPolicy.Decision.TRY_PARTNER_APP_LINK, decide("https://www.cafeyn.co/fr/externallogin/abc"))
        assertEquals(PortalLinkPolicy.Decision.TRY_PARTNER_APP_LINK, decide("https://app.mozaik.co/autologin.php?k=1"))
        assertEquals(PortalLinkPolicy.Decision.LOAD, decide("https://app.mozaik.co/catalogue"))
        assertEquals(PortalLinkPolicy.Decision.LOAD, decide("https://evilcafeyn.co/externallogin/abc"))
    }

    @Test
    fun otherSchemesOnlyFromTheMainFrame() {
        assertEquals(PortalLinkPolicy.Decision.PARTNER_APP_ONLY, decide("intent://open#Intent;scheme=x;end"))
        assertEquals(PortalLinkPolicy.Decision.PARTNER_APP_ONLY, decide("cafeyn://open"))
        assertEquals(PortalLinkPolicy.Decision.BLOCK, decide("intent://open#Intent;end", mainFrame = false))
        assertEquals(PortalLinkPolicy.Decision.BLOCK, decide("file:///sdcard/x"))
        assertEquals(PortalLinkPolicy.Decision.BLOCK, decide("javascript:alert(1)"))
    }

    @Test
    fun mailAndPhoneOnlyAfterATap() {
        assertEquals(PortalLinkPolicy.Decision.BLOCK, decide("mailto:contact@example.org"))
        assertEquals(PortalLinkPolicy.Decision.EXTERNAL, decide("mailto:contact@example.org", gesture = true))
        assertEquals(PortalLinkPolicy.Decision.EXTERNAL, decide("tel:0100000000", gesture = true))
        assertEquals(
            PortalLinkPolicy.Decision.BLOCK,
            decide("sms:0100000000", gesture = true).let {
                if (it == PortalLinkPolicy.Decision.PARTNER_APP_ONLY) PortalLinkPolicy.Decision.BLOCK else it
            }
        )
    }
}
