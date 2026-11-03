package com.pocketccd.prototype.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.pocketccd.prototype.camera.ExposureProfile
import com.pocketccd.prototype.camera.LensStop
import com.pocketccd.prototype.ui.glass.GlassButton
import com.pocketccd.prototype.ui.glass.GlassChip
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.gallery.decodeSampledBitmap
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass
import kotlin.math.abs
import kotlin.math.roundToInt

/** 一次点按对焦的视觉状态。 */
private data class FocusMark(val x: Float, val y: Float, val state: FocusState, val at: Long)

private enum class FocusState { Working, Locked, Failed }

/**
 * 取景页。全屏预览 + 玻璃浮层，没有任何机身装饰。
 *
 * 与旧版（`FilmCaptureScreen` 的"机身 + 取景窗 + 底盖"三段式）的根本差别：
 * **预览就是屏幕**，所有控件都是浮在画面上的玻璃芯片，不再有一块"相机壳"占据版面。
 * 这样在 360dp 宽的手机上，画面能拿到的面积从约 62% 提到接近 100%。
 *
 * 保留的功能一个不少：点按对焦（带 AF LOCK/FAIL 真实回执）、光学档变焦、EV 补偿、
 * 闪光灯、九宫格、权限降级、快门闪白（闪白在 AppRoot 最外层）。
 * 去掉的是"36 格刻度"与"换卷"—— 卷模型已按 D-070 移除。
 */
