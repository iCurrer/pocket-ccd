package com.pocketccd.prototype.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.camera.ExposureProfile
import com.pocketccd.prototype.camera.LensStop
import com.pocketccd.prototype.ui.artwork.filmArtworkRes
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.theme.ConsoleColor
import com.pocketccd.prototype.ui.theme.ConsoleElevation
import com.pocketccd.prototype.ui.theme.ConsoleShape
import com.pocketccd.prototype.ui.theme.ConsoleSpace
import com.pocketccd.prototype.ui.theme.ConsoleType
import com.pocketccd.prototype.ui.theme.consolePanel
import com.pocketccd.prototype.ui.theme.consolePress
import com.pocketccd.prototype.ui.theme.consoleRecess
import com.pocketccd.prototype.ui.theme.drawBevel
import com.pocketccd.prototype.ui.theme.drawInsetShade
import com.pocketccd.prototype.ui.theme.drawMachinedDisc
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 横屏拍摄台（Light Camera Console）。
 *
 * ## 它替掉了什么
 *
 * 上一版横屏是"预览 + 玻璃浮层"：控件是一堆压在画面上的深色玻璃芯片。能用，但读到的是
 * "一台手机在放视频"，不是"一台相机"（D-089 设计返工）。
 *
 * 这一版把整页当成**机身**：暖白面板、银灰键、黄铜刻度，取景窗是一块嵌进去的深色画框，
 * 左边是装着的胶卷，右边是快门与曝光，底下一条状态条。控件不再是浮在画面上的贴纸，
 * 而是**长在机身上**的零件 —— 所以画面可以更大，控件反而更清楚。
 *
 * ## 版面怎么算
 *
 * 横向只有 360dp 高（OnePlus 13 横屏 792×360dp），一寸都不能浪费：
 *
 * ```
 * ┌─ 品牌 ─┐┌────────── 取景窗（4:3）──────────┐┌─ 工具条 ─┐
 * │  胶卷  ││                                  ││  EV 刻度 │
 * │  卡    ││      实时预览 + 角标 + 变焦       ││  快门    │
 * │        ││                                  ││ 缩略/闪光 │
 * └────────┘└──────────────────────────────────┘└──────────┘
 * ┌──────────────── 状态条（横跨） ─────────────────────────┐
 * ```
 *
 * 品牌与工具条**不占一整行**，而是各自坐在左右两列的顶上 —— 参考图就是这么排的，
 * 这样取景窗能吃满剩余高度（约 73% 屏高），而不是被一条顶栏啃掉 40dp。
 *
 * ## 数据口径
 *
 * 这一页显示的每一个数字都来自真实状态：张数、变焦倍率、EV（`profile.evOf(index)`）、
 * 闪光灯开/关/无、贴花状态。**没有 ISO、快门时间、光圈、白平衡** —— CameraX 没有
 * 对应接口，写上去就是编的。参考图底部那条 `ISO 200 / 1/250s / F5.6 / 5500K` 明确不抄。
 */
