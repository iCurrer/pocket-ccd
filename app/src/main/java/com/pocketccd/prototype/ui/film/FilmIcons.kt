package com.pocketccd.prototype.ui.film

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hand-drawn icons instead of `material-icons-extended`.
 *
 * The extended pack is ~2.5 MB and its filled style does not match the thin
 * line icons in the design. Nine paths are cheaper than one dependency, and
 * D-024 requires an explicit review before adding any dependency.
 *
 * All geometry is authored on a 24x24 viewport and scaled to the requested size,
 * so the same icon works at 15dp (top bar) and 22dp (thumbnail).
 */
enum class FilmIconKind {
    Flash,
    FlashOff,
    Grid,
    Tune,
    Filmstrip,
    ImageMultiple,
    Close,
    Check,
    ChevronRight,

    /** 相机机身 + 镜头，用于「继续拍摄」。原型取自 mdi:camera-outline。 */
    Camera,

    /** 返回箭头（D-063 1:1 版面）。 */
    ArrowLeft,

    /** 更多（三点）。 */
    More,

    /** 收藏（空心心形）。 */
    Heart,

    /** 分享（托盘 + 上箭头）。 */
    Share,

    /** 保存（托盘 + 下箭头）。 */
    Save,

    /** 发现（罗盘）。 */
    Compass,
}

/**
 * 图标的默认语义文案（TalkBack 读的那一句）。
 *
 * 为什么映射放在这里而不是调用点：这些图标全部是"纯图标可点控件"（[FilmRoundButton]），
 * 调用点（取景页 / 相册页 / 成片页 / 设置页）只传 `icon` 和开关状态，改一处就能让
 * 所有页面同时获得描述，也不会出现"取景页写了、设置页忘了"的半覆盖。
 *
 * `active` 参与文案：闪光灯/九宫格是开合型开关，按钮上没有任何文字，只有底色变化，
 * 读屏如果只说"闪光灯"用户无法知道当前状态；带上"已开/已关"才是完整的语义。
 * 非开合型图标忽略 `active`。
 *
 * 这是**无障碍文案**，不是界面文案 —— 不要拿它去做可见标签（可见标签有字号/对比度要求）。
 */
fun FilmIconKind.accessibilityLabel(active: Boolean): String = when (this) {
    FilmIconKind.Flash -> if (active) "闪光灯已开" else "闪光灯已关"
    // FlashOff 是"已关"的图标；active 仍参与，防止哪天调用方传了 FlashOff 却想要开态文案。
    FilmIconKind.FlashOff -> if (active) "闪光灯已开" else "闪光灯已关"
    FilmIconKind.Grid -> if (active) "九宫格参考线已开" else "九宫格参考线已关"
    FilmIconKind.Tune -> "设置"
    FilmIconKind.Filmstrip -> "胶卷库"
    FilmIconKind.ImageMultiple -> "相册"
    FilmIconKind.Close -> "关闭"
    FilmIconKind.Check -> "确认"
    FilmIconKind.ChevronRight -> "进入"
    FilmIconKind.Camera -> "继续拍摄"
    FilmIconKind.ArrowLeft -> "返回"
    FilmIconKind.More -> "更多"
    FilmIconKind.Heart -> if (active) "已收藏" else "收藏"
    FilmIconKind.Share -> "分享"
    FilmIconKind.Save -> "保存"
    FilmIconKind.Compass -> "发现"
}

/**
 * 图标的默认动作提示（TalkBack 在"双击即可…"后面补的那半句）。
 *
 * 开关类用"切换…"，导航类用"打开…"，关闭键就是"关闭"。写成映射而不是统一
 * "点击"：读屏用户靠这半句判断按下去会发生什么，泛泛的"点击"等于没说。
 */
fun FilmIconKind.actionLabel(): String = when (this) {
    FilmIconKind.Flash -> "切换闪光灯"
    FilmIconKind.FlashOff -> "切换闪光灯"
    FilmIconKind.Grid -> "切换九宫格参考线"
    FilmIconKind.Tune -> "打开设置"
    FilmIconKind.Filmstrip -> "打开胶卷库"
    FilmIconKind.ImageMultiple -> "打开相册"
    FilmIconKind.Close -> "关闭"
    FilmIconKind.Check -> "确认"
    FilmIconKind.ChevronRight -> "进入"
    FilmIconKind.Camera -> "继续拍摄"
    FilmIconKind.ArrowLeft -> "返回上一页"
    FilmIconKind.More -> "更多操作"
    FilmIconKind.Heart -> "切换收藏"
    FilmIconKind.Share -> "分享照片"
    FilmIconKind.Save -> "保存照片"
    FilmIconKind.Compass -> "打开发现"
}

