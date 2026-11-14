package com.paperplan.app.core.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.paperplan.app.core.ui.theme.PaperGlassBorder
import com.paperplan.app.core.ui.theme.PaperMint
import com.paperplan.app.core.ui.theme.PaperSurface
import com.paperplan.app.core.ui.theme.PaperSurfaceElevated

/**
 * Lightweight dark glass card adhering to GPU-friendly composition:
 * single translucent/dark surface + subtle 1dp hairline border without heavy shadows.
 */
@Composable
fun GlassWorkspaceCard(
    modifier: Modifier = Modifier,
    elevated: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (elevated) PaperSurfaceElevated else PaperSurface
        ),
        border = BorderStroke(1.dp, PaperGlassBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/**
 * Hardware-accelerated pinch-to-zoom & pan preview box.
 * Transform runs strictly inside GPU RenderThread via Modifier.graphicsLayer without recomposition.
 */
@Composable
fun GpuZoomableBitmapPreview(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var scale by remember(bitmap) { mutableFloatStateOf(1f) }
    var offsetX by remember(bitmap) { mutableFloatStateOf(0f) }
    var offsetY by remember(bitmap) { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 380.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0B10))
            .clipToBounds()
            .pointerInput(bitmap) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (pointerCount > 1 || scale > 1.02f) {
                            val nextScale = (scale * zoomChange).coerceIn(1f, 4f)
                            scale = nextScale
                            if (nextScale > 1.02f) {
                                offsetX += panChange.x
                                offsetY += panChange.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = imageBitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        )
    }
}

@Composable
fun StatusBadgePill(
    label: String,
    accent: Color = PaperMint
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

fun formatFileBytes(bytes: Long): String = when {
    bytes >= 1_048_576L -> "%.2f MB".format(bytes / 1_048_576f)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