@OptIn(ExperimentalTextApi::class)
@Composable
internal fun CameraConsole(
    stockName: String,
    /** pack 的稳定 id，用于查包装图（[filmArtworkRes]）。null = 没有卷。 */
    stockId: String?,
    /** 这一支胶卷的画幅（pack 帧尺寸，如 `4000×3000`）。null = 还没扫到，不显示。 */
    stockDetail: String?,
    shotsWithStock: Int,
    flashOn: Boolean,
    flashAvailable: Boolean,
    gridOn: Boolean,
    canShoot: Boolean,
    blockReason: String?,
    notice: String?,
    imprintLabel: String?,
    lastThumbnail: String?,
    zoom: Float,
    lensStops: List<LensStop>,
    exposureProfile: ExposureProfile?,
    evIndex: Int,
    permissionGranted: Boolean,
    /** EV 精调面板是否展开（顶栏那颗 EV 键控制）。 */
    evOpen: Boolean,
    focus: FocusMark?,
    switcherOpen: Boolean,
    viewfinder: (@Composable (Modifier) -> Unit)?,
    onShutter: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onToggleEv: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFilms: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenSwitcher: () -> Unit,
    onCloseSwitcher: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onTapToFocus: (Offset) -> Unit,
    onZoomChange: (Float) -> Float,
    onEvChange: (Int) -> Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // 横屏时状态栏在顶、手势条在底，两块都会压到控件。取景窗四周让开它们（同 D-080 的理由）。
    val statusTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    // 左右还要让开**挖孔/侧边导航**：横屏时前摄孔几乎总落在左（或右）边，
    // 机身面板贴到 12dp 就会被那一眼黑孔咬掉一块。safeDrawing 同时覆盖挖孔与侧边栏。
    val safeLeft = with(density) {
        WindowInsets.safeDrawing.getLeft(this, layoutDirection).toDp()
    }
    val safeRight = with(density) {
        WindowInsets.safeDrawing.getRight(this, layoutDirection).toDp()
    }
    val haptics = LocalHapticFeedback.current
    val swipeUpThresholdPx = with(density) { 40.dp.toPx() }

    val ready = permissionGranted && viewfinder != null

    BoxWithConstraints(modifier.fillMaxSize().background(ConsoleColor.body)) {
        val footerHeight = 34.dp
        // 贴合脸/掌心的两块边（上=状态栏、下=手势条）必须留够空气：手势条那条白线
        // 紧贴着状态条时，整页会显得被"顶到屏幕边上"。底部再兜一个 14dp 的地板 ——
        // 有些 ROM 在横屏把 navigationBars 报成 0，此时就只剩地板撑着。
        val topGap = statusTop + ConsoleSpace.tight
        val bottomGap = maxOf(14.dp, navBottom + 8.dp)
        val pageStart = ConsoleSpace.page + safeLeft
        val pageEnd = ConsoleSpace.page + safeRight
        // 其余全给三栏。coerceAtLeast 是给小窗（分屏）兜底，免得约束为负时 Row 塌成 0 高。
        val rowHeight = (
            maxHeight - topGap - bottomGap - footerHeight - ConsoleSpace.tight
            ).coerceAtLeast(150.dp)
        val rowWidth = (maxWidth - pageStart - pageEnd).coerceAtLeast(240.dp)
        val gap = ConsoleSpace.gap

        // 三栏宽度：取景窗先按 4:3 吃满高度，剩下的宽度给左右两列。
        // **必须让取景窗先拿**：它是页面主体，左右两列是"机身侧边"，被挤窄只是更紧凑。
        //
        // 但左右两列各有**上限**（左 180 / 右 186）：4:3 在 2.2:1 的屏上横向必然富余
        // 150dp 左右，若把这些富余都塞给侧栏，胶卷卡会变成一张 230dp 宽、里面只有
        // 一支小胶卷的空卡。上限之外的部分交给 `Arrangement.Center` 摊成页面留白 ——
        // 那正是机身四周该有的"边"，也是躲开挖孔之后看着最舒服的一版。
        val minSide = 132.dp + 152.dp
        val bezel = ViewfinderBezel
        val viewfinderWidth = minOf(
            (rowHeight - bezel * 2) * 4f / 3f,
            (rowWidth - minSide - gap * 2).coerceAtLeast(120.dp),
        )
        val viewfinderHeight = viewfinderWidth * 3f / 4f
        val sideTotal = (rowWidth - viewfinderWidth - gap * 2).coerceAtLeast(minSide)
        val rightWidth = (sideTotal * 0.48f).coerceIn(152.dp, 186.dp)
        val leftWidth = (sideTotal - rightWidth).coerceIn(132.dp, 180.dp)

        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(topGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .padding(start = pageStart, end = pageEnd),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左列：品牌 + 胶卷卡
                Column(Modifier.width(leftWidth).fillMaxHeight()) {
                    ConsoleBrand(ready = canShoot)
                    Spacer(Modifier.height(ConsoleSpace.tight))
                    FilmPanel(
                        stockName = stockName,
                        stockId = stockId,
                        stockDetail = stockDetail,
                        shotsWithStock = shotsWithStock,
                        onClick = onOpenFilms,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                }

                Spacer(Modifier.width(gap))

                ConsoleViewfinder(
                    modifier = Modifier.size(
                        width = viewfinderWidth + bezel * 2,
                        height = viewfinderHeight + bezel * 2,
                    ),
                    bezel = bezel,
                    ready = ready,
                    gridOn = gridOn,
                    focus = focus,
                    zoom = zoom,
                    lensStops = lensStops,
                    permissionGranted = permissionGranted,
                    blockReason = blockReason,
                    viewfinder = viewfinder,
                    onTapToFocus = onTapToFocus,
                    onOpenAppSettings = onOpenAppSettings,
                    onZoomChange = onZoomChange,
                )

                Spacer(Modifier.width(gap))

                // 右列：工具条 + EV + 快门 + 缩略图/闪光
                Column(Modifier.width(rightWidth).fillMaxHeight()) {
                    ConsoleToolbar(
                        gridOn = gridOn,
                        evOpen = evOpen,
                        evAdjusted = exposureProfile != null && evIndex != exposureProfile.index,
                        hasExposure = exposureProfile != null,
                        onToggleGrid = onToggleGrid,
                        onToggleEv = onToggleEv,
                        onOpenSettings = onOpenSettings,
                    )
                    Spacer(Modifier.height(ConsoleSpace.tight))
                    EvSection(
                        profile = exposureProfile,
                        index = evIndex,
                        expanded = evOpen,
                        // onEvChange 返回"实际生效的档位"（机型可能只给 ±1）。
                        // 这里不接返回值：父层已经把 applied 写回 evIndex，界面下一帧自己对齐。
                        onChange = { next -> onEvChange(next) },
                    )
                    Spacer(Modifier.height(ConsoleSpace.tight))
                    // 快门吃掉剩下的高度：面板高度变了（EV 展开、屏更矮）它自己缩，不会溢出。
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val diameter = minOf(maxWidth, maxHeight).coerceIn(64.dp, 108.dp)
                        ShutterButton(enabled = canShoot, size = diameter, onClick = onShutter)
                    }
                    Spacer(Modifier.height(ConsoleSpace.tight))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ThumbnailKey(
                            path = lastThumbnail,
                            size = 48.dp,
                            onClick = onOpenGallery,
                        )
                        Spacer(Modifier.width(ConsoleSpace.gap))
                        ConsoleToolButton(
                            label = when {
                                !flashAvailable -> "这台镜头没有闪光灯"
                                flashOn -> "关闭闪光灯"
                                else -> "打开闪光灯"
                            },
                            active = flashOn && flashAvailable,
                            enabled = flashAvailable,
                            onClick = onToggleFlash,
                        ) {
                            GlassIcon(
                                kind = if (flashOn && flashAvailable) {
                                    GlassIconKind.Flash
                                } else {
                                    GlassIconKind.FlashOff
                                },
                                tint = it,
                                size = 20.dp,
                                filled = flashOn && flashAvailable,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(ConsoleSpace.tight))

            ConsoleStatusBar(
                stockName = stockName,
                shotsWithStock = shotsWithStock,
                zoom = zoom,
                ev = exposureProfile?.evOf(evIndex),
                flashOn = flashOn,
                flashAvailable = flashAvailable,
                imprintLabel = imprintLabel,
                alert = blockReason ?: notice,
                alertIsBlocking = blockReason != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(footerHeight)
                    .padding(start = pageStart, end = pageEnd)
                    // 上滑打开"去哪儿"抽屉（D-079 的手势在横版没有 dock 可挂，改挂状态条）。
                    // 挂在这条上而不是整屏：整屏会跟取景器的点按对焦抢事件。
                    .pointerInput(Unit) {
                        var dragged = 0f
                        detectVerticalDragGestures(
                            onDragStart = { dragged = 0f },
                            onDragCancel = { dragged = 0f },
                            onVerticalDrag = { _, amount -> dragged += amount },
                            onDragEnd = {
                                if (dragged < -swipeUpThresholdPx) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onOpenSwitcher()
                                }
                            },
                        )
                    },
            )

            Spacer(Modifier.height(bottomGap))
        }

        // ---- 抽屉：相机页是根页面，没有 TabBar，去别的页面只有这一条路 ----
        AnimatedVisibility(
            visible = switcherOpen,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f))
                    .pointerInput(Unit) { detectTapGestures { onCloseSwitcher() } },
            )
        }

        AnimatedVisibility(
            visible = switcherOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        ) {
            ConsoleSwitcher(
                onGallery = onOpenGallery,
                onFilms = onOpenFilms,
                onDiscover = onOpenDiscover,
                onSettings = onOpenSettings,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 机身零件
// ---------------------------------------------------------------------------

/** 品牌行与工具条共用的高度：两列顶部必须同高，否则胶卷卡和 EV 刻度会错开。 */
private val ToolStripHeight = 44.dp

/**
 * 取景窗四周的亮色边框（机身镁合金边）。
 *
 * 深色屏幕直接贴在暖白机身上时，交界处会糊成一条脏边 —— 参考图里那一圈浅灰边不是装饰，
 * 是"屏幕嵌在机身里"的唯一证据。它是**画在 4:3 画幅外面**的，不吃画面像素。
 */
private val ViewfinderBezel = 3.dp

/**
 * 按下进度 0..1。
 *
 * 返回 [Animatable] 而不是 Float：**缩放和内阴影读的是同一个值**，而且都在图层/绘制
 * 阶段读（`graphicsLayer{}` / `consolePress{}`），按压动画不触发重组。实体键的手感
 * 来自"轮廓收一点 + 表面陷进去"两件事同相位发生，分两个动画各跑各的就会晃。
 */
@Composable
private fun rememberPressAmount(active: Boolean): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(active) {
        progress.animateTo(
            targetValue = if (active) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        )
    }
    return progress
}

/** 左上角：品牌字 + 状态灯。灯亮=快门可用，灯暗=不可拍（不编第三种状态）。 */
@Composable
private fun ConsoleBrand(ready: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ToolStripHeight).padding(start = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Pocket CCD",
            style = ConsoleType.brand,
            color = ConsoleColor.primaryText,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (ready) ConsoleColor.accent else ConsoleColor.mutedText.copy(alpha = 0.55f),
                ),
        )
    }
}

