package com.pocketccd.prototype.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketccd.prototype.ui.film.LocalFilmTheme
import com.pocketccd.prototype.ui.film.PaperHairline
import com.pocketccd.prototype.ui.film.PaperPageHeader
import com.pocketccd.prototype.ui.film.PaperSectionLabel

/**
 * 发现页（D-063 1:1 版面，B 方案脚手架）。
 *
 * 只展示本机真实数据：照片数、胶卷数、本卷进度。没有内容源之前不编造列表、
 * 不写“敬请期待”。后续智能体接手时，把新内容插在页脚之前即可。
 */
@Composable
fun DiscoverScreen(
    photoCount: Int,
    stockCount: Int,
    rollShots: Int,
    rollCapacity: Int,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(p.appBg)
            .verticalScroll(rememberScrollState()),
    ) {
        PaperPageHeader(
            title = "发现",
            subtitle = "DISCOVER",
        )

        Spacer(Modifier.height(8.dp))

        PaperSectionLabel(index = "01", title = "本机胶片档案")
        DiscoverRow(label = "照片", value = "$photoCount 张")
        PaperHairline(startInset = spec.metrics.pageMargin, endInset = spec.metrics.pageMargin)
        DiscoverRow(label = "胶卷", value = "$stockCount 卷")
        PaperHairline(startInset = spec.metrics.pageMargin, endInset = spec.metrics.pageMargin)
        DiscoverRow(label = "本卷", value = "%02d / %02d".format(rollShots, rollCapacity))
        PaperHairline(startInset = spec.metrics.pageMargin, endInset = spec.metrics.pageMargin)

        Spacer(Modifier.height(24.dp))

        Text(
            text = "这里将接入创作者的胶片档案与拍摄记录；当前只显示本机已有数据，不预置内容。",
            modifier = Modifier.padding(horizontal = spec.metrics.pageMargin),
            color = p.textSecondary,
            fontFamily = spec.type.bodyFamily,
            fontSize = spec.type.body,
            lineHeight = spec.type.bodyLineHeight,
        )

        // 底部 TabBar 是 AppRoot 的覆盖层，这里留出 88dp 不让内容被压住。
        Spacer(Modifier.height(88.dp))
    }
}

@Composable
private fun DiscoverRow(label: String, value: String) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spec.metrics.pageMargin, vertical = 18.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = p.textPrimary,
            fontFamily = spec.type.bodyFamily,
            fontSize = spec.type.body,
            lineHeight = spec.type.bodyLineHeight,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = value,
            color = p.textSecondary,
            fontFamily = spec.type.monoFamily,
            fontSize = spec.type.mono,
            lineHeight = spec.type.monoLineHeight,
            letterSpacing = 0.06.sp,
        )
    }
}
