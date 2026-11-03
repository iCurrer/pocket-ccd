package com.pocketccd.prototype.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.camera.ImprintSettings
import com.pocketccd.prototype.ui.glass.GlassDivider
import com.pocketccd.prototype.ui.glass.GlassIcon
import com.pocketccd.prototype.ui.glass.GlassIconButton
import com.pocketccd.prototype.ui.glass.GlassIconKind
import com.pocketccd.prototype.ui.glass.GlassSurface
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass

/**
 * 设置页。四组真实信息 + 一件真实开关，没有任何"待接入"的假入口。
 *
 * 与旧版（`SettingsScreen` 的四分类纸面列表）同构，但换成玻璃卡，并去掉两处
 * 已经名不副实的东西：旧版顶部的"胶卷 N 支"点进去其实是重扫（现在写清是重扫），
 * 旧版页脚的版本行重复了两次。
 */
@Composable
fun SettingsScreen(
    stockCount: Int,
    photoCount: Int,
    versionName: String,
    modifier: Modifier = Modifier,
    onRefreshStocks: () -> Unit = {},
    /** 片边 / 日期后背的当前状态（D-081）。 */
    imprint: ImprintSettings = ImprintSettings(),
    onToggleFilmEdge: () -> Unit = {},
    onToggleDateBack: () -> Unit = {},
    /**
     * 诊断入口：跑一遍引擎自测并显示样张。**只有 debug / benchmark 包才传**（D-075）——
     * 发布包里这一行为 null，界面上不会出现"开发者工具"。
     */
    onRunSelfTest: (() -> Unit)? = null,
) {
    val p = MaterialTheme.glass
    var licensesOpen by remember { mutableStateOf(false) }

    if (licensesOpen) {
        LicensesScreen(onBack = { licensesOpen = false })
        return
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = "设置", style = GlassType.title, color = p.onGlass)
                Text(text = "本机数据与声明", style = GlassType.caption, color = p.onGlassMuted)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
        ) {
            // 成像：两个**烘焙进像素**的开关（D-081）。放在"胶卷"之前，因为它们
            // 直接决定下一张照片长什么样。
            Group(title = "成像") {
                ToggleRow(
                    title = "片边",
                    subtitle = "把成片合成到 135 半格胶片条上（4:3，不裁切）",
                    checked = imprint.filmEdge,
                    onToggle = onToggleFilmEdge,
                )
                GlassDivider()
                ToggleRow(
                    title = "日期后背",
                    subtitle = "拍摄日期印在画面右下角，关掉后只影响新拍的照片",
                    checked = imprint.dateBack,
                    onToggle = onToggleDateBack,
                )
                GlassDivider()
                StaticRow(title = "片边画幅", value = "半格 135 · 24×18mm")
            }

            Group(title = "胶卷") {
                LinkRow(
                    title = "重新扫描胶卷",
                    value = "已安装 $stockCount 支",
                    onClick = onRefreshStocks,
                )
                GlassDivider()
                StaticRow(title = "胶卷文件位置", value = "Android/data/…/files/film/")
            }

            Group(title = "关于") {
                LinkRow(
                    title = "开源与素材声明",
                    value = stringResource(R.string.settings_licenses_value),
                    onClick = { licensesOpen = true },
                )
                GlassDivider()
                StaticRow(title = "本机照片", value = "$photoCount 张")
                GlassDivider()
                StaticRow(title = "版本", value = versionName)
            }

            if (onRunSelfTest != null) {
                Group(title = "诊断") {
                    LinkRow(
                        title = "运行成像自测",
                        value = "引擎 · 分块 · 样张",
                        onClick = onRunSelfTest,
                    )
                    GlassDivider()
                    StaticRow(
                        title = "自测做什么",
                        value = "一致性 / 耗时 / NaN / 样张",
                    )
                }
            }

            GlassSurface(
                shape = GlassShape.card,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(14.dp),
            ) {
                Text(
                    text = "这个应用没有网络权限。照片只写在本机：应用私有目录一份、" +
                        "系统相册一份，不会上传到任何地方。",
                    style = GlassType.caption,
                    color = p.onGlassMuted,
                )
            }

            Spacer(Modifier.height(GlassSpace.dockReserve))
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    val p = MaterialTheme.glass
    Column {
        Text(
            text = title,
            style = GlassType.badge,
            color = p.onGlassMuted,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
        )
        GlassSurface(
            shape = GlassShape.card,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Column { content() }
        }
    }
}

@Composable
private fun LinkRow(title: String, value: String, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = title, onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = GlassType.body, color = p.onGlass)
            Spacer(Modifier.height(2.dp))
            Text(text = value, style = GlassType.caption, color = p.onGlassMuted)
        }
        GlassIcon(GlassIconKind.Chevron, p.onGlassMuted, size = 16.dp)
    }
}

@Composable
private fun StaticRow(title: String, value: String) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = GlassType.body, color = p.onGlass, modifier = Modifier.weight(1f))
        Text(text = value, style = GlassType.numeric, color = p.onGlassMuted)
    }
}

/**
 * 带开关的一行。
 *
 * 整行可点（不只是 Switch 那一小块 32dp 的热区）：设置页里"点标题没反应"是最常见的
 * 小挫败。Switch 自己也会吃掉点击，不会重复切换。
 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val p = MaterialTheme.glass
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Switch,
                onClickLabel = title,
                onClick = onToggle,
            )
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = GlassType.body, color = p.onGlass)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = GlassType.caption, color = p.onGlassMuted)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onGlass,
                checkedTrackColor = p.accent,
                uncheckedThumbColor = p.onGlassMuted,
                uncheckedTrackColor = p.fillStrong,
            ),
        )
    }
}

/**
 * 开源与素材声明（FOT-024）。
 *
 * 内容全部来自 `strings.xml` 的 `licenses_*`：CC BY-SA 4.0 的披露义务要求署名
 * 与修改说明可读，**不许**为了好看把这些句子改短。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val p = MaterialTheme.glass
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = GlassSpace.page, end = GlassSpace.page, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                label = "返回",
                onClick = onBack,
                icon = { tint -> GlassIcon(GlassIconKind.Back, tint) },
            )
            Spacer(Modifier.width(GlassSpace.gap))
            Text(
                text = stringResource(R.string.licenses_title),
                style = GlassType.cardTitle,
                color = p.onGlass,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassSpace.page),
            verticalArrangement = Arrangement.spacedBy(GlassSpace.gap),
        ) {
            Text(
                text = stringResource(R.string.licenses_intro),
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
            LicenseSection(R.string.licenses_section_engine, R.string.licenses_engine_body)
            LicenseSection(R.string.licenses_section_packs, R.string.licenses_packs_body)
            LicenseSection(R.string.licenses_section_thirdparty, R.string.licenses_thirdparty_body)
            LicenseSection(R.string.licenses_section_trademark, R.string.licenses_trademark_body)
            Spacer(Modifier.height(GlassSpace.dockReserve))
        }
    }
}

@Composable
private fun LicenseSection(titleRes: Int, bodyRes: Int) {
    val p = MaterialTheme.glass
    GlassSurface(
        shape = GlassShape.card,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
    ) {
        Column {
            Text(text = stringResource(titleRes), style = GlassType.cardTitle, color = p.onGlass)
            Spacer(Modifier.height(GlassSpace.tight))
            Text(
                text = stringResource(bodyRes),
                style = GlassType.caption,
                color = p.onGlassMuted,
            )
        }
    }
}