/** 右上角工具条：九宫格 · 曝光补偿 · 设置。全部绑真实回调，没有装饰键。 */
@Composable
private fun ConsoleToolbar(
    gridOn: Boolean,
    evOpen: Boolean,
    evAdjusted: Boolean,
    hasExposure: Boolean,
    onToggleGrid: () -> Unit,
    onToggleEv: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(ToolStripHeight),
        horizontalArrangement = Arrangement.spacedBy(ConsoleSpace.tight, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConsoleToolButton(
            label = if (gridOn) "关闭九宫格" else "打开九宫格",
            active = gridOn,
            onClick = onToggleGrid,
        ) { tint -> GlassIcon(GlassIconKind.Grid, tint, size = 20.dp) }

        // 没有曝光档位表（相机会话还没绑上）时不画这颗键：画了也是点了没反应的假键。
        if (hasExposure) {
            ConsoleToolButton(
                label = "曝光补偿 ${if (evOpen) "收起微调" else "微调"}",
                active = evOpen || evAdjusted,
                onClick = onToggleEv,
            ) { tint -> GlassIcon(GlassIconKind.Exposure, tint, size = 20.dp) }
        }

        ConsoleToolButton(label = "设置", onClick = onOpenSettings) { tint ->
            GlassIcon(GlassIconKind.Gear, tint, size = 20.dp)
        }
    }
}

/**
 * 圆形工具键：**一颗装在机身上的实体键**（D-094）。
 *
 * 上一版是一块圆板 + 1dp 描边，远看和胶卷卡是同一个明度，读不出"能按"。新版是两层：
 *
 * 1. **外壳**：凸起的浅盘，双向倒角（左上一圈亮、右下一圈暗）。
 * 2. **内凹面**：比外壳小 3dp 的一圈，用 [consoleRecess] 压进去 —— 键帽中间是钝的、
 *    外沿是薄的，这个明暗关系才让它像"车出来的一颗键"而不是"一个圆图标"。
 *
 * 视觉 44dp —— 比 Material 的 48dp 地板小一档，这是量过的取舍：
 * 右侧控制列在 792dp 宽的横屏上只有 ~180dp，三颗 48dp 的键连间距要 160dp，
 * 键再大就得把取景窗挤窄。44dp 在 3x 屏上是 11mm，实际点击没问题。
 */
@Composable
private fun ConsoleToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 44.dp,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressAmount(pressed && enabled)
    val tint = when {
        !enabled -> ConsoleColor.mutedText.copy(alpha = 0.5f)
        active -> ConsoleColor.accentDeep
        else -> ConsoleColor.secondaryText
    }

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val scale = 1f - 0.07f * press.value
                scaleX = scale
                scaleY = scale
            }
            .consolePanel(
                color = if (active) {
                    ConsoleColor.accent.copy(alpha = 0.30f)
                } else {
                    ConsoleColor.panelLight
                },
                shape = CircleShape,
                elevation = if (enabled) ConsoleElevation.key + 1.dp else 2.dp,
                borderColor = if (active) ConsoleColor.accent else ConsoleColor.bevelEdge,
                bevel = if (enabled) 1f else 0.5f,
            )
            // 按下：沿圆轮廓压出一圈内阴影，看起来是"陷进去"而不是"缩一下"。
            .consolePress(shape = CircleShape, pressed = { press.value }, blur = 4.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        // 内凹面：键帽的"顶面"。它和外沿之间那一圈明暗，就是这颗键的厚度。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp)
                .consoleRecess(
                    shape = CircleShape,
                    // 选中态的内凹面不换色（换色会和外壳的金底打架），
                    // 靠外壳那一圈金边 + 深金图标表达。
                    color = Color.Transparent,
                    depth = 3.5.dp,
                    shade = if (enabled) 0.26f else 0.12f,
                    rimLight = 0.62f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            icon(tint)
        }
    }
}

/**
 * 左侧胶卷卡：包装图 + 卷名 + 张数 + 切换入口。
 *
 * 整张卡都是点击区（`onOpenFilms`）。卡里的"切换胶卷"条**不再单独挂点击** ——
 * 两个可点节点套在一起，读屏会念两遍、点击会触发两次；它只是把"点这里会发生什么"写出来。
 */
@Composable
private fun FilmPanel(
    stockName: String,
    /** pack 的稳定 id（文件名去扩展名）。用它查包装图；null = 没有卷，走占位暗盒。 */
    stockId: String?,
    stockDetail: String?,
    shotsWithStock: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressAmount(pressed)
    // 包装图资源在这里解析一次。`painterResource` 内部按资源 id 缓存解码结果，
    // 重组不会重复解码；换卷只换 id，等于只换这一张。
    val artworkRes = filmArtworkRes(stockId)
    val artwork = artworkRes?.let { painterResource(it) }
    val meta = if (stockDetail != null) {
        "已拍 $shotsWithStock 张 · $stockDetail"
    } else {
        "已拍 $shotsWithStock 张"
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                val scale = 1f - 0.015f * press.value
                scaleX = scale
                scaleY = scale
            }
            .consolePanel(
                color = ConsoleColor.panelLight,
                shape = ConsoleShape.panel,
                elevation = 9.dp,
                // 大面积件的倒角要收：同样的强度摊在 180dp 的卡上会读成"塑料壳"。
                bevel = 0.55f,
            )
            // 整张卡也是可点的（进胶卷库）。大面积件的内阴影要淡 ——
            // 卡越大，同样的浓度越像一块凹陷的塑料板。
            .consolePress(shape = ConsoleShape.panel, pressed = { press.value }, strength = 0.20f)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClickLabel = "切换胶卷",
                onClick = onClick,
            )
            .semantics {
                contentDescription = "当前胶卷 $stockName，$meta，点击切换胶卷"
            }
            .padding(ConsoleSpace.inner),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 包装图容器：2:3 竖向，高由可用空间决定，宽度跟着算 ——
        // 换成真图只换里面的画家，容器不变，版面不动。
        BoxWithConstraints(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val height = minOf(maxHeight, maxWidth * 1.5f)
            FilmArtwork(
                modifier = Modifier.size(width = height * 2f / 3f, height = height),
                painter = artwork,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = stockName,
            style = ConsoleType.filmName,
            color = ConsoleColor.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = meta,
            style = ConsoleType.filmMeta,
            color = ConsoleColor.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(10.dp))
        // "切换胶卷"是卡上**挖出来的**一格，不是又贴了一张小卡（D-094）：
        // 它不承接点击（整张卡都可点），所以它必须读成"槽"而不是"键"。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .consoleRecess(
                    shape = ConsoleShape.info,
                    color = ConsoleColor.body.copy(alpha = 0.92f),
                    depth = 3.dp,
                    shade = 0.30f,
                    // 槽有 180dp 宽，0.55 的底边反光会连成一条白线，像"高光条"而不是"槽底"。
                    rimLight = 0.40f,
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            GlassIcon(GlassIconKind.Film, ConsoleColor.secondaryText, size = 14.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "切换胶卷",
                style = ConsoleType.button,
                color = ConsoleColor.primaryText,
            )
            Spacer(Modifier.width(4.dp))
            GlassIcon(GlassIconKind.Chevron, ConsoleColor.mutedText, size = 14.dp)
        }
    }
}

/**
 * 胶卷包装图。2:3，装进卡上的**凹槽**里。
 *
 * [painter] 来自 [filmArtworkRes]（真图，`docs/artwork/film/` 生成、透明底）；
 * 为 null 时画 Canvas 占位暗盒 —— 用户自己 push 进 `files/film/` 的 pack 没有图，
 * 必须有个能看的东西，不能是空白。两条路共用同一个容器、比例和凹槽。
 */
