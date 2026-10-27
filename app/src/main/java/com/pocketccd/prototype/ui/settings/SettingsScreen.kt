package com.pocketccd.prototype.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.filmClickable

/**
 * 设置页（T9-020 / D-045 / D-063；v0.6.0「白纸档案」1:1 版面 B 方案）。
 *
 * 版面结构（`docs/paper-archive-1to1-layout.md` §2/§6）：
 * 1. 页头：左 Serif「设置」+ 右侧 Tune 图标；**没有左侧返回箭头**（返回交给系统/AppRoot）。
 * 2. 四个**就地可展开**分类（不新增子页）：
 *    `01 保存与输出` / `02 相机与拍摄` / `03 胶卷管理` / `04 关于`。
 *    分类行 = 编号 mono 11 + Serif 标题 + 11sp 副题 + 右侧箭头（展开时转 90°）；
 *    展开后复用 [ColophonRow]，同一时间只展开一个（手风琴），保持纸面安静。
 * 3. 页脚：`Pocket-CCD` + `A Camera App for Film Lovers` + 真实 `BuildConfig` 版本/构建号。
 *    作者字段**不写** —— 版式规格要求"无真实来源就不写"（D-002）。
 * 4. 内容底部统一留 88dp（56dp 底部 TabBar + 24dp 呼吸 + 余量），由 AppRoot 叠加
 *    [com.pocketccd.prototype.ui.film.PaperNavBar]，页面自己不让位就会被压住。
 *
 * 事实口径（D-002）：
 * - `保存位置` 的副题写「本应用 + 系统相册」：成片同时写应用私有目录
 *   （`files/Pictures/pocket-ccd/`）与系统相册（`Pictures/Pocket CCD/`）。
 * - `照片质量` 的 92 是真实 JPEG 质量（`PhotoStore.save` 默认值）。
 * - `胶卷` 的卷数来自真实扫描结果，点行触发重扫。
 * - `相机权限` 读的是真实授权状态（`checkSelfPermission`，回前台时刷新），
 *   点击跳**本应用**的系统详情页（用户在那里才能重新授权）。
 * - 版本/构建号来自真实 `BuildConfig`。
 *
 * 兼容说明：[onBack] 参数保留（AppRoot 仍按原签名调用），但 1:1 版面的页头不放
 * 返回箭头 —— 系统返回由 AppRoot 的 BackHandler 处理。等"更多设置"子页由其他
 * 智能体接入时，可直接复用该参数或替换成新的回调。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    stockCount: Int = 0,
    onRefreshStocks: () -> Unit = {},
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val context = LocalContext.current

    // 相机权限是真实状态；从系统设置页返回后（ON_RESUME）重新读一次，
    // 否则用户授权完回来这一行还写着「未允许」。
    var cameraPermissionGranted by remember { mutableStateOf(context.hasCameraPermission()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                cameraPermissionGranted = context.hasCameraPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 唯一能重新授予被永久拒绝的相机权限的地方：本应用的系统详情页。
    val openAppSettings: () -> Unit = {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }
    }

    // 手风琴：null = 全部收起；同一时间只展开一个分类。
    var expandedSection by remember { mutableStateOf<Int?>(null) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsHeader()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp),
            ) {
                item {
                    CategorySection(
                        index = "01",
                        title = "保存与输出",
                        subtitle = "位置 · 画质",
                        expanded = expandedSection == 1,
                        onToggle = { expandedSection = if (expandedSection == 1) null else 1 },
                    ) {
                        // 两行都是真实值：保存位置是两处真实写入目标，92 是真实 JPEG 质量。
                        ColophonRow(
                            title = stringResource(R.string.settings_save_location),
                            subtitle = stringResource(R.string.settings_save_location_value),
                            value = "相册",
                        )
                        ColophonHairline()
                        ColophonRow(
                            title = stringResource(R.string.settings_quality),
                            subtitle = stringResource(R.string.settings_quality_subtitle),
                            value = stringResource(R.string.settings_quality_value),
                        )
                    }
                }

                item {
                    CategorySection(
                        index = "02",
                        title = "相机与拍摄",
                        subtitle = "权限 · 系统设置",
                        expanded = expandedSection == 2,
                        onToggle = { expandedSection = if (expandedSection == 2) null else 2 },
                    ) {
                        // 本轮没有其他真实可调的拍摄设置：只放这一行真实权限入口，
                        // 不拿"滤镜/模式"之类没有接线的项凑数（D-002）。
                        ColophonRow(
                            title = "相机权限",
                            subtitle = "在系统设置中管理本应用的相机权限",
                            value = if (cameraPermissionGranted) "已允许" else "未允许",
                            onClick = openAppSettings,
                            onClickLabel = "打开系统设置中的相机权限",
                            showChevron = true,
                        )
                    }
                }

                item {
                    CategorySection(
                        index = "03",
                        title = "胶卷管理",
                        subtitle = "本机胶卷 · 重新扫描",
                        expanded = expandedSection == 3,
                        onToggle = { expandedSection = if (expandedSection == 3) null else 3 },
                    ) {
                        // 卷随 APK 内置（D-048），首次启动自动落盘；这一行是给手动
                        // adb push 覆盖的场景留的重扫入口，点了才重扫，不用杀进程重进。
                        ColophonRow(
                            title = stringResource(R.string.settings_film_roll),
                            subtitle = stringResource(R.string.settings_film_roll_hint),
                            value = "$stockCount 卷",
                            onClick = onRefreshStocks,
                            onClickLabel = "重新扫描胶卷",
                            showChevron = true,
                        )
                    }
                }

                item {
                    CategorySection(
                        index = "04",
                        title = "关于",
                        subtitle = "版本 · 隐私 · 开源声明",
                        expanded = expandedSection == 4,
                        onToggle = { expandedSection = if (expandedSection == 4) null else 4 },
                    ) {
                        ColophonRow(
                            title = stringResource(R.string.settings_version),
                            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        )
                        ColophonHairline()
                        ColophonRow(
                            title = stringResource(R.string.settings_privacy),
                            subtitle = stringResource(R.string.settings_privacy_note),
                            value = stringResource(R.string.settings_privacy_value),
                        )
                        ColophonHairline()
                        // CC BY-SA 4.0 要求署名与修改声明随分发物走。pack 内置进
                        // APK 之后，这一页就不是"可选彩蛋"了 —— 对外发包前必须有。
                        ColophonRow(
                            title = stringResource(R.string.settings_licenses),
                            subtitle = stringResource(R.string.settings_licenses_subtitle),
                            value = stringResource(R.string.settings_licenses_value),
                            onClick = { showLicenses = true },
                            onClickLabel = "打开开源与素材声明",
                            showChevron = true,
                        )
                        if (BuildConfig.DEBUG) {
                            ColophonHairline()
                            // 诊断信息只在 debug 构建存在（release 不显示这一行）。
                            ColophonRow(
                                title = stringResource(R.string.settings_diagnostics),
                                subtitle = stringResource(R.string.settings_diagnostics_value),
                                onClick = { showDiagnostics = true },
                                onClickLabel = "查看诊断信息",
                                showChevron = true,
                            )
                        }
                    }
                }

                item { ColophonFooter() }
            }
        }

        // 声明页盖满整屏（含设置页自己的页头），返回即关闭。
        if (showLicenses) {
            // 覆盖层打开时系统返回先关它，而不是退出设置页：AppRoot 的 BackHandler
            // 会把 SETTINGS 直接回取景页。内层 BackHandler 后注册，优先消费返回键。
            BackHandler { showLicenses = false }
            LicensesScreen(onBack = { showLicenses = false })
        }
    }

    if (showDiagnostics) {
        // 不显式指定容器/内容色：AppRoot 用 PocketCcdTheme(darkTheme = false) 提供
        // 纸色 M3 亮色方案（surface = sheetBg、onSurface = 墨色），对话框自动继承。
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_diagnostics_title),
                    fontFamily = spec.type.titleFamily,
                    fontSize = spec.type.optionTitle,
                    lineHeight = spec.type.bodyLineHeight,
                    fontWeight = FontWeight.Medium,
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_diagnostics_body,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    fontFamily = spec.type.monoFamily,
                    fontSize = spec.type.mono,
                    lineHeight = spec.type.monoLineHeight,
                )
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text(
                        text = stringResource(R.string.settings_close),
                        fontFamily = spec.type.bodyFamily,
                        fontSize = spec.type.body,
                        lineHeight = spec.type.labelLineHeight,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
        )
    }
}

/**
 * 1:1 版面的设置页头：左 Serif「设置」+ 右侧 Tune 图标，高 64dp、左右 24dp、下接 1px 线。
 * 没有返回箭头（§2）。右侧图标的"更多设置"回调尚未由其他智能体接入，所以这里只画
 * 48dp 视觉位、**不加点击语义** —— 不做点了没反应的假按钮（D-002）。
 */
