package com.pocketccd.prototype.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R
import com.pocketccd.prototype.ui.film.FilmPaperSpec
import com.pocketccd.prototype.ui.film.FilmTheme
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.PaperPageHeader

/**
 * 开源与素材声明（D-048 的硬性合规页；D-063「白纸档案」版式）。
 *
 * 合规页的第一要求是**能读**，不是"看起来像个 App 页面"。所以：
 * 1. 纸面整页 + 版权页式页头（Serif 标题 + mono `LICENSES` 副题）；
 * 2. 四节用「01–04 序号 + Serif 标题 + 1px 规则线」分隔，不用卡片、描边块；
 * 3. 正文无衬线 14sp / 22sp 行高，按空行分段，长段落不做等宽（中文等宽字面率差）。
 *
 * **正文一字不改**：四节正文与导语都走 `stringResource`，措辞是合规项，改它要过
 * `docs/fotufilm-license-inventory.md` 清单。这里只改版式。
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
        PaperPageHeader(
            title = stringResource(R.string.licenses_title),
            subtitle = stringResource(R.string.licenses_subtitle),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            // 底部 TabBar 由 AppRoot 叠在页面之上（覆盖层不会自动隐藏它），
            // 留 88dp 保证最后一条合规说明滚到底时不被压住。
            contentPadding = PaddingValues(bottom = 64.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.licenses_intro),
                    color = p.textSecondary,
                    fontFamily = spec.type.bodyFamily,
                    fontSize = spec.type.body,
                    lineHeight = spec.type.bodyLineHeight,
                    modifier = Modifier.padding(
                        start = spec.metrics.pageMargin,
                        end = spec.metrics.pageMargin,
                        top = 24.dp,
                        bottom = 8.dp,
                    ),
                )
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

/**
 * 一节：编号（mono `textTertiary`）+ Serif 标题 + 14/22 正文（按空行分段）+ 1px 线。
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = spec.metrics.pageMargin,
                    end = spec.metrics.pageMargin,
                    top = 24.dp,
                    bottom = 20.dp,
                ),
        ) {
            Text(
                text = index,
                color = p.textTertiary,
                fontFamily = spec.type.monoFamily,
                fontSize = spec.type.mono,
                lineHeight = spec.type.monoLineHeight,
                modifier = Modifier.width(34.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = p.textPrimary,
                    fontFamily = spec.type.titleFamily,
                    fontSize = spec.type.optionTitle,
                    lineHeight = spec.type.bodyLineHeight,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(10.dp))
                // 按空行分段：合规文字本身用空行分了段，渲染时保留这个结构。
                body.split("\n\n").forEachIndexed { i, paragraph ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(
                        text = paragraph,
                        color = p.textSecondary,
                        fontFamily = spec.type.bodyFamily,
                        fontSize = spec.type.body,
                        lineHeight = spec.type.bodyLineHeight,
                    )
                }
            }
        }
        if (showDivider) {
            PaperHairline(
                startInset = spec.metrics.pageMargin,
                endInset = spec.metrics.pageMargin,
            )
        }
    }
}

@Preview(widthDp = 412, heightDp = 915, showBackground = true, backgroundColor = 0xFFECEAE4)
@Composable
private fun LicensesScreenPreview() {
    FilmTheme(FilmPaperSpec) {
        LicensesScreen(onBack = {})
    }
}
