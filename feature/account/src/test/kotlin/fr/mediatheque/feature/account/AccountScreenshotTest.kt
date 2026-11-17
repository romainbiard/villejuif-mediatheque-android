// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import fr.mediatheque.core.data.AccountSync
import fr.mediatheque.core.data.SessionStore
import fr.mediatheque.core.data.demo.DemoAccountRepository
import fr.mediatheque.core.data.demo.DemoSessionRepository
import fr.mediatheque.core.datastore.InMemoryValueStore
import fr.mediatheque.core.model.AccountSnapshot
import fr.mediatheque.core.model.Inbox
import fr.mediatheque.core.model.Paris
import fr.mediatheque.core.model.SampleData
import fr.mediatheque.core.screenshottesting.ScreenshotTest
import fr.mediatheque.core.screenshottesting.ScreenshotVariant
import fr.mediatheque.core.testing.SilentSyncNotifier
import fr.mediatheque.core.testing.TestClock
import fr.mediatheque.core.testing.settingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The account in demo data, on November 17, 2026: one loan late by 20 days, one this week, a hold ready. */
class AccountScreenshotTest : ScreenshotTest() {
    @get:Rule
    val folder = TemporaryFolder()

    private val clock = TestClock(Paris.instant(2026, 11, 17, 12))
    private val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val snapshot = AccountSnapshot(SampleData.loans, SampleData.holds, clock.instant())

    @After
    fun closeStores() = storeScope.cancel()

    private fun sync(account: DemoAccountRepository) =
        AccountSync(account, InMemoryValueStore(), InMemoryValueStore(), SilentSyncNotifier, settingsStore(storeScope, folder.root), clock)

    @Test
    fun home() = capture("account_home", ScreenshotVariant.PHONE + ScreenshotVariant.TABLET) {
        AccountHome(
            patron = SampleData.patron,
            snapshot = snapshot,
            refreshError = null,
            isRefreshing = false,
            unread = 2,
            now = clock.instant(),
            onRefresh = {},
            onSignOut = {},
            onOpenLoans = {},
            onOpenHolds = {},
            onOpenNotifications = {},
            linkGroups = accountLinkGroups {}
        )
    }

    @Test
    fun login() {
        val settings = settingsStore(storeScope, folder.root)
        val account = DemoAccountRepository()
        val sessionStore = SessionStore(
            DemoSessionRepository(),
            InMemoryValueStore(),
            settings,
            AccountSync(account, InMemoryValueStore(), InMemoryValueStore(), SilentSyncNotifier, settings, clock),
            clock
        )
        val viewModel = LoginViewModel(DemoSessionRepository(), sessionStore)
        capture("account_login") { LoginScreen(viewModel) }
    }

    @Test
    fun loans() {
        val account = DemoAccountRepository()
        val viewModel = LoansViewModel(account, InMemoryValueStore(snapshot), sync(account), clock)
        capture("account_loans") { LoansScreen(viewModel, onBack = {}) }
    }

    @Test
    fun holds() {
        val viewModel = HoldsViewModel(DemoAccountRepository(), InMemoryValueStore(snapshot))
        capture("account_holds") { HoldsScreen(viewModel, onBack = {}) }
    }

    @Test
    fun notifications() {
        val account = DemoAccountRepository()
        val viewModel = NotificationsViewModel(InMemoryValueStore<Inbox>(SampleData.inbox), account, sync(account), clock)
        capture("account_notifications") { NotificationsScreen(viewModel, onOpenSettings = {}, onBack = {}) }
    }
}
