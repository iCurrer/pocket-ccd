package com.pocketccd.prototype.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Structural design tokens for the prototype.
 *
 * These values are intentionally centralized but still provisional: T1-014's
 * visual review may replace the palette/type scale without touching screens.
 */
object PocketCcdColors {
    val Background = Color(0xFF0B0B0D)
    val BackgroundElevated = Color(0xFF151518)
    val Surface = Color(0xFF1C1C21)
    val SurfaceHigh = Color(0xFF26262C)
    val SurfaceHighest = Color(0xFF313138)
    val Outline = Color(0xFF3A3A42)
    val OutlineSoft = Color(0xFF2A2A31)

    val TextPrimary = Color(0xFFF4F1EA)
    val TextSecondary = Color(0xFFA9A49A)
    val TextTertiary = Color(0xFF6F6A62)

    val Accent = Color(0xFFFF5A36)
    val AccentPressed = Color(0xFFE04A2A)
    val AccentSoft = Color(0x33FF5A36)
    val OnAccent = Color(0xFF2A0B04)

    val Error = Color(0xFFFFB4AB)
    val OnError = Color(0xFF690005)

    val LensGlass = Color(0xFF070708)
    val LensRing = Color(0xFF45454D)
    val LensHighlight = Color(0x66FFFFFF)
    val LensBlue = Color(0xFF24344A)
    val LensBlueDeep = Color(0xFF0B1520)
    val LensCore = Color(0xFF07131F)
    val Flash = Color(0xFFFFF3D6)
}

object PocketCcdSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object PocketCcdRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 100.dp
}

object PocketCcdSizes {
    val touchTarget = 48.dp
    val shutter = 84.dp
    val cameraBodyWidth = 268.dp
    val cameraBodyHeight = 208.dp
    val lensOuter = 132.dp
    val lensInner = 84.dp
    val lensCore = 30.dp
    val lensHighlightDot = 12.dp
    val galleryThumb = 56.dp
    val galleryLens = 22.dp
    val icon = 22.dp
    val hintIcon = 20.dp
    val flashDot = 6.dp
    val viewfinderWidth = 38.dp
    val viewfinderHeight = 24.dp
    val accentStripWidth = 44.dp
    val accentStripHeight = 4.dp
    val actionHeight = 64.dp
    val ctaMinWidth = 144.dp
    val hairline = 1.dp
    val lensBorder = 2.dp
    val lensCanvasInset = 8.dp
    val lensArcInset = 10.dp
    val lensArcSizeInset = 20.dp
    val lensArcStroke = 3.dp
}

object PocketCcdMotion {
    const val FastMs = 120
    const val BaseMs = 200
    const val SlowMs = 320
}
