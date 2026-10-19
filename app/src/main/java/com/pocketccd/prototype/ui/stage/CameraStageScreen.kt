package com.pocketccd.prototype.ui.stage

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.components.ToyCameraBody
import com.pocketccd.prototype.ui.components.ToyRoundButton
import com.pocketccd.prototype.ui.components.ToyScreen
import com.pocketccd.prototype.ui.theme.PocketCcdColors
import com.pocketccd.prototype.ui.theme.PocketCcdRadius
import com.pocketccd.prototype.ui.theme.PocketCcdSizes
import com.pocketccd.prototype.ui.theme.PocketCcdSpacing
import com.pocketccd.prototype.ui.theme.PocketCcdTheme
import com.pocketccd.prototype.ui.theme.PocketCcdType
import kotlinx.coroutines.launch

/**
 * Single persistent toy-camera shell for home and shooting. The body frame is
 * driven directly by vertical drag progress; the shutter/flash move to their
 * shooting positions, the lens becomes the viewfinder, and film/settings fade
 * out. There is no AnimatedContent between home and camera, so no second layer
 * can flash in during the pull-down.
 *
 * The viewfinder is a slot: the route hands in a real CameraX surface when the
 * permission is granted and the pack size matches, otherwise the static test
 * frame stays in place. This composable never touches the camera itself.
 */
@Composable
fun CameraStageScreen(
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onShutter: () -> Unit,
    modifier: Modifier = Modifier,
    stockLabel: String? = null,
    cameraLive: Boolean = false,
    canShoot: Boolean = true,
    blockReason: String? = null,
    onCycleStock: () -> Unit = {},
    onFlashChange: (Boolean) -> Unit = {},
    viewfinder: @Composable (Modifier) -> Unit = { CameraViewfinder(modifier = it) },
) {
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 220.dp.toPx() }

    var flashOn by remember { mutableStateOf(false) }
    var frontCamera by remember { mutableStateOf(false) }
    val flashAvailable = !frontCamera

    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (expanded) 420 else 360,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        PocketCcdColors.RetroRedLight,
                        PocketCcdColors.RetroRed,
                        PocketCcdColors.RetroRedDeep,
                    ),
                ),
            )
            .pointerInput(expanded) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        val current = progress.value
                        when {
                            !expanded && current >= 0.35f -> onExpand()
                            expanded && current <= 0.65f -> onCollapse()
                            !expanded -> scope.launch {
                                progress.animateTo(0f, tween(280))
                            }
                            else -> scope.launch {
                                progress.animateTo(1f, tween(280))
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            progress.snapTo(
                                (progress.value + dragAmount / thresholdPx)
                                    .coerceIn(0f, 1f),
                            )
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val collapsedSize = minOf(300.dp, maxWidth - 32.dp)
        val expandedWidth = maxWidth - 24.dp
        val expandedHeight = maxHeight - 24.dp
        val bodyWidth = lerpDp(collapsedSize, expandedWidth, progress.value)
        val bodyHeight = lerpDp(collapsedSize, expandedHeight, progress.value)

        ToyCameraBody(
            modifier = Modifier.size(width = bodyWidth, height = bodyHeight),
        ) {
            StageFaceplate(
                progress = progress.value,
                flashOn = flashOn,
                flashAvailable = flashAvailable,
                frontCamera = frontCamera,
                onToggleFlash = {
                    if (flashAvailable) {
                        flashOn = !flashOn
                        onFlashChange(flashOn)
                    }
                },
                onToggleCamera = {
                    frontCamera = !frontCamera
                    if (frontCamera) {
                        flashOn = false
                        // 前置没有闪光灯，切过去时要把后摄的闪光灯模式一起关掉，
                        // 否则切回来时相机仍按上次的 ON 模式闪。
                        onFlashChange(false)
                    }
                },
                onShutter = {
                    when {
                        progress.value <= 0.7f -> onExpand()
                        canShoot -> onShutter()
                    }
                },
                onOpenGallery = onOpenGallery,
                onOpenSettings = onOpenSettings,
                stockLabel = stockLabel,
                cameraLive = cameraLive,
                canShoot = canShoot,
                blockReason = blockReason,
                onCycleStock = onCycleStock,
                viewfinder = viewfinder,
            )
        }
    }
}