@Composable
private fun FilmArtwork(modifier: Modifier = Modifier, painter: Painter? = null) {
    Box(
        // 装胶卷的那一格是**凹槽**：胶卷筒立在卡片里，不是贴在卡片上。
        // 换真图后这层凹槽仍然成立 —— 影棚产品图本来就带自己的投影，
        // 再加一圈"墙"反而说明它是被放在台面上拍的，而不是贴上去的。
        modifier = modifier.consoleRecess(
            shape = ConsoleShape.artwork,
            color = ConsoleColor.panel,
            depth = 4.dp,
            shade = 0.26f,
            rimLight = 0.60f,
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                // 内缩 4dp：图是 2:3、槽也是 2:3，不留边的话 Fit 会**正好铺满**，
                // 四壁的内阴影被整块盖掉，凹陷感归零 —— 看起来就是一张贴纸，
                // 而不是"立在槽里的一支卷"（截图里抓到的就是这个）。
                modifier = Modifier.fillMaxSize().padding(4.dp),
                // Fit + 透明底：真图长宽比可以跟容器不同，也不能被裁掉边角。
                contentScale = ContentScale.Fit,
            )
        } else {
            Canvas(Modifier.fillMaxSize()) { drawCartridgePlaceholder() }
        }
    }
}

/**
 * 暗盒轮廓：罐身 + 顶轴 + 标签带 + 底缘。
 *
 * 几处让它是"胶卷"而不是"笔记本/电池"的细节：
 *
 * 1. **罐身矮胖**（宽:高 ≈ 1:1.15）。竖长条 + 横线格 = 一眼笔记本；
 * 2. **标签带只占中段，且只有一行抽象字**（外加上面一个小色块当 logo 位）。
 *    三行横线是"横格纸"，一行才是包装；
 * 3. **顶轴很窄**，是根卷轴头，不是手机摄像头模组；
 * 4. **横向高光 + 上下压暗**给出圆柱感。
 *
 * 只描述形体，不描述任何一家的包装 —— 真图以后单独生成。
 */
private fun DrawScope.drawCartridgePlaceholder() {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    // 罐身按**容器高度**定尺寸（不是按宽度）：真暗盒是竖长的一根，
    // 按宽度算会在这张 2:3 的容器里缩成中间一小块，四周全是白边。
    val bodyHeight = minOf(h * 0.66f, w * 1.34f)
    val bodyWidth = bodyHeight * 0.84f
    val left = (w - bodyWidth) / 2f
    val top = (h - bodyHeight) / 2f
    val bodyCorner = CornerRadius(4.dp.toPx())
    val dark = Color(0xFF3B3A35)
    val stroke = 1.dp.toPx()

    // 落地阴影：罐子**站在**凹槽里，不是浮着。没有这一层，整张卡片会读成平面贴图。
    // 用 canvas 的纵向压缩 + 径向渐变画一个椭圆，比 `drawOval` 的硬边自然。
    drawIntoCanvas { canvas ->
        canvas.save()
        canvas.translate(left + bodyWidth / 2f, top + bodyHeight * 0.985f)
        canvas.scale(1f, 0.22f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                center = Offset.Zero,
                radius = bodyWidth * 0.78f,
            ),
            radius = bodyWidth * 0.78f,
            center = Offset.Zero,
        )
        canvas.restore()
    }

    // 罐身
    drawRoundRect(
        color = Color(0xFFF6F4EC),
        topLeft = Offset(left, top),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = bodyCorner,
    )

    // 顶盖：一个暗色椭圆。**这是"圆柱"唯一无可替代的证据** —— 少了它，
    // 无论标签画得多细，整体都是一本小书（第一版就是这个毛病）。
    // 高度给到 0.24：0.16 的椭圆看着像一只盘子悬在罐口上。
    val capHeight = bodyHeight * 0.24f
    drawOval(
        // 顶盖别用深灰：暗盒的盖是同色金属，深灰会读成"罐子开了个口"。
        color = Color(0xFF8E8A81),
        topLeft = Offset(left, top - capHeight * 0.46f),
        size = Size(bodyWidth, capHeight),
    )
    // 顶盖的受光面：椭圆上半部一道亮弧，金属盖才不会是一块平涂。
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.45f), Color.Transparent),
            startY = top - capHeight * 0.46f,
            endY = top + capHeight * 0.54f,
        ),
        topLeft = Offset(left, top - capHeight * 0.46f),
        size = Size(bodyWidth, capHeight),
    )
    // 卷轴孔：椭圆正中一个小槽。
    drawOval(
        color = Color(0xFF23221E).copy(alpha = 0.70f),
        topLeft = Offset(left + bodyWidth * 0.41f, top - capHeight * 0.05f),
        size = Size(bodyWidth * 0.18f, capHeight * 0.26f),
    )
    // 从顶盖伸出来的卷轴头：窄、短，是"能转"的那一小截。
    val spoolWidth = bodyWidth * 0.12f
    val spoolHeight = capHeight * 0.58f
    drawRoundRect(
        color = Color(0xFF38352F),
        topLeft = Offset(left + (bodyWidth - spoolWidth) / 2f, top - capHeight * 0.96f),
        size = Size(spoolWidth, spoolHeight),
        cornerRadius = CornerRadius(spoolWidth * 0.42f),
    )
    // 轴的受光侧
    drawRoundRect(
        color = Color.White.copy(alpha = 0.26f),
        topLeft = Offset(left + (bodyWidth - spoolWidth) / 2f, top - capHeight * 0.96f),
        size = Size(spoolWidth * 0.36f, spoolHeight),
        cornerRadius = CornerRadius(spoolWidth * 0.42f),
    )

    // 标签带：黄铜色，只占中段 —— 只用自己的强调色，不写任何厂商色
    // 标签几乎包满罐身，只在上沿和底缘各留一道白 —— 这才是"包了膜的暗盒"，
    // 留太多白会变成一张卡片。上沿留白要给够顶盖压下来的那一截（0.16）。
    val bandTop = top + bodyHeight * 0.17f
    val bandHeight = bodyHeight * 0.70f
    drawRect(
        color = ConsoleColor.accent.copy(alpha = 0.92f),
        topLeft = Offset(left + stroke, bandTop),
        size = Size(bodyWidth - stroke * 2f, bandHeight),
    )
    // 标签上的"logo 位"：一个小色块 + 一行抽象字，不画假文字
    drawRect(
        color = dark.copy(alpha = 0.55f),
        topLeft = Offset(left + bodyWidth * 0.16f, bandTop + bandHeight * 0.20f),
        size = Size(bodyWidth * 0.20f, bandHeight * 0.16f),
    )
    drawLine(
        color = dark.copy(alpha = 0.45f),
        start = Offset(left + bodyWidth * 0.16f, bandTop + bandHeight * 0.62f),
        end = Offset(left + bodyWidth * 0.84f, bandTop + bandHeight * 0.62f),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = dark.copy(alpha = 0.30f),
        start = Offset(left + bodyWidth * 0.16f, bandTop + bandHeight * 0.78f),
        end = Offset(left + bodyWidth * 0.60f, bandTop + bandHeight * 0.78f),
        strokeWidth = 1.5.dp.toPx(),
        cap = StrokeCap.Round,
    )

    // 圆柱高光：左亮右暗 + 上下压暗，罐身才不是一块平板
    drawRoundRect(
        brush = Brush.horizontalGradient(
            listOf(
                Color.White.copy(alpha = 0.34f),
                Color.Transparent,
                Color.Black.copy(alpha = 0.08f),
            ),
            startX = left,
            endX = left + bodyWidth,
        ),
        topLeft = Offset(left, top),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = bodyCorner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
            startY = top,
            endY = top + bodyHeight * 0.16f,
        ),
        topLeft = Offset(left, top),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = bodyCorner,
    )

    // 底缘 + 边框
    drawRoundRect(
        color = dark.copy(alpha = 0.62f),
        topLeft = Offset(left + bodyWidth * 0.08f, top + bodyHeight - 3.dp.toPx()),
        size = Size(bodyWidth * 0.84f, 4.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
    drawRoundRect(
        color = ConsoleColor.border,
        topLeft = Offset(left, top),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = bodyCorner,
        style = Stroke(stroke),
    )
}

// ---------------------------------------------------------------------------
// 取景窗
// ---------------------------------------------------------------------------

@Composable
private fun ConsoleViewfinder(
    modifier: Modifier,
    bezel: Dp,
    ready: Boolean,
    gridOn: Boolean,
    focus: FocusMark?,
    zoom: Float,
    lensStops: List<LensStop>,
    permissionGranted: Boolean,
    blockReason: String?,
    viewfinder: (@Composable (Modifier) -> Unit)?,
    onTapToFocus: (Offset) -> Unit,
    onOpenAppSettings: () -> Unit,
    onZoomChange: (Float) -> Float,
) {
    // 外层 = 机身里的那一圈亮边；内层 = 真正的 4:3 画面（也是点按对焦的坐标原点）。
    // 亮边画在画幅**外面**，所以窗口尺寸 +bezal*2，画面本身一米没少。
    Box(
        modifier = modifier
            .shadow(
                elevation = 16.dp,
                shape = ConsoleShape.viewfinder,
                clip = false,
                ambientColor = ConsoleColor.shadowContact,
                spotColor = ConsoleColor.shadowContact,
            )
            .clip(ConsoleShape.viewfinder)
            .background(ConsoleColor.panelDark)
            .drawWithContent {
                // 镁合金边的倒角：左上受光、右下背光。取景窗是整页最深的件，
                // 没有这圈明暗，它读起来就是"挖了个洞"，而不是"嵌了一块屏"。
                drawBevel(ConsoleShape.viewfinder, strength = 0.85f, width = 4.dp)
                // 上沿受光（同面板同一套光），下沿一根更深的线收口。
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.75f),
                            Color.Transparent,
                        ),
                        startY = 0f,
                        endY = bezel.toPx() * 1.6f,
                    ),
                )
                drawContent()
            }
            .border(1.dp, ConsoleColor.border, ConsoleShape.viewfinder)
            .padding(bezel),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ConsoleShape.viewfinderInner)
                .background(ConsoleColor.viewfinder),
        ) {
            if (ready && viewfinder != null) {
                viewfinder(Modifier.fillMaxSize())

                // 暗角：只压边缘亮度，不碰像素（D-041 的规矩在浅色机身里照样成立）。
                Box(
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.radialGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.30f)),
                                    center = Offset(size.width / 2f, size.height * 0.46f),
                                    radius = size.maxDimension * 0.62f,
                                ),
                            )
                        },
                )

                if (gridOn) ConsoleGrid()

                // 点按层：坐标是**取景窗自己的像素**，直接交给 meteringPointFactory。
                // 这一层铺满画面、且没有被任何内边距挤过，所以不需要反过来换算。
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { offset -> onTapToFocus(offset) }
                        },
                )

                focus?.let { FocusReticle(it) }
                ConsoleCornerMarks()

                ZoomBadge(
                    text = "4:3",
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                )

                ConsoleZoomBar(
                    zoom = zoom,
                    stops = lensStops,
                    onZoomChange = onZoomChange,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                )
            } else {
                ConsoleBlocked(
                    permissionGranted = permissionGranted,
                    reason = blockReason,
                    onOpenAppSettings = onOpenAppSettings,
                )
            }

            // 画面内侧一圈极淡的暗线：深色玻璃的边缘需要一点自己的厚度，
            // 否则它和外面那圈亮边之间是硬切。
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, ConsoleColor.viewfinderEdge, ConsoleShape.viewfinderInner),
            )
        }
    }
}

