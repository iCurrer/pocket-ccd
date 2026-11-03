package com.pocketccd.prototype.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass

/** 底部五个目的地。**拍摄**是圆心，其余四个是平级 Tab。 */
enum class GlassTab(val label: String, val icon: GlassIconKind) {
    Gallery("相册", GlassIconKind.Gallery),
    Films("胶卷", GlassIconKind.Film),
    Camera("拍摄", GlassIconKind.Aperture),
    Discover("发现", GlassIconKind.Sparkle),
    Settings("设置", GlassIconKind.Gear),
}

/**
 * 悬浮玻璃 TabBar。
 *
 * 它是本工程里**唯一真正"糊掉背后内容"的控件**：由 [GlassBackdrop] 的 overlay 槽
 * 提供 `Backdrop`，所以滚动到 TabBar 底下的照片会被实际模糊 + 边缘折射，而不是被一层
 * 半透明白盖住。参数（模糊 16dp、折射 22/24dp）比普通面板更强 —— 它面积大、
 * 又永远压在会动的内容上，效果弱了就像一块灰条。
 *
 * 选中态不用"另一块实心底"：玻璃上再叠实心块会把层次糊平。这里是
 * 图标 + 文字同时上强调色，加一条 3dp 指示条。
 */
@Composable
fun GlassNavBar(
    active: GlassTab,
    onSelect: (GlassTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        modifier = modifier.fillMaxWidth().padding(horizontal = GlassSpace.page),
        shape = GlassShape.panel,
        overPhoto = true,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        blurRadius = 16.dp,
        lensHeight = 22.dp,
        lensAmount = 24.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassTab.entries.forEach { tab ->
                NavItem(tab = tab, selected = tab == active, onClick = { onSelect(tab) })
            }
        }
    }
}

@Composable
private fun NavItem(tab: GlassTab, selected: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.glass
    val tint by animateColorAsState(
        targetValue = if (selected) p.accent else p.onGlassMuted,
        animationSpec = tween(180),
        label = "nav-tint",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 读在 graphicsLayer 里：按压只更新图层，不触发重组。
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "nav-press",
    )

    Column(
        modifier = Modifier
            .graphicsLayer {
                val scale = 1f - 0.04f * press
                scaleX = scale
                scaleY = scale
            }
            // 不 clip 成胶囊：这一格是 44×60 的竖条，用 pill 去裁它只会得到一个
            // "被压扁的椭圆"轮廓，和旁边真正的圆形按钮是两套语言。
            .clickable(
                interactionSource = interaction,
                // 玻璃上不用涟漪：按压反馈是整体轻微收拢 + 图标变色。
                indication = null,
                role = Role.Tab,
                onClickLabel = tab.label,
                onClick = onClick,
            )
            .semantics { contentDescription = tab.label }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 五个 Tab 用同一个光学尺寸：之前拍摄键单独放大到 26dp，一排里就它"胖一圈"。
            GlassIcon(
                kind = tab.icon,
                tint = tint,
                size = 24.dp,
                filled = selected && tab == GlassTab.Camera,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(text = tab.label, style = GlassType.badge, color = tint)
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(width = 14.dp, height = 3.dp)
                .clip(CircleShape)
                .background(if (selected) p.accent else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}
