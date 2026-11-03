package com.pocketccd.prototype.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.pocketccd.prototype.ui.theme.GlassPalette
import com.pocketccd.prototype.ui.theme.GlassShape
import com.pocketccd.prototype.ui.theme.GlassSpace
import com.pocketccd.prototype.ui.theme.GlassType
import com.pocketccd.prototype.ui.theme.glass

/**
 * 玻璃组件层。**全部建立在 `io.github.kyant0:backdrop`（Kyant0/AndroidLiquidGlass）之上。**
 *
 * ## 它和旧实现（手绘半透明 + 描边 + 竖直渐变）的根本区别
 *
 * 旧版是"画得像玻璃"：一层白色低透明度填充、一圈 1px 描边、一条从顶部往下 220px 的
 * 白色渐变。它有三个无法修的硬伤 ——
 *
 * 1. **描边恒定 1dp、渐变终点写死 220px**：不同密度/不同高度的面板上，那条"厚度感"
 *    要么看不见，要么变成一道硬边；
 * 2. **玻璃不会随内容变化**：底下是照片还是黑底，面板一模一样，看着就是塑料；
 * 3. **按压反馈是 `scale` + 涟漪**：涟漪会把玻璃糊成一块 Android 塑料。
 *
 * 新实现交给库做：`Modifier.drawBackdrop` 把元素背后的内容录成图层 → RenderEffect
 * 模糊 → AGSL 折射（`lens`）→ AGSL 边缘高光（`Highlight`）→ 可选内/外阴影。
 * 按压时用 `layerBlock` 缩放**面板本身**，库会反向变换背景图层，于是玻璃"鼓起来"而
 * 背景不动 —— 这正是 Liquid Glass 的手感，涟漪被去掉。
 *
 * ## 三级降级（都不崩、不画假东西）
 *
 * | 设备 | 效果 |
 * |---|---|
 * | API 33+ | 模糊 + 折射 + 方向性边缘高光（AGSL） |
 * | API 31/32 | 模糊 + 平涂边缘高光（无 RuntimeShader） |
 * | API 29/30 | 只是"着色 + 1px 高光描边"（Compose 侧没有 RenderEffect） |
 *
 * 另外，**背后没有可采样内容时**（取景页的相机预览是独立 Surface，录不到像素），
 * [LocalGlassBackdrop] 为 null，组件连模糊都不申请，只画着色 + 高光 ——
 * 宁可少一层效果，也不伪造一层假模糊。
 */

/** 无 backdrop 时的占位：画不出任何东西，且被库当作"空图层"跳过。 */
private val NoBackdrop: Backdrop = emptyBackdrop()

/** 玻璃的通用参数。全部用 dp 表达，由各自的 Density 换算成 px。 */
private object GlassTuning {
    /** 面板类（卡片、dock）的背景模糊半径。 */
    val blur = 10.dp

    /** 折射带高度：从边缘往里多少距离开始弯折。 */
    val lensHeight = 14.dp

    /** 折射量（边缘像素被"吸"进来的位移）。 */
    val lensAmount = 18.dp

    /** 图标钮/芯片这种小控件用的更小的折射。 */
    val smallLensHeight = 8.dp
    val smallLensAmount = 10.dp
    val smallBlur = 6.dp

    /** 按下时面板整体胀大多少（不是缩小 —— 玻璃是"被挤压"）。 */
    val pressLift = 2.dp
}

