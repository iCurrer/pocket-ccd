package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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

    // EV 刻度不再常驻取景画面：工具条里的 EV 键点开后，在取景窗底部展开 4 秒。
    var evExpanded by remember { mutableStateOf(false) }
    // 换卷会把本卷计数归零，紧挨快门很容易误触（D-060 已知债）。加一次明确确认。
    var showNewRollConfirm by remember { mutableStateOf(false) }
    LaunchedEffect(evExpanded, evIndex) {
        if (evExpanded) {
            delay(4000)
            evExpanded = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(p.appBg)
                .statusBarsPadding(),
        ) {
            // 2026-10-23 第三版布局：窗 → 工具条 → 底盖，三块连续，中间不留死黑。
            // 取景窗宽度 0.86；同时给底盖保底 240dp，避免小屏把按键挤没。
            // 顶栏按 D-063 §5.1 是下划线 tab，热区必须 48dp（原先是 44dp 的胶囊条）。
            val frameWidth = minOf(
                maxWidth * 0.86f,
                (maxHeight - 48.dp - 52.dp - 240.dp) * 0.75f,
            )
            val frameHeight = frameWidth * 4f / 3f

            Column(modifier = Modifier.fillMaxSize()) {
                // 顶栏只留胶卷 tab：下划线式（12sp mono 纸白 + 1px 白 20%），
                // 不再有金色圆点和胶囊底。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    StockTab(name = stockName, onClick = onOpenFilms)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    FilmViewfinder(
                        modifier = Modifier.size(width = frameWidth, height = frameHeight),
                    ) {
                        val slot = viewfinder
                        if (slot != null) {
                            slot(Modifier.fillMaxSize())

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectTapGestures { offset ->
                                            focusLock = null
                                            focusPoint = FocusPoint(offset.x, offset.y, System.nanoTime())
                                            tapToFocus(offset.x, offset.y) { ok -> focusLock = ok }
                                        }
                                    },
                            )

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

                        // EV 刻度：只在工具条 EV 键展开时出现，压在画面底部（4s 自动收）。
                        val exposure = exposureProfile
                        if (evExpanded && exposure != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 10.dp),
                            ) {
                                FilmEvRuler(
                                    profile = exposure,
                                    evIndex = evIndex,
                                    onEvChange = { onEvChange(it) },
                                )
                            }
                        }

                        FocusRing(point = focusPoint, lock = focusLock)
                    }
                }

                // 工具条：闪光灯 / 九宫格 | 变焦档位 | EV / 设置。
                // 所有拍摄参数离开画面，画面完整留给构图。
                // D-063：未激活图标 = 纸白 46%，激活 = 白 12% 圆底 + 纸白图标。
                val toolbarInactive = p.textPrimary.copy(alpha = 0.46f)
                Row(
                    // 左右 12dp（取景页 12/16/20 体系里的一档）：48dp 热区比旧版宽，
                    // 3 个光学档位 + EV 键在 360dp 屏上刚好排得下，不再靠 16dp 边距。
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilmRoundButton(
                        icon = if (flashOn) FilmIconKind.Flash else FilmIconKind.FlashOff,
                        onClick = {
                            if (flashAvailable) {
                                onToggleFlash()
                                showToast(if (flashOn) "闪光灯已关闭" else "闪光灯已开启")
                            } else {
                                showToast("这台设备没有闪光灯")
                            }
                        },
                        active = flashOn && flashAvailable,
                        inactiveTint = toolbarInactive,
                    )
                    FilmRoundButton(
                        icon = FilmIconKind.Grid,
                        onClick = {
                            onToggleGrid()
                            showToast(if (gridOn) "九宫格参考线已关闭" else "九宫格参考线已开启")
                        },
                        active = gridOn,
                        inactiveTint = toolbarInactive,
                    )
                    Spacer(Modifier.weight(1f))
                    FilmZoomStops(
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
                    Spacer(Modifier.weight(1f))
                    exposureProfile?.let { exposure ->
                        FilmEvToolbarKey(
                            profile = exposure,
                            evIndex = evIndex,
                            expanded = evExpanded,
                            onClick = { evExpanded = !evExpanded },
                        )
                    }
                    FilmRoundButton(
                        icon = FilmIconKind.Tune,
                        onClick = onOpenSettings,
                        inactiveTint = toolbarInactive,
                    )
                }

                // 底盖：填满剩余高度，计数窗口在中段，按键与快门压在下段。
                FilmBodyPanel(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Spacer(Modifier.height(12.dp))
                        if (blockReason != null) {
                            Text(
                                text = blockReason,
                                color = p.error,
                                fontSize = spec.type.monoSmall,
                                fontFamily = FontFamily.Monospace,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        // 计数器做成机身"帧数窗"：EXP + 大号真实张数。
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "EXP",
                                color = p.textTertiary,
                                fontSize = spec.type.label,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.32.em,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "%02d".format(shotsTaken),
                                    // D-063 §5.1：计数是 30sp mono 纸白（counterBig）。
                                    color = p.textPrimary,
                                    fontSize = spec.type.counterBig,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Text(
                                    text = " / %02d".format(rollCapacity),
                                    color = p.textTertiary,
                                    fontSize = spec.type.value,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(bottom = 5.dp),
                                )
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 44.dp),
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
                                onClick = { showNewRollConfirm = true },
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        FilmExposureSlot(
                            fraction = if (rollCapacity <= 0) {
                                0f
                            } else {
                                shotsTaken.toFloat() / rollCapacity
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        // 换卷确认：换卷键紧挨快门，误触一次就归零（D-060 已知债）。
        // 确认框只说事实：本卷已拍多少张、照片会保留、计数归零。
        if (showNewRollConfirm) {
            AlertDialog(
                onDismissRequest = { showNewRollConfirm = false },
                containerColor = spec.palette.panelMetal.last(),
                titleContentColor = spec.palette.textPrimary,
                textContentColor = spec.palette.textSecondary,
                title = { Text("结束本卷？") },
                text = {
                    Text("本卷已拍 $shotsTaken 张。照片会保留在相册，计数器归零。")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNewRollConfirm = false
                            onNewRoll()
                        },
                    ) {
                        Text("换卷", color = spec.palette.textPrimary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNewRollConfirm = false }) {
                        Text("取消", color = spec.palette.textSecondary)
                    }
                },
            )
        }

        // 快门闪白不在取景页内部（会被成片页盖住）；Toast 挂在页面最外层。
        FilmToast(
            text = toastText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 250.dp),
        )
    }
}

