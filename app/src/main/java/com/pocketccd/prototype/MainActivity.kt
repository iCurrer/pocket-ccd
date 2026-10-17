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
            // App launches on the red camera home; AppRoot switches icon
            // appearance to dark on cream/mint pages.
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
