package com.pocketccd.prototype.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.ui.theme.ConsoleElevation
import com.pocketccd.prototype.ui.theme.GlassPalette
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.consolePanel
import com.pocketccd.prototype.ui.theme.consolePress
import com.pocketccd.prototype.ui.theme.glass

/**
 * 通用控件层（文件名保留 `glass/`，材质已不是玻璃）。
 *
 * ## 从"深色玻璃"到"浅色机身"改了什么（D-090）
 *
 * 旧实现把 `io.github.kyant0:backdrop` 当作地基：`Modifier.drawBackdrop` 把元素背后的
 * 内容录成图层 → RenderEffect 模糊 → AGSL 折射 → 边缘高光。它是真玻璃，但代价是
 * **每个控件都要一条离屏录制 + 全屏模糊**，而且在"相机机身"这个隐喻下是错的：
 * 机身是暖白的实体塑料与金属，不是一块会透出背面的玻璃。
 *
 * 新实现只做三件事：暖白填充、1dp 描边、大半径低浓度外阴影（[consolePanel]）。
 * 按下反馈从"面板胀大"改成"陷进去"（[consolePress]）—— 内阴影比缩放更像实体键。
 *
 * ## 为什么控件名还叫 `Glass*`
 *
 * 改名要动 8 个页面、上百处调用点，而这次改动的验收标准是**看起来是一台机器**。
 * 名字与材质不一致这件事，记在 DECISION_LOG 的待清理项里，不在发布前夜动它。
 */

/** 无 backdrop 时代的遗留常量：只影响"多浮一点"，不影响材质。 */
private object GlassTuning {
    /** 芯片、图标键这类小控件的浮起高度。 */
    val keyLift = ConsoleElevation.key

    /** 卡片类面板的浮起高度。 */
    val panelLift = ConsoleElevation.card
}

/**
 * 一块面板。
 *
 * @param overPhoto 面板是否压在**影像**（照片/实时预览）上。压在影像上时换成近实心
 *   暖白（`veil`）：影像亮度不可控，半透明浅板在亮部会糊、在暗部会消失。
 * @param interactive 是否参与按压反馈（陷下去）。纯展示面板传 false。
 * @param onClick 非空即整块可点。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.card,
    overPhoto: Boolean = false,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(GlassSpace.inner),
    /** 覆盖描边色。选中态（当前装着的卷）用黄铜，其余保持默认。 */
    borderColor: Color? = null,
    /**
     * 倒角强度。卡片普遍比键帽大一个量级，同样的强度摊在大面上会读成"塑料壳" ——
     * 0.55 是量过的：小键给满、整张卡给一半，两者并排时厚度看起来才是一套。
     */
    bevel: Float = 0.55f,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = MaterialTheme.glass
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && interactive)

    Box(
        modifier = modifier
            .consolePanel(
                color = if (overPhoto) p.veil else p.surfaceRaised,
                shape = shape,
                elevation = if (overPhoto) GlassTuning.panelLift + 4.dp else GlassTuning.panelLift,
                borderColor = borderColor ?: p.border,
                bevel = bevel,
            )
            .consolePress(shape = shape, pressed = { press.value })
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        // 不用涟漪：涟漪是 Android 塑料感的来源，按压反馈由内阴影表达。
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(contentPadding),
    ) {
        content()
    }
}

/**
 * 图标钮：**正方形 + 正圆**，默认 48dp（Material 的最小可点尺寸）。
 *
 * @param label 读屏文案。**必填且无默认值** —— 编译器就挡住"只画图标不写语义"。
 * @param active 选中/开启态：黄铜底 + 深金图标。
 */
@Composable
fun GlassIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    overPhoto: Boolean = false,
    size: Dp = 48.dp,
    icon: @Composable (tint: Color) -> Unit,
) {
    val p = MaterialTheme.glass
    val buttonSize = if (size < 48.dp) 48.dp else size
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && enabled)

    val tint = when {
        !enabled -> p.onGlassMuted.copy(alpha = 0.45f)
        active -> p.accentDeep
        else -> p.onGlass
    }
    val fill = when {
        !enabled -> if (overPhoto) p.veil.copy(alpha = 0.72f) else p.surface
        active -> p.accent.copy(alpha = 0.22f)
        overPhoto -> p.veil
        else -> p.surfaceRaised
    }
    val border = when {
        active -> p.accent
        else -> p.border
    }

    Box(
        modifier = modifier
            // 宽高由同一个 size 决定：任何父级约束下都不会拉成椭圆。
            .size(buttonSize)
            .graphicsLayer {
                // 缩一点点：轮廓先"让开"，内阴影再"压进去"。
                val scale = 1f - 0.05f * press.value
                scaleX = scale
                scaleY = scale
            }
            .consolePanel(
                color = fill,
                shape = GlassShape.pill,
                elevation = GlassTuning.keyLift,
                borderColor = border,
            )
            .consolePress(shape = GlassShape.pill, pressed = { press.value })
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
    }
}

/**
 * 芯片：胶卷名、变焦档、筛选项。
 *
 * 选中态是**黄铜染色**（低透明金底 + 深金字 + 金色描边），不是实心色块：
 * 实心强调底在同一排芯片里会跳成一块贴纸。
 */
