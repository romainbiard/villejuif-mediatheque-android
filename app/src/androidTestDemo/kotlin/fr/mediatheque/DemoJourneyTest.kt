// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque

import android.Manifest
import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import fr.mediatheque.feature.account.R as AccountR
import fr.mediatheque.feature.search.R as SearchR
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * The main journeys of the demo build, with the Accessibility Test Framework checking every screen and every tap
 * (labels, touch targets, contrast). Run on the CI on a Gradle managed device: `pixel6Api35DemoDebugAndroidTest`.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DemoJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
        .around(compose)

    private fun text(@StringRes id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun waitFor(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), TIMEOUT)

    private fun isShown(@StringRes id: Int) = compose.onAllNodes(hasText(text(id))).fetchSemanticsNodes().isNotEmpty()

    private fun checkScreen() = compose.onRoot().tryPerformAccessibilityChecks()

    @Before
    fun openTheAccount() {
        compose.enableAccessibilityChecks()
        // A first launch shows the welcome setup once: the primer (skipped when notifications are already allowed),
        // then the settings.
        val steps = listOf(AccountR.string.welcome_not_now, AccountR.string.welcome_finish, AccountR.string.home_my_loans)
        compose.waitUntil(TIMEOUT) { steps.any(::isShown) }
        if (isShown(AccountR.string.welcome_not_now)) {
            compose.onNode(hasText(text(AccountR.string.welcome_not_now))).performClick()
            waitFor(text(AccountR.string.welcome_finish))
        }
        if (isShown(AccountR.string.welcome_finish)) compose.onNode(hasText(text(AccountR.string.welcome_finish))).performClick()
        waitFor(text(AccountR.string.home_my_loans))
    }

    @Test
    fun everyDestinationOpens() {
        checkScreen()
        listOf(R.string.tab_search, R.string.tab_agenda, R.string.tab_digital, R.string.tab_info, R.string.tab_account).forEach { tab ->
            compose.onAllNodes(hasText(text(tab))).onFirst().performClick()
            compose.waitForIdle()
            checkScreen()
        }
    }

    @Test
    fun loansAreRenewed() {
        compose.onNode(hasText(text(AccountR.string.home_my_loans))).performClick()
        waitFor(text(AccountR.string.loans_renew))
        checkScreen()
        compose.onAllNodes(hasText(text(AccountR.string.loans_renew))).onFirst().performClick()
        compose.waitForIdle()
    }

    @Test
    fun aRecordIsFoundAndOpened() {
        compose.onAllNodes(hasText(text(R.string.tab_search))).onFirst().performClick()
        compose.waitUntilAtLeastOneExists(hasSetTextAction(), TIMEOUT)
        compose.onNode(hasSetTextAction()).performTextInput("harry")
        waitFor("Harry Potter à l'école des sorciers")
        checkScreen()
        compose.onAllNodes(hasText("Harry Potter à l'école des sorciers")).onFirst().performClick()
        waitFor(text(SearchR.string.document_where))
        checkScreen()
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
