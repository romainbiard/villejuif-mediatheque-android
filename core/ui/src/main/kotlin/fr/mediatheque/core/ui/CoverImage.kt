// Médiathèque
// SPDX-License-Identifier: MIT
// See LICENSE in the project root for the full license text and copyright notice.

package fr.mediatheque.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import kotlin.math.absoluteValue

/**
 * The item's cover, or a cover generated in the City's colors while it loads or when there is none.
 * Decorative for TalkBack: the row around it says the title. The caller sets the size.
 */
@Composable
fun CoverImage(url: String?, title: String, seed: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.clip(shape).clearAndSetSemantics {}) {
        if (url == null) {
            GeneratedCover(title, seed)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                loading = { GeneratedCover(title, seed) },
                error = { GeneratedCover(title, seed) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Background and shape colors, stable for a given identifier; the text color reads on that background. */
private data class Artwork(val background: Color, val shape: Color, val text: Color)

private val palette = listOf(
    Artwork(Color(0xFF773DBD), Color(0xFFE0005A), Color.White),
    Artwork(Color(0xFFFEDC1B), Color(0xFFE0005A), Color(0xFF1C1B22)),
    Artwork(Color(0xFF84DADE), Color(0xFF5714A4), Color(0xFF1C1B22)),
    Artwork(Color(0xFF5714A4), Color(0xFF84DADE), Color.White),
    Artwork(Color(0xFFA60A3D), Color(0xFFFEDC1B), Color.White),
    Artwork(Color(0xFF1C1B22), Color(0xFFE0005A), Color.White)
)

@Composable
private fun GeneratedCover(title: String, seed: Int) {
    val artwork = palette[seed.absoluteValue % palette.size]
    BoxWithConstraints(Modifier.fillMaxSize().background(artwork.background), contentAlignment = Alignment.BottomStart) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = artwork.shape,
                radius = size.width * 0.55f,
                center = Offset(size.width * 0.2f, size.height * 0.05f)
            )
        }
        // Only on covers big enough to read the title.
        if (maxWidth > 80.dp) {
            Text(
                text = title,
                color = artwork.text,
                fontWeight = FontWeight.Black,
                fontSize = (maxWidth.value / 9).sp,
                lineHeight = (maxWidth.value / 8).sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(maxWidth / 12)
            )
        }
    }
}
