// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.core.designsystem.R
import fr.mediatheque.core.designsystem.theme.MediathequeColors

/** Section title with the short violet bar above it, a pattern taken from the website. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    val heading = @Composable { headingModifier: Modifier ->
        Column(modifier = headingModifier.semantics(mergeDescendants = true) { heading() }) {
            Box(
                Modifier
                    .size(width = 28.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.height(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
    }
    if (isLargeText()) {
        // The trailing pills go under the title rather than squeezing it.
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            heading(Modifier)
            trailing()
        }
    } else {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            heading(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Status pills side by side, going to the next line when they no longer fit (large text, narrow window). */
@Composable
fun PillRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        content()
    }
}

/**
 * The patron chose a large text size (150 % and more): rows put their actions under the text instead of beside it,
 * so a title keeps more than a word per line.
 */
@Composable
fun isLargeText(): Boolean = LocalDensity.current.fontScale >= LARGE_TEXT_SCALE

private const val LARGE_TEXT_SCALE = 1.5f

/** A short status in a tinted pill: "En retard", "Disponible"… */
@Composable
fun StatusPill(text: String, style: StatusStyle, modifier: Modifier = Modifier) {
    val (foreground, background) = when (style) {
        StatusStyle.SUCCESS -> MediathequeColors.extended.success.let { it to it.copy(alpha = SOFT_ALPHA) }
        StatusStyle.DANGER -> MaterialTheme.colorScheme.error.let { it to it.copy(alpha = SOFT_ALPHA) }
        StatusStyle.WARNING -> MediathequeColors.extended.warning to MediathequeColors.extended.yellow.copy(alpha = 0.3f)
        StatusStyle.ACCENT -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.primary.copy(alpha = SOFT_ALPHA)
        StatusStyle.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant.let { it to it.copy(alpha = SOFT_ALPHA) }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = foreground,
        modifier = modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

enum class StatusStyle { SUCCESS, DANGER, WARNING, ACCENT, NEUTRAL }

/** An action in an empty or error state. */
data class StateAction(val label: String, val onClick: () -> Unit)

/** Empty or error state: a tinted badge with the brand's shapes, a title, a message and up to two actions. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primary: StateAction? = null,
    secondary: StateAction? = null
) {
    val tint = MaterialTheme.colorScheme.primary
    val extended = MediathequeColors.extended
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = extended.card
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Decoration only: the title and message say it all.
            Box(Modifier.height(120.dp).width(160.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).background(tint.copy(alpha = SOFT_ALPHA), CircleShape))
                Box(Modifier.offset(52.dp, (-44).dp).size(24.dp).background(extended.yellow, CircleShape))
                Box(Modifier.offset((-56).dp, 42.dp).size(14.dp).background(extended.magenta, CircleShape))
                Box(Modifier.offset(48.dp, 46.dp).size(10.dp).border(3.dp, extended.cyan, CircleShape))
                Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(40.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            primary?.let { Button(onClick = it.onClick, modifier = Modifier.padding(top = 8.dp)) { Text(it.label) } }
            secondary?.let { TextButton(onClick = it.onClick) { Text(it.label) } }
        }
    }
}

/**
 * A line above data already on screen when the refresh failed ("Hors ligne · dernières données enregistrées").
 * TalkBack reads it out without moving the focus.
 */
@Composable
fun StaleBanner(text: String, @DrawableRes icon: Int, modifier: Modifier = Modifier) {
    val extended = MediathequeColors.extended
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(extended.yellow.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = extended.warning, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = extended.warning)
    }
}

/** A placeholder row while a list loads. Hidden from TalkBack, which hears "Chargement" from the screen instead. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    Row(modifier = modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(width = 52.dp, height = 76.dp).background(color, RoundedCornerShape(8.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth().height(14.dp).background(color, RoundedCornerShape(4.dp)))
            Box(Modifier.size(width = 140.dp, height = 12.dp).background(color, RoundedCornerShape(4.dp)))
            Box(Modifier.size(width = 90.dp, height = 12.dp).background(color, RoundedCornerShape(4.dp)))
        }
    }
}

/**
 * The main button. Busy, it keeps its size and color, faded, with a spinner over its hidden label,
 * instead of turning grey.
 */
@Composable
fun BusyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = DISABLED_ALPHA),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, modifier = Modifier.alpha(if (busy) 0f else 1f))
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

/** Villejuif's crest, tinted. The name, crest and visual identity belong to the City of Villejuif. */
@Composable
fun VillejuifCrest(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.tertiary, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.villejuif_crest),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
    )
}

/** Pale background paired with a status color. */
private const val SOFT_ALPHA = 0.14f

/** Tint opacity of a button that cannot be used (disabled or busy). */
private const val DISABLED_ALPHA = 0.5f