@Composable
fun GlassChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    overPhoto: Boolean = false,
    leading: (@Composable (Color) -> Unit)? = null,
    trailing: (@Composable (Color) -> Unit)? = null,
) {
    val p = MaterialTheme.glass
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed)
    val textColor = if (selected) p.accentDeep else p.onGlassMuted
    val fill = when {
        selected -> p.accent.copy(alpha = 0.20f)
        overPhoto -> p.veil
        else -> p.surfaceRaised
    }

    Row(
        modifier = modifier
            .consolePanel(
                color = fill,
                shape = GlassShape.chip,
                elevation = GlassTuning.keyLift,
                borderColor = if (selected) p.accent else p.border,
                // 芯片是长的：上下倒角要收一点，否则两端会亮成一截白条。
                bevel = 0.75f,
            )
            .consolePress(shape = GlassShape.chip, pressed = { press.value })
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading(textColor)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = GlassType.label,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing(textColor)
        }
    }
}

/**
 * 文字按钮：失败页的"重试"、空态的"去拍摄"、激活页的"激活"。
 *
 * 主按钮（[primary]）是**这一屏唯一的实体键**，所以它的投影比卡片重一档
 * （[ConsoleElevation.hero]）：它离机身最远，按下去的手感也最强。
 *
 * @param height 键高。48dp 是默认；激活页那种"整屏只有一个动作"的版式用 52dp ——
 *   否则那颗键在一大片留白里会显得怯。
 * @param textStyle 文字样式。默认 [GlassType.label]；主按钮建议传 [GlassType.action]。
 * @param busy 处理中。**外观与可用态一致**（实心黄铜）、但不接受点击 —— "正在核对…"
 *   是一句要读得出来的状态文案，把它画成禁用灰字等于让用户以为按钮坏了。
 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    textStyle: TextStyle = GlassType.label,
    busy: Boolean = false,
) {
    val p = MaterialTheme.glass
    val interaction = remember { MutableInteractionSource() }
    // 按压反馈只在真能按的时候走：busy 时按下去没有意义。
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && enabled && !busy)
    val lit = primary && (enabled || busy)

    val textColor = when {
        // 禁用态的文字要**比底色深得多**：0.40 的深字压在 0.38 的淡金上是一团雾，
        // "正在核对…"这种必须读得出来的状态文案会彻底消失（截图里抓到过）。
        !enabled && !busy && primary -> p.onAccent.copy(alpha = 0.68f)
        !enabled && !busy -> p.onGlassMuted.copy(alpha = 0.60f)
        primary -> p.onAccent
        else -> p.onGlass
    }
    val fill = when {
        // 禁用态的主键**仍然是黄铜**，只是不通电：换成灰底会和旁边的凹槽糊成一块，
        // 用户读到的不是"还不能按"，而是"这里没有按钮"。
        !enabled && !busy && primary -> p.accent.copy(alpha = 0.46f)
        !enabled && !busy -> p.surface
        primary -> p.accent
        else -> p.surfaceRaised
    }

    Box(
        modifier = modifier
            .height(height)
            .graphicsLayer {
                val scale = 1f - 0.03f * press.value
                scaleX = scale
                scaleY = scale
            }
            .consolePanel(
                color = fill,
                shape = GlassShape.chip,
                elevation = if (lit) ConsoleElevation.hero else ConsoleElevation.key + 1.dp,
                borderColor = when {
                    lit -> p.accentDeep.copy(alpha = 0.55f)
                    primary -> p.accentDeep.copy(alpha = 0.22f)
                    else -> p.border
                },
                bevel = if (enabled || busy) 0.85f else 0.4f,
            )
            .consolePress(
                shape = GlassShape.chip,
                pressed = { press.value },
                strength = if (primary) 0.26f else 0.42f,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !busy,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = textStyle, color = textColor)
    }
}

/** 分隔线：1px，比描边淡一档。 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            // Box 没有子元素时宽度是 0：不显式撑满，分隔线在 Column 里根本画不出来。
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.glass.border.copy(alpha = 0.62f)),
    )
}

/** 压暗层：全屏查看器、弹层背后。 */
@Composable
fun GlassScrim(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.glass.scrim))
}

/**
 * 面板里的文字统一用"面板上文字"色，避免每处手写 tint。
 * 需要强调色的子树单独用 [LocalContentColor] 覆盖即可。
 */
@Composable
fun GlassContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val p = MaterialTheme.glass
    CompositionLocalProvider(LocalContentColor provides p.onGlass) {
        Box(modifier) { content() }
    }
}

// ---------------------------------------------------------------------------
// 内部件
// ---------------------------------------------------------------------------

/**
 * 按压进度 0..1（不是 Boolean 直接切）。
 *
 * 返回 [Animatable]，在 **draw 块里读 `value`** 而不是在组合期读：
 * 这样按压动画不会触发重组，只重画，12MP 拍照时主线程本来就紧。
 */
@Composable
private fun rememberPress(pressed: Boolean): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        progress.animateTo(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.66f, stiffness = 1100f),
        )
    }
    return progress
}
