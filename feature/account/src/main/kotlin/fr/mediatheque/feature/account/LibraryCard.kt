// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.feature.account

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.VillejuifCrest
import fr.mediatheque.core.designsystem.component.isLargeText
import fr.mediatheque.core.designsystem.theme.MediathequeColors
import fr.mediatheque.core.domain.Code39
import fr.mediatheque.core.model.Patron
import fr.mediatheque.core.ui.parisFormat

/** The card's Code 39 barcode, black on white with quiet zones, as on the plastic card. */
@Composable
internal fun Barcode(value: String, modifier: Modifier = Modifier) {
    val elements = Code39.elements(value) ?: return
    Canvas(modifier.background(Color.White)) {
        // Quiet zone of ten narrow modules on each side, as the symbology asks.
        val quiet = 10.0
        val total = Code39.width(elements, WIDE_RATIO) + 2 * quiet
        val module = size.width / total.toFloat()
        var x = (quiet * module).toFloat()
        for (element in elements) {
            val width = module * (if (element.isWide) WIDE_RATIO.toFloat() else 1f)
            if (element.isBar) drawRect(Color.Black, Offset(x, 0f), Size(width, size.height))
            x += width
        }
    }
}

private const val WIDE_RATIO = 3.0

/**
 * The card on the account's home, as the plastic card: the City's crest, the patron's name and the card's validity on
 * the brand violet, two discs of the City's colors in the corner, and the barcode on white. Tap it to show it full
 * screen at the front desk.
 */
@Composable
internal fun LibraryCardSummary(patron: Patron, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.card_description, patron.displayName, patron.cardNumber)
    val action = stringResource(R.string.card_tap_to_enlarge)
    val largeText = isLargeText()
    val magenta = MediathequeColors.extended.magenta
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MediathequeColors.extended.brandSurface,
        contentColor = MediathequeColors.extended.onBrandSurface,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$description. $action"
            role = Role.Button
        }
    ) {
        Column(
            Modifier
                .drawBehind {
                    // The City's discs, cut by the card's corner as on the website's banners.
                    drawCircle(magenta.copy(alpha = 0.55f), radius = 110.dp.toPx(), center = Offset(size.width - 30.dp.toPx(), 0f))
                    drawCircle(DeepViolet.copy(alpha = 0.7f), radius = 60.dp.toPx(), center = Offset(size.width, 120.dp.toPx()))
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VillejuifCrest(Modifier.size(width = 34.dp, height = 26.dp), tint = Color.White)
                        Text(stringResource(R.string.card_title), style = MaterialTheme.typography.labelLarge)
                    }
                    Text(
                        patron.displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    patron.cardExpiry?.let {
                        Text(
                            stringResource(R.string.card_valid_until_long, parisFormat().dayMonthYear(it)),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.85f)
                        )
                    }
                }
                if (!largeText) FreePill()
            }
            Surface(shape = RoundedCornerShape(18.dp), color = Color.White, contentColor = CardInk) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Barcode(patron.cardNumber, Modifier.fillMaxWidth().height(48.dp).clearAndSetSemantics {})
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            patron.cardNumber,
                            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 2.sp),
                            modifier = Modifier.weight(1f)
                        )
                        if (!largeText) {
                            Text(action, style = MaterialTheme.typography.bodySmall, color = CardInkSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/** Library membership is free in Villejuif: said on the card, as on the City's website. */
@Composable
private fun FreePill() {
    Text(
        stringResource(R.string.card_free),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// The card keeps its colors in the dark theme, like the plastic card.
private val DeepViolet = Color(0xFF5714A4)
private val CardInk = Color(0xFF1C1B22)
private val CardInkSecondary = Color(0xFF5E5B66)

/**
 * The card full screen for the front desk's reader: brightness at maximum and the screen kept on while shown, both
 * given back on close. It works in every orientation (large screens ignore orientation locks), and the barcode can
 * turn half a turn to face the reader.
 */
@Composable
internal fun FullScreenCard(patron: Patron, onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    var flipped by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(activity) {
        val window = activity?.window
        val previous = window?.attributes?.screenBrightness
        window?.let {
            it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL }
            it.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.let {
                it.attributes = it.attributes.apply { screenBrightness = previous ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
                it.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.card_libraries), style = MaterialTheme.typography.titleMedium, color = Color.Black)
                Text(patron.displayName, style = MaterialTheme.typography.headlineSmall, color = Color.Black)
                val barcodeDescription = stringResource(R.string.card_barcode_description, patron.cardNumber)
                Barcode(
                    patron.cardNumber,
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .rotate(if (flipped) 180f else 0f)
                        .clearAndSetSemantics { contentDescription = barcodeDescription }
                )
                Text(patron.cardNumber, style = MaterialTheme.typography.headlineMedium, color = Color.Black)
                patron.cardExpiry?.let {
                    Text(stringResource(R.string.card_valid_until, parisFormat().dayMonth(it)), color = Color.DarkGray)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { flipped = !flipped }) {
                        Icon(painterResource(DesignR.drawable.ic_screen_rotation), contentDescription = null, Modifier.size(18.dp))
                        Text(stringResource(R.string.card_rotate), Modifier.padding(start = 8.dp))
                    }
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(painterResource(DesignR.drawable.ic_close), contentDescription = stringResource(R.string.close), tint = Color.Black)
            }
        }
    }
}
