package com.pocketccd.prototype.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Toy-camera material system: butter-yellow plastic body, cream faceplate,
 * mint controls, coral shutter, deep-teal viewfinder. Think a chunky 2000s
 * toy compact camera, not a web dashboard and not a pro camera console.
 */
object PocketCcdColors {
    val Background = Color(0xFFE6F0EA)          // pale mint desk / room
    val BackgroundElevated = Color(0xFFD9E7E0)
    val Surface = Color(0xFFFFF4E2)             // cream faceplate
    val SurfaceHigh = Color(0xFFF3DFC2)
    val SurfaceHighest = Color(0xFFEAD2B0)
    val Outline = Color(0xFFC8B79E)
    val OutlineSoft = Color(0xFFE2D2BC)

    val TextPrimary = Color(0xFF2F2923)
    val TextSecondary = Color(0xFF5B4F43)
    val TextTertiary = Color(0xFF7A6B5C)

    val Accent = Color(0xFFE76F51)              // coral shutter
    val AccentPressed = Color(0xFFC8553D)
    val AccentSoft = Color(0x26E76F51)
    val AccentText = Color(0xFFB84F38)
    val OnAccent = Color(0xFF2B1710)

    val Error = Color(0xFFB84A3A)
    val OnError = Color(0xFFFFF9F0)
    val Warning = Color(0xFFB8862F)

    val LensGlass = Color(0xFF143433)
    val LensRing = Color(0xFF6FB7A4)
    val LensHighlight = Color(0x88FFFFFF)
    val LensBlue = Color(0xFF2E5E59)
    val LensBlueDeep = Color(0xFF143C39)
    val LensCore = Color(0xFF071B1A)
    val Flash = Color(0xFFF7D774)

    // Body and display surfaces.
    val CameraPanelTop = Color(0xFFF2C14E)      // butter yellow
    val CameraPanelBottom = Color(0xFFDFA92F)
    val CameraPanelHighlight = Color(0xFFFFF2C7)
    val ViewfinderTop = Color(0xFF173B3A)       // deep teal LCD/viewfinder
    val ViewfinderBottom = Color(0xFF0C2423)
    val ViewfinderInk = Color(0xFFD8F1E6)
    val ShutterOuter = Color(0xFFFFF4E2)
    val ShutterInner = Color(0xFFE76F51)
    val ShutterInnerPressed = Color(0xFFC8553D)
    val ShutterHighlight = Color(0xCCFFFFFF)
    val Mint = Color(0xFF9FD8C4)
    val MintDeep = Color(0xFF6FB7A4)
    val Butter = Color(0xFFF7D774)

    // Retro red toy-camera family: softened Kodak-like red instead of neon
    // #F12D27. Less eye strain, more vintage plastic.
    val RetroRed = Color(0xFFD9382E)
    val RetroRedLight = Color(0xFFEE5B4A)
    val RetroRedDeep = Color(0xFFA8231C)
    val RetroInk = Color(0xFF1E1210)
    val RetroBlack = Color(0xFF12100F)
    val RetroOrange = Color(0xFFE8872E)
    val RetroOrangeDeep = Color(0xFFC76A1E)
    val RetroGreen = Color(0xFF8FC7A8)
    val RetroGreenDeep = Color(0xFF5E9C7B)
    val RetroCream = Color(0xFFF7E9D7)
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
    val viewfinder = 8.dp
    val lg = 20.dp
    val xl = 28.dp
    val body = 36.dp
    val pill = 100.dp
}

object PocketCcdSizes {
    val touchTarget = 48.dp
    val shutter = 72.dp
    val shutterOuter = 92.dp
    val shutterRing = 84.dp
    val sideControl = 48.dp
    val topBarHeight = 44.dp
    val nameplateHeight = 28.dp

    val cameraBodyWidth = 300.dp
    val cameraBodyHeight = 300.dp
    val cameraPanelMargin = 12.dp
    val cameraPanelPadding = 12.dp
    val viewfinderPadding = 14.dp
    val viewfinderInnerBorder = 1.dp

    val lensOuter = 148.dp
    val lensInner = 92.dp
    val lensCore = 30.dp
    val lensHighlightDot = 12.dp
    val galleryThumb = 48.dp
    val galleryLens = 18.dp
    val icon = 20.dp
    val hintIcon = 18.dp
    val flashDot = 10.dp
    val accentStripWidth = 36.dp
    val accentStripHeight = 3.dp

    val actionHeight = 56.dp
    val ctaMinWidth = 120.dp
    val hairline = 1.dp
    val lensBorder = 2.dp
    val lensCanvasInset = 8.dp
    val lensArcInset = 10.dp
    val lensArcSizeInset = 20.dp
    val lensArcStroke = 2.dp

    val pullThreshold = 88.dp
}

object PocketCcdMotion {
    const val FastMs = 120
    const val BaseMs = 220
    const val SlowMs = 320
}
