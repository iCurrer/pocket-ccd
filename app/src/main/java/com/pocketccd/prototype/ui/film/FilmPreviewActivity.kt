package com.pocketccd.prototype.ui.film

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/**
 * Isolated entry point for the M9 redesign (T9-012 needs a way to compare old
 * and new without touching [com.pocketccd.prototype.MainActivity]).
 *
 * Launch with:
 * `adb shell am start -n com.pocketccd.prototype/.ui.film.FilmPreviewActivity`
 *
 * This screen uses a **drawn stand-in scene**, not the camera. It exists to
 * check spacing, metal rendering and chip legibility against the design; it is
 * never the shipping entry point and never claims to be a live preview.
 */
class FilmPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = android.graphics.Color.TRANSPARENT),
        )
        setContent {
            FilmTheme {
                var flashOn by remember { mutableStateOf(false) }
                var gridOn by remember { mutableStateOf(false) }
                var shots by remember { mutableIntStateOf(12) }

                FilmCaptureScreen(
                    stockName = "KODAK PORTRA 400",
                    stockMeta = "KODAK PORTRA 400 · C-41",
                    shotsTaken = shots,
                    rollCapacity = 36,
                    megapixels = "12.6MP",
                    flashOn = flashOn,
                    gridOn = gridOn,
                    canShoot = true,
                    blockReason = null,
                    onShutter = { if (shots < 36) shots += 1 },
                    onToggleFlash = { flashOn = !flashOn },
                    onToggleGrid = { gridOn = !gridOn },
                    onOpenGallery = {},
                    onOpenFilms = {},
                    onNewRoll = { shots = 0 },
                    onOpenSettings = {},
                    viewfinder = { DemoNightScene(it) },
                )
            }
        }
    }
}

/**
 * A drawn night landscape, mirroring the CSS scene in the design file so the
 * overlay contrast can be judged without a camera. Deterministic seed: the same
 * stars every launch, which makes screenshot diffing possible.
 */
@Composable
fun DemoNightScene(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.verticalGradient(
                0.0f to Color(0xFF04101F),
                0.42f to Color(0xFF082238),
                0.68f to Color(0xFF0D3349),
                1.0f to Color(0xFF0A1D29),
            ),
        )

        val rnd = Random(7)
        repeat(70) {
            val x = rnd.nextFloat() * w
            val yRatio = rnd.nextFloat() * 0.55f
            val fade = 1f - (yRatio / 0.55f) * 0.6f
            val alpha = (0.55f * fade * (0.4f + rnd.nextFloat() * 0.6f)).coerceIn(0f, 1f)
            drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = 0.7.dp.toPx(),
                center = Offset(x, yRatio * h),
            )
        }

        // Warm haze near the horizon, where the "town" sits.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE8B278).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(w * 0.38f, h * 0.74f),
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.38f, h * 0.74f),
        )

        val farRidge = Path().apply {
            moveTo(0f, h * 0.62f)
            lineTo(w * 0.12f, h * 0.52f)
            lineTo(w * 0.24f, h * 0.60f)
            lineTo(w * 0.38f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.55f)
            lineTo(w * 0.66f, h * 0.38f)
            lineTo(w * 0.80f, h * 0.50f)
            lineTo(w * 0.92f, h * 0.42f)
            lineTo(w, h * 0.52f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(farRidge, Color(0xFF0C1A22).copy(alpha = 0.85f))

        val nearRidge = Path().apply {
            moveTo(0f, h * 0.72f)
            lineTo(w * 0.16f, h * 0.62f)
            lineTo(w * 0.30f, h * 0.70f)
            lineTo(w * 0.44f, h * 0.58f)
            lineTo(w * 0.60f, h * 0.68f)
            lineTo(w * 0.76f, h * 0.56f)
            lineTo(w * 0.90f, h * 0.66f)
            lineTo(w, h * 0.60f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(nearRidge, Color(0xFF04080C))

        // Distant town lights.
        val lights = Random(19)
        repeat(26) {
            val x = w * (0.22f + lights.nextFloat() * 0.62f)
            val y = h * (0.86f + lights.nextFloat() * 0.03f)
            drawCircle(
                color = Color(0xFFFFD6A0).copy(alpha = 0.35f * (0.5f + lights.nextFloat() * 0.5f)),
                radius = 1.dp.toPx(),
                center = Offset(x, y),
            )
        }

        // Vignette, matching the design's radial + vertical darkening.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f)),
                center = Offset(w / 2f, h / 2f),
                radius = w * 0.78f,
            ),
        )
    }
}
