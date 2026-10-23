package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.camera.ExposureProfile
import com.pocketccd.prototype.camera.LensStop
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * The shooting screen, rebuilt from `docs/reference/film-dark-home-2026-10-19.html`.
 *
 * Structure is the design's eight bands: top bar → viewfinder (weight 1) →
 * counter → capture controls → parameter footer → progress bar.
 *
 * Two deliberate departures from the design, both required by D-040:
 *
 * - No `1/125` / `f/2.8` / `+0.3` / `ISO 400`. CameraX does not expose those
 *   without Camera2 Interop, so printing them would be inventing data.
 * - No `DNG` and no `3:2`. This app writes JPEG and captures 4:3.
 *
 * 2026-10-21 密度整改后，取景页只保留卷名、卷内计数与进度。`LIVE` / `12.6MP` /
 * `4:3` 三个 chip 与底部的 `AUTO · 4:3 · JPEG`、`余 N 张` 全部删除：它们是真值，
 * 但用户此刻不用它们做任何决定（真实 ≠ 该上屏，见 D-050）。参数留在成片页。
 */
@Composable
fun FilmCaptureScreen(
    stockName: String,
    /** 本卷已拍张数（自换卷起计），不是照片库总数。 */
    shotsTaken: Int,
    rollCapacity: Int,
    flashOn: Boolean,
    gridOn: Boolean,
    canShoot: Boolean,
    blockReason: String?,
    onShutter: () -> Unit,
    onToggleFlash: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpenGallery: () -> Unit,
    /**
     * 打开胶卷库（T9-016）。原来的行为是"点一下切到下一卷"，卷多了以后
     * 要连点好几次才知道切到了哪一卷；改为进库挑选。pill 自带 `ChevronRight`，
     * 视觉本来就是"进入"，不是"切换"。
     */
    onOpenFilms: () -> Unit,
    onNewRoll: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 权限被永久拒绝后去应用详情页开权限。系统弹窗不会再出现，这是唯一出口。 */
    onOpenAppSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    permissionGranted: Boolean = true,
    /** 该镜头有没有闪光灯。false 时按钮不再假装能开（前置镜头常见）。 */
    flashAvailable: Boolean = true,
    /** Null when the camera is not available yet (no permission / no pack). */
    viewfinder: (@Composable (Modifier) -> Unit)? = null,
    /**
     * 点按对焦。坐标是**取景器 View 的像素**，由 `pointerInput` 原样交出。
     * 结果回调传真实对焦成败，界面据此显示 `AF LOCK` / `AF FAIL`。
     */
    onTapToFocus: (x: Float, y: Float, onResult: (Boolean) -> Unit) -> Unit =
        { _, _, _ -> },
    /**
     * 变焦档位变化。**返回实际生效的倍率**：请求 3.2x 但机型只到 1x 时，
     * 界面照实显示 1x 并说明原因，不做"按钮亮着画面没变"的假象。
     */
    onZoomChange: (Float) -> Float = { it },
    /** 当前变焦比。由上层持有：相机重绑会把变焦重置为 1x，只有一处真相才能对上。 */
    zoom: Float = 1f,
    /**
     * 该机的真实光学档位（D-044）。空列表表示探不到，变焦控件会退回 1x/2x 数码档。
     */
    lensStops: List<LensStop> = emptyList(),
    /**
     * EV 补偿的真实范围（D-052）。null = 该机不支持或相机未绑定，控件整块不出现。
     */
    exposureProfile: ExposureProfile? = null,
    /** 当前 EV 档位 **index**，不是 EV 值（换算在 [ExposureProfile] 里）。 */
    evIndex: Int = 0,
    /** 设置 EV，返回实际生效的 index（已按硬件范围 clamp）。 */
    onEvChange: (Int) -> Int = { it },
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val m = spec.metrics

    // 交互状态都留在页面内：它们只影响这一屏的表现，没必要上升到 ViewModel，
    // 否则点一下对焦要穿过三层状态流才画得出一个环。
    var toastText by remember { mutableStateOf<String?>(null) }
    var toastId by remember { mutableIntStateOf(0) }
    var focusPoint by remember { mutableStateOf<FocusPoint?>(null) }
    var focusLock by remember { mutableStateOf<Boolean?>(null) }
    val showToast: (String) -> Unit = { message ->
        toastText = message
        toastId++
    }
    // pointerInput 只跑一次，回调要始终指向最新那个，否则相机句柄晚一步绑定
    // 就会一直调到"还没有相机"的旧闭包上。
    val tapToFocus by rememberUpdatedState(onTapToFocus)

    // 对应设计稿的 2200ms 自动消失。文案相同时 toastId 仍会变，所以能连续触发。
    LaunchedEffect(toastId) {
        if (toastId == 0) return@LaunchedEffect
        delay(2200)
        toastText = null
    }

    Box(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.appBg)
            .statusBarsPadding(),
    ) {
        // 2026-10-23：顶栏只留胶卷 pill。闪光灯/九宫格/设置下移到取景窗下方的
        // 工具条 —— 上方越空，取景窗越像"一台相机的窗"，而不是"一个带图标的页面"。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            StockPill(name = stockName, onClick = onOpenFilms)
        }

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // 不再加水平 padding：取景窗宽度已经在下面按 0.86 收过，
                // 再加一层 12dp 会变成 0.86×(412−24)，等于缩两次。
                .padding(top = 2.dp),
            // 把取景窗顶到上方：重心上移之后，下面的工具条与底盖才有"机身"的层次。
            contentAlignment = Alignment.TopCenter,
        ) {
            // 3:4 viewfinder, matching the design's 369x490 ratio. The preview
            // itself is FIT_CENTER inside (never cropped — a framed image must
            // match the captured image, see CameraPreview), so the frame has to
            // match 4:3 too or the photo would be cropped relative to what the
            // user saw.
            // 取景窗不再顶满可用宽度：胶卷相机的取景器四周有一圈机身/边框，顶满宽度
            // 会读成"手机预览框"，不像取景窗。0.86 是"看得出收了一圈、构图又不费劲"
            // 的折中；可用高度不够时仍以高度为准（保持 3:4 不变形）。
            val frameWidth = minOf(maxWidth * 0.86f, maxHeight * 3f / 4f)

            FilmViewfinder(
                modifier = Modifier.size(width = frameWidth, height = frameWidth * 4f / 3f),
            ) {
                val slot = viewfinder
                if (slot != null) {
                    slot(Modifier.fillMaxSize())

                    // 对应设计稿的 `#vfTap`（z-10）。放在预览之上、所有 chrome 之下：
                    // 后加的 chip / 变焦钮仍然先拿到点击，这里只吃"点空白处"。
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    focusLock = null
                                    // id 用递增计数器而不是坐标：同一处连点两次也要重播动画。
                                    focusPoint = FocusPoint(offset.x, offset.y, System.nanoTime())
                                    tapToFocus(offset.x, offset.y) { ok -> focusLock = ok }
                                }
                            },
                    )

                    // Design layer that was missing in v1: without it the corners
                    // and reticle vanish on a bright subject.
                    ViewfinderVignette()
                } else {
                    ViewfinderPlaceholder(
                        permissionGranted = permissionGranted,
                        blockReason = blockReason,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                }

                ViewfinderCorners()

                if (gridOn) {
                    ViewfinderGrid()
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    ViewfinderReticle()
                }

                // 取景器右缘一列：变焦档位在上、EV 在下。两者都是"拍摄参数"，
                // 用同一只拇指够，且都不占画面中心。
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // 设计稿把变焦钮放在右侧垂直居中（`right-3 top-1/2`）。
                    ViewfinderZoom(
                        zoom = zoom,
                        stops = lensStops,
                        onSelect = { ratio ->
                            if (abs(zoom - ratio) > 0.05f) {
                                val applied = onZoomChange(ratio)
                                showToast(
                                    if (applied < ratio - 0.01f) {
                                        "该镜头最高 %.1fx".format(applied)
                                    } else {
                                        "变焦 %.1fx".format(applied)
                                    },
                                )
                            }
                        },
                    )

                    // 这台机器不支持曝光补偿时**整块不出现**（D-052）：
                    // 画一条拖不动的刻度比不画更糟，用户会怪自己没拖对。
                    val exposure = exposureProfile
                    if (exposure != null) {
                        Spacer(Modifier.height(2.dp))
                        ViewfinderEv(
                            profile = exposure,
                            evIndex = evIndex,
                            onEvChange = { onEvChange(it) },
                        )
                    }
                }

                FocusRing(point = focusPoint, lock = focusLock)

            }
        }

        // 取景窗下方的工具条：闪光灯 / 九宫格 / 设置。放在这里而不是顶栏，
        // 一是把上方空间让给取景窗，二是这三颗都在拇指弧内。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilmRoundButton(
                icon = if (flashOn) FilmIconKind.Flash else FilmIconKind.FlashOff,
                onClick = {
                    if (flashAvailable) {
                        onToggleFlash()
                        showToast(if (flashOn) "闪光灯已关闭" else "闪光灯已开启")
                    } else {
                        // 没有闪光灯单元时不发 onToggleFlash：否则图标会亮、
                        // 底层 setFlash 却静默降级，等于骗用户。
                        showToast("这台设备没有闪光灯")
                    }
                },
                active = flashOn && flashAvailable,
            )
            FilmRoundButton(
                icon = FilmIconKind.Grid,
                onClick = {
                    onToggleGrid()
                    showToast(if (gridOn) "九宫格参考线已关闭" else "九宫格参考线已开启")
                },
                active = gridOn,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            FilmRoundButton(
                icon = FilmIconKind.Tune,
                onClick = onOpenSettings,
            )
        }

        // 底部整块是机身底盖（D-051）：计数、快门（嵌在凹槽里）、副键（嵌在面板上）、
        // 曝光刻度槽，全部装在这一块上。导航栏的空白由面板自己吃下，否则金属板
        // 会在手势条上方断掉，露出底下的 #0B0C0E。
        //
        // 计数行原先悬在取景窗和面板之间，是整页唯一"没有着落"的一行（D-053 收进来）：
        // 它本来就是机身该印的东西，飘在空中反而像界面标签。收进来之后面板总高不变，
        // 取景窗也没被挤矮 —— 原来那 27dp 本来就在面板之外占着。
        FilmBodyPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 只在拍不了的时候出现（没有胶卷 / 本卷拍满 / 相机没绑上 / 预览出错）。
                // 一切正常时这块是空的 —— 那正是取景页该有的样子。
                if (blockReason != null) {
                    Text(
                        text = blockReason,
                        color = p.error,
                        fontSize = spec.type.monoSmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$shotsTaken / $rollCapacity EXP",
                    color = p.textTertiary,
                    fontSize = spec.type.monoSmall,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.16.em,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // 副键到屏幕边缘 64dp：两个键的中心落在距中线 ~110dp 处，
                    // 拇指自然垂落的位置，也给快门留出 42dp 误触安全带。
                    .padding(horizontal = 64.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilmBodyKey(
                    label = "相册",
                    icon = FilmIconKind.ImageMultiple,
                    onClick = onOpenGallery,
                )

                FilmShutter(
                    onClick = onShutter,
                    enabled = canShoot,
                )

                FilmBodyKey(
                    label = "换卷",
                    icon = FilmIconKind.Filmstrip,
                    onClick = onNewRoll,
                )
            }

            FilmExposureSlot(
                fraction = if (rollCapacity <= 0) {
                    0f
                } else {
                    shotsTaken.toFloat() / rollCapacity
                },
            )

            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(Modifier.height(10.dp))
        }
    }

        // 两个覆盖层单独挂在最外层 Box 上：它们在 Column 之外，所以不受
        // 状态栏/导航栏内边距影响。
        // 快门闪白不在这里 —— 按下快门会立刻导航到成片页，闪白画在取景页
        // 内部就会被成片页盖住（真机实测整帧均值只差 11，等于没有）。
        // 它挂在 PocketCcdApp 的最外层，见那边的注释。
        FilmToast(
            text = toastText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 180dp 让它落在取景器下沿与卷信息行之间那条缝里（152dp 会压住
                // 「GOLD 200 · 4096×3072」，真机截图量的）。
                .padding(bottom = 180.dp),
        )
    }
}

