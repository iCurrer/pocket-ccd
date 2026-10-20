package com.pocketccd.prototype.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Material 形状令牌。页面的圆角、间距、尺寸一律走
 * [com.pocketccd.prototype.ui.film.FilmMetrics]，这里是 M3 兜底。
 *
 * 这里原先还有一整套「红色塑料玩具机身」的设计令牌（`PocketCcdColors` 的
 * 薄荷底 / 奶油面板 / 珊瑚快门、`PocketCcdSizes` 的机身与镜头直径、
 * `PocketCcdMotion`）。M9 换成暗色胶片机身（D-039）后它们只服务已经删除的
 * `ui/stage` 与 `ui/components`，整块移除。
 */
object PocketCcdRadius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 20.dp
    val xl = 28.dp
}