/**
 * 一块玻璃面板。
 *
 * @param overPhoto 面板是否压在**影像**（照片/实时预览）上。压在影像上时多一层深色
 *   蒙版：影像亮度不可控，只靠 6% 白色叠加，白字在亮部会糊掉。
 * @param interactive 是否参与按压反馈（面板胀大 + 边缘高光增强）。纯展示面板传 false。
 * @param onClick 非空即整块可点。
 * @param blurRadius 背景模糊半径。面积大的面板（TabBar、动作条）可以调高，
 *   小芯片调低 —— 半径和控件尺寸不匹配时，边缘会糊出"脏边"。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = GlassShape.card,
    overPhoto: Boolean = false,
    interactive: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(GlassSpace.inner),
    blurRadius: Dp = GlassTuning.blur,
    lensHeight: Dp = GlassTuning.lensHeight,
    lensAmount: Dp = GlassTuning.lensAmount,
    content: @Composable BoxScope.() -> Unit,
) {
    val p = MaterialTheme.glass
    val backdrop = LocalGlassBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && interactive)
    // 折射只对圆角矩形/胶囊有定义（SDF 依赖四个角半径）；自定义 Shape 会抛，提前挡住。
    val lensable = shape is CornerBasedShape

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop ?: NoBackdrop,
                shape = { shape },
                effects = {
                    // 只有真的有背景可采样时才申请模糊/折射：否则是在空图层上白烧 GPU。
                    if (backdrop != null) {
                        vibrancy()
                        blur(blurRadius.toPx())
                        if (lensable) {
                            lens(lensHeight.toPx(), lensAmount.toPx())
                        }
                    }
                },
                highlight = { glassHighlight(press.value, width = 0.6.dp) },
                shadow = { glassShadow(strength = if (overPhoto) 1f else 0.6f) },
                innerShadow = {
                    InnerShadow(radius = 12.dp, alpha = 0.22f + 0.20f * press.value)
                },
                layerBlock = if (interactive) pressLayer(press) else null,
                onDrawSurface = { drawGlassSurface(p, overPhoto) },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        // 玻璃不用涟漪：涟漪是 Android 塑料感的来源，按压反馈由
                        // 面板胀大 + 高光增强表达。
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
        // 面板内部不能再采样"页面背景"：那会把面板自己的背面重新画一遍（穿帮）。
        CompositionLocalProvider(LocalGlassBackdrop provides null) {
            content()
        }
    }
}

/**
 * 玻璃图标钮：**正方形 + 正圆**，默认 48dp（Material 的最小可点尺寸）。
 *
 * 之前是 `size + 4.dp`（44 视觉 / 48 热区）两套尺寸并存，再加上调用点各自传 size，
 * 屏幕上就出现了"大大小小的椭圆"。现在只有一个尺寸来源：`size` 同时是宽、高、直径。
 *
 * @param label 读屏文案。**必填且无默认值** —— 编译器就挡住了"只画图标不写语义"。
 * @param active 选中/开启态：强调色实底 + 反色图标。
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
    val backdrop = LocalGlassBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && enabled)
    val tint = when {
        !enabled -> p.onGlassMuted.copy(alpha = 0.38f)
        active -> p.onAccent
        else -> if (overPhoto) Color.White.copy(alpha = 0.92f) else p.onGlass
    }

    Box(
        modifier = modifier
            // 宽高由同一个 size 决定：任何父级约束下都不会拉成椭圆。
            .size(size)
            .drawBackdrop(
                backdrop = backdrop ?: NoBackdrop,
                shape = { CircleShape },
                effects = {
                    if (backdrop != null) {
                        vibrancy()
                        blur(GlassTuning.smallBlur.toPx())
                        lens(
                            GlassTuning.smallLensHeight.toPx(),
                            GlassTuning.smallLensAmount.toPx(),
                        )
                    }
                },
                highlight = { glassHighlight(press.value, width = 0.7.dp) },
                shadow = { glassShadow(strength = 0.5f) },
                layerBlock = pressLayer(press),
                onDrawSurface = {
                    when {
                        !enabled -> drawRect(Color.White.copy(alpha = 0.04f))
                        active -> drawRect(p.accent)
                        overPhoto -> {
                            drawRect(p.veil)
                            drawRect(Color.White.copy(alpha = 0.08f))
                        }
                        else -> drawRect(p.fill)
                    }
                },
            )
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
 * 选中态是**强调色玻璃**（低透明强调填充 + 强调色文字），不是实心色块：
 * 实心强调底在玻璃上会跳成一块贴纸。
 *
 * @param overPhoto 芯片是否直接压在实时预览/照片上（取景页顶部那两枚就是）。
 *   压在影像上时要垫一层深色蒙版，否则 6% 白在亮部完全读不出字。
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
    val backdrop = LocalGlassBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed)
    val textColor = if (selected) p.onGlass else p.onGlassMuted

    Row(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop ?: NoBackdrop,
                shape = { GlassShape.chip },
                effects = {
                    if (backdrop != null) {
                        vibrancy()
                        blur(GlassTuning.smallBlur.toPx())
                        lens(
                            GlassTuning.smallLensHeight.toPx(),
                            GlassTuning.smallLensAmount.toPx(),
                        )
                    }
                },
                highlight = { glassHighlight(press.value, width = 0.6.dp) },
                shadow = { glassShadow(strength = 0.45f) },
                layerBlock = pressLayer(press),
                onDrawSurface = {
                    // 压在影像上先垫深色蒙版；选中态是强调色低透明叠加，不是实心底。
                    if (overPhoto) drawRect(p.veil)
                    drawRect(if (overPhoto) Color.White.copy(alpha = 0.08f) else p.fill)
                    if (selected) drawRect(p.accent.copy(alpha = 0.30f))
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (leading != null) {
            leading(if (selected) p.accent else textColor)
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

/** 文字按钮：失败页的"重试"、空态的"去拍摄"。 */
@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    enabled: Boolean = true,
) {
    val p = MaterialTheme.glass
    val backdrop = LocalGlassBackdrop.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberPress(pressed && enabled)
    val textColor = when {
        !enabled -> p.onGlassMuted.copy(alpha = 0.4f)
        primary -> if (p.accent.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else p.onAccent
        else -> p.onGlass
    }

    Box(
        modifier = modifier
            .height(48.dp)
            .drawBackdrop(
                backdrop = backdrop ?: NoBackdrop,
                shape = { GlassShape.chip },
                effects = {
                    if (backdrop != null) {
                        vibrancy()
                        blur(GlassTuning.smallBlur.toPx())
                        lens(
                            GlassTuning.smallLensHeight.toPx(),
                            GlassTuning.smallLensAmount.toPx(),
                        )
                    }
                },
                highlight = { glassHighlight(press.value, width = 0.6.dp) },
                shadow = { glassShadow(strength = 0.5f) },
                layerBlock = pressLayer(press),
                onDrawSurface = {
                    when {
                        !enabled -> drawRect(Color.White.copy(alpha = 0.05f))
                        primary -> drawRect(p.accent)
                        else -> drawRect(p.fillStrong)
                    }
                },
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = text,
                onClick = onClick,
            )
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = GlassType.label, color = textColor)
    }
}

