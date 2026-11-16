// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Villejuif's visual identity on Material 3. The brand colors are the website's (--rose, --falu_red…), the same
// as the iOS app's; the other roles are derived from them. Every text/background pair reaches WCAG AA (4.5:1),
// checked in both themes. The name, crest and visual identity belong to the City of Villejuif.

private object Brand {
    /** The website's `--rose`: main accent (buttons, selected destination, account header). */
    val violet = Color(0xFF773DBD)
    val violetLight = Color(0xFFAE8BE6)
    val violetDeep = Color(0xFF5714A4)
    val violetPale = Color(0xFFC9B0F2)

    /** `--falu_red`, the logo's color: the City's identity, highlights. */
    val magenta = Color(0xFFE0005A)
    val magentaLight = Color(0xFFFF4D8D)
    val cyan = Color(0xFF84DADE)
    val yellow = Color(0xFFFEDC1B)

    val ink = Color(0xFF1C1B22)
    val paper = Color(0xFFF4F3F8)
}

internal val LightColors = lightColorScheme(
    primary = Brand.violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEBDDFF),
    onPrimaryContainer = Color(0xFF2B0A55),
    inversePrimary = Brand.violetLight,
    secondary = Brand.violetDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDFB),
    onSecondaryContainer = Color(0xFF2A0B5A),
    tertiary = Brand.magenta,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3F0018),
    error = Color(0xFFA60A3D),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADF),
    onErrorContainer = Color(0xFF40000F),
    background = Brand.paper,
    onBackground = Brand.ink,
    surface = Brand.paper,
    onSurface = Brand.ink,
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF5E5B66),
    surfaceTint = Brand.violet,
    inverseSurface = Color(0xFF312F36),
    inverseOnSurface = Brand.paper,
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color.Black,
    surfaceBright = Brand.paper,
    surfaceDim = Color(0xFFDCDAE1),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9FD),
    surfaceContainer = Color(0xFFEFEEF4),
    surfaceContainerHigh = Color(0xFFE9E8EF),
    surfaceContainerHighest = Color(0xFFE3E2E9)
)

internal val DarkColors = darkColorScheme(
    primary = Brand.violetLight,
    onPrimary = Brand.ink,
    primaryContainer = Color(0xFF5B2E99),
    onPrimaryContainer = Color(0xFFEBDDFF),
    inversePrimary = Brand.violet,
    secondary = Brand.violetPale,
    onSecondary = Color(0xFF2E0F5C),
    secondaryContainer = Color(0xFF45238A),
    onSecondaryContainer = Color(0xFFEBDDFF),
    tertiary = Brand.magentaLight,
    onTertiary = Color(0xFF3F0018),
    tertiaryContainer = Color(0xFF8C0036),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFF6B8E),
    onError = Color(0xFF3F0011),
    errorContainer = Color(0xFF8C0030),
    onErrorContainer = Color(0xFFFFDADF),
    background = Color(0xFF0F0E13),
    onBackground = Brand.paper,
    surface = Color(0xFF0F0E13),
    onSurface = Brand.paper,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFB4B1BE),
    surfaceTint = Brand.violetLight,
    inverseSurface = Color(0xFFE5E1E9),
    inverseOnSurface = Color(0xFF312F36),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color.Black,
    surfaceBright = Color(0xFF353339),
    surfaceDim = Color(0xFF0F0E13),
    surfaceContainerLowest = Color(0xFF0A090D),
    surfaceContainerLow = Color(0xFF17161B),
    surfaceContainer = Brand.ink,
    surfaceContainerHigh = Color(0xFF26252C),
    surfaceContainerHighest = Color(0xFF312F37)
)

/** Higher contrast (system setting, Android 14+): darker accents and secondary text in light, lighter in dark. */
internal fun ColorScheme.withHigherContrast(isDark: Boolean): ColorScheme = if (isDark) {
    copy(
        primary = Color(0xFFC9AEF0),
        onSurfaceVariant = Color(0xFFDAD8E2),
        error = Color(0xFFFF9AB2),
        outline = Color(0xFFB4B1BE)
    )
} else {
    copy(
        primary = Color(0xFF5E2A9E),
        onSurfaceVariant = Color(0xFF3F3C46),
        error = Color(0xFF84002D),
        outline = Color(0xFF5E5B66)
    )
}

/** Colors outside Material's roles: statuses, and the brand colors used as such. */
@Immutable
data class ExtendedColors(
    /** On time, available, renewed. */
    val success: Color,
    /** Due soon. */
    val warning: Color,
    /** Cards: white on the light background, a step lighter than the dark background (never darker, as a hole). */
    val card: Color,
    /** The brand violet as a surface under white text (account header, library card): the same in both themes. */
    val brandSurface: Color,
    val onBrandSurface: Color,
    /** The City's magenta, cyan and yellow, for generated covers and illustrations. */
    val magenta: Color,
    val cyan: Color,
    val yellow: Color
)

internal val LightExtendedColors = ExtendedColors(
    card = Color.White,
    success = Color(0xFF006E43),
    warning = Color(0xFF7A4F00),
    brandSurface = Brand.violet,
    onBrandSurface = Color.White,
    magenta = Brand.magenta,
    cyan = Brand.cyan,
    yellow = Brand.yellow
)

internal val DarkExtendedColors = LightExtendedColors.copy(
    card = Brand.ink,
    success = Color(0xFF4CC38A),
    warning = Color(0xFFF5C451),
    magenta = Brand.magentaLight
)

internal fun ExtendedColors.withHigherContrast(isDark: Boolean): ExtendedColors = if (isDark) {
    copy(success = Color(0xFF86DDB1), warning = Color(0xFFFFDB8A))
} else {
    copy(success = Color(0xFF005434), warning = Color(0xFF5C3B00))
}