/**
 * 纯绘制图标。**没有自己的语义**：它总是被 [FilmRoundButton] / [FilmPanelKey] 这类
 * 有语义的宿主包住，宿主负责 `contentDescription`。给 Canvas 单独加语义会让
 * TalkBack 把同一句话读两遍（宿主一次、图标一次）。
 */
@Composable
fun FilmIcon(
    kind: FilmIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 17.dp,
    strokeWidth: Dp = 1.6.dp,
) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension / 24f
        val w = strokeWidth.toPx()
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        when (kind) {
            FilmIconKind.Flash -> {
                val path = Path().apply {
                    moveTo(13f * s, 2f * s)
                    lineTo(4f * s, 14f * s)
                    lineTo(10f * s, 14f * s)
                    lineTo(9f * s, 22f * s)
                    lineTo(20f * s, 10f * s)
                    lineTo(13.5f * s, 10f * s)
                    lineTo(14.5f * s, 2f * s)
                    close()
                }
                drawPath(path, tint)
            }

            FilmIconKind.FlashOff -> {
                // No slash. At 17dp the slash and the bolt's waist occupy the same
                // few pixels and the pair reads as an asterisk (device v4). The
                // button's fill already says on/off — accent when on, dark when
                // off — so the icon only has to stay legible: same bolt, dimmed.
                val path = Path().apply {
                    moveTo(13f * s, 2f * s)
                    lineTo(4f * s, 14f * s)
                    lineTo(10f * s, 14f * s)
                    lineTo(9f * s, 22f * s)
                    lineTo(20f * s, 10f * s)
                    lineTo(13.5f * s, 10f * s)
                    lineTo(14.5f * s, 2f * s)
                    close()
                }
                drawPath(path, tint.copy(alpha = 0.45f))
            }

            FilmIconKind.Grid -> {
                drawLine(tint, o(4f, 4f), o(20f, 4f), w)
                drawLine(tint, o(20f, 4f), o(20f, 20f), w)
                drawLine(tint, o(20f, 20f), o(4f, 20f), w)
                drawLine(tint, o(4f, 20f), o(4f, 4f), w)
                drawLine(tint, o(12f, 4f), o(12f, 20f), w * 0.8f)
                drawLine(tint, o(4f, 12f), o(20f, 12f), w * 0.8f)
            }

            FilmIconKind.Tune -> {
                drawLine(tint, o(3f, 7f), o(21f, 7f), w)
                drawLine(tint, o(3f, 12f), o(21f, 12f), w)
                drawLine(tint, o(3f, 17f), o(21f, 17f), w)
                drawCircle(tint, radius = 2.1f * s, center = o(8f, 7f))
                drawCircle(tint, radius = 2.1f * s, center = o(15f, 12f))
                drawCircle(tint, radius = 2.1f * s, center = o(10f, 17f))
            }

            FilmIconKind.Filmstrip -> {
                // Frame + two rows of sprocket squares. Two vertical rails read as
                // a "pause" glyph at 22dp, so the holes have to be blocks, not lines.
                drawRect(
                    color = tint,
                    topLeft = o(4f, 5f),
                    size = Size(16f * s, 14f * s),
                    style = Stroke(width = w),
                )
                for (i in 0..2) {
                    val x = 6.4f + i * 4.6f
                    drawRect(tint, o(x, 6.3f), Size(2.2f * s, 1.7f * s))
                    drawRect(tint, o(x, 16.0f), Size(2.2f * s, 1.7f * s))
                }
            }

            FilmIconKind.ImageMultiple -> {
                // Two offset frames only. An inner landscape silhouette was tried
                // first and read as a gear at 22dp — silhouette over detail.
                drawRect(
                    color = tint,
                    topLeft = o(8f, 4f),
                    size = Size(12f * s, 12f * s),
                    style = Stroke(width = w),
                )
                drawRect(
                    color = tint,
                    topLeft = o(4f, 8f),
                    size = Size(12f * s, 12f * s),
                    style = Stroke(width = w),
                )
            }

            FilmIconKind.Close -> {
                drawLine(tint, o(6f, 6f), o(18f, 18f), w)
                drawLine(tint, o(18f, 6f), o(6f, 18f), w)
            }

            FilmIconKind.Check -> {
                val path = Path().apply {
                    moveTo(5f * s, 12.5f * s)
                    lineTo(10f * s, 17.5f * s)
                    lineTo(19f * s, 6.5f * s)
                }
                drawPath(path, tint, style = Stroke(width = w * 1.2f))
            }

            FilmIconKind.ChevronRight -> {
                val path = Path().apply {
                    moveTo(9f * s, 5f * s)
                    lineTo(16f * s, 12f * s)
                    lineTo(9f * s, 19f * s)
                }
                drawPath(path, tint, style = Stroke(width = w))
            }

            FilmIconKind.Camera -> {
                // 机身描边 + 顶部取景凸起 + 镜头圆。凸起画成折线而不是矩形，
                // 因为 14dp 下两条横线挨在一起会被抗锯齿抹成一根粗线。
                drawRoundRect(
                    color = tint,
                    topLeft = o(3f, 7f),
                    size = Size(18f * s, 13f * s),
                    cornerRadius = CornerRadius(2.4f * s, 2.4f * s),
                    style = Stroke(width = w),
                )
                val bump = Path().apply {
                    moveTo(8.6f * s, 7f * s)
                    lineTo(9.5f * s, 4.4f * s)
                    lineTo(14.5f * s, 4.4f * s)
                    lineTo(15.4f * s, 7f * s)
                }
                drawPath(bump, tint, style = Stroke(width = w))
                drawCircle(
                    color = tint,
                    radius = 3.5f * s,
                    center = o(12f, 13.5f),
                    style = Stroke(width = w),
                )
            }

            FilmIconKind.ArrowLeft -> {
                drawLine(tint, o(19f, 12f), o(6f, 12f), w)
                drawLine(tint, o(6f, 12f), o(11f, 7f), w)
                drawLine(tint, o(6f, 12f), o(11f, 17f), w)
            }

            FilmIconKind.More -> {
                drawCircle(tint, radius = 1.8f * s, center = o(6f, 12f))
                drawCircle(tint, radius = 1.8f * s, center = o(12f, 12f))
                drawCircle(tint, radius = 1.8f * s, center = o(18f, 12f))
            }

            FilmIconKind.Heart -> {
                val path = Path().apply {
                    moveTo(12f * s, 20f * s)
                    lineTo(4.6f * s, 12.2f * s)
                    cubicTo(2.2f * s, 9.6f * s, 3.4f * s, 5.2f * s, 7.2f * s, 5.2f * s)
                    cubicTo(9.4f * s, 5.2f * s, 11.1f * s, 6.5f * s, 12f * s, 8.1f * s)
                    cubicTo(12.9f * s, 6.5f * s, 14.6f * s, 5.2f * s, 16.8f * s, 5.2f * s)
                    cubicTo(20.6f * s, 5.2f * s, 21.8f * s, 9.6f * s, 19.4f * s, 12.2f * s)
                    close()
                }
                drawPath(path, tint, style = Stroke(width = w))
            }

            FilmIconKind.Share -> {
                drawLine(tint, o(12f, 3.5f), o(12f, 14.5f), w)
                drawLine(tint, o(12f, 3.5f), o(8f, 7.5f), w)
                drawLine(tint, o(12f, 3.5f), o(16f, 7.5f), w)
                drawLine(tint, o(5f, 12f), o(5f, 20.5f), w)
                drawLine(tint, o(5f, 20.5f), o(19f, 20.5f), w)
                drawLine(tint, o(19f, 20.5f), o(19f, 12f), w)
            }

            FilmIconKind.Save -> {
                drawLine(tint, o(12f, 3.5f), o(12f, 14.5f), w)
                drawLine(tint, o(12f, 14.5f), o(8f, 10.5f), w)
                drawLine(tint, o(12f, 14.5f), o(16f, 10.5f), w)
                drawLine(tint, o(5f, 17.5f), o(5f, 20.5f), w)
                drawLine(tint, o(5f, 20.5f), o(19f, 20.5f), w)
                drawLine(tint, o(19f, 20.5f), o(19f, 17.5f), w)
            }

            FilmIconKind.Compass -> {
                // 四角罗盘星。旧版是圆 + 斜线在 20dp 下读成“禁止”图标
                // （真机 v0.6.1 反馈），改用整块实心星，远看也不会变成一个斜杠。
                val star = Path().apply {
                    moveTo(12f * s, 3f * s)
                    lineTo(14.1f * s, 9.9f * s)
                    lineTo(21f * s, 12f * s)
                    lineTo(14.1f * s, 14.1f * s)
                    lineTo(12f * s, 21f * s)
                    lineTo(9.9f * s, 14.1f * s)
                    lineTo(3f * s, 12f * s)
                    lineTo(9.9f * s, 9.9f * s)
                    close()
                }
                drawPath(star, tint)
            }
        }
    }
}
