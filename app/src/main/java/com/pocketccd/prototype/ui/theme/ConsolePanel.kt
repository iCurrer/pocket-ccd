package com.pocketccd.prototype.ui.theme

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * **浅色材质的公共底子（D-090 / D-094）。**
 *
 * 这套材质原来只长在拍摄页里（`CameraConsole.kt` 的私有 `consolePanel`）。既然整个 App
 * 都要从"深色玻璃"换成"暖白机身 + 黄铜标记"，那它就不能再是某个页面的私有实现 ——
 * 相机页和相册页必须用**同一个函数**画面板，否则两边会各自漂移出 1dp 的圆角差、
 * 2% 的浓度差，远看统一、近看两套。
 *
 * ## D-094：从"贴纸"到"零件"
 *
 * 一版只有三件事：阴影、顶边受光、1dp 描边。结果所有面板亮得一样、边得一样 —— 卡片
 * 看起来是**印**在机身上的，不是**装**在机身上的。缺的是"光有方向"这件事：
 *
 * 1. **表面曲率**：上表面受光、下沿渐暗（竖向渐变）。一块平板被这层渐变一压就有了弧度。
 * 2. **双向倒角**：左上一圈亮线（受光面）、右下一圈暗线（背光面）。这是"倒角"的几何
 *    本质 —— 单圈描边只是边界，双圈明暗才是厚度。做法是一条**沿对角渐变**的描边：
 *    描边宽度是关键，渐变在路径上取样，左上取得白、右下取得黑，一圈就自带明暗。
 * 3. **接触阴影**：面板与机身之间那道极窄的暗缝，只有 1dp，但少了它面板像浮在空中。
 *
 * 三件事都是纯绘制，没有离屏图层、没有模糊 —— 列表里几十张卡也不会掉帧。真需要
 * 大半径模糊的地方（快门、抽屉）另有 `contactShadow`/`soft` 参数走系统 shadow。
 */
fun Modifier.consolePanel(
    color: Color,
    shape: Shape,
    elevation: Dp,
    borderColor: Color = ConsoleColor.bevelEdge,
    topLight: Boolean = true,
    /** 接触阴影：嵌进机身的深色件（取景窗）用 30% 的浓影，否则压不住暖白。 */
    contactShadow: Boolean = false,
    /** 倒角强度 0..1。小件（芯片、键帽）用满，大板（整张卡片）减半，否则像塑料壳。 */
    bevel: Float = 1f,
): Modifier {
    // 半透明填充（选中态的淡金底）不叠曲率：渐变会把那层薄色压成脏灰。
    val opaque = color.alpha > 0.95f
    return this
        .shadow(
            elevation = elevation,
            shape = shape,
            clip = false,
            ambientColor = if (contactShadow) ConsoleColor.shadowContact else ConsoleColor.shadow,
            spotColor = if (contactShadow) ConsoleColor.shadowContact else ConsoleColor.shadow,
        )
        .clip(shape)
        .background(color)
        .drawBehind {
            if (opaque) drawSurfaceCurve()
            if (bevel > 0.01f) drawBevel(shape, strength = bevel)
        }
        .border(0.75.dp, borderColor, shape)
}

/**
 * 凹槽：面板上**挖出来的一格**（切换胶卷条、状态条里的读数窗、邀请码输入框）。
 *
 * 与 [consolePanel] 正好是反的：上沿暗、下沿亮、内圈一圈阴影。用错方向的凹陷比不凹更糟 ——
 * 凸起的槽会被读成"又贴了一张小卡"。
 *
 * @param color 槽底颜色。**必须比所在面板暗一档**，否则凹不进去。
 * @param depth 内阴影扩散半径。8dp 高的细槽用 3dp，整块输入区用 5dp。
 */
