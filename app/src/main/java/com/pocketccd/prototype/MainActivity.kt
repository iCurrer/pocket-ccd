package com.pocketccd.prototype

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pocketccd.prototype.ui.PocketCcdRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // 全 App 只有一套浅色基底（暖白机身，见 ui/theme/Theme.kt），所以系统栏图标
            // 全程用**深色**，不再按目的地切换 —— 换页时也不会有一瞬间图标的颜色对不上。
            // 深色玻璃时代这里传的是 `dark()`（浅色图标）；换浅色主题时最容易漏的一处，
            // 漏了就是"暖白页面上顶着一排白图标"，等于状态栏瞎了。
            // scrim 取透明：取景页要的是"预览铺到状态栏底下"，其余页由自己的底色接管。
            statusBarStyle = SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(scrim = Color.TRANSPARENT, darkScrim = Color.TRANSPARENT),
        )
        setContent {
            // 主题在 PocketCcdRoute 内部套上：设置页要能在运行期切换"跟随壁纸取色"，
            // 主题若钉死在 Activity 这一层，开关就得重进 Activity 才生效。
            PocketCcdRoute()
        }
    }
}
