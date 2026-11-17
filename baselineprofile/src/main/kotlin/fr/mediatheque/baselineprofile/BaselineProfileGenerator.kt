// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Startup, then each destination of the navigation and a search, on the demo build. The texts are those of the
 * device language: the generator runs on an English device, like the CI's managed devices.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        // A first launch shows the welcome setup once.
        device.wait(Until.findObject(By.text("Not now")), TIMEOUT)?.click()
        device.wait(Until.findObject(By.text("Done")), TIMEOUT)?.click()
        device.wait(Until.hasObject(By.text("My loans")), TIMEOUT)

        listOf("Search", "Events", "Digital", "Info", "Account").forEach { tab ->
            device.findObject(By.text(tab))?.click()
            device.waitForIdle()
        }

        device.findObject(By.text("My loans"))?.click()
        device.wait(Until.hasObject(By.text("Renew")), TIMEOUT)
        device.pressBack()

        device.findObject(By.text("Search"))?.click()
        device.wait(Until.findObject(By.text("Title, author, subject, ISBN")), TIMEOUT)?.text = "harry"
        device.wait(Until.findObject(By.textContains("Harry Potter")), TIMEOUT)?.click()
        device.wait(Until.hasObject(By.text("Where to find it")), TIMEOUT)
        device.pressBack()
    }

    private companion object {
        /** The demo release build, whatever the local applicationId: the Gradle plugin passes it. */
        val TARGET_PACKAGE: String = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId is passed by the baseline profile Gradle plugin")
        const val TIMEOUT = 5_000L
    }
}
