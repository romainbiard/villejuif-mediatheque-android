// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.screenshottesting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import fr.mediatheque.core.designsystem.theme.MediathequeTheme
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** How a screen is drawn: theme, text size, and window. */
enum class ScreenshotVariant(val suffix: String, val darkTheme: Boolean, val fontScale: Float, val qualifiers: String) {
    LIGHT("light", darkTheme = false, fontScale = 1f, qualifiers = RobolectricDeviceQualifiers.Pixel7),
    DARK("dark", darkTheme = true, fontScale = 1f, qualifiers = RobolectricDeviceQualifiers.Pixel7),

    /** The largest text size Android offers (200 %): nothing may be cut off. */
    LARGE_FONT("font200", darkTheme = false, fontScale = 2f, qualifiers = RobolectricDeviceQualifiers.Pixel7),

    /** A tablet in landscape: the navigation rail and the list beside its detail. */
    TABLET("tablet", darkTheme = false, fontScale = 1f, qualifiers = RobolectricDeviceQualifiers.MediumTablet)
    ;

    companion object {
        /** Light and dark on a phone, and the largest text: what every screen is checked in. */
        val PHONE = listOf(LIGHT, DARK, LARGE_FONT)
    }
}

/**
 * Base of the screenshot tests: Robolectric with native graphics, on a phone, French as the app's source language
 * (set by the build for every test). The reference images are in src/test/screenshots, see the
 * `mediatheque.android.screenshots` convention plugin.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
abstract class ScreenshotTest {
    /** Draws [content] in each variant and compares it with (or records) `src/test/screenshots/<name>_<variant>.png`. */
    protected fun capture(name: String, variants: List<ScreenshotVariant> = ScreenshotVariant.PHONE, content: @Composable () -> Unit) {
        variants.forEach { variant ->
            // The locale with the device: setting the qualifiers alone would reset it to English.
            RuntimeEnvironment.setQualifiers("fr-rFR-${variant.qualifiers}")
            captureRoboImage("src/test/screenshots/${name}_${variant.suffix}.png") {
                ScreenshotFrame(variant, content)
            }
        }
    }
}

@Composable
private fun ScreenshotFrame(variant: ScreenshotVariant, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, variant.fontScale),
        // No network and no native map: images and the map draw their placeholders, as in a preview.
        LocalInspectionMode provides true
    ) {
        MediathequeTheme(darkTheme = variant.darkTheme, highContrast = false) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
}
