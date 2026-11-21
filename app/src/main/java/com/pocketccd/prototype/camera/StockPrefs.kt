package com.pocketccd.prototype.camera

import android.content.Context

/**
 * 上次装的是哪一支卷（D-089 追加）。
 *
 * ## 为什么值得落盘
 *
 * 卷模型在 D-070 之后就是"一个 stockId 过滤器"：换卷不重置任何计数。于是"上次拍的是哪支"
 * 完全是**用户偏好**，没有任何业务状态依附在它上面 —— 每次启动都回到第一支（扫描顺序里
 * 的 GOLD 200）只会让人每次开相机都要重选一遍，纯属摩擦。
 *
 * ## 只存用户明确装过的卷
 *
 * `selectStock` 是唯一写入口。相机绑定后若发现这支卷这台机器拍不了，会自动换一支
 * （[com.pocketccd.prototype.navigation.AppViewModel.onCameraBound]）——那次**不写**：
 * 它是设备能力兜底，不是用户的选择。真写下去，用户换台机器就再也找不回自己选的那支了。
 *
 * ## 和 `_selectedStockId` 的关系
 *
 * 存的是 id 字符串而不是下标：胶卷库的顺序来自扫目录，多一支少一支都会让下标错位，
 * 而 id 里已经带了 pack 尺寸（`gold200__4000x3000`），换机后对不上就自然失效、退回第一支。
 */
class StockPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** 上次装的卷 id；没选过 / 从没存过时返回 null（调用方退回"第一支可用的"）。 */
    fun loadSelectedStockId(): String? =
        prefs.getString(KEY_SELECTED_STOCK, null)?.takeIf { it.isNotBlank() }

    fun saveSelectedStockId(id: String) {
        prefs.edit().putString(KEY_SELECTED_STOCK, id).apply()
    }

    companion object {
        private const val FILE_NAME = "film_selection"
        private const val KEY_SELECTED_STOCK = "selected_stock_id"
    }
}
