package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.film.LocalFilmTheme

/**
 * 开源与素材声明（D-048 的硬性合规页）。
 *
 * 为什么必须在 App 里，而不是只放仓库里：
 * `.fswp` 是从 Fotufilm 的 film profile 预烘焙出来的派生资产，按 **CC BY-SA 4.0** 分发，
 * 条款要求署名、给链接、声明是否修改，并且**不得用技术手段阻止用户取出或替换**这些文件。
 * APK 现在自带三份 pack，收到包的人手里就有这三份 CC BY-SA 资产 —— 所以声明必须随包走，
 * 不能指望对方去翻 GitHub。`files/film/` 保持可读写也是同一原因。
 *
 * 版式沿用 `FilmTheme`：近黑底 + 香槟金 + 等宽小字，与设置页同一套标题结构。
 * 正文全部走 `stringResource`，不硬编码 —— 这段文字的措辞是合规项，改它要过一遍清单。
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg)
            .padding(horizontal = spec.metrics.gutterWide),
    ) {
        SettingsHeader(
            onBack = onBack,
            title = stringResource(R.string.licenses_title),
            subtitle = stringResource(R.string.licenses_subtitle),
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                LicenseBlock(
                    title = stringResource(R.string.licenses_section_engine),
                    body = stringResource(R.string.licenses_engine_body),
                )
            }
            item {
                LicenseBlock(
                    title = stringResource(R.string.licenses_section_packs),
                    body = stringResource(R.string.licenses_packs_body),
                )
            }
            item {
                LicenseBlock(
                    title = stringResource(R.string.licenses_section_thirdparty),
                    body = stringResource(R.string.licenses_thirdparty_body),
                )
            }
            item {
                LicenseBlock(
                    title = stringResource(R.string.licenses_section_trademark),
                    body = stringResource(R.string.licenses_trademark_body),
                )
            }
        }
    }
}

@Composable
private fun LicenseBlock(title: String, body: String) {
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(p.controlFill)
                .border(spec.metrics.hairline, p.hairline, shape)
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Text(
                text = body,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 17.sp,
                    letterSpacing = 0.02.em,
                    color = p.inkStrong,
                ),
                // 地址与路径不能省略号截断 —— 截断了就等于没给。
                overflow = TextOverflow.Visible,
            )
        }
    }
}
