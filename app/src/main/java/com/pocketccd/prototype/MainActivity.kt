package com.pocketccd.prototype

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pocketccd.prototype.ui.PocketCcdRoute
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            // 冷启动固定落在取景页（黑闸门），所以初始系统栏图标用浅色；
            // 进入纸面页时由 AppRoot 按目的地切回深色图标（D-063）。
            // 与 themes.xml 的 windowBackground 同色，冷启动不会先闪一层别的底色。
            statusBarStyle = SystemBarStyle.dark(scrim = Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(scrim = Color.TRANSPARENT),
        )
        setContent {
            PocketCcdTheme {
                PocketCcdRoute()
            }
        }
    }
}
