package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.film.FilmIconKind
import com.pocketccd.prototype.ui.film.FilmRoundButton
import com.pocketccd.prototype.ui.film.LocalFilmTheme

/**
 * 开源与素材声明（D-048 的硬性合规页；2026-10-23 版式重做）。
 *
 * **为什么重做**：旧版把整段合规文字塞进等宽字体 + 描边卡片里 —— 中文在等宽字体下
 * 字面率极差，长段落读起来像日志；四张描边卡片又把本来该读的文字切成一堆"控件"。
 * 合规页的第一要求是**能读**，不是"看起来像个 App 页面"。
 *
 * 新版只做三件事：
 * 1. 正文改回无衬线、13sp/22sp 行高，按空行分段；
 * 2. 每节用「序号 + 标题 + 一条细线」分隔，不再用卡片描边；
 * 3. 标题 16sp、副题等宽 10sp 拉开字距，页头与设置页同一结构。
 *
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
            .background(p.appBg),
    ) {
        LicensesHeader(onBack = onBack)

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(top = 6.dp, bottom = 44.dp),
        ) {
            item {
                Text(
                    text = "本应用使用的胶片引擎、胶片档案与第三方组件如下。胶片档案按 " +
                        "CC BY-SA 4.0 分发，文件保持可读出、可替换。",
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = spec.type.body,
                        lineHeight = 22.sp,
                        letterSpacing = 0.01.em,
                        color = p.textSecondary,
                    ),
                )
                Spacer(Modifier.height(26.dp))
            }

            item {
                LicenseSection(
                    index = "01",
                    title = stringResource(R.string.licenses_section_engine),
                    body = stringResource(R.string.licenses_engine_body),
                )
            }
            item {
                LicenseSection(
                    index = "02",
                    title = stringResource(R.string.licenses_section_packs),
                    body = stringResource(R.string.licenses_packs_body),
                )
            }
            item {
                LicenseSection(
                    index = "03",
                    title = stringResource(R.string.licenses_section_thirdparty),
                    body = stringResource(R.string.licenses_thirdparty_body),
                )
            }
            item {
                LicenseSection(
                    index = "04",
                    title = stringResource(R.string.licenses_section_trademark),
                    body = stringResource(R.string.licenses_trademark_body),
                    showDivider = false,
                )
            }
        }
    }
}

@Composable
private fun LicensesHeader(onBack: () -> Unit) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilmRoundButton(icon = FilmIconKind.Close, onClick = onBack)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.licenses_title),
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = spec.type.sheetTitle,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.06.em,
                    color = p.textPrimary,
                ),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.licenses_subtitle),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = spec.type.label,
                    letterSpacing = 0.24.em,
                    color = p.textTertiary,
                ),
            )
        }
        Spacer(Modifier.weight(1f))
        // 与左侧 48dp 热区等宽的占位：标题因此真的居中。
        Spacer(Modifier.width(48.dp))
    }
}

/**
 * 一节：序号（金、等宽）+ 标题 + 正文（无衬线、按空行分段）+ 细线。
 *
 * 序号不是装饰：合规条目有四类，给每个编号让用户能说"第 2 条说的是文件位置"。
 */
@Composable
private fun LicenseSection(
    index: String,
    title: String,
    body: String,
    showDivider: Boolean = true,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = index,
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = spec.type.label,
                    letterSpacing = 0.10.em,
                    color = p.accent,
                ),
                modifier = Modifier
                    .width(34.dp)
                    .padding(top = 2.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TextStyle(
                        fontFamily = FontFamily.Default,
                        fontSize = spec.type.optionTitle,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.02.em,
                        color = p.textPrimary,
                    ),
                )
                Spacer(Modifier.height(10.dp))
                // 按空行分段：合规文字本身用空行分了段，渲染时保留这个结构。
                body.split("\n\n").forEachIndexed { i, paragraph ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        text = paragraph,
                        style = TextStyle(
                            fontFamily = FontFamily.Default,
                            fontSize = spec.type.body,
                            lineHeight = 22.sp,
                            letterSpacing = 0.01.em,
                            color = p.textSecondary,
                        ),
                    )
                }
            }
        }
        if (showDivider) {
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(p.hairline),
            )
            Spacer(Modifier.height(22.dp))
        }
    }
}