@Composable
fun CameraScreen(
    stockName: String,
    /** 这一支胶卷累计拍过多少张（卷模型移除后，只有这一层计数是真的）。 */
    shotsWithStock: Int,
    flashOn: Boolean,
    flashAvailable: Boolean,
    gridOn: Boolean,
    canShoot: Boolean,
    blockReason: String?,
    permissionGranted: Boolean,
    modifier: Modifier = Modifier,
    /** 最近一张成片的缩略图路径；没有时相册钮显示占位图标。 */
    lastThumbnail: String? = null,
    /**
     * 一次性提示（例如"这台设备拍不了这卷，已换成另一卷"）。
     *
     * 旧版是**静默换卷**（复审 P3-12）：用户选了 A，绑定后发现分辨率不够，代码悄悄
     * 换成 B，屏幕上没有任何交代。现在换卷必须留下这句话，由外壳负责超时清空。
     */
    notice: String? = null,
    /** 本次快门的贴花状态（135 半格 · 日期）。null = 都不开，不显示任何提示。 */
    imprintLabel: String? = null,
    zoom: Float = 1f,
    lensStops: List<LensStop> = emptyList(),
    exposureProfile: ExposureProfile? = null,
    evIndex: Int = 0,
    viewfinder: (@Composable (Modifier) -> Unit)? = null,
    onShutter: () -> Unit = {},
    onToggleFlash: () -> Unit = {},
    onToggleGrid: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onOpenFilms: () -> Unit = {},
    onOpenDiscover: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onTapToFocus: (Float, Float, (Boolean) -> Unit) -> Unit = { _, _, _ -> },
    onZoomChange: (Float) -> Float = { it },
    onEvChange: (Int) -> Int = { it },
    /**
     * 打开时是否展开 EV 面板。**只给截图测试用**：这个面板是内部状态，没有它就没法
     * 在 Robolectric 里把"面板展开 + 变焦轨收起"这个组合渲染出来核对（D-076）。
     */
    evInitiallyOpen: Boolean = false,
    /** 同上，给截图测试用：打开时上滑抽屉已经展开（D-079）。 */
    switcherInitiallyOpen: Boolean = false,
) {
    val p = MaterialTheme.glass
    var focus by remember { mutableStateOf<FocusMark?>(null) }
    var evOpen by remember { mutableStateOf(evInitiallyOpen) }

    // 上滑 dock 打开的"去哪儿"抽屉（D-079）。相机页是根页面、没有 TabBar，
    // 相册/胶卷/发现/设置原本散在四五个入口上（dock 缩略图、胶卷芯片、右上角设置），
    // 抽屉把它们收成一处，手势也只有一种：从 dock 往上推。
    var switcherOpen by remember { mutableStateOf(switcherInitiallyOpen) }

    val haptics = LocalHapticFeedback.current
    // 上滑判定阈值：48dp，和触屏最小热区同一个数量级 —— 太灵敏会让"按住快门往上微动"
    // 也弹出抽屉，太迟钝则要甩一大段。
    val swipeUpThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }

    // 对焦标记 2.2 秒后淡出：留着会一直挡画面中心。
    LaunchedEffect(focus?.at) {
        if (focus != null) {
            delay(2_200)
            focus = null
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        // 横版（D-083）：拍摄页被强制横屏，控件不能还堆在上下两头 —— 横屏只有 361dp 高。
        val landscape = maxWidth > maxHeight

        // ---- 取景窗（D-080）----
        // 不再是"预览即屏幕"：画面缩进一个带圆角、描边、角标的窗里，四周留给控件。
        // 比例取 **3:4** —— 那是成片的比例（传感器 4:3 横置，落盘时转 90°），
        // 所以窗里看到的取景和最后那张照片是同一个画幅，不会再有上下的黑边。
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 横版：取景窗变 4:3 横向；控件挪到左右两侧后，上下预留只要一点点。
            // 宽 / 高
            val windowAspect = if (landscape) 4f / 3f else 3f / 4f
            val topReserve = if (landscape) 12.dp else ViewfinderTopReserve
            val bottomReserve = if (landscape) 12.dp else ViewfinderBottomReserve
            val bandHeight = maxHeight - topReserve - bottomReserve
            val maxWindowWidth = minOf(maxWidth - GlassSpace.page * 2, CameraChromeMaxWidth)
            val windowWidth = minOf(maxWindowWidth, bandHeight * windowAspect)
            val windowHeight = windowWidth / windowAspect

            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(topReserve))
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(windowWidth, windowHeight)
                            .clip(ViewfinderShape)
                            .background(Color.Black)
                            .border(1.dp, Color.White.copy(alpha = 0.18f), ViewfinderShape),
                    ) {
                        if (permissionGranted && viewfinder != null) {
                            viewfinder(Modifier.fillMaxSize())

                            // 暗角：只压边缘亮度，不动像素（D-041）。
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.42f),
                                                ),
                                                center = Offset(size.width / 2f, size.height * 0.46f),
                                                radius = size.maxDimension * 0.62f,
                                            ),
                                        )
                                    },
                            )

                            if (gridOn) GridOverlay()

                            // 点按层：坐标是**取景器 View 像素**，直接交给 meteringPointFactory。
                            // 现在它就是取景窗本身，所以点窗外的黑边不会再去对焦。
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            focus = FocusMark(
                                                offset.x,
                                                offset.y,
                                                FocusState.Working,
                                                System.nanoTime(),
                                            )
                                            onTapToFocus(offset.x, offset.y) { ok ->
                                                focus = FocusMark(
                                                    offset.x,
                                                    offset.y,
                                                    if (ok) FocusState.Locked else FocusState.Failed,
                                                    System.nanoTime(),
                                                )
                                            }
                                        }
                                    },
                            )

                            focus?.let { FocusReticle(it) }
                            ViewfinderCornerMarks()
                        }
                    }
                }
                Spacer(Modifier.height(bottomReserve))
            }
        }

        if (!(permissionGranted && viewfinder != null)) {
            PermissionState(
                granted = permissionGranted,
                reason = blockReason,
                onOpenAppSettings = onOpenAppSettings,
            )
        }

        // ---- 顶部：胶卷名 · 张数 / 工具（横版不画，控件挪到左右两侧）----
        if (!landscape) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = CameraChromeMaxWidth)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = GlassSpace.page, vertical = GlassSpace.tight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 胶卷名吃掉剩余宽度：窄屏上它自己省略，而不是把右边三个按钮挤出屏幕
            // （旧布局没有 weight，360dp 机器上设置钮直接被推到屏幕外）。
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                GlassChip(
                    // 张数并进同一枚芯片：少一个浮块，顶栏才有空间。
                    text = if (shotsWithStock > 0) "$stockName · $shotsWithStock" else stockName,
                    // 直接压在实时预览上：要那层深色蒙版，否则亮部读不出字。
                    overPhoto = true,
                    onClick = onOpenFilms,
                    leading = { tint -> GlassIcon(GlassIconKind.Film, tint, size = 16.dp) },
                    trailing = { tint -> GlassIcon(GlassIconKind.Chevron, tint, size = 16.dp) },
                )
            }
            Spacer(Modifier.width(GlassSpace.tight))
            if (exposureProfile != null) {
                GlassIconButton(
                    label = "曝光补偿",
                    active = evOpen || evIndex != exposureProfile.index,
                    overPhoto = true,
                    size = 44.dp,
                    onClick = { evOpen = !evOpen },
                    icon = { tint -> GlassIcon(GlassIconKind.Exposure, tint) },
                )
                Spacer(Modifier.width(6.dp))
            }
            GlassIconButton(
                label = if (gridOn) "关闭九宫格" else "打开九宫格",
                active = gridOn,
                overPhoto = true,
                size = 44.dp,
                onClick = onToggleGrid,
                icon = { tint -> GlassIcon(GlassIconKind.Grid, tint) },
            )
            Spacer(Modifier.width(6.dp))
            GlassIconButton(
                label = "设置",
                overPhoto = true,
                size = 44.dp,
                onClick = onOpenSettings,
                icon = { tint -> GlassIcon(GlassIconKind.Gear, tint) },
            )
        }
        }

        // ---- 右侧：变焦档 ----
        // EV 面板展开时收起变焦轨：两者都锚在右侧/中央，360dp 屏幕上必然叠在一起。
        // 没有会话时（权限被拒 / 相机还没绑上）不画变焦轨：那两个圆钮点了不会有任何
        // 反应，摆在那里就是在骗人（D-076 截图核对时发现的）。
        if (!landscape && !evOpen && permissionGranted && viewfinder != null) {
            ZoomRail(
                modifier = Modifier
                    .align(if (landscape) Alignment.TopEnd else Alignment.CenterEnd)
                    .padding(end = GlassSpace.page)
                    .then(if (landscape) Modifier.statusBarsPadding() else Modifier),
                zoom = zoom,
                stops = lensStops,
                onZoomChange = onZoomChange,
            )
        }

        // ---- EV 面板（点顶部 EV 钮展开）----
        AnimatedVisibility(
            visible = evOpen && exposureProfile != null,
            enter = fadeIn(tween(140)) + scaleIn(tween(140), initialScale = 0.94f),
            exit = fadeOut(tween(110)) + scaleOut(tween(110), targetScale = 0.96f),
            modifier = Modifier
                // 不再压在画面正中：EV 面板展开时用户往往正是在构图，挡住中心等于
                // 挡住主体。放到 dock 上方，手指也够得着（D-076）。
                .align(Alignment.BottomCenter)
                .padding(
                    start = GlassSpace.page,
                    end = GlassSpace.page,
                    bottom = 132.dp,
                ),
        ) {
            if (exposureProfile != null) {
                EvPanel(
                    profile = exposureProfile,
                    index = evIndex,
                    onChange = { next -> onEvChange(next) },
                )
            }
        }

        // ---- 底部：提示 / 不可拍原因 / dock（横版不画）----
        if (!landscape) {
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (notice != null) {
                GlassSurface(
                    shape = GlassShape.card,
                    overPhoto = true,
                    modifier = Modifier.padding(horizontal = GlassSpace.page),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(text = notice, style = GlassType.caption, color = p.onGlass)
                }
                Spacer(Modifier.height(GlassSpace.tight))
            }

            if (blockReason != null && (permissionGranted && viewfinder != null)) {
                GlassSurface(
                    shape = GlassShape.chip,
                    overPhoto = true,
                    modifier = Modifier.padding(horizontal = GlassSpace.page),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = blockReason,
                        style = GlassType.caption,
                        color = p.onGlass,
                    )
                }
                Spacer(Modifier.height(GlassSpace.tight))
            }

            // 贴花状态（D-081）：只在开着的时候出现，一行小字，用户永远知道
            // "这一张会被怎么处理"。不开就不占位置。
            if (imprintLabel != null && !switcherOpen) {
                Text(
                    text = imprintLabel,
                    style = GlassType.badge,
                    color = p.onGlassMuted,
                    modifier = Modifier.padding(bottom = GlassSpace.tight),
                )
            }

            // 抽屉展开时 dock 淡出：两都是 BottomCenter 的玻璃面板，同时在场会叠成一坨
            // （截图里能看到快门从抽屉底下透出来）。抽屉是 dock 的"向上展开态"，
            // 展开的时候 dock 就该让位（D-079）。
            AnimatedVisibility(
                visible = !switcherOpen,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(140)),
            ) {
                GlassSurface(
                    modifier = Modifier
                    // 同顶栏：宽度上限 560dp，平板上的 dock 不会拉成一条 760dp 的玻璃条。
                    .widthIn(max = CameraChromeMaxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = GlassSpace.page)
                    // 上滑打开抽屉（D-079）。挂在面板上而不是整屏：整屏会跟取景器的
                    // 点按对焦打架 —— 上滑和点按都在同一块区域里，谁先吃事件就成了玄学。
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragCancel = { dragged = 0f },
                            onVerticalDrag = { _, amount -> dragged += amount },
                            onDragEnd = {
                                if (dragged < -swipeUpThresholdPx) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    evOpen = false
                                    switcherOpen = true
                                }
                            },
                        )
                    },
                shape = GlassShape.panel,
                overPhoto = true,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 抓手：告诉用户"这块可以往上推"。没有它，手势没有任何提示。
                    Box(
                        Modifier
                            .padding(bottom = 8.dp)
                            .size(width = 32.dp, height = 3.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.30f)),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 左右两格等重：快门才是**真正**居中。旧版用 SpaceBetween，
                        // 左缩略图 52dp、右闪光灯 48dp，快门被挤偏了 2dp。
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            GalleryKey(lastThumbnail, onOpenGallery)
                        }
                        ShutterKey(canShoot, onShutter)
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            GlassIconButton(
                                label = when {
                                    !flashAvailable -> "这台镜头没有闪光灯"
                                    flashOn -> "关闭闪光灯"
                                    else -> "打开闪光灯"
                                },
                                active = flashOn && flashAvailable,
                                enabled = flashAvailable,
                                overPhoto = true,
                                onClick = onToggleFlash,
                                icon = { tint ->
                                    GlassIcon(
                                        kind = if (flashOn && flashAvailable) {
                                            GlassIconKind.Flash
                                        } else {
                                            GlassIconKind.FlashOff
                                        },
                                        tint = tint,
                                        filled = flashOn && flashAvailable,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(GlassSpace.gap))
        }
        }

        // ---- 横版：控件排到左右两侧（D-083）----
        // 横屏只有 361dp 高，竖版那套"上下堆叠"会把取景窗压到两百来 dp；把控件挪到
        // 两侧以后，窗能吃满高度，才像一台横着拿的相机。
        if (landscape) {
            // 左上：胶卷芯片
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = GlassSpace.page, top = GlassSpace.tight),
            ) {
                GlassChip(
                    text = if (shotsWithStock > 0) "$stockName · $shotsWithStock" else stockName,
                    overPhoto = true,
                    onClick = onOpenFilms,
                    leading = { tint -> GlassIcon(GlassIconKind.Film, tint, size = 16.dp) },
                    trailing = { tint -> GlassIcon(GlassIconKind.Chevron, tint, size = 16.dp) },
                )
            }

            // 右上：EV + 设置
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = GlassSpace.page, top = GlassSpace.tight),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (exposureProfile != null) {
                    GlassIconButton(
                        label = "曝光补偿",
                        active = evOpen || evIndex != exposureProfile.index,
                        overPhoto = true,
                        size = 44.dp,
                        onClick = { evOpen = !evOpen },
                        icon = { tint -> GlassIcon(GlassIconKind.Exposure, tint) },
                    )
                }
                GlassIconButton(
                    label = if (gridOn) "关闭九宫格" else "打开九宫格",
                    active = gridOn,
                    overPhoto = true,
                    size = 44.dp,
                    onClick = onToggleGrid,
                    icon = { tint -> GlassIcon(GlassIconKind.Grid, tint) },
                )
                GlassIconButton(
                    label = "设置",
                    overPhoto = true,
                    size = 44.dp,
                    onClick = onOpenSettings,
                    icon = { tint -> GlassIcon(GlassIconKind.Gear, tint) },
                )
            }

            // 左中：相册 · 闪光
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = GlassSpace.page),
                verticalArrangement = Arrangement.spacedBy(GlassSpace.tight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GalleryKey(lastThumbnail, onOpenGallery)
                GlassIconButton(
                    label = when {
                        !flashAvailable -> "这台镜头没有闪光灯"
                        flashOn -> "关闭闪光灯"
                        else -> "打开闪光灯"
                    },
                    active = flashOn && flashAvailable,
                    enabled = flashAvailable,
                    overPhoto = true,
                    onClick = onToggleFlash,
                    icon = { tint ->
                        GlassIcon(
                            kind = if (flashOn && flashAvailable) {
                                GlassIconKind.Flash
                            } else {
                                GlassIconKind.FlashOff
                            },
                            tint = tint,
                            filled = flashOn && flashAvailable,
                        )
                    },
                )
            }

            // 右中：快门 · 九宫格（变焦轨在右上）
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = GlassSpace.page),
                verticalArrangement = Arrangement.spacedBy(GlassSpace.tight),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShutterKey(canShoot, onShutter)
                if (!evOpen && permissionGranted && viewfinder != null) {
                    ZoomRail(zoom = zoom, stops = lensStops, onZoomChange = onZoomChange)
                }
            }

            if (imprintLabel != null && !switcherOpen) {
                Text(
                    text = imprintLabel,
                    style = GlassType.badge,
                    color = p.onGlassMuted,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = GlassSpace.page, bottom = GlassSpace.gap),
                )
            }
        }

        // ---- 上滑抽屉：相册 / 胶卷 / 发现 / 设置 ----
        // 遮罩在下、面板在上；点遮罩或按返回都能关。
        AnimatedVisibility(
            visible = switcherOpen,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f))
                    .pointerInput(Unit) {
                        detectTapGestures { switcherOpen = false }
                    },
            )
        }

        AnimatedVisibility(
            visible = switcherOpen,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            DestinationSwitcher(
                onGallery = onOpenGallery,
                onFilms = onOpenFilms,
                onDiscover = onOpenDiscover,
                onSettings = onOpenSettings,
            )
        }

        BackHandler(enabled = switcherOpen) { switcherOpen = false }
    }
}

