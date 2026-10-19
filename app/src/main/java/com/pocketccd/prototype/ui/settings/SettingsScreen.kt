package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.BuildConfig
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.film.FilmIcon
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme

/**
 * 设置页（T9-020 / D-045）。原型没有这一页，版式按 `FilmTheme` 令牌自行重排：
 * 与取景/成片/相册同一套近黑底、香槟金、等宽小字。
 *
 * **删掉了**旧版的「默认滤镜」分段控件（日间/闪光/夜间）。它没有接线 ——
 * 选了不落盘、也不影响任何成像路径，是一个点了没反应的假开关。
 * D-041 也确认了不做"预览风格"维度，留着只会让人以为真能改。
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg)
            .padding(horizontal = spec.metrics.gutterWide),
    ) {
        SettingsHeader(onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SettingsSection(title = "保存与输出") {
                    SettingsRow(
                        title = stringResource(R.string.settings_save_location),
                        subtitle = stringResource(R.string.settings_save_location_value),
                        value = "相册",
                    )
                    Hairline()
                    SettingsRow(
                        title = stringResource(R.string.settings_quality),
                        subtitle = stringResource(R.string.settings_quality_subtitle),
                        value = stringResource(R.string.settings_quality_value),
                    )
                    Hairline()
                    // pack 走 adb push 进应用私有目录，点了才重扫 —— 用户往目录里
                    // 补卷之后不用杀进程重进。
                    SettingsRow(
                        title = stringResource(R.string.settings_film_roll),
                        subtitle = stringResource(R.string.settings_film_roll_hint),
                        value = "$stockCount 卷",
                        onClick = onRefreshStocks,
                    )
                }
            }
            item {
                SettingsSection(title = "关于") {
                    SettingsRow(
                        title = stringResource(R.string.settings_version),
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    Hairline()
                    SettingsRow(
                        title = stringResource(R.string.settings_privacy),
                        subtitle = stringResource(R.string.settings_privacy_note),
                        value = stringResource(R.string.settings_privacy_value),
                    )
                }
            }
            if (BuildConfig.DEBUG) {
                item {
                    SettingsSection(title = "开发者") {
                        SettingsRow(
                            title = stringResource(R.string.settings_diagnostics),
                            subtitle = stringResource(R.string.settings_diagnostics_value),
                            onClick = { showDiagnostics = true },
                        )
                    }
                }
            }
        }
    }

    if (showDiagnostics) {
        // Material3 的 AlertDialog 默认用浅色容器，在暗色页上会弹一块白。
        // 显式给三个颜色，让它跟着令牌走。
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            containerColor = p.sheetBg,
            titleContentColor = p.textPrimary,
            textContentColor = p.ink,
            title = {
                Text(
                    text = stringResource(R.string.settings_diagnostics_title),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_diagnostics_body,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { showDiagnostics = false }) {
                    Text(
                        text = stringResource(R.string.settings_close),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = p.accent,
                    )
                }
            },
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    val p = LocalFilmTheme.current.palette
    // 与成片页/相册页同一套居中标题结构，三个页面来回切不会有标题跳位。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmRoundButton(
            icon = FilmIconKind.Close,
            onClick = onBack,
            iconSize = 15.dp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(4f),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.04.em,
                    color = p.textPrimary,
                ),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.settings_subtitle),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    letterSpacing = 0.14.em,
                    color = p.inkFaint,
                ),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        // 与左侧按钮等宽的占位，保证标题真正居中。
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.sp,
                letterSpacing = 0.16.em,
                color = p.inkGhost,
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.controlFill)
                .border(spec.metrics.hairline, p.hairline, shape),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalFilmTheme.current.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = p.textPrimary,
                ),
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        lineHeight = 12.sp,
                        letterSpacing = 0.06.em,
                        color = p.inkGhost,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    letterSpacing = 0.06.em,
                    color = p.inkMuted,
                ),
            )
        }
        if (onClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            FilmIcon(
                kind = FilmIconKind.ChevronRight,
                tint = p.inkGhost,
                iconSize = 14.dp,
            )
        }
    }
}

@Composable
private fun Hairline() {
    val p = LocalFilmTheme.current.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp)
            .height(1.dp)
            .background(p.hairline),
    )
}

@Preview(widthDp = 360, heightDp = 800, showBackground = true, backgroundColor = 0xFF0B0C0E)
@Composable
private fun SettingsScreenPreview() {
    FilmTheme {
        SettingsScreen(onBack = {}, stockCount = 3)
    }
}
