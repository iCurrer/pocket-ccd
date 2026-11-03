package com.pocketccd.prototype.camera

import android.content.Context

/**
 * 片边 / 日期后背的开关（D-081）。
 *
 * 只存**开关**，不存每张照片的状态 —— 贴花是烘焙进像素的，拍完就定型了；
 * 再给每张照片记"这张有没有片边"只会多一份可能与像素不符的账。
 *
 * 读写都在主线程（一次点击），SharedPreferences 的 apply 是异步落盘，够用。
 */
class ImprintPrefs(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun load(): ImprintSettings = ImprintSettings(
        filmEdge = prefs.getBoolean(KEY_FILM_EDGE, false),
        dateBack = prefs.getBoolean(KEY_DATE_BACK, false),
        dateOrder = DateOrder.fromId(prefs.getString(KEY_DATE_ORDER, null)),
    )

    fun save(settings: ImprintSettings) {
        prefs.edit()
            .putBoolean(KEY_FILM_EDGE, settings.filmEdge)
            .putBoolean(KEY_DATE_BACK, settings.dateBack)
            .putString(KEY_DATE_ORDER, settings.dateOrder.id)
            .apply()
    }

    companion object {
        private const val FILE_NAME = "imprint"
        private const val KEY_FILM_EDGE = "film_edge"
        private const val KEY_DATE_BACK = "date_back"
        private const val KEY_DATE_ORDER = "date_order"
    }
}