// ---------------------------------------------------------------------------
// 快门
// ---------------------------------------------------------------------------

/**
 * 快门：外圈玻璃环 + 内圈实心盘。
 *
 * 视觉 74dp、热区 74dp（远超 48dp 地板）。不可拍时**不隐藏**，只降透明度并禁用 ——
 * 按钮消失会让用户以为界面坏了，灰着还能读到上面的原因芯片。
 *
 * 按下有触感反馈（D-074）：玻璃按键没有涟漪，没有震动的话"按没按到"只能靠闪白，
 * 而闪白要等采集回来才看得到。
 */
@Composable
private fun ShutterKey(enabled: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    val haptics = LocalHapticFeedback.current
    val ring = if (enabled) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.30f)
    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .border(2.dp, ring, CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "快门",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { contentDescription = "快门" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.22f)),
        )
        // 中心一个大光圈点：玻璃面板上纯白圆盘容易和面板边界糊在一起，加个凹点定位。
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (enabled) p.background.copy(alpha = 0.16f) else Color.Transparent),
        )
    }
}

// ---------------------------------------------------------------------------
// 上滑抽屉（D-079）
// ---------------------------------------------------------------------------

/**
 * 从 dock 上滑打开的"去哪儿"面板：相册 / 胶卷 / 发现 / 设置。
 *
 * 为什么需要它：相机页是**根页面**，没有底部 TabBar。相册能从 dock 的缩略图进、
 * 胶卷能从顶部芯片进、设置只有右上角那颗齿轮——用户得记住"哪个入口在哪儿"，
 * 而"发现"在相机页根本进不去。抽屉把这四个摊开成一排，手势只有一种：从 dock 往上推。
 *
 * 视觉上沿用 dock：同一块玻璃面板、同一套 48dp 圆钮、同一条抓手。它是 dock 的
 * "向上展开态"，而不是另起一套弹层。
 */
