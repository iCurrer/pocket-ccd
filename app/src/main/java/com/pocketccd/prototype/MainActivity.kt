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
            // 四个页面都是近黑底（FilmDarkSpec.palette.appBg），系统栏图标固定浅色。
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