@Composable
private fun SettingsHeader() {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = spec.metrics.pageMargin),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                modifier = Modifier.weight(1f),
                color = p.textPrimary,
                fontFamily = spec.type.titleFamily,
                fontSize = spec.type.sheetTitle,
                lineHeight = spec.type.sheetTitleLineHeight,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier.size(spec.metrics.touchTarget),
                contentAlignment = Alignment.Center,
            ) {
                FilmIcon(
                    kind = FilmIconKind.Tune,
                    tint = p.textPrimary,
                    iconSize = 18.dp,
                    strokeWidth = 1.5.dp,
                )
            }
        }
        PaperHairline()
    }
}

/**
 * 可展开分类行：编号 mono 11 + Serif 标题 + 11sp 副题 + 右侧箭头。
 *
 * 点击任意位置展开/收起（整行可点、≥64dp）；箭头 120ms 旋转到 90°，展开内容用
 * 120ms 高度+淡入，均取 `spec.motion.stateMs`。分类底部统一一条 1px 墨线。
 */
@Composable
private fun CategorySection(
    index: String,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(spec.motion.stateMs),
        label = "settings-category-arrow",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .filmClickable(
                    onClick = onToggle,
                    scaleTo = 1f,
                    overlayAlpha = 0.05f,
                    haptic = false,
                    role = Role.Button,
                    onClickLabel = if (expanded) "收起$title" else "展开$title",
                )
                // 读屏要知道当前是开还是合，而不是只听到一句"展开…"。
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
                .padding(horizontal = spec.metrics.pageMargin, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = index,
                color = p.textTertiary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.monoSmall,
                lineHeight = spec.type.monoLineHeight,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = p.textPrimary,
                    fontFamily = spec.type.titleFamily,
                    fontSize = spec.type.optionTitle,
                    lineHeight = spec.type.bodyLineHeight,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = p.textSecondary,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.monoSmall,
                    lineHeight = spec.type.labelLineHeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            FilmIcon(
                kind = FilmIconKind.ChevronRight,
                tint = p.textTertiary,
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                iconSize = 16.dp,
                strokeWidth = 1.5.dp,
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(animationSpec = tween(spec.motion.stateMs)) +
                fadeIn(animationSpec = tween(spec.motion.stateMs)),
            exit = shrinkVertically(animationSpec = tween(spec.motion.stateMs)) +
                fadeOut(animationSpec = tween(spec.motion.stateMs)),
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }

        PaperHairline(startInset = spec.metrics.pageMargin, endInset = spec.metrics.pageMargin)
    }
}