/** 取景窗四角的 L 形角标（右上角故意不画：那颗角是"4:3"角标的位置，参考图也如此）。 */
@Composable
private fun ConsoleCornerMarks() {
    Canvas(Modifier.fillMaxSize()) {
        val inset = 14.dp.toPx()
        val length = 22.dp.toPx()
        val stroke = 1.5.dp.toPx()
        val color = ConsoleColor.onViewfinder.copy(alpha = 0.52f)
        val right = size.width - inset
        val bottom = size.height - inset

        listOf(
            Offset(inset, inset) to listOf(Offset(length, 0f), Offset(0f, length)),
            Offset(inset, bottom) to listOf(Offset(length, 0f), Offset(0f, -length)),
            Offset(right, bottom) to listOf(Offset(-length, 0f), Offset(0f, -length)),
        ).forEach { (corner, arms) ->
            arms.forEach { arm ->
                drawLine(
                    color = color,
                    start = corner,
                    end = Offset(corner.x + arm.x, corner.y + arm.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun ConsoleGrid() {
    Canvas(Modifier.fillMaxSize()) {
        val color = Color.White.copy(alpha = 0.20f)
        for (i in 1..2) {
            drawLine(color, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height), 1f)
            drawLine(color, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f), 1f)
        }
    }
}

/** 取景窗角标（画幅 / 变焦倍率）：深底胶囊，只为在画面上读得清。 */
@Composable
private fun ZoomBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = ConsoleType.tag, color = ConsoleColor.onViewfinder)
    }
}

/**
 * 取景窗底部的变焦档。
 *
 * 档位只来自 [lensStops]，并且**过滤掉小于 1× 的档**：CameraX 会把 <1 的倍率 clamp 到 1，
 * 画出来就是一个点不动的假按钮（旧问题 P1-13）。`lensStops` 为空时退回 1×/2× 两档数码，
 * 这是旧版就有的兜底，不是编造 —— 两档点下去都是真的 `setZoomRatio`。
 */
@Composable
private fun ConsoleZoomBar(
    zoom: Float,
    stops: List<LensStop>,
    onZoomChange: (Float) -> Float,
    modifier: Modifier = Modifier,
) {
    val options = remember(stops) {
        val optical = stops
            .filter { it.zoomRatio >= 1f }
            .distinctBy { (it.zoomRatio * 10f).roundToInt() }
            .sortedBy { it.zoomRatio }
        if (optical.size >= 2) optical else listOf(LensStop(1f), LensStop(2f))
    }

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.30f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { stop ->
            val ratio = stop.zoomRatio
            // 相对误差 15%：真实倍率是 3.1999 这类值，精确相等永远不成立。
            val selected = abs(zoom - ratio) <= ratio * 0.15f
            val label = stop.ratioLabel
            Box(
                modifier = Modifier
                    .size(width = 42.dp, height = 26.dp)
                    .clip(CircleShape)
                    // 选中档是**暖白的实体键**贴在深色画面上（参考图的读法）：
                    // 它不是"被点亮的字"，而是"机身上伸进画面的一颗键"。
                    .background(
                        if (selected) {
                            Color(0xFFF3F1E9)
                        } else {
                            Color.Black.copy(alpha = 0.26f)
                        },
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) {
                            ConsoleColor.accent
                        } else {
                            Color.White.copy(alpha = 0.18f)
                        },
                        shape = CircleShape,
                    )
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "变焦 $label",
                        onClick = { onZoomChange(ratio) },
                    )
                    .semantics {
                        contentDescription = buildString {
                            append("变焦 ")
                            append(label)
                            stop.equivalentFocalMm?.let {
                                append("，等效 ")
                                append(it)
                                append(" 毫米")
                            }
                            if (stop.optical) append("，光学镜头")
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = ConsoleType.tag,
                    color = if (selected) {
                        ConsoleColor.primaryText
                    } else {
                        ConsoleColor.onViewfinder.copy(alpha = 0.88f)
                    },
                )
            }
        }
    }
}

