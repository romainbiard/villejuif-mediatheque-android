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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.mediatheque.core.designsystem.R as DesignR
import fr.mediatheque.core.designsystem.component.VillejuifCrest
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

/** The card on the account's home: tap it to show it full screen at the front desk. */
@Composable
internal fun LibraryCardSummary(patron: Patron, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.card_description, patron.displayName, patron.cardNumber)
    val action = stringResource(R.string.card_tap_to_enlarge)
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
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VillejuifCrest(Modifier.size(width = 40.dp, height = 30.dp), tint = Color.White)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.card_title), style = MaterialTheme.typography.labelLarge)
                    Text(patron.displayName, style = MaterialTheme.typography.titleLarge)
                }
            }
            Surface(shape = RoundedCornerShape(12.dp), color = Color.White) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Barcode(patron.cardNumber, Modifier.fillMaxWidth().height(56.dp).clearAndSetSemantics {})
                    Text(patron.cardNumber, style = MaterialTheme.typography.labelLarge, color = Color.Black)
                }
            }
        }
    }
}

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
