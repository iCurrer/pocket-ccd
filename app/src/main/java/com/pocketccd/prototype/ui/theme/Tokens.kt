package com.pocketccd.prototype.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Structural design tokens for the prototype.
 *
 * The palette is deliberately matte and low-chroma: a camera body, a few
 * hairlines, and one orange accent. Avoid adding glows/gradients here unless a
 * physical material needs them.
 */
object PocketCcdColors {
    val Background = Color(0xFF070708)
    val BackgroundElevated = Color(0xFF0C0C0E)
    val Surface = Color(0xFF121214)
    val SurfaceHigh = Color(0xFF19191C)
    val SurfaceHighest = Color(0xFF202024)
    val Outline = Color(0xFF2B2B30)
    val OutlineSoft = Color(0xFF1E1E22)

    val TextPrimary = Color(0xFFE7E2D8)
    val TextSecondary = Color(0xFF8E887D)
    val TextTertiary = Color(0xFF5C574F)

    val Accent = Color(0xFFE8562F)
    val AccentPressed = Color(0xFFC44524)
    val AccentSoft = Color(0x26E8562F)
    val OnAccent = Color(0xFF1A0804)

    val Error = Color(0xFFD98B80)
    val OnError = Color(0xFF2A0705)

    val LensGlass = Color(0xFF060607)
    val LensRing = Color(0xFF3D3D42)
    val LensHighlight = Color(0x55FFFFFF)
    val LensBlue = Color(0xFF1D2A38)
    val LensBlueDeep = Color(0xFF091018)
    val LensCore = Color(0xFF050A0E)
    val Flash = Color(0xFFE8DFC8)

    // Camera body / viewfinder surfaces.
    val CameraPanelTop = Color(0xFF161619)
    val CameraPanelBottom = Color(0xFF0B0B0D)
    val CameraPanelHighlight = Color(0x18FFFFFF)
    val ViewfinderTop = Color(0xFF071012)
    val ViewfinderBottom = Color(0xFF020405)
    val ShutterOuter = Color(0xFF29292E)
    val ShutterInner = Color(0xFFE8562F)
    val ShutterInnerPressed = Color(0xFFC44524)
    val ShutterHighlight = Color(0x66FFFFFF)
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
    val viewfinder = 6.dp
    val lg = 20.dp
    val xl = 28.dp
    val pill = 100.dp
}

object PocketCcdSizes {
    val touchTarget = 48.dp
    val shutter = 68.dp
    val shutterOuter = 88.dp
    val shutterRing = 80.dp
    val sideControl = 44.dp
    val topBarHeight = 44.dp
    val nameplateHeight = 28.dp

    val cameraBodyWidth = 236.dp
    val cameraBodyHeight = 184.dp
    val cameraPanelMargin = 12.dp
    val cameraPanelPadding = 16.dp
    val viewfinderPadding = 16.dp
    val viewfinderInnerBorder = 1.dp

    val lensOuter = 112.dp
    val lensInner = 68.dp
    val lensCore = 24.dp
    val lensHighlightDot = 10.dp
    val galleryThumb = 48.dp
    val galleryLens = 18.dp
    val icon = 20.dp
    val hintIcon = 18.dp
    val flashDot = 5.dp
    val accentStripWidth = 36.dp
    val accentStripHeight = 3.dp

    val actionHeight = 56.dp
    val ctaMinWidth = 120.dp
    val hairline = 1.dp
    val lensBorder = 1.dp
    val lensCanvasInset = 7.dp
    val lensArcInset = 9.dp
    val lensArcSizeInset = 18.dp
    val lensArcStroke = 2.dp

    val pullThreshold = 88.dp
}

object PocketCcdMotion {
    const val FastMs = 120
    const val BaseMs = 220
    const val SlowMs = 320
}
