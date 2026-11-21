package com.pocketccd.prototype.ui.artwork

import androidx.annotation.DrawableRes
import com.pocketccd.prototype.R
import com.pocketccd.prototype.fotufilm.FilmStockCatalog

/**
 * 胶卷 id → 包装图 drawable。
 *
 * **必须先过 [FilmStockCatalog.filmIdOf]**：`FilmStock.id` 是 pack 的完整文件名去掉
 * 扩展名，形如 `gold200__4000x3000`（尺寸后缀是为了同款卷能有多个分辨率变体）。
 * 包装图只跟"哪一支卷"有关，跟分辨率无关 —— 直接用 id 做相等判断会一条也匹配不上，
 * 界面会静默回落到占位暗盒（截图里看到过：胶卷库三个画格还是旧图标）。
 * 复用它而不是在这里再写一遍 `substringBefore("__")`，是为了 id 的口径只有一个源头。
 *
 * 用这张表而不是拼资源名：资源名拼接只能走反射（`getIdentifier`），R8 会把字符串
 * 连带留下、漏图也发现不了。显式表 + `else -> null`，缺图就是回落到占位暗盒，不会崩。
 *
 * **缺图是正常状态**：用户 `adb push` 一支新 pack 进 `files/film/`，这里自然没有它的图。
 * 加图 = 出一张 2:3 透明底 PNG，跑 `tools/make_film_artwork.py`，再在这张表里加一行。
 * 图出自 `docs/artwork/film/`，流程与重做方式见那里的 README。
 */
@DrawableRes
fun filmArtworkRes(stockId: String?): Int? = when (stockId?.let(FilmStockCatalog::filmIdOf)) {
    "gold200" -> R.drawable.film_gold200
    "portra400" -> R.drawable.film_portra400
    "ektar100" -> R.drawable.film_ektar100
    "trix400" -> R.drawable.film_trix400
    "acros100" -> R.drawable.film_acros100
    "provia100f" -> R.drawable.film_provia100f
    "velvia50" -> R.drawable.film_velvia50
    "cinestill800t" -> R.drawable.film_cinestill800t
    else -> null
}
