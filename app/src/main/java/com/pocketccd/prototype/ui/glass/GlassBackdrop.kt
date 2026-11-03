package com.pocketccd.prototype.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.pocketccd.prototype.ui.theme.glass

/**
 * 当前子树可以采样的"真实背景"（[com.kyant.backdrop.Backdrop]）。
 *
 * 这是整个玻璃体系的前提：Kyant 的 Backdrop 库不是"自己画一层半透明白"，而是**把
 * 元素背后的内容先录进一张 GraphicsLayer，再对这张图层做模糊/折射/边缘高光**。
 * 所以"能不能做出真玻璃"取决于一个问题：**这块玻璃背后有没有内容可录**。
 *
 * - `null`：背后没有可采样内容（取景页的相机预览是独立 Surface，不经过 Compose
 *   渲染管线，录不到像素）。此时组件退化成"着色 + 边缘高光"的静态玻璃，
 *   **不会**伪造一层假模糊。
 * - 非 null：由 [GlassBackdrop] 提供，页面内容被录成图层，浮在内容之上的玻璃
 *   （TabBar）真正会把它糊掉。
 *
 * 用 `staticCompositionLocalOf`：这个值一次组合内不变，换了就整棵子树重建，
 * 不需要逐读跟踪。
 */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * 纸面层的容器：底色 + 极光 + **被录制的页面内容** + 浮在最上面的玻璃层。
 *
 * ## 为什么要分成 [content] 和 [overlay] 两个槽
 *
 * [content] 会被 `Modifier.layerBackdrop` 录进一张图层，[overlay] 里的玻璃再采样它。
 * 两者必须分开，否则录图层的过程中会有玻璃去采**正在录制的自己** —— 那是未定义行为
 * （轻则一帧空白，重则自引用）。所以：
 *
 * - 页面里的卡片、列表、图片 → [content]，它们能在 TabBar 底下被糊掉；
 * - 只有 TabBar 这类"浮在所有内容之上"的控件 → [overlay]。
 *
 * 页面内部的玻璃（卡片、芯片）拿不到这层 backdrop（[GlassSurface] 会把它清成 null），
 * 因为它们在 [content] 里面，采自己就是递归。它们走"静态玻璃"分支。
 *
 * [content] 刻意放在最后一个参数：调用点写起来还是 `GlassBackdrop(...) { 页面 }`，
 * 只有需要浮层的页面才额外传 `overlay`。
 */
@Composable
fun GlassBackdrop(
    modifier: Modifier = Modifier,
    /**
     * 是否把 [content] 录成图层。
     *
     * 没有浮层要采样时（详情页没有 TabBar）就不录 —— 录制是一遍全屏离屏渲染，
     * 白付这份带宽没有意义。关掉以后 [LocalGlassBackdrop] 也是 null，
     * 页面里的玻璃统一走静态分支。
     */
    captureContent: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val p = MaterialTheme.glass
    val backdrop = rememberLayerBackdrop()

    Box(modifier.fillMaxSize().background(p.background)) {
        GlassAurora(Modifier.fillMaxSize())

        // 录图层：这一层的每一帧绘制结果都会被 overlay 里的玻璃采样。
        Box(
            Modifier
                .fillMaxSize()
                .then(if (captureContent) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            content()
        }

        if (captureContent) {
            CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
                Box(Modifier.fillMaxSize()) { overlay() }
            }
        } else {
            Box(Modifier.fillMaxSize()) { overlay() }
        }
    }
}

/**
 * 页面极光：两颗低透明度径向光斑。
 *
 * 位置与半径**按实际尺寸算**（`size.width * 0.12f` 这种），不写死像素 ——
 * 旧实现把中心写成 `Offset(1080f, 2100f)`、半径写成 `900f`，在任何非 1080×2400 的
 * 屏幕上都会偏出画面或糊成一片。
 *
 * 光斑只需要"有颜色倾向"，不需要真的被模糊：颜色本身已经是低 alpha，边界看不出来。
 */
@Composable
private fun GlassAurora(modifier: Modifier = Modifier) {
    val p = MaterialTheme.glass
    Box(
        modifier.drawBehind {
            drawRect(
                Brush.radialGradient(
                    colors = listOf(p.glowA.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.10f, -size.height * 0.05f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(p.glowB.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(size.width * 1.02f, size.height * 1.04f),
                    radius = size.maxDimension * 0.80f,
                ),
            )
        },
    )
}