/**
 * 顶栏胶卷 tab（D-063「白纸档案」）。
 *
 * 方向 C 的取景页只刻纸白线：胶卷名是一条 12sp mono 的下划线 tab，不再是
 * "金色圆点 + 胶囊"。热区撑到 48dp（父级顶栏也是 48dp），视觉只有一行字和 1px 线。
 */
@Composable
private fun StockTab(name: String, onClick: () -> Unit) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .filmClickable(
                onClick = onClick,
                onClickLabel = "打开胶卷库选择胶卷",
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        // 用 IntrinsicSize 把下划线卡在文字宽度上：fillMaxWidth 在这里只会撑满
        // 整条顶栏，那读起来是"分隔线"，不是 tab。
        Column(
            modifier = Modifier.width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = name,
                color = p.textPrimary,
                fontSize = spec.type.mono,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.16.em,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            // 下划线：白 20%、1px，宽度就是这行字的宽度。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.20f)),
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
            // 闸门兜底：和取景窗同一个纯黑（D-063 去掉旧版偏蓝的 #070C11）。
            .background(spec.palette.viewfinderBg),
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
                // 占位态没有画面，底下是纯色 viewfinderBg，可以用 textSecondary（7.2:1）；
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
                            // 白 16% 边（hairlineStrong），不再是 accent 色描边。
                            spec.palette.hairlineStrong,
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
                        color = spec.palette.textPrimary,
                        fontSize = spec.type.body,
                        letterSpacing = 0.12.em,
                    )
                }
            }
        }
    }
}
