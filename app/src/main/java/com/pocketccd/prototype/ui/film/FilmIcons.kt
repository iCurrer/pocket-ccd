package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Nine hand-drawn icons instead of `material-icons-extended`.
 *
 * The extended pack is ~2.5 MB and its filled style does not match the thin
 * line icons in the design. Nine paths are cheaper than one dependency, and
 * D-024 requires an explicit review before adding any dependency.
 *
 * All geometry is authored on a 24x24 viewport and scaled to the requested size,
 * so the same icon works at 15dp (top bar) and 22dp (thumbnail).
 */
enum class FilmIconKind {
    Flash,
    FlashOff,
    Grid,
    Tune,
    Filmstrip,
    ImageMultiple,
    Close,
    Check,
    ChevronRight,
}

@Composable
fun FilmIcon(
    kind: FilmIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 17.dp,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension / 24f
        val w = strokeWidth.toPx()
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        when (kind) {
            FilmIconKind.Flash -> {
                val path = Path().apply {
                    moveTo(13f * s, 2f * s)
                    lineTo(4f * s, 14f * s)
                    lineTo(10f * s, 14f * s)
                    lineTo(9f * s, 22f * s)
                    lineTo(20f * s, 10f * s)
                    lineTo(13.5f * s, 10f * s)
                    lineTo(14.5f * s, 2f * s)
                    close()
                }
                drawPath(path, tint)
            }

            FilmIconKind.FlashOff -> {
                // No slash. At 17dp the slash and the bolt's waist occupy the same
                // few pixels and the pair reads as an asterisk (device v4). The
                // button's fill already says on/off — accent when on, dark when
                // off — so the icon only has to stay legible: same bolt, dimmed.
                val path = Path().apply {
                    moveTo(13f * s, 2f * s)
                    lineTo(4f * s, 14f * s)
                    lineTo(10f * s, 14f * s)
                    lineTo(9f * s, 22f * s)
                    lineTo(20f * s, 10f * s)
                    lineTo(13.5f * s, 10f * s)
                    lineTo(14.5f * s, 2f * s)
                    close()
                }
                drawPath(path, tint.copy(alpha = 0.45f))
            }

            FilmIconKind.Grid -> {
                drawLine(tint, o(4f, 4f), o(20f, 4f), w)
                drawLine(tint, o(20f, 4f), o(20f, 20f), w)
                drawLine(tint, o(20f, 20f), o(4f, 20f), w)
                drawLine(tint, o(4f, 20f), o(4f, 4f), w)
                drawLine(tint, o(12f, 4f), o(12f, 20f), w * 0.8f)
                drawLine(tint, o(4f, 12f), o(20f, 12f), w * 0.8f)
            }

            FilmIconKind.Tune -> {
                drawLine(tint, o(3f, 7f), o(21f, 7f), w)
                drawLine(tint, o(3f, 12f), o(21f, 12f), w)
                drawLine(tint, o(3f, 17f), o(21f, 17f), w)
                drawCircle(tint, radius = 2.1f * s, center = o(8f, 7f))
                drawCircle(tint, radius = 2.1f * s, center = o(15f, 12f))
                drawCircle(tint, radius = 2.1f * s, center = o(10f, 17f))
            }

            FilmIconKind.Filmstrip -> {
                // Frame + two rows of sprocket squares. Two vertical rails read as
                // a "pause" glyph at 22dp, so the holes have to be blocks, not lines.
                drawRect(
                    color = tint,
                    topLeft = o(4f, 5f),
                    size = Size(16f * s, 14f * s),
                    style = Stroke(width = w),
                )
                for (i in 0..2) {
                    val x = 6.4f + i * 4.6f
                    drawRect(tint, o(x, 6.3f), Size(2.2f * s, 1.7f * s))
                    drawRect(tint, o(x, 16.0f), Size(2.2f * s, 1.7f * s))
                }
            }

            FilmIconKind.ImageMultiple -> {
                // Two offset frames only. An inner landscape silhouette was tried
                // first and read as a gear at 22dp — silhouette over detail.
                drawRect(
                    color = tint,
                    topLeft = o(8f, 4f),
                    size = Size(12f * s, 12f * s),
                    style = Stroke(width = w),
                )
                drawRect(
                    color = tint,
                    topLeft = o(4f, 8f),
                    size = Size(12f * s, 12f * s),
                    style = Stroke(width = w),
                )
            }

            FilmIconKind.Close -> {
                drawLine(tint, o(6f, 6f), o(18f, 18f), w)
                drawLine(tint, o(18f, 6f), o(6f, 18f), w)
            }

            FilmIconKind.Check -> {
                val path = Path().apply {
                    moveTo(5f * s, 12.5f * s)
                    lineTo(10f * s, 17.5f * s)
                    lineTo(19f * s, 6.5f * s)
                }
                drawPath(path, tint, style = Stroke(width = w * 1.2f))
            }

            FilmIconKind.ChevronRight -> {
                val path = Path().apply {
                    moveTo(9f * s, 5f * s)
                    lineTo(16f * s, 12f * s)
                    lineTo(9f * s, 19f * s)
                }
                drawPath(path, tint, style = Stroke(width = w))
            }
        }
    }
}