/** 玻璃上的分隔线：1px，比描边淡一档。 */
@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(MaterialTheme.glass.border.copy(alpha = 0.5f)),
    )
}

/** 压暗层：全屏查看器、弹层背后。 */
@Composable
fun GlassScrim(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(MaterialTheme.glass.scrim))
}

/**
 * 玻璃面板里的文字统一用"玻璃上文字"色，避免每处手写 tint。
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
 * 返回 [Animatable]，在 **draw / layer 块里读 `value`** 而不是在组合期读：
 * 这样按压动画不会触发重组，只重画，12MP 拍照时主线程本来就紧。
 */
@Composable
private fun rememberPress(pressed: Boolean): Animatable<Float, *> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        progress.animateTo(
            targetValue = if (pressed) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 900f),
        )
    }
    return progress
}

/** `layerBlock`：面板胀大，库会反向变换背景图层，于是玻璃"鼓"而背景不动。 */
private fun pressLayer(press: Animatable<Float, *>): GraphicsLayerScope.() -> Unit = {
    val scale = 1f + GlassTuning.pressLift.toPx() / size.height * press.value
    scaleX = scale
    scaleY = scale
}

/**
 * 边缘高光：方向性（默认 45°）的白色描边，按下去更亮。
 *
 * 这条边才是"玻璃"的主视觉 —— 旧实现的毛病正是把它画成了一条不带方向的 1px 实线。
 */
private fun glassHighlight(press: Float, width: Dp): Highlight =
    Highlight.Default.copy(
        width = width,
        alpha = 0.55f + 0.45f * press,
    )

/** 外部阴影。strength 越小越"贴"，芯片要贴、面板要浮。 */
private fun glassShadow(strength: Float): Shadow =
    Shadow(
        radius = (18f * strength).dp,
        offset = DpOffset(0.dp, (6f * strength).dp),
        alpha = 0.5f * strength,
    )

/** 面板底色：影像上多一层深色蒙版，否则白字在亮部读不清。 */
private fun DrawScope.drawGlassSurface(p: GlassPalette, overPhoto: Boolean) {
    if (overPhoto) {
        drawRect(p.veil)
        drawRect(Color.White.copy(alpha = 0.08f))
    } else {
        drawRect(p.fill)
    }
}
