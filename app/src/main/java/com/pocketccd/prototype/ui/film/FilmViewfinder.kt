package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

/**
 * The metal bezel around the live preview.
 *
 * Design uses `linear-gradient` + `inset box-shadow` + `box-shadow`. Compose
 * gives us one `shadow()` (which does respect custom colours on API 28+, so the
 * outer drop shadow is real) but no inset shadow — the top 1px highlight is
 * therefore drawn by hand in [drawBehind]. Without that single line the frame
 * reads as flat grey instead of brushed metal; it is the whole trick.
 */
@Composable
fun FilmViewfinder(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val m = spec.metrics
    val outerShape = RoundedCornerShape(m.viewfinderRadius)

    Box(
        modifier = modifier
            .shadow(
                elevation = 20.dp,
                shape = outerShape,
                clip = false,
                ambientColor = Color.Black,
                spotColor = Color.Black,
            )
            .drawBehind {
                val r = m.viewfinderRadius.toPx()
                drawRoundRect(
                    brush = Brush.linearGradient(
                        0.0f to p.bezelMetal[0],
                        0.36f to p.bezelMetal[1],
                        0.66f to p.bezelMetal[2],
                        1.0f to p.bezelMetal[3],
                        start = Offset.Zero,
                        // The container is tall, so a corner-to-corner gradient
                        // stretches the four stops over ~800dp and reads as flat
                        // grey. Compressing it into the top fifth restores the
                        // lit-from-above look of the 3px CSS bezel.
                        end = Offset(size.width * 0.75f, size.height * 0.20f),
                    ),
                    cornerRadius = CornerRadius(r, r),
                )
                val h = m.hairline.toPx()
                drawLine(
                    color = Color.White.copy(alpha = 0.22f),
                    start = Offset(r, h / 2f),
                    end = Offset(size.width - r, h / 2f),
                    strokeWidth = h,
                )
            }
            .padding(m.bezel)
            .clip(RoundedCornerShape(m.viewfinderRadius - m.bezel))
            .background(p.viewfinderBg),
        content = content,
    )
}

/**
 * Darkens the edges of the live preview.
 *
 * The design has this layer (`.vf-vignette`) and it was skipped in the first
 * implementation — the device immediately showed why it matters: on a white
 * wall the framing corners and the reticle are invisible without it. Composited
 * under the chrome, over the preview.
 */
@Composable
fun ViewfinderVignette(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color.Black.copy(alpha = 0.45f),
                0.18f to Color.Transparent,
                0.72f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.55f),
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                0.0f to Color.Transparent,
                0.48f to Color.Transparent,
                1.0f to Color.Black.copy(alpha = 0.5f),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.75f,
            ),
        )
    }
}

/**
 * Four L-shaped framing marks. Fixed size so they do not stretch on wide screens.
 *
 * Every line is drawn twice: a fat dark underlay first, then the thin white
 * stroke. A single white hairline disappears on a bright subject, and the
 * preview can be anything.
 */
@Composable
fun ViewfinderCorners(modifier: Modifier = Modifier) {
    val m = LocalFilmTheme.current.metrics
    Canvas(modifier.fillMaxSize()) {
        val ix = m.cornerInsetX.toPx()
        val iy = m.cornerInsetY.toPx()
        val arm = m.cornerArm.toPx()
        val w = m.hairline.toPx()
        val right = size.width - ix
        val bottom = size.height - iy

        val segments = listOf(
            Offset(ix, iy) to Offset(ix + arm, iy),
            Offset(ix, iy) to Offset(ix, iy + arm),
            Offset(right, iy) to Offset(right - arm, iy),
            Offset(right, iy) to Offset(right, iy + arm),
            Offset(ix, bottom) to Offset(ix + arm, bottom),
            Offset(ix, bottom) to Offset(ix, bottom - arm),
            Offset(right, bottom) to Offset(right - arm, bottom),
            Offset(right, bottom) to Offset(right, bottom - arm),
        )

        segments.forEach { (a, b) ->
            drawLine(Color.Black.copy(alpha = 0.40f), a, b, w * 3.5f)
        }
        segments.forEach { (a, b) ->
            drawLine(Color.White.copy(alpha = 0.72f), a, b, w)
        }
    }
}

/** Rule-of-thirds guides. Off by default — a clean finder is the point. */
@Composable
fun ViewfinderGrid(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val c = Color.White.copy(alpha = 0.15f)
        val w = 1.dp.toPx()
        val thirdW = size.width / 3f
        val thirdH = size.height / 3f
        drawLine(c, Offset(thirdW, 0f), Offset(thirdW, size.height), w)
        drawLine(c, Offset(thirdW * 2f, 0f), Offset(thirdW * 2f, size.height), w)
        drawLine(c, Offset(0f, thirdH), Offset(size.width, thirdH), w)
        drawLine(c, Offset(0f, thirdH * 2f), Offset(size.width, thirdH * 2f), w)
    }
}

/** Centre reticle: four 17dp arms around a 3dp dot, with a deliberate gap. */
@Composable
fun ViewfinderReticle(modifier: Modifier = Modifier) {
    val m = LocalFilmTheme.current.metrics
    Canvas(modifier.size(m.reticle)) {
        val w = 1.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val arm = 17.dp.toPx()

        val segments = listOf(
            Offset(cx, 0f) to Offset(cx, arm),
            Offset(cx, size.height - arm) to Offset(cx, size.height),
            Offset(0f, cy) to Offset(arm, cy),
            Offset(size.width - arm, cy) to Offset(size.width, cy),
        )

        segments.forEach { (a, b) -> drawLine(Color.Black.copy(alpha = 0.35f), a, b, w * 3f) }
        segments.forEach { (a, b) -> drawLine(Color.White.copy(alpha = 0.85f), a, b, w) }

        drawCircle(Color.Black.copy(alpha = 0.35f), radius = 2.4.dp.toPx(), center = Offset(cx, cy))
        drawCircle(Color.White.copy(alpha = 0.95f), radius = 1.5.dp.toPx(), center = Offset(cx, cy))
    }
}


