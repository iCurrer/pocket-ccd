package com.pocketccd.prototype.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.PaperPageHeader
import com.pocketccd.prototype.ui.film.PaperSectionLabel
import com.pocketccd.prototype.ui.film.filmClickable

/**
 * 设置页（T9-020 / D-045，D-063「白纸档案」版权页版式）。
 *
 * 这一页不是"设置面板"，是一张**版权页（colophon）**：编号小节 + 规则线 + 条目行，
 * 没有卡片、描边块、玻璃、阴影，也没有金属。层级只靠留白与 1px 墨线。
 *
 * **删掉了**旧版的「默认滤镜」分段控件（日间/闪光/夜间）。它没有接线 ——
 * 选了不落盘、也不影响任何成像路径，是一个点了没反应的假开关。
 * D-041 也确认了不做"预览风格"维度，留着只会让人以为真能改。
 *
 * 事实口径（D-002）：本页每个值都必须有来源。
 * - `保存位置` 的副题写「本应用 + 系统相册」：成片确实同时写应用私有目录
 *   （`files/Pictures/pocket-ccd/`）与系统相册（`Pictures/Pocket CCD/`），
 *   旧文案「仅本应用」是错的（`PhotoStore.save` 两份都写）。
 * - `照片质量` 的 92 是真实 JPEG 质量（`PhotoStore.save` 默认值）。
 * - `胶卷` 的卷数来自真实扫描结果，点行触发重扫（内置 pack 已随 APK 释放）。
 *
 * 说明：本页当前没有任何布尔型设置，所以没有引入 [com.pocketccd.prototype.ui.film.PaperSwitch]
 * —— 没有可切换的真值就不该画一个开关（D-002）。将来加真开关时按它的 48dp 整行规格用。
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
    var showDiagnostics by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PaperPageHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item {
                    PaperSectionLabel(index = "01", title = "保存与输出")

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
                    ColophonHairline()
                    // 卷随 APK 内置（D-048），首次启动自动落盘；这一行是给
                    // 手动 adb push 覆盖的场景留的重扫入口，点了才重扫，
                    // 不用杀进程重进。
                    ColophonRow(
                        title = stringResource(R.string.settings_film_roll),
                        subtitle = stringResource(R.string.settings_film_roll_hint),
                        value = "$stockCount 卷",
                        onClick = onRefreshStocks,
                        onClickLabel = "重新扫描胶卷",
                        showChevron = true,
                    )
                    ColophonHairline()
                }

                item {
                    PaperSectionLabel(index = "02", title = "关于")

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
                    ColophonHairline()
                }

                if (BuildConfig.DEBUG) {
                    item {
                        // 开发者条目只在 debug 构建出现；编号顺延成 03，版式与正式条目一致。
                        PaperSectionLabel(index = "03", title = "开发者")
                        ColophonRow(
                            title = stringResource(R.string.settings_diagnostics),
                            subtitle = stringResource(R.string.settings_diagnostics_value),
                            onClick = { showDiagnostics = true },
                            onClickLabel = "查看诊断信息",
                            showChevron = true,
                        )
                        ColophonHairline()
                    }
                }
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
        // 不再显式指定容器/内容色：外层 AppRoot 已用 PocketCcdTheme(darkTheme = false)
        // 提供纸色 M3 亮色方案（surface = sheetBg、onSurface = 墨色），对话框自动继承。
        // 文字仍走本页字阶令牌，避免 M3 默认字型与页头分叉。
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

@Preview(widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun SettingsScreenPreview() {
    FilmTheme(FilmPaperSpec) {
        SettingsScreen(onBack = {}, stockCount = 3)
    }
}
