package com.pocketccd.prototype.camera

import java.util.Calendar

/**
 * 日期后背的设置与文本生成。
 *
 * 这个文件**故意不碰 android.graphics**：日期格式化与校验是纯计算，放进 JVM 单测
 * 里跑才有意义（闰年、越界、空值都要能测）。绘制在 [ImprintRenderer] 里。
 */

/** `'26 11 02` 还是 `11 02 '26`。和上游 SpektraLab 的 `captureDate()` 一致。 */
enum class DateOrder(internal val id: String) {
    /** 年-月-日：`'26 11 2`（上游的默认顺序）。 */
    YearMonthDay("ymd"),

    /** 月-日-年：`11 2 '26`。 */
    MonthDayYear("mdy");

    companion object {
        fun fromId(id: String?): DateOrder =
            entries.firstOrNull { it.id == id } ?: YearMonthDay
    }
}

/**
 * 一次拍摄要烧进像素的东西。**快门按下那一刻快照**，冲洗中途改设置不影响在途的这张。
 *
 * @param filmEdge 合成到 135 半格胶片条上
 * @param dateBack 把日期盖在画面右下角
 */
data class ImprintSettings(
    val filmEdge: Boolean = false,
    val dateBack: Boolean = false,
    val dateOrder: DateOrder = DateOrder.YearMonthDay,
) {
    val any: Boolean get() = filmEdge || dateBack

    /** 相机页那行状态字：`135 半格 · 日期`。两个都没开时为 null。 */
    fun label(): String? {
        if (!any) return null
        val parts = buildList {
            if (filmEdge) add("135 半格")
            if (dateBack) add("日期")
        }
        return parts.joinToString(" · ")
    }
}

/**
 * 拍摄时间 → 后背字样。
 *
 * 规则照抄上游 `captureDate()`：**年份补两位、月日不补零**，年份前缀一个单引号
 * （真实日期后背就是这么印的）。上游还会校验年份 > 1800、月份 1–12、按闰年算当月
 * 天数 —— 我们的输入是自家 `capturedAt`，只会是合法值，但校验仍然留着：
 * 时钟被拨到 1970/1971 时不至于印出 `'70 …` 这种明显的假日期。
 */
object DateStamp {

    /** 低于这个时间说明设备时钟没同步过，宁可不印。 */
    private const val MIN_VALID_MILLIS = 946_684_800_000L // 2000-01-01

    fun text(capturedAt: Long, order: DateOrder): String? {
        if (capturedAt < MIN_VALID_MILLIS) return null
        val calendar = Calendar.getInstance().apply { timeInMillis = capturedAt }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        if (year <= 1800 || month !in 1..12 || day !in 1..31) return null
        val yy = year % 100
        return when (order) {
            DateOrder.YearMonthDay -> "'%02d %d %d".format(yy, month, day)
            DateOrder.MonthDayYear -> "%d %d '%02d".format(month, day, yy)
        }
    }
}