@Composable
private fun DestinationSwitcher(
    onGallery: () -> Unit,
    onFilms: () -> Unit,
    onDiscover: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassSurface(
            modifier = Modifier
                .widthIn(max = CameraChromeMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = GlassSpace.page),
            shape = GlassShape.panel,
            overPhoto = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .padding(bottom = 10.dp)
                        .size(width = 32.dp, height = 3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.30f)),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SwitcherItem("相册", GlassIconKind.Gallery, onGallery)
                    SwitcherItem("胶卷", GlassIconKind.Film, onFilms)
                    SwitcherItem("发现", GlassIconKind.Sparkle, onDiscover)
                    SwitcherItem("设置", GlassIconKind.Gear, onSettings)
                }
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        Spacer(Modifier.height(GlassSpace.gap))
    }
}

@Composable
private fun SwitcherItem(label: String, kind: GlassIconKind, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    Column(
        modifier = Modifier
            .width(78.dp)
            .clip(GlassShape.chip)
            // 整格可点（圆钮只占 48dp，标签也要能点）；圆钮自己会吃掉点击，不会重复触发。
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassIconButton(
            label = label,
            overPhoto = true,
            onClick = onClick,
            icon = { tint -> GlassIcon(kind, tint) },
        )
        Spacer(Modifier.height(5.dp))
        Text(text = label, style = GlassType.badge, color = p.onGlassMuted)
    }
}