/** 无相机会话时的取景窗内容：说明 + 唯一的出口（应用设置）。 */
@Composable
private fun ConsoleBlocked(
    permissionGranted: Boolean,
    reason: String?,
    onOpenAppSettings: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassIcon(GlassIconKind.Warning, ConsoleColor.accent, size = 26.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (permissionGranted) "相机还没准备好" else "需要相机权限",
            style = ConsoleType.filmName,
            color = ConsoleColor.onViewfinder,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // 权限被拒时 blockReason 本身就是"需要相机权限才能取景"，跟标题重复；
            // 那种情况换成解释"为什么只能去应用设置"，不然框里就是同一句话说两遍。
            text = when {
                !permissionGranted && (reason == null || reason.contains("权限")) ->
                    "取景与拍照都依赖摄像头。权限被永久拒绝时系统不会再弹窗，只能到应用设置里手动打开。"
                reason != null -> reason
                else -> "正在启动相机，稍等一下就好。"
            },
            style = ConsoleType.filmMeta,
            color = ConsoleColor.onViewfinder.copy(alpha = 0.60f),
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (!permissionGranted) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .consolePanel(
                        color = ConsoleColor.accent.copy(alpha = 0.22f),
                        shape = CircleShape,
                        elevation = 0.dp,
                        borderColor = ConsoleColor.accent.copy(alpha = 0.75f),
                        bevel = 0.6f,
                    )
                    .clickable(role = Role.Button, onClickLabel = "打开应用设置", onClick = onOpenAppSettings)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "打开应用设置",
                    style = ConsoleType.button,
                    color = ConsoleColor.accentLight,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 曝光
// ---------------------------------------------------------------------------

/**
 * EV 刻度：真实范围来自 [ExposureProfile]，一格就是硬件的一档（常见 1/3 EV）。
 *
 * 交互只有一种：**按住 / 拖动刻度**，落点换算成最近的一档。直接拖动而不做滑杆，
 * 是因为滑杆的连续值会被硬件四舍五入，界面和硬件读数会对不上（旧版踩过一次）。
 * 松开后再想微调一步，用顶栏那颗 EV 键展开的 −/+ 行。
 *
 * 没有任何曝光读数时（profile 为空）整块不画，只留一行灰字说明 —— 不画假刻度。
 */
@Composable
private fun EvSection(
    profile: ExposureProfile?,
    index: Int,
    expanded: Boolean,
    onChange: (Int) -> Unit,
) {
    if (profile == null) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "曝光 —",
                style = ConsoleType.readout,
                color = ConsoleColor.mutedText,
            )
        }
        return
    }

    val ev = profile.evOf(index)
    val adjusted = index != profile.index

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EvScale(
            profile = profile,
            index = index,
            onChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                // 两端各让 3dp：−2 / +2 正好压在刻度端点上，不留缝会贴着列边。
                .padding(horizontal = 3.dp),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "EV " + evText(ev),
            style = ConsoleType.readoutHero,
            color = if (adjusted) ConsoleColor.accentDeep else ConsoleColor.primaryText,
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvStepKey("减少曝光", GlassIconKind.Remove, index > profile.minIndex) {
                    onChange(index - 1)
                }
                EvStepKey("归零", null, index != 0 && 0 in profile.minIndex..profile.maxIndex) {
                    onChange(0)
                }
                EvStepKey("增加曝光", GlassIconKind.Add, index < profile.maxIndex) {
                    onChange(index + 1)
                }
            }
        }
    }
}

@Composable
private fun EvStepKey(
    label: String,
    icon: GlassIconKind?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(26.dp)
            // 小键也是"装上去的"，不是画上去的：凸起 + 倒角 + 1.5dp 的阴影。
            // 26dp 高再怎么压也只有 1dp 的余量可用，所以倒角强度给满、投影给轻。
            .consolePanel(
                color = if (enabled) ConsoleColor.panelLight else ConsoleColor.panel,
                shape = ConsoleShape.info,
                elevation = if (enabled) 2.dp else 0.dp,
                borderColor = if (enabled) ConsoleColor.bevelEdge else ConsoleColor.borderSoft,
                bevel = if (enabled) 0.9f else 0.3f,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            GlassIcon(
                kind = icon,
                tint = if (enabled) ConsoleColor.secondaryText else ConsoleColor.mutedText.copy(alpha = 0.5f),
                size = 16.dp,
            )
        } else {
            Text(
                text = "0",
                style = ConsoleType.readout,
                color = if (enabled) ConsoleColor.secondaryText else ConsoleColor.mutedText.copy(alpha = 0.5f),
            )
        }
    }
}

/**
 * 刻度条本体：整行 EV 值标签 + 每一档一根竖刻度 + 当前位置一根金色长针。
 *
 * 用 Canvas 画（而不是若干 Text 排一行）：标签必须**对在刻度的真实位置**上。
 * 若按 `SpaceBetween` 摊开，遇到非对称的曝光范围（例如 −2..+1）标签就会跟刻度错位，
 * 那比不画还糟。
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun EvScale(
    profile: ExposureProfile,
    index: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = ConsoleType.readout
    val minIndex = profile.minIndex
    val maxIndex = profile.maxIndex
    val span = (maxIndex - minIndex).coerceAtLeast(1)

    // 手势里必须读**最新**的档位和回调，但 pointerInput 的 key 不能带上 index ——
    // 带上就意味着每改一档都会重启手势协程，拖动从第二档起就断了。
    val latestIndex by rememberUpdatedState(index)
    val latestChange by rememberUpdatedState(onChange)

    Canvas(
        modifier = modifier.pointerInput(minIndex, maxIndex) {
            // 一次手势既当点按又当拖动：按下就定位，按住横move 就连续改。
            // 不用 detectTapGestures + detectDragGestures 两套：它们会在同一个节点上抢 down 事件。
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                emitEv(down.position.x, size.width, minIndex, maxIndex, latestIndex, latestChange)
                down.consume()
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) break
                    emitEv(change.position.x, size.width, minIndex, maxIndex, latestIndex, latestChange)
                    change.consume()
                }
            }
        },
    ) {
        val tickTop = 18.dp.toPx()
        val tickBottom = 30.dp.toPx()

        fun xOf(i: Int): Float = size.width * (i - minIndex) / span

        // 刻度
        for (i in minIndex..maxIndex) {
            val x = xOf(i)
            val current = i == index
            val whole = abs(i * profile.stepEv - (i * profile.stepEv).roundToInt()) < 0.01f
            val top = when {
                current -> 14.dp.toPx()
                whole -> 17.dp.toPx()
                else -> 21.dp.toPx()
            }
            drawLine(
                color = when {
                    current -> ConsoleColor.accent
                    whole -> ConsoleColor.secondaryText.copy(alpha = 0.75f)
                    else -> ConsoleColor.mutedText.copy(alpha = 0.55f)
                },
                start = Offset(x, top),
                end = Offset(x, tickBottom),
                strokeWidth = if (current) 2.dp.toPx() else 1.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        // 整档标签（−2 … +2 里落在范围内的那些）
        for (value in -3..3) {
            val at = (value / profile.stepEv).roundToInt()
            if (at !in minIndex..maxIndex) continue
            if (abs(at * profile.stepEv - value) > 0.02f) continue
            val text = when {
                value > 0 -> "+$value"
                value < 0 -> "−${-value}"
                else -> "0"
            }
            val layout = measurer.measure(text, labelStyle)
            val x = (xOf(at) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x, 0f),
                color = if (value == 0) {
                    ConsoleColor.primaryText.copy(alpha = 0.85f)
                } else {
                    ConsoleColor.secondaryText
                },
            )
        }
    }
}

/**
 * 把一个横向坐标换成 EV 档位。**只在档位真的变了才回调**：一次拖动会发上百个 move 事件，
 * 每个都去 `setExposureCompensationIndex` 会把相机控制线程淹掉。
 */