@Composable
private fun StageFaceplate(
    progress: Float,
    flashOn: Boolean,
    flashAvailable: Boolean,
    frontCamera: Boolean,
    onToggleFlash: () -> Unit,
    onToggleCamera: () -> Unit,
    onShutter: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    stockLabel: String?,
    cameraLive: Boolean,
    canShoot: Boolean,
    blockReason: String?,
    onCycleStock: () -> Unit,
    viewfinder: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val cw = maxWidth
        val ch = maxHeight

        // Home -> shooting positions, expressed as fractions of the faceplate.
        val brandY = lerpDp(ch * 0.08f, ch * 0.92f, progress)
        val lensSize = lerpDp(cw * 0.56f, cw * 0.26f, progress)
        val lensX = cw * 0.34f
        val lensY = lerpDp(ch * 0.52f, ch * 0.52f, progress)
        val viewfinderSize = cw * 0.84f
        val viewfinderY = ch * 0.33f
        val flashSize = cw * 0.19f
        val flashX = lerpDp(cw * 0.74f, cw * 0.18f, progress)
        val flashY = lerpDp(ch * 0.38f, ch * 0.78f, progress)
        val shutterSize = lerpDp(cw * 0.30f, cw * 0.32f, progress)
        val shutterX = lerpDp(cw * 0.74f, cw * 0.5f, progress)
        val shutterY = lerpDp(ch * 0.64f, ch * 0.78f, progress)
        val filmAlpha = (1f - progress * 1.8f).coerceIn(0f, 1f)
        val settingsAlpha = filmAlpha
        val flipAlpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)

        LensGraphic(
            modifier = Modifier
                .size(lensSize)
                .offset(
                    x = lensX - lensSize / 2,
                    y = lensY - lensSize / 2,
                )
                .alpha((1f - progress * 1.4f).coerceIn(0f, 1f)),
        )

        Box(
            modifier = Modifier
                .size(viewfinderSize)
                .offset(
                    x = cw * 0.5f - viewfinderSize / 2,
                    y = viewfinderY,
                )
                .alpha(progress)
                .graphicsLayer {
                    scaleX = 0.88f + 0.12f * progress
                    scaleY = 0.88f + 0.12f * progress
                },
        ) {
            viewfinder(Modifier.fillMaxSize())

            // 卷名叠在取景框内侧底部，像真机上的卷标；点一下换卷。
            if (stockLabel != null) {
                StockChip(
                    label = stockLabel,
                    // 预览还没起来时（权限未给/正在绑定）用暗色，避免让人以为已经在拍。
                    live = cameraLive,
                    onClick = onCycleStock,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }

        Text(
            text = "POCKET CCD",
            style = PocketCcdType.Brand.copy(
                fontSize = 16.sp,
                letterSpacing = 1.5.sp,
            ),
            color = PocketCcdColors.RetroInk,
            modifier = Modifier
                .offset(y = brandY)
                .fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        ToyRoundButton(
            onClick = onToggleFlash,
            contentDescription = stringResource(
                when {
                    !flashAvailable -> R.string.camera_flash_unavailable
                    flashOn -> R.string.camera_flash_on
                    else -> R.string.camera_flash_off
                },
            ),
            modifier = Modifier.offset(
                x = flashX - flashSize / 2,
                y = flashY - flashSize / 2,
            ),
            size = flashSize,
            color = if (flashAvailable) {
                if (flashOn) PocketCcdColors.RetroGreen else PocketCcdColors.RetroGreenDeep
            } else {
                PocketCcdColors.RetroRedDeep
            },
            enabled = flashAvailable,
        ) {
            FlashIcon(
                on = flashOn,
                enabled = flashAvailable,
                modifier = Modifier.size(flashSize * 0.38f),
            )
        }

        StageShutter(
            size = shutterSize,
            modifier = Modifier.offset(
                x = shutterX - shutterSize / 2,
                y = shutterY - shutterSize / 2,
            ),
            enabled = canShoot,
            onClick = onShutter,
        )

        // 按不下去时必须有话说。静默失效是这类相机页最难查的问题：
        // 用户只会觉得"快门坏了"，而真正原因可能是没有 pack、权限没给或分辨率不匹配。
        if (progress > 0.8f && !canShoot && blockReason != null) {
            Text(
                text = blockReason,
                style = PocketCcdType.MonoMicro,
                color = PocketCcdColors.RetroCream.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(y = ch * 0.93f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }

        ToyRoundButton(
            onClick = onToggleCamera,
            contentDescription = stringResource(
                if (frontCamera) R.string.camera_flip_front else R.string.camera_flip_back,
            ),
            modifier = Modifier
                .offset(x = cw * 0.82f - flashSize / 2, y = ch * 0.78f - flashSize / 2)
                .alpha(flipAlpha),
            size = flashSize,
            color = PocketCcdColors.RetroRedDeep,
            enabled = flipAlpha > 0.8f,
        ) {
            FlipCameraIcon(
                front = frontCamera,
                modifier = Modifier.size(flashSize * 0.38f),
            )
        }

        FilmButton(
            onClick = onOpenGallery,
            modifier = Modifier
                .offset(x = cw * 0.12f, y = ch * 0.86f)
                .alpha(filmAlpha),
            enabled = filmAlpha > 0.5f,
        )

        ToyRoundButton(
            onClick = onOpenSettings,
            contentDescription = stringResource(R.string.action_settings),
            modifier = Modifier
                .offset(x = cw * 0.80f, y = ch * 0.86f)
                .alpha(settingsAlpha),
            size = cw * 0.15f,
            color = PocketCcdColors.RetroRedDeep,
            enabled = settingsAlpha > 0.5f,
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = PocketCcdColors.RetroCream,
                modifier = Modifier.size(cw * 0.095f),
            )
        }
    }
}

@Composable
private fun StageShutter(
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "stage-shutter-scale",
    )
    val description = stringResource(R.string.camera_shutter)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // 不能拍的时候压暗但不隐藏：让人看见快门在哪，也知道它现在按不下去。
                alpha = if (enabled) 1f else 0.5f
            }
            .shadow(
                elevation = 16.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = PocketCcdColors.RetroInk.copy(alpha = 0.5f),
                spotColor = PocketCcdColors.RetroInk.copy(alpha = 0.62f),
            )
            .clip(CircleShape)
            .background(PocketCcdColors.RetroInk)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .padding(size * 0.06f),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            PocketCcdColors.RetroOrange,
                            PocketCcdColors.RetroOrangeDeep,
                        ),
                    ),
                )
                .border(
                    width = 2.dp,
                    color = PocketCcdColors.RetroCream.copy(alpha = 0.58f),
                    shape = CircleShape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = size * 0.18f, top = size * 0.12f)
                    .size(size * 0.24f)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
            )
        }
    }
}