fun Modifier.consoleRecess(
    shape: Shape,
    color: Color = ConsoleColor.panel,
    depth: Dp = 3.dp,
    shade: Float = 0.34f,
    /** 下沿反光强度。槽底是浅色时反光让它读成"浅槽"而不是"黑洞"。 */
    rimLight: Float = 0.5f,
): Modifier = this
    .clip(shape)
    .background(color)
    .drawBehind {
        // 内圈暗带：沿轮廓画模糊描边，外侧被 clip 裁掉，剩下的就是由边向心的衰减。
        drawInsetShade(
            shape = shape,
            color = ConsoleColor.shadowContact,
            alpha = shade,
            blur = depth,
        )
        // 上沿最暗：光从上面来，槽口的上半是被遮住的那一半。
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.13f),
                0.62f to Color.Transparent,
            ),
        )
        // 下沿反光：槽底把光反弹回来，贴着下边缘一条**细**亮线。
        // 区间不能给宽：0.80–1.0 的渐变在 40dp 的格子上是 9dp 的亮带，
        // 读起来像"键帽顶面的高光"，整格就翻成凸的了。0.94 起才是"一线"。
        if (rimLight > 0.01f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0.94f to Color.Transparent,
                    1f to Color.White.copy(alpha = 0.50f * rimLight),
                ),
            )
        }
    }

/**
 * 按下态的内阴影：一块被压进去的浅色面板。
 *
 * 这是从新拟态里唯一值得借的一招 —— 按下时"陷下去"比"缩一下"更像实体键。做法是
 * 沿形状边缘画一圈**模糊描边**：描边以轮廓线为中心，外侧那一半被父级的 `clip` 裁掉，
 * 留在里面的就是一个由边缘向中心衰减的暗带 —— 这就是内阴影的几何本质。
 *
 * 上方再压一道竖向渐变：光从上面来，凹坑的**上沿**最暗。少了这一道，内环四周一样黑，
 * 看起来像"描了条黑边"而不是"陷进去了"。
 *
 * @param pressed 0..1 的按压进度。**传 lambda 而不是 Float**：值在 draw 阶段读，
 *   按压动画只触发重绘，不触发重组。
 */
fun Modifier.consolePress(
    shape: Shape,
    pressed: () -> Float,
    blur: Dp = 5.dp,
    strength: Float = 0.42f,
): Modifier = this.drawBehind {
    val amount = pressed()
    if (amount <= 0.004f) return@drawBehind

    drawInsetShade(
        shape = shape,
        color = ConsoleColor.shadowContact,
        alpha = strength * amount,
        blur = blur,
    )
    // 上沿最暗：凹坑的受光面在下，暗面在上。
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.10f * amount),
            0.55f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        ),
    )
}

/**
 * 表面曲率：顶部受光、底部微暗。
 *
 * 只在**不透明**面板上画。半透明填充（淡金选中底）叠这层会变成脏灰。
 */
internal fun DrawScope.drawSurfaceCurve(
    topAlpha: Float = 0.28f,
    bottomAlpha: Float = 0.042f,
) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = topAlpha),
            0.42f to Color.Transparent,
            1f to Color.Black.copy(alpha = bottomAlpha),
        ),
    )
    // 顶沿一道极窄的实光：不是渐变能替代的，它只有 1.5dp，读起来是"倒角的抛光边"。
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.62f),
                Color.White.copy(alpha = 0.12f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = 2.2.dp.toPx(),
        ),
    )
}

/**
 * 双向倒角：沿轮廓一圈，左上白、右下黑。
 *
 * 用**对角线性渐变的描边**实现：渐变在路径像素位置取样，所以同一条描边左上是高光、
 * 右下是暗线，中间自动过渡 —— 不需要分段、不需要两层描边、不需要知道圆角半径。
 *
 * 描边宽度是 3dp 而不是 1.5dp：外侧一半被 `clip(shape)` 裁掉，视觉上才是 1.5dp 的
 * 窄倒角；宽度不够会读成"描了个边"，而不是"磨了个角"。
 */
internal fun DrawScope.drawBevel(
    shape: Shape,
    strength: Float = 1f,
    width: Dp = 3.dp,
) {
    val path = Path()
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                // 亮侧收敛到 0.60：0.72 会把卡片的左上角烧成一道白钩。
                0f to Color.White.copy(alpha = 0.60f * strength),
                0.30f to Color.White.copy(alpha = 0.16f * strength),
                0.54f to Color.Transparent,
                // 暗侧刻意比亮侧弱一档：暗边会和 0.75dp 的外描边叠加成"双重黑线"，
                // 而浅色机身最怕的就是边缘发脏。
                0.80f to Color.Black.copy(alpha = 0.065f * strength),
                1f to Color.Black.copy(alpha = 0.105f * strength),
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        ),
        style = Stroke(width = width.toPx()),
    )
}

