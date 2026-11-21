package com.pocketccd.prototype.ui.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.glass

/** 底部五个目的地。**拍摄**在中央，是从任何页面回到取景页的唯一入口。 */
enum class GlassTab(val label: String, val icon: GlassIconKind) {
    Gallery("相册", GlassIconKind.Gallery),
    Films("胶卷", GlassIconKind.Film),
    Camera("拍摄", GlassIconKind.Aperture),
    Discover("发现", GlassIconKind.Sparkle),
    Settings("设置", GlassIconKind.Gear),
}

/** 一格的**可点区域**高度。比胶囊高，胶囊四周要留出气。 */
private val SlotHeight = 46.dp

/** 胶囊本体。比格子矮 6dp：胶囊压满格子会读成"一块底"，而不是"浮着的一片"。 */
private val PillHeight = 40.dp

/** 胶囊宽度上限。窄屏（320dp）时按格宽收，绝不溢出到邻格。 */
private val PillMaxWidth = 52.dp

/** 面板内留白：只有一圈，胶囊四边到这里为止。 */
private val PanelInset = 7.dp

/**
 * 悬浮底部 dock：**一块没有描边、没有阴影的浅板，加一个滑动的胶囊**。
 *
 * ## 元素只有三件
 *
 * 浅板、胶囊、五个图标。文字标签、指示条、描边、阴影全部删掉 —— 上一版的层次
 * 靠"再加一个形状"堆出来（每个 Tab 一条指示线，五个 Tab 就是五条），那不是质感，
 * 是噪音。现在**同一时刻画面上只有一个胶囊**，选中与否由它落在谁身上回答。
 *
 * ## 为什么可以不要文字
 *
 * 五个图标都是 Material Symbols Rounded 的标准形（相册 / theater / 光圈 / 星芒 / 齿轮），
 * 在"相机 App 的底部"这个语境里不需要注解。省下的高度让 dock 从 69dp 降到 60dp，
 * 列表页能多看一行。可读性由 `contentDescription` 保证，屏幕阅读器不受影响。
 *
 * ## 胶囊怎么定位
 *
 * 五格等宽，所以中心位置是**算出来的**，不靠 `onGloballyPositioned` 测量：
 * 第 i 格中心 = `slot × i + slot / 2`。测量有第一帧为 0 的问题（胶囊会从最左边
 * 飞过来），而等宽布局本来就是确定的，没必要测。
 *
 * ## 为什么浅板不要阴影了
 *
 * 旧版靠 14dp 环境阴影证明"我浮着"。但底色差本身就够：`surfaceRaised` 比 `body`
 * 亮 13 级。两件事叠在一起的结果是浅色屏幕上多一圈脏灰边 —— 极简的浮起只需要明度差。
 */
@Composable
fun GlassNavBar(
    active: GlassTab,
    onSelect: (GlassTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = MaterialTheme.glass
    val tabs = GlassTab.entries
    val index = tabs.indexOf(active).coerceAtLeast(0)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = GlassSpace.page)
            .background(color = p.surfaceRaised, shape = GlassShape.panel)
            .padding(PanelInset),
    ) {
        // 五格等宽：胶囊位置直接由格宽算出，见类注释。
        val slot = maxWidth / tabs.size
        val pillWidth = minOf(PillMaxWidth, slot - 6.dp)
        val target = slot * index + (slot - pillWidth) / 2
        val pillX by animateDpAsState(
            targetValue = target,
            // 略欠阻尼：落位时有一点点"吸住"的余味，但不会弹过头。
            animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
            label = "nav-pill",
        )

        Box(
            modifier = Modifier
                .offset(x = pillX, y = (SlotHeight - PillHeight) / 2)
                .size(width = pillWidth, height = PillHeight)
                .clip(RoundedCornerShape(percent = 50))
                // 黄铜兑进面板色（18%），不是灰：胶囊必须和"选中=黄铜"同源。
                .background(p.accent.copy(alpha = 0.18f)),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SlotHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = tab == active,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: GlassTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = MaterialTheme.glass
    // 未选中要**明显**退下去：它和选中项的差别是整套设计的全部信息量。
    // 55% 的次级灰在浅板上约等于 #A9A79E，比正文浅、比描边淡，正好是"在场但不说话"。
    val tint by animateColorAsState(
        targetValue = if (selected) p.accentDeep else p.onGlassMuted.copy(alpha = 0.55f),
        animationSpec = tween(220),
        label = "nav-tint",
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // 读在 graphicsLayer 里：按压只更新图层，不触发重组。
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "nav-press",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            // 点按热区 = 整格（约 71×46），比 24dp 的图标大得多。
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClickLabel = tab.label,
                onClick = onClick,
            )
            .semantics {
                contentDescription = tab.label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassIcon(
            kind = tab.icon,
            tint = tint,
            size = 24.dp,
            modifier = Modifier.graphicsLayer {
                val scale = 1f - 0.08f * press
                scaleX = scale
                scaleY = scale
            },
        )
    }
}
