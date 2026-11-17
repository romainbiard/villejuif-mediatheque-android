// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.designsystem.theme

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * The app's theme: Villejuif's colors, not the wallpaper's (Material You), as Material allows for a brand.
 * It follows the system's dark theme and, on Android 14+, its contrast setting.
 */
@Composable
fun MediathequeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    highContrast: Boolean = rememberSystemHighContrast(),
    content: @Composable () -> Unit
) {
    val colors = (if (darkTheme) DarkColors else LightColors).let { if (highContrast) it.withHigherContrast(darkTheme) else it }
    val extended = (if (darkTheme) DarkExtendedColors else LightExtendedColors).let {
        if (highContrast) it.withHigherContrast(darkTheme) else it
    }
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(colorScheme = colors, typography = MediathequeTypography, content = content)
    }
}

/** The colors outside Material's roles, in the current theme. */
object MediathequeColors {
    val extended: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Medium or high contrast chosen in the system settings (Android 14+), followed live. */
@Composable
private fun rememberSystemHighContrast(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val context = LocalContext.current
    val uiModeManager = remember(context) { context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager }
    var contrast by remember { mutableFloatStateOf(uiModeManager.contrast) }
    DisposableEffect(uiModeManager) {
        val listener = UiModeManager.ContrastChangeListener { contrast = it }
        uiModeManager.addContrastChangeListener(context.mainExecutor, listener)
        onDispose { uiModeManager.removeContrastChangeListener(listener) }
    }
    // 0 is standard, 0.5 medium, 1 high.
    return contrast >= MEDIUM_CONTRAST
}

private const val MEDIUM_CONTRAST = 0.5f

/** The brand color schemes, for surfaces outside the app's Compose tree (the home screen widget). */
object MediathequeColorSchemes {
    val light: androidx.compose.material3.ColorScheme get() = LightColors
    val dark: androidx.compose.material3.ColorScheme get() = DarkColors
}
