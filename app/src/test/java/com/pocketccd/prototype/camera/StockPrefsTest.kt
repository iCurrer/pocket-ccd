package com.pocketccd.prototype.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 胶卷选择的落盘（D-089 追加）。
 *
 * 这几行代码本身没什么可测的，值得测的是**口径**：`load` 必须把空串当成"没选过"，
 * 否则一次误写就会让界面显示一支不存在的卷（然后被自动换卷盖掉，用户以为记忆坏了）。
 * 键名写错也一样 —— 存进去读不出来，表现是"记忆功能没生效"，而编译期完全看不出来。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class StockPrefsTest {

    @Test
    fun `存进去的卷 id 能原样读回来`() {
        val context = RuntimeEnvironment.getApplication()
        StockPrefs(context).saveSelectedStockId("gold200__4000x3000")

        assertEquals("gold200__4000x3000", StockPrefs(context).loadSelectedStockId())
    }

    @Test
    fun `空串等于没选过`() {
        val context = RuntimeEnvironment.getApplication()
        StockPrefs(context).saveSelectedStockId("")

        assertNull(StockPrefs(context).loadSelectedStockId())
    }
}