// ---------------------------------------------------------------------------
// 相册入口（最近一张）
// ---------------------------------------------------------------------------

@Composable
private fun GalleryKey(thumbnailPath: String?, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    val bitmap = rememberLastThumbnail(thumbnailPath)
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(p.fillStrong)
            .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = "打开相册",
                onClick = onClick,
            )
            .semantics { contentDescription = "打开相册" },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "最近一张成片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            GlassIcon(GlassIconKind.Gallery, p.onGlassMuted, size = 22.dp)
        }
    }
}

/**
 * 缩略图按 52dp 采样解码，只在 IO 线程跑；路径变了才重解。
 *
 * 复用相册页那套 [decodeSampledBitmap]：它先 `inJustDecodeBounds` 读尺寸、再按采样率
 * 解码，12MP 原图上不会解出 48MB 的位图。这里**不**缓存 —— 只有一张，且点位很小。
 */
@Composable
private fun rememberLastThumbnail(path: String?): ImageBitmap? {
    val density = LocalDensity.current
    val px = with(density) { 52.dp.roundToPx() }
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = path, key2 = px) {
        value = if (path == null) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeSampledBitmap(path, px, px)?.asImageBitmap() }
        }
    }
    return state.value
}

// ---------------------------------------------------------------------------
// 变焦
// ---------------------------------------------------------------------------