@Composable
private fun LensGraphic(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = PocketCcdColors.RetroInk.copy(alpha = 0.45f),
                spotColor = PocketCcdColors.RetroInk.copy(alpha = 0.55f),
            )
            .clip(CircleShape)
            .background(PocketCcdColors.RetroBlack)
            .border(PocketCcdSizes.lensBorder, PocketCcdColors.RetroGreenDeep, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF2E5E59),
                            Color(0xFF143C39),
                            PocketCcdColors.RetroBlack,
                        ),
                    ),
                )
                .border(1.dp, PocketCcdColors.RetroGreen.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(PocketCcdColors.RetroBlack)
                    .border(1.dp, PocketCcdColors.RetroGreen.copy(alpha = 0.5f), CircleShape),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 12.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f)),
            )
        }
    }
}

/**
 * 卷名条。叠在取景框下沿，点一下换卷。
 *
 * [live] 只控制小圆点的颜色：绿=取景器已经出画面，橙=还是静态占位。
 * 不能用它替代真实状态文案 —— 它是给"扫一眼"用的，不是诊断。
 */
@Composable
private fun StockChip(
    label: String,
    live: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(PocketCcdRadius.pill))
            .background(PocketCcdColors.RetroBlack.copy(alpha = 0.55f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(
                    if (live) PocketCcdColors.RetroGreen else PocketCcdColors.RetroOrange,
                ),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = PocketCcdType.MonoMicro,
            color = PocketCcdColors.RetroCream,
        )
    }
}

/** 没有相机时的静态取景框。保留它，预览起不来时不至于露出黑洞。 */
@Composable
internal fun CameraViewfinder(
    modifier: Modifier = Modifier,
) {
    ToyScreen(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF5E7B71), Color(0xFFB9A17E)),
                ),
            )
            drawRect(
                color = Color(0xFF344D43),
                topLeft = Offset(0f, size.height * 0.62f),
                size = Size(size.width, size.height * 0.38f),
            )
            drawCircle(
                color = Color(0xFFE8C56A).copy(alpha = 0.7f),
                radius = size.width * 0.05f,
                center = Offset(size.width * 0.7f, size.height * 0.28f),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .size(7.dp)
                .clip(CircleShape)
                .background(PocketCcdColors.RetroGreen),
        )
    }
}

@Composable
private fun FilmButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    val description = stringResource(R.string.home_photo_wall)
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(PocketCcdRadius.pill))
            .background(
                Brush.verticalGradient(
                    listOf(PocketCcdColors.RetroGreen, PocketCcdColors.RetroGreenDeep),
                ),
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "FILM",
            style = PocketCcdType.MonoLabel,
            color = PocketCcdColors.RetroInk,
        )
    }
}

@Composable
private fun FlashIcon(
    on: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        !enabled -> PocketCcdColors.RetroCream.copy(alpha = 0.35f)
        on -> PocketCcdColors.RetroInk
        else -> PocketCcdColors.RetroCream
    }
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.56f, 0f)
            lineTo(size.width * 0.16f, size.height * 0.56f)
            lineTo(size.width * 0.47f, size.height * 0.56f)
            lineTo(size.width * 0.38f, size.height)
            lineTo(size.width * 0.86f, size.height * 0.42f)
            lineTo(size.width * 0.54f, size.height * 0.42f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
private fun FlipCameraIcon(
    front: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        val inset = 2.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(
            color = PocketCcdColors.RetroCream,
            startAngle = if (front) 205f else 25f,
            sweepAngle = 145f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = PocketCcdColors.RetroCream,
            startAngle = if (front) 25f else 205f,
            sweepAngle = 145f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    start + (stop - start) * fraction

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFD9382E)
@Composable
private fun CameraStageHomePreview() {
    PocketCcdTheme {
        CameraStageScreen(
            expanded = false,
            onExpand = {},
            onCollapse = {},
            onOpenGallery = {},
            onOpenSettings = {},
            onShutter = {},
        )
    }
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFFD9382E)
@Composable
private fun CameraStageCameraPreview() {
    PocketCcdTheme {
        CameraStageScreen(
            expanded = true,
            onExpand = {},
            onCollapse = {},
            onOpenGallery = {},
            onOpenSettings = {},
            onShutter = {},
        )
    }
}