/**
 * 沿轮廓画一圈模糊描边，留下的暗带就是内阴影。
 *
 * 形状走 `createOutline` 的三个分支，不用 `Path.addOutline`（那是 Compose 1.5+ 的扩展，
 * 位置随版本变）。这里只需要圆角矩形、圆、胶囊三类，手写就够。
 */
internal fun DrawScope.drawInsetShade(
    shape: Shape,
    color: Color,
    alpha: Float,
    blur: Dp,
) {
    if (alpha <= 0f) return
    val blurPx = blur.toPx()
    if (blurPx <= 0f) return

    val path = Path()
    when (val outline = shape.createOutline(size, layoutDirection, this)) {
        is Outline.Rectangle -> path.addRect(outline.rect)
        is Outline.Rounded -> path.addRoundRect(outline.roundRect)
        is Outline.Generic -> path.addPath(outline.path)
    }

    drawIntoCanvas { canvas ->
        // 直接建**原生** Paint：Compose 的 Paint 上没有 maskFilter，旧写法
        // `paint.asFrameworkPaint()` 从 ui-graphics 1.9 起标废弃（"改用平台扩展"）。
        // 模糊本来就只有原生层有，那就整条路都走原生，不经过 Compose 的 Paint。
        val native = Paint().apply {
            this.color = color.copy(alpha = color.alpha * alpha).toArgb()
            style = Paint.Style.STROKE
            strokeWidth = blurPx * 2f
            strokeCap = Paint.Cap.ROUND
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
            isAntiAlias = true
        }
        canvas.nativeCanvas.drawPath(path.asAndroidPath(), native)
    }
}

/**
 * **金属盘**：同心车纹 + 中心受光 + 边缘压暗。
 *
 * 快门、银盘键用它。三层的顺序很要紧 —— 车纹必须在底色之上、压暗之下，否则纹路
 * 会被压暗层吃掉；中心光斑又在最上，它是"球面最高点"。
 *
 * @param sheen 中心高光强度。大银盘 0.28，小键帽 0.16；超过 0.4 就成了塑料球。
 */
internal fun DrawScope.drawMachinedDisc(
    radius: Float,
    sheen: Float = 0.26f,
    rings: Int = 44,
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    // 同心车纹：极低对比的一圈圈，密而不匀才像车出来的（等距会像靶子）。
    // 密度要高、对比要低 —— 30 圈 5% 会是"涟漪"，44 圈 3.2% 才是"拉丝"。
    repeat(rings) { i ->
        val t = (i + 1f) / rings
        drawCircle(
            color = if (i % 2 == 0) {
                Color.White.copy(alpha = 0.042f * (1f - t * 0.5f))
            } else {
                Color.Black.copy(alpha = 0.026f * (1f - t * 0.4f))
            },
            radius = radius * t,
            center = center,
            style = Stroke(width = radius * 0.008f + 0.5f),
        )
    }
    // 中心**不画**径向光斑：盘心一旦有个亮圆，整盘立刻读成一颗塑料球。
    // 金属的亮是成带的、偏一侧的，由下面那条斜向柔光带负责。
    // 边缘压暗：金属盘的外沿永远比盘心暗，这一圈是"弧度"的证据。
    // 起点放到 0.70 —— 太早开始压暗会让整盘变成一个球。
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                0.70f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.26f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    // 一道斜向柔光带（左上到右下）：**这是盘面唯一的高光**，也是它读成"平面金属"
    // 而不是"球"的关键 —— 球的高光是一个点，平面金属的高光是一条带。
    rotate(degrees = -34f, pivot = center) {
        drawOval(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.40f to Color.White.copy(alpha = sheen * 1.15f),
                    0.62f to Color.White.copy(alpha = sheen * 0.55f),
                    1f to Color.Transparent,
                ),
                startY = center.y - radius * 0.62f,
                endY = center.y + radius * 0.62f,
            ),
            topLeft = Offset(center.x - radius, center.y - radius * 0.62f),
            size = Size(radius * 2f, radius * 1.24f),
        )
    }
}

/** 面板浮起高度。**同一层级的控件必须用同一个值**，否则一排卡片会高低不齐。 */
object ConsoleElevation {
    /** 压在内容上的浮层：底部 dock、抽屉。 */
    val dock = 14.dp

    /** 页面里的卡片。 */
    val card = 8.dp

    /** 芯片、圆形键。 */
    val key = 5.dp

    /** 实体键（快门、主按钮）：投影最重，因为它离机身最远。 */
    val hero = 12.dp
}