/**
 * 右侧竖排变焦轨。
 *
 * 档位来源优先真实光学镜头表（D-044）；探不到就退回 1×/2× 两档数码。
 * **0.6× 及以下一律不显示**：CameraX 上小于 1 的倍率会被 clamp 到 1，
 * 画出来就是一个点不动的按钮（旧问题 P1-13）。
 */
@Composable
private fun ZoomRail(
    modifier: Modifier = Modifier,
    zoom: Float,
    stops: List<LensStop>,
    onZoomChange: (Float) -> Float,
) {
    val options = remember(stops) {
        val optical = stops.map { it.zoomRatio }.filter { it >= 1f }
        if (optical.size >= 2) optical.distinct().sorted() else listOf(1f, 2f)
    }
    if (options.isEmpty()) return

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GlassSpace.tight),
    ) {
        options.forEach { ratio ->
            // 选中判定用相对误差 15%：真实倍率是 3.1999 这种值，精确相等永远不成立。
            val selected = abs(zoom - ratio) <= ratio * 0.15f
            // 用圆钮不用胶囊：右侧这条轨和顶部那排按钮是同一类控件，
            // 混着"胶囊 + 圆形"就会看着像一堆大大小小的椭圆。
            GlassIconButton(
                label = "变焦 ${formatRatio(ratio)}",
                active = selected,
                overPhoto = true,
                size = 44.dp,
                onClick = { onZoomChange(ratio) },
                icon = { tint ->
                    Text(text = formatRatio(ratio), style = GlassType.label, color = tint)
                },
            )
        }
    }
}

