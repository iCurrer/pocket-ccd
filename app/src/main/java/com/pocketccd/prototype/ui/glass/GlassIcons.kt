package com.pocketccd.prototype.ui.glass

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pocketccd.prototype.R

/**
 * 图标：**Material Symbols Rounded**（Google 官方，Apache-2.0）。
 *
 * ## 为什么不再手画
 *
 * 上一版是照着 24 格栅自己描的 18 个图标（齿轮 = 圆 + 6 条辐条、光圈 = 圆 + 3 条弦）。
 * 问题不在"能不能画出来"，而在**描得不像任何一套成熟图标**：齿高不一致、圆角半径各自
 * 为政、同一个视觉重量下笔画粗细不齐。放在玻璃上就是"AI 画出来的"那种脏。
 *
 * 现在每个图标都是 Material Symbols Rounded 的原始几何：
 *
 * - 24×24 视口、统一笔画、统一光学重心 —— 换尺寸只是整体缩放，不会"小的那个变粗"；
 * - 资源是 `res/drawable/ic_glass_*.xml`，由 [tools/gen_glass_icons.py] 从官方 SVG
 *   生成（脚本进仓库，图标不是手抄的，可复现、可替换）；
 * - **不引 `material-icons-extended`**：那套包几十 MB，而本工程 release 没开 R8，
 *   图标类会全量留下。这里只带用到的 21 个矢量，每个不到 1KB。
 *
 * [filled] 只对少数图标有意义（收藏、发现、拍摄），它们有 `_fill` 变体；
 * 其余图标忽略这个参数 —— 宁可不响应，也不拿一个描边图标冒充"已选中"。
 */
enum class GlassIconKind {
    Gallery,
    Film,
    Aperture,
    Sparkle,
    Gear,
    Flash,
    FlashOff,
    Grid,
    Close,
    Back,
    Chevron,
    Share,
    Heart,
    Refresh,
    Info,
    Warning,
    Check,
    Zoom,
    Exposure,
    Add,
    Remove,
}

@Composable
fun GlassIcon(
    kind: GlassIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    filled: Boolean = false,
) {
    Icon(
        painter = painterResource(if (filled) filledDrawableOf(kind) else drawableOf(kind)),
        // 图标本身是装饰：语义由外层按钮/文字提供，重复朗读只会更吵。
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/**
 * 每个 [GlassIconKind] 对应的矢量资源。
 *
 * 加新图标要做两件事：在这里加一条分支、把 XML 放进 `res/drawable`。
 * 漏掉分支编译不过（`when` 穷尽），漏掉 XML 才会崩 —— 所以
 * `GlassComponentContractTest` 盯着这张表，确保每个枚举都有落点。
 */
private fun drawableOf(kind: GlassIconKind): Int = when (kind) {
    GlassIconKind.Gallery -> R.drawable.ic_glass_gallery
    GlassIconKind.Film -> R.drawable.ic_glass_film
    GlassIconKind.Aperture -> R.drawable.ic_glass_aperture
    GlassIconKind.Sparkle -> R.drawable.ic_glass_sparkle
    GlassIconKind.Gear -> R.drawable.ic_glass_gear
    GlassIconKind.Flash -> R.drawable.ic_glass_flash
    GlassIconKind.FlashOff -> R.drawable.ic_glass_flash_off
    GlassIconKind.Grid -> R.drawable.ic_glass_grid
    GlassIconKind.Close -> R.drawable.ic_glass_close
    GlassIconKind.Back -> R.drawable.ic_glass_back
    GlassIconKind.Chevron -> R.drawable.ic_glass_chevron
    GlassIconKind.Share -> R.drawable.ic_glass_share
    GlassIconKind.Heart -> R.drawable.ic_glass_heart
    GlassIconKind.Refresh -> R.drawable.ic_glass_refresh
    GlassIconKind.Info -> R.drawable.ic_glass_info
    GlassIconKind.Warning -> R.drawable.ic_glass_warning
    GlassIconKind.Check -> R.drawable.ic_glass_check
    GlassIconKind.Zoom -> R.drawable.ic_glass_zoom
    GlassIconKind.Exposure -> R.drawable.ic_glass_exposure
    GlassIconKind.Add -> R.drawable.ic_glass_add
    GlassIconKind.Remove -> R.drawable.ic_glass_remove
}

/** 有实心变体的图标；没有的退回描边版。 */
private fun filledDrawableOf(kind: GlassIconKind): Int = when (kind) {
    GlassIconKind.Heart -> R.drawable.ic_glass_heart_fill
    GlassIconKind.Sparkle -> R.drawable.ic_glass_sparkle_fill
    GlassIconKind.Aperture -> R.drawable.ic_glass_aperture_fill
    else -> drawableOf(kind)
}
