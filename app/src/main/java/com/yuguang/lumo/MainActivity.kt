package com.yuguang.lumo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuguang.lumo.ui.AiApp
import com.yuguang.lumo.ui.onboarding.OnboardingScreen
import com.yuguang.lumo.ui.prefs.ThemeMode
import com.yuguang.lumo.ui.prefs.UiPreferences
import com.yuguang.lumo.ui.theme.AiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var uiPreferences: UiPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 内容延伸到系统栏，输入框再单独避让键盘和导航栏。
        enableEdgeToEdge()
        setContent {
            val mode by uiPreferences.themeMode.collectAsStateWithLifecycle()
            val onboarded by uiPreferences.onboarded.collectAsStateWithLifecycle()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            AiTheme(darkTheme = dark) {
                if (onboarded) AiApp() else OnboardingScreen()
            }
        }
    }
}