/** 取景页浮层（顶栏 / dock）的最大宽度：再宽就该按平板重新排，而不是把控件拉开。 */
private val CameraChromeMaxWidth = 560.dp

/**
 * 取景窗上下要留出的高度（估算值，用于在"顶栏与 dock 之间"给窗定高）。
 *
 * 顶：状态栏 + 顶栏 + 一点呼吸；底：dock + 导航栏 + 一点呼吸。
 * 这两个数只影响窗能有多大，不影响布局正确性 —— 窗总是居中在那个带子里。
 */
private val ViewfinderTopReserve = 96.dp
private val ViewfinderBottomReserve = 152.dp

/** 取景窗的圆角。比 dock 的 32dp 小一档：窗是"画框"，不该比机身更圆。 */
private val ViewfinderShape = RoundedCornerShape(26.dp)

/**
 * 取景角标：四角各一个 L 形短标。
 *
 * 有边框已经能说明"这是一块画面"，角标再加一层"取景器"的语义 ——
 * 它比画满的十字/网格克制，不会盖住画面内容。
 */
@Composable
private fun ViewfinderCornerMarks() {
    Canvas(Modifier.fillMaxSize()) {
        val inset = 12.dp.toPx()
        val length = 16.dp.toPx()
        val stroke = 2.dp.toPx()
        val color = Color.White.copy(alpha = 0.55f)
        val right = size.width - inset
        val bottom = size.height - inset

        drawLine(color, Offset(inset, inset), Offset(inset + length, inset), stroke, StrokeCap.Round)
        drawLine(color, Offset(inset, inset), Offset(inset, inset + length), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, inset), Offset(right - length, inset), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, inset), Offset(right, inset + length), stroke, StrokeCap.Round)
        drawLine(color, Offset(inset, bottom), Offset(inset + length, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(inset, bottom), Offset(inset, bottom - length), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right - length, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(right, bottom), Offset(right, bottom - length), stroke, StrokeCap.Round)
    }
}

private fun formatRatio(ratio: Float): String {
    val rounded = (ratio * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}×" else "$rounded×"
}

// ---------------------------------------------------------------------------
// EV
// ---------------------------------------------------------------------------

/**
 * EV 面板：真实范围来自 `ExposureProfile`，一次只挪一档。
 *
 * 不做滑杆：CameraX 的 `exposureCompensationStep` 常见是 1/3 或 1/6 EV，
 * 滑杆的连续值会被硬件四舍五入，界面与硬件读数会对不上（旧版就是这么踩的）。
 */
@Composable
private fun EvPanel(
    profile: ExposureProfile,
    index: Int,
    onChange: (Int) -> Unit,
) {
    val p = MaterialTheme.glass
    val ev = profile.evOf(index)
    GlassSurface(
        shape = GlassShape.card,
        overPhoto = true,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                label = "减少曝光",
                enabled = index > profile.minIndex,
                overPhoto = true,
                onClick = { onChange(index - 1) },
                icon = { tint -> GlassIcon(GlassIconKind.Remove, tint) },
            )
            Spacer(Modifier.width(GlassSpace.gap))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = (if (ev > 0f) "+" else "") + String.format("%.1f", ev) + " EV",
                    style = GlassType.numericLarge,
                    color = p.onGlass,
                )
                Text(
                    text = "上限 ${fmtEv(profile.maxEv)} / 下限 ${fmtEv(profile.minEv)}",
                    style = GlassType.badge,
                    color = p.onGlassMuted,
                )
            }
            Spacer(Modifier.width(GlassSpace.gap))
            GlassIconButton(
                label = "增加曝光",
                enabled = index < profile.maxIndex,
                overPhoto = true,
                onClick = { onChange(index + 1) },
                icon = { tint -> GlassIcon(GlassIconKind.Add, tint) },
            )
            Spacer(Modifier.width(GlassSpace.tight))
            GlassButton(
                text = "归零",
                primary = false,
                onClick = { onChange(0.coerceIn(profile.minIndex, profile.maxIndex)) },
            )
        }
    }
}

