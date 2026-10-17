package com.pocketccd.prototype.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Structural design tokens for the prototype.
 *
 * Palette direction: warm toy-camera materials (oat canvas, dusty sage body,
 * deep pine viewfinder, terracotta shutter) instead of a high-contrast
 * black/orange "pro camera" UI. Keep saturation and pure black/white low to
 * reduce eye strain.
 */
object PocketCcdColors {
    val Background = Color(0xFFF4EFE6)          // warm oat canvas
    val BackgroundElevated = Color(0xFFEDE6DA)
    val Surface = Color(0xFFFBF7EF)             // warm paper / button cap
    val SurfaceHigh = Color(0xFFE7DECD)
    val SurfaceHighest = Color(0xFFDED3C1)
    val Outline = Color(0xFFC9BEAC)
    val OutlineSoft = Color(0xFFDED4C4)

    val TextPrimary = Color(0xFF26241F)         // soft ink, not pure black
    val TextSecondary = Color(0xFF3F3C34)
    val TextTertiary = Color(0xFF6B6458)

    val Accent = Color(0xFFD46A4C)              // terracotta shutter
    val AccentPressed = Color(0xFFB9543A)
    val AccentSoft = Color(0x26D46A4C)
    val AccentText = Color(0xFFA94A32)          // accessible terracotta text on oat
    val OnAccent = Color(0xFF1A0804)            // dark text if ever placed on terracotta

    val Error = Color(0xFFB85F4D)
    val OnError = Color(0xFFFFF8EF)

    val LensGlass = Color(0xFF1A2421)
    val LensRing = Color(0xFF6E7D6B)
    val LensHighlight = Color(0x66FFFFFF)
    val LensBlue = Color(0xFF38514D)
    val LensBlueDeep = Color(0xFF16231F)
    val LensCore = Color(0xFF0B1210)
    val Flash = Color(0xFFE8C56A)               // butter-yellow flash window

    // Camera body / viewfinder surfaces.
    val CameraPanelTop = Color(0xFFB3C0AD)      // dusty sage, light top plate
    val CameraPanelBottom = Color(0xFF96A790)   // sage shade
    val CameraPanelHighlight = Color(0x66FFFFFF)
    val ViewfinderTop = Color(0xFF1B2523)       // deep pine, not pure black
    val ViewfinderBottom = Color(0xFF0C1211)
    val ViewfinderInk = Color(0xFFC6D0C4)       // labels over the dark viewfinder
    val ShutterOuter = Color(0xFFE9DFCF)        // ivory metal ring
    val ShutterInner = Color(0xFFD46A4C)
    val ShutterInnerPressed = Color(0xFFB9543A)
    val ShutterHighlight = Color(0xAAFFFFFF)
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
