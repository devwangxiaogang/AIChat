package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.myapplication.ui.AiApp
import com.example.myapplication.ui.theme.AiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到系统栏，输入框再单独避让键盘和导航栏。
        enableEdgeToEdge()
        setContent {
            AiTheme {
                AiApp()
            }
        }
    }
}