private fun fmtEv(value: Float): String =
    (if (value > 0f) "+" else "") + String.format("%.1f", value)

// ---------------------------------------------------------------------------
// 九宫格 / 对焦环 / 权限态
// ---------------------------------------------------------------------------

@Composable
private fun GridOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val color = Color.White.copy(alpha = 0.22f)
        val stroke = 1f
        for (i in 1..2) {
            drawLine(color, Offset(w * i / 3f, 0f), Offset(w * i / 3f, h), stroke)
            drawLine(color, Offset(0f, h * i / 3f), Offset(w, h * i / 3f), stroke)
        }
    }
}

/**
 * 对焦回执：四角括号 + 一行真实状态。
 *
 * 三态来自 `startFocusAndMetering` 的真实回调，**不是**"点了就一定锁定"——
 * 旧原型永远显示 AF LOCK，那是假话（T9-006）。
 */
@Composable
private fun FocusReticle(mark: FocusMark) {
    val density = LocalDensity.current
    val x = with(density) { mark.x.toDp() }
    val y = with(density) { mark.y.toDp() }
    val color = when (mark.state) {
        FocusState.Working -> Color.White.copy(alpha = 0.85f)
        FocusState.Locked -> Color(0xFF9BE7A0)
        FocusState.Failed -> Color(0xFFFFB4AB)
    }
    val label = when (mark.state) {
        FocusState.Working -> "对焦中"
        FocusState.Locked -> "AF LOCK"
        FocusState.Failed -> "AF FAIL"
    }
    val progress = remember(mark.at) { Animatable(0f) }
    LaunchedEffect(mark.at) { progress.animateTo(1f, tween(180)) }

    Box(
        Modifier
            .graphicsLayer {
                translationX = x.toPx() - 34.dp.toPx()
                translationY = y.toPx() - 34.dp.toPx()
                alpha = progress.value
                scaleX = 0.86f + 0.14f * progress.value
                scaleY = 0.86f + 0.14f * progress.value
            }
            .size(68.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension
            val arm = s * 0.26f
            val stroke = 1.6.dp.toPx()
            val inset = 2.dp.toPx()
            // 四个角，各两条短线
            listOf(
                Offset(inset, inset) to listOf(Offset(arm, 0f), Offset(0f, arm)),
                Offset(s - inset, inset) to listOf(Offset(-arm, 0f), Offset(0f, arm)),
                Offset(inset, s - inset) to listOf(Offset(arm, 0f), Offset(0f, -arm)),
                Offset(s - inset, s - inset) to listOf(Offset(-arm, 0f), Offset(0f, -arm)),
            ).forEach { (corner, arms) ->
                arms.forEach { d ->
                    drawLine(
                        color = color,
                        start = corner,
                        end = Offset(corner.x + d.x, corner.y + d.y),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = label,
            style = GlassType.badge,
            color = color,
        )
    }
}

@Composable
private fun PermissionState(
    granted: Boolean,
    reason: String?,
    onOpenAppSettings: () -> Unit,
) {
    val p = MaterialTheme.glass
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassSurface(
            shape = GlassShape.card,
            modifier = Modifier.padding(horizontal = 28.dp),
            contentPadding = PaddingValues(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GlassIcon(GlassIconKind.Warning, p.accent, size = 28.dp)
                Spacer(Modifier.height(GlassSpace.gap))
                Text(
                    text = if (granted) "相机还没准备好" else "需要相机权限",
                    style = GlassType.cardTitle,
                    color = p.onGlass,
                )
                Spacer(Modifier.height(GlassSpace.tight))
                Text(
                    text = reason ?: if (granted) {
                        "正在启动相机，稍等一下就好。"
                    } else {
                        "取景与拍照都依赖摄像头。权限被永久拒绝时，系统不会再弹窗，" +
                            "只能到应用设置里手动打开。"
                    },
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
                if (!granted) {
                    Spacer(Modifier.height(GlassSpace.inner))
                    GlassButton(text = "打开应用设置", onClick = onOpenAppSettings)
                }
            }
        }
    }
}