@Composable
private fun StockPill(name: String, onClick: () -> Unit) {
    val spec = LocalFilmTheme.current
    val shape = RoundedCornerShape(50)

    // 视觉是 29dp 的胶囊，命中区必须 48dp：外层 Box 撑到 48dp 并接管点击，
    // 内层只负责画。之前 clickable 挂在 29dp 的胶囊上，是典型的「看着够大、
    // 实际点不中」（T9 无障碍退出条件要求 ≥48dp）。
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(50))
            .filmClickable(
                onClick = onClick,
                onClickLabel = "打开胶卷库选择胶卷",
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(shape)
                .background(spec.palette.controlFill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(spec.palette.accent),
            )
            Text(
                text = name,
                color = spec.palette.ink,
                fontSize = spec.type.mono,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.16.em,
                maxLines = 1,
            )
            FilmIcon(
                kind = FilmIconKind.ChevronRight,
                tint = spec.palette.inkFaint,
                iconSize = 12.dp,
            )
        }
    }
}


/**
 * What sits in the finder when there is no preview: permission not granted, or
 * camera not bound yet. Never a fabricated image — a dark panel plus the real
 * reason is more honest than a placeholder photo.
 */
@Composable
private fun ViewfinderPlaceholder(
    permissionGranted: Boolean,
    blockReason: String?,
    onOpenAppSettings: () -> Unit,
) {
    val spec = LocalFilmTheme.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070C11)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (permissionGranted) "正在准备取景器" else "需要相机权限",
                color = spec.palette.inkStrong,
                fontSize = spec.type.optionTitle,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    !permissionGranted -> "在系统设置中允许使用相机"
                    blockReason != null -> blockReason
                    else -> "等待相机绑定"
                },
                // 占位态没有画面，底下是纯色 #070C11，可以用 textSecondary（7.2:1）；
                // 不要用 inkFaint(3.15:1)，那是压真实画面时才不得已的选择。
                color = spec.palette.textSecondary,
                fontSize = spec.type.body,
            )
            // 权限被永久拒绝（点了两次「拒绝」）后，系统弹窗不会再出现，
            // 光写一行「在系统设置中允许」等于让用户自己去找入口。给一个真按钮。
            if (!permissionGranted) {
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        // 命中区 48dp（视觉胶囊约 42dp），权限页是用户唯一能自救的地方，
                        // 不能因为差 6dp 点不中。
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(spec.palette.controlFill)
                        .border(
                            spec.metrics.hairline,
                            spec.palette.accent.copy(alpha = 0.55f),
                            RoundedCornerShape(50),
                        )
                        .filmClickable(
                            onClickLabel = "打开系统设置授予相机权限",
                            onClick = onOpenAppSettings,
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "去设置",
                        color = spec.palette.accent,
                        fontSize = spec.type.body,
                        letterSpacing = 0.12.em,
                    )
                }
            }
        }
    }
}
