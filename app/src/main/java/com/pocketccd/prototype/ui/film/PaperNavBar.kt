package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * D-063 1:1 版面：纸面页底部 4 Tab。
 *
 * 只出现在纸面页（GALLERY / FILMS / DISCOVER / SETTINGS / RESULT）；相机页没有 TabBar，
 * 因为启动即全屏取景是硬约束。相册查看器的黑底灯箱打开时由 AppRoot 隐藏它。
 *
 * 视觉保持"版权页"的克制：不填底、不做胶囊，只用图标/文字颜色 + 字重表达选中。
 * 高度 56dp + 系统手势条 inset；纸面的背景一直铺到屏幕下沿。
 */
enum class PaperTab(val label: String, val icon: FilmIconKind) {
    GALLERY("相册", FilmIconKind.ImageMultiple),
    FILMS("胶卷", FilmIconKind.Filmstrip),
    DISCOVER("发现", FilmIconKind.Compass),
    SETTINGS("设置", FilmIconKind.Tune),
}

@Composable
fun PaperNavBar(
    active: PaperTab,
    onSelect: (PaperTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = LocalFilmTheme.current
    val p = spec.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(p.appBg)
            .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(p.hairline),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaperTab.entries.forEach { tab ->
                val selected = tab == active
                val color = if (selected) p.textPrimary else p.textTertiary
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .filmClickable(
                            onClick = { if (!selected) onSelect(tab) },
                            scaleTo = 0.97f,
                            role = Role.Tab,
                            onClickLabel = tab.label,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    FilmIcon(
                        kind = tab.icon,
                        tint = color,
                        iconSize = 20.dp,
                        strokeWidth = 1.5.dp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        color = color,
                        fontFamily = spec.type.bodyFamily,
                        fontSize = spec.type.label,
                        lineHeight = 14.sp,
                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
