package com.pocketccd.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pocketccd.prototype.ui.PocketCcdRoute
import com.pocketccd.prototype.ui.theme.PocketCcdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PocketCcdTheme {
                PocketCcdRoute()
            }
        }
    }
}