private fun emitEv(
    x: Float,
    widthPx: Int,
    minIndex: Int,
    maxIndex: Int,
    currentIndex: Int,
    onChange: (Int) -> Unit,
) {
    if (widthPx <= 0) return
    val t = (x / widthPx.toFloat()).coerceIn(0f, 1f)
    val next = (minIndex + t * (maxIndex - minIndex)).roundToInt()
    if (next != currentIndex) onChange(next)
}

/** `+0.3` / `−0.3` / `0.0`。负号用 U+2212：等宽字下它和加号同宽，读数不会左右跳。 */
private fun evText(ev: Float): String = when {
    abs(ev) < 0.001f -> "0.0"
    ev > 0f -> String.format(java.util.Locale.US, "+%.1f", ev)
    else -> String.format(java.util.Locale.US, "−%.1f", -ev)
}

// ---------------------------------------------------------------------------
// 快门 / 缩略图
// ---------------------------------------------------------------------------

/**
 * 快门：黄铜外环 + 拉丝银盘 + 中心微凹（D-094 重做）。
 *
 * 上一版用四个叠起来的 `Box` 各涂一层渐变，问题是**层与层之间没有交界**：银盘和
 * 黄铜环直接贴在一起，读起来是一块圆饼上刷了两种颜色。实体快门上有三道交界，
 * 少一道就不像金属：
 *
 * 1. 环的外沿：一圈窄亮线（车刀走过留下的抛光面）；
 * 2. 环与盘之间：一道**暗缝**，环是凸的、盘是凹的，两者不可能平齐；
 * 3. 盘的内沿：一圈由边向心的暗带（盘沉在环里）。
 *
 * 所以这一版改成一块 Canvas 顺序绘制：环 → 缝 → 盘 → 车纹 → 盘心光斑 → 盘沿压暗。
 * 顺序即物理层次，任何一层换个位置都会立刻读错。
 *
 * 不可拍时**不隐藏**，只换成灰环灰盘并禁用 —— 按钮消失会让人以为界面坏了，
 * 灰着还能读到状态条上写的不可拍原因。
 *
 * 按下有触感：实体快门没有涟漪，没有震动的话"按没按到"只能等闪白，
 * 而闪白要等采集回来。
 */
@Composable
private fun ShutterButton(enabled: Boolean, size: Dp, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPressAmount(pressed && enabled)

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                // 金属件被按下时不会"缩"，只会沉进机身 —— 缩 2% 够表达，再多就成了按钮动画。
                val scale = 1f - 0.02f * press.value
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = if (enabled) ConsoleElevation.hero else 4.dp,
                shape = CircleShape,
                clip = false,
                ambientColor = ConsoleColor.shadowContact,
                spotColor = ConsoleColor.shadowContact,
            )
            .clip(CircleShape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = "快门",
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            )
            .semantics { contentDescription = "快门" },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // `size` 在这个作用域里被参数（Dp）挡住了，先取边长再算。
            val full = this.size.width
            val outer = minOf(this.size.width, this.size.height) / 2f
            val ringWidth = outer * 0.155f
            val platter = outer - ringWidth
            val center = Offset(full / 2f, this.size.height / 2f)

            // ---- 黄铜环 ----
            drawCircle(
                brush = if (enabled) {
                    // 环是车出来的金属：四个方向各有一段亮、一段暗，才是"转过一圈"的环。
                    Brush.sweepGradient(
                        listOf(
                            ConsoleColor.accentDeep,
                            ConsoleColor.accentLight,
                            ConsoleColor.accent,
                            ConsoleColor.accentDeep,
                            Color(0xFFB08E4F),
                            ConsoleColor.accentLight,
                            ConsoleColor.accentDeep,
                        ),
                        center = center,
                    )
                } else {
                    Brush.linearGradient(
                        listOf(ConsoleColor.panelDark, ConsoleColor.panel),
                        start = Offset(0f, 0f),
                        end = Offset(full, this.size.height),
                    )
                },
                radius = outer,
                center = center,
            )
            // 环外沿抛光：一圈窄亮线 + 一圈更外的暗收口。
            drawCircle(
                color = Color.White.copy(alpha = if (enabled) 0.34f else 0.20f),
                radius = outer - 0.9.dp.toPx(),
                center = center,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawCircle(
                color = ConsoleColor.accentDeep.copy(alpha = if (enabled) 0.55f else 0.25f),
                radius = outer - 0.25.dp.toPx(),
                center = center,
                style = Stroke(width = 0.9.dp.toPx()),
            )
            // 环内侧的阴影侧：右下暗、左上亮，环才有厚度。
            drawCircle(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.26f),
                        Color.Transparent,
                        ConsoleColor.accentDeep.copy(alpha = 0.42f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(full, this.size.height),
                ),
                radius = platter + ringWidth * 0.42f,
                center = center,
                style = Stroke(width = ringWidth * 0.85f),
            )

            // ---- 暗缝：环凸盘凹，两者之间必须有一道看不透的窄缝 ----
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = if (enabled) 0.30f else 0.14f),
                    ),
                    center = center,
                    radius = platter,
                ),
                radius = platter,
                center = center,
            )

            // ---- 银盘 ----
            drawCircle(
                brush = if (enabled) {
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            // 盘心不能太白：上一版 0f 给到 #F2F0E9，整个盘读成一颗塑料球。
                            // 金属盘的亮度变化主要发生在**边缘**，中心是平的。
                            0f to Color(0xFFE6E3DA),
                            0.42f to Color(0xFFDCD9D0),
                            0.78f to Color(0xFFC6C3B9),
                            1f to Color(0xFFA8A59C),
                        ),
                        center = center,
                        radius = platter,
                    )
                } else {
                    Brush.linearGradient(
                        listOf(ConsoleColor.panelLight, ConsoleColor.panel),
                        start = Offset(0f, 0f),
                        end = Offset(full, this.size.height),
                    )
                },
                radius = platter,
                center = center,
            )

            // 车纹 + 盘心光斑 + 盘沿压暗（共用同一个函数，别处也用这套金属）。
            if (enabled) {
                drawMachinedDisc(radius = platter, sheen = 0.11f, rings = 30)
                // 盘沿的**镜面亮线**：实体银盘被车过一刀，边缘总会挂住一圈光。
                // 少了这一圈，盘面就只是一块渐变圆 —— 有亮线才是金属。
                drawCircle(
                    color = Color.White.copy(alpha = 0.40f),
                    radius = platter - 1.1.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx()),
                )
                // 亮线内侧接一道暗：亮暗成对出现才有"翻起来"的边。
                drawCircle(
                    color = Color.Black.copy(alpha = 0.10f),
                    radius = platter - 2.6.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }

            // 盘沉在环里：内沿一圈暗带，上沿比下沿深（光从上面来）。
            drawInsetShade(
                shape = CircleShape,
                color = ConsoleColor.shadowContact,
                alpha = if (enabled) 0.30f else 0.16f,
                blur = 4.dp,
            )
            drawCircle(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.14f),
                    0.55f to Color.Transparent,
                    startY = center.y - platter,
                    endY = center.y + platter,
                ),
                radius = platter,
                center = center,
            )

            // 按下：整个盘面再沉一点（内阴影加深）。
            if (press.value > 0.01f) {
                drawInsetShade(
                    shape = CircleShape,
                    color = ConsoleColor.shadowContact,
                    alpha = 0.34f * press.value,
                    blur = 6.dp,
                )
            }
        }
    }
}

