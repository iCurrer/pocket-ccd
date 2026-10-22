package com.pocketccd.prototype.camera

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 收藏（星标）的持久化。
 *
 * 原型里收藏只存在内存 Set 里，刷新即丢（见 `docs/interaction-spec.md` 的「收藏」一行）。
 * 这里落成 SharedPreferences：`favorites` + 一个 `Set<String>`，key 是照片 id
 * （`film_<毫秒>_<stockId>`，天然唯一且跨启动稳定）。不写数据库、不引 Room —— 收藏是
 * 一个 id 集合，为它开一张表属于杀鸡用牛刀。
 *
 * **Android 依赖只在这个类里**；判定逻辑 [toggleFavoriteId] 是顶层纯函数，JVM 单测
 * 可以直接调，不需要 Robolectric（项目没这个依赖，也不打算加）。
 *
 * 读取/写入都包了 `runCatching`：收藏是本地非关键状态，SharedPreferences 不可用时
 * 应当降级成"本次会话可见、下次启动丢失"，而不是让整个相册页崩掉（Compose 预览环境
 * 里没有真实 SharedPreferences，这个降级也顺手让预览能渲染）。写失败会记一条日志。
 */
class FavoritesStore(context: Context) {

    // 懒初始化：构造函数会在 Composable 的 remember 里跑，此时不该碰磁盘。
    private val prefs: SharedPreferences by lazy {
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    /** 当前全部收藏 id。返回副本，调用方随便改（SharedPreferences 自己返回的 Set 不允许原地改）。 */
    fun ids(): Set<String> = runCatching {
        prefs.getStringSet(KEY_IDS, emptySet())?.toSet().orEmpty()
    }.getOrDefault(emptySet())

    fun isFavorite(id: String): Boolean = id in ids()

    /**
     * 翻转一张照片的收藏状态，返回翻转后的完整集合。
     *
     * 返回集合而不是 Boolean：调用方（相册页）要把它直接塞进 Compose 状态，
     * 多读一次 `ids()` 既多一次磁盘读取，也可能读到写入的中间态。
     */
    fun toggle(id: String): Set<String> {
        val next = toggleFavoriteId(ids(), id)
        runCatching {
            // 用 apply：收藏不是"换卷"那种马上要读回的值，异步落盘即可。
            prefs.edit().putStringSet(KEY_IDS, next).apply()
        }.onFailure { error ->
            Log.w(TAG, "收藏写入失败：$id", error)
        }
        return next
    }

    private companion object {
        const val PREF_NAME = "favorites"
        const val KEY_IDS = "favorite_ids_v1"
        const val TAG = "PocketCcdFavorites"
    }
}

/**
 * 收藏集合的纯翻转逻辑（可 JVM 单测）。
 *
 * 空 id 直接原样返回：照片 id 为空说明是坏数据，把它写进收藏集合只会留下永远匹配不上、
 * 也永远删不掉的幽灵条目。
 */
fun toggleFavoriteId(current: Set<String>, id: String): Set<String> {
    if (id.isBlank()) return current
    return if (id in current) current - id else current + id
}