/**
 * 版权页条目行：左标题 14sp + 副题 13sp（`textSecondary`）+ 右值 14sp mono。
 *
 * 行高 ≥56dp；有动作时**整行可点**（点击区域含左右 24dp 页边距）。
 * 列表行沿用 D-060 的高频列表约定：只叠 5% 墨覆盖，不缩放、不震动。
 */
@Composable
private fun ColophonRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    showChevron: Boolean = false,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(
                if (onClick != null) {
                    Modifier.filmClickable(
                        onClick = onClick,
                        scaleTo = 1f,
                        overlayAlpha = 0.05f,
                        haptic = false,
                        onClickLabel = onClickLabel,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = spec.metrics.pageMargin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = p.textPrimary,
                fontFamily = spec.type.bodyFamily,
                fontSize = spec.type.body,
                lineHeight = spec.type.bodyLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    // 13sp 副题：FilmPaperSpec 里 13sp 这一档沿用共享 FilmType 的
                    // `rail` 命名（取景档位轨），纸面页借用；不写裸字号。
                    text = subtitle,
                    color = p.textSecondary,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.rail,
                    lineHeight = spec.type.labelLineHeight,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = value,
                color = p.textSecondary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.value,
                lineHeight = spec.type.monoLineHeight,
                textAlign = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showChevron) {
            Spacer(Modifier.width(8.dp))
            FilmIcon(
                kind = FilmIconKind.ChevronRight,
                tint = p.textTertiary,
                iconSize = 16.dp,
                strokeWidth = 1.5.dp,
            )
        }
    }
}

/** 条目行的 1px 分隔线：与内容同宽（左右各让出 24dp 页边距）。 */
@Composable
private fun ColophonHairline() {
    val margin = LocalFilmTheme.current.metrics.pageMargin
    PaperHairline(startInset = margin, endInset = margin)
}

/**
 * 页脚（§6.3）：`Pocket-CCD` Serif + 英文副题 + Version / Build / Author 三行。
 *
 * 版式按设计板：左对齐、标签左 / 值右对齐（mono），不是把版本号塞成一行居中。
 * 作者字段没有真实来源，显示 `—`（D-002），不写死设计板上的名字。
 */
@Composable
private fun ColophonFooter() {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spec.metrics.pageMargin),
    ) {
        // 04 分类行自己已经有一条 hairline；页脚不再重复画第二条线。
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Pocket-CCD",
            color = p.textPrimary,
            fontFamily = spec.type.titleFamily,
            fontSize = spec.type.optionTitle * 1.125f,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "A Camera App for Film Lovers",
            color = p.textSecondary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.monoSmall,
            lineHeight = spec.type.labelLineHeight,
        )
        Spacer(Modifier.height(18.dp))
        FooterMetaRow(label = "Version", value = BuildConfig.VERSION_NAME)
        FooterMetaRow(label = "Build", value = BuildConfig.VERSION_CODE.toString())
        FooterMetaRow(label = "Author", value = "—")
        Spacer(Modifier.height(32.dp))
    }
}

/** 页脚一行：左 11sp mono 标签，右 11sp mono 值。 */
@Composable
private fun FooterMetaRow(label: String, value: String) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = p.textTertiary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.monoSmall,
            lineHeight = spec.type.labelLineHeight,
        )
        Text(
            text = value,
            color = p.textPrimary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.monoSmall,
            lineHeight = spec.type.labelLineHeight,
        )
    }
}

/** 相机权限探针：与 AppRoot 的同名判断保持一致（READ 权限未申请，只能读 CAMERA）。 */
private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Preview(widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun SettingsScreenPreview() {
    FilmTheme(FilmPaperSpec) {
        SettingsScreen(onBack = {}, stockCount = 3)
    }
}