/** 最近一张成片。没有照片时是统一占位图标，不给假缩略图。 */
@Composable
private fun ThumbnailKey(path: String?, size: Dp, onClick: () -> Unit) {
    val bitmap = rememberLastThumbnail(path)
    Box(
        modifier = Modifier
            .size(size)
            .consolePanel(
                color = ConsoleColor.panel,
                shape = CircleShape,
                elevation = 6.dp,
                borderColor = ConsoleColor.border,
            )
            .clickable(role = Role.Button, onClickLabel = "打开相册", onClick = onClick)
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
            GlassIcon(GlassIconKind.Gallery, ConsoleColor.secondaryText, size = 20.dp)
        }
    }
}

// ---------------------------------------------------------------------------
// 状态条
// ---------------------------------------------------------------------------

/**
 * 底部状态条：横跨整页的一行浅色信息栏。
 *
 * 只放**真实存在**的东西：卷名、这张卷拍过几张、当前倍率、EV、闪光灯、贴花状态。
 * 参考图这里是 `ISO 200 / 1/250s / F5.6 / 5500K` —— CameraX 一个都给不出来
 * （`CameraControl` 只有变焦/曝光补偿/闪光灯/对焦四件事），照抄就是编。
 *
 * 不可拍的原因也走这一条（替换掉中间那组参数）而不是另起一个浮层：横屏只有 360dp 高，
 * 多一层浮块就压到取景窗上。
 */
@Composable
private fun ConsoleStatusBar(
    stockName: String,
    shotsWithStock: Int,
    zoom: Float,
    ev: Float?,
    flashOn: Boolean,
    flashAvailable: Boolean,
    imprintLabel: String?,
    alert: String?,
    alertIsBlocking: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.consolePanel(
            color = ConsoleColor.panelLight,
            shape = CircleShape,
            elevation = 7.dp,
            bevel = 0.7f,
        ).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIcon(GlassIconKind.Film, ConsoleColor.secondaryText, size = 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            text = stockName,
            style = ConsoleType.status,
            color = ConsoleColor.primaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = " · 已拍 $shotsWithStock 张",
            style = ConsoleType.status,
            color = ConsoleColor.secondaryText,
            maxLines = 1,
        )

        Spacer(Modifier.width(10.dp))
        ConsoleStatusDivider()

        // 读数窗：中间那组参数压进一条**凹槽**里（D-094）。
        // 它不承接点击（上滑手势挂在外层），所以是槽而不是键 —— 这也让"实时读数"
        // 和左边"装了什么卷"在材质上分开了：一个是刻在机身上的标签，一个是亮着的显示窗。
        //
        // 槽**按内容宽度收缩**（不是 weight 撑满）：一条从中间一直空到右边的长槽会读成
        // "缺了块面板"，而不是"一格显示器"。
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .consoleRecess(
                        shape = CircleShape,
                        color = ConsoleColor.body.copy(alpha = 0.88f),
                        depth = 3.dp,
                        shade = 0.32f,
                        rimLight = 0.50f,
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (alert != null) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                if (alertIsBlocking) ConsoleColor.danger else ConsoleColor.accent,
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = alert,
                        style = ConsoleType.status,
                        color = if (alertIsBlocking) ConsoleColor.danger else ConsoleColor.secondaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = zoomLabel(zoom),
                        style = ConsoleType.status,
                        color = ConsoleColor.primaryText,
                    )
                    ConsoleStatusDivider()
                    if (ev != null) {
                        Text(
                            text = "EV " + evText(ev),
                            style = ConsoleType.status,
                            color = if (abs(ev) > 0.001f) {
                                ConsoleColor.accentDeep
                            } else {
                                ConsoleColor.secondaryText
                            },
                        )
                        ConsoleStatusDivider()
                    }
                    if (imprintLabel != null) {
                        Text(
                            text = imprintLabel,
                            style = ConsoleType.status,
                            color = ConsoleColor.secondaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        ConsoleStatusDivider()
                    }
                    Text(
                        text = when {
                            !flashAvailable -> "无闪光灯"
                            flashOn -> "闪光 开"
                            else -> "闪光 关"
                        },
                        style = ConsoleType.status,
                        color = when {
                            !flashAvailable -> ConsoleColor.mutedText
                            flashOn -> ConsoleColor.accentDeep
                            else -> ConsoleColor.secondaryText
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConsoleStatusDivider() {
    Box(Modifier.padding(horizontal = 7.dp).size(width = 1.dp, height = 12.dp).background(ConsoleColor.borderSoft))
}

private fun zoomLabel(zoom: Float): String {
    val rounded = (zoom * 10f).roundToInt() / 10f
    return if (rounded % 1f == 0f) "${rounded.toInt()}×" else "$rounded×"
}

// ---------------------------------------------------------------------------
// 抽屉
// ---------------------------------------------------------------------------

/**
 * "去哪儿"抽屉的浅色版。玻璃版（[DestinationSwitcher]）是深色半透明的，
 * 放在暖白机身上像一块贴错的玻璃，所以这里用同一套机身令牌重画一遍。
 *
 * 手势/层级跟玻璃版完全一致：遮罩在下、面板在上、点遮罩关。入口仍然是
 * "从底部状态条往上推"（横版没有 dock 可推）。
 */
@Composable
private fun ConsoleSwitcher(
    onGallery: () -> Unit,
    onFilms: () -> Unit,
    onDiscover: () -> Unit,
    onSettings: () -> Unit,
) {
    val density = LocalDensity.current
    val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = ConsoleSpace.page)
                .fillMaxWidth()
                .consolePanel(
                    color = ConsoleColor.panelLight,
                    shape = ConsoleShape.panel,
                    elevation = 16.dp,
                )
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(bottom = 10.dp)
                    .size(width = 32.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(ConsoleColor.border),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ConsoleSwitcherItem("相册", GlassIconKind.Gallery, onGallery)
                ConsoleSwitcherItem("胶卷", GlassIconKind.Film, onFilms)
                ConsoleSwitcherItem("发现", GlassIconKind.Sparkle, onDiscover)
                ConsoleSwitcherItem("设置", GlassIconKind.Gear, onSettings)
            }
        }
        Spacer(Modifier.height(navBottom + ConsoleSpace.tight))
    }
}

@Composable
private fun ConsoleSwitcherItem(label: String, kind: GlassIconKind, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConsoleToolButton(label = label, onClick = onClick, size = 48.dp) { tint ->
            GlassIcon(kind, tint, size = 22.dp)
        }
        Spacer(Modifier.height(5.dp))
        Text(text = label, style = ConsoleType.button, color = ConsoleColor.secondaryText)
    }
}
