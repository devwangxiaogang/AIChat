package com.yuguang.lumo.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.yuguang.lumo.ui.chat.ChatScreen
import com.yuguang.lumo.ui.list.ConversationListScreen
import com.yuguang.lumo.ui.settings.AboutScreen
import com.yuguang.lumo.ui.settings.AppearanceScreen
import com.yuguang.lumo.ui.settings.DataSettingsScreen
import com.yuguang.lumo.ui.settings.ProviderSettingsScreen
import com.yuguang.lumo.ui.settings.SettingsHomeScreen
import com.yuguang.lumo.ui.settings.VoicePrefsScreen
import com.yuguang.lumo.ui.shell.AppBottomBar
import com.yuguang.lumo.ui.shell.AppSection
import com.yuguang.lumo.ui.translate.TranslateScreen
import com.yuguang.lumo.ui.voice.VoiceChatScreen

/** 会话列表、翻译、语音、某个会话，以及设置中心（含二级页）。 */
object Routes {
    const val Conversations = "conversations"
    const val Translate = "translate"
    const val Voice = "voice"
    const val Chat = "chat/{id}"

    const val Settings = "settings"
    const val Provider = "settings/provider/{provider}"
    const val VoicePrefs = "settings/voice"
    const val Appearance = "settings/appearance"
    const val Data = "settings/data"
    const val About = "settings/about"

    fun chat(id: Long) = "chat/$id"
    fun provider(id: String) = "settings/provider/$id"
}

@Composable
fun AiApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val section = when (route) {
        Routes.Translate -> AppSection.Translate
        Routes.Voice -> AppSection.VoiceChat
        else -> AppSection.Chat
    }
    val showBottomBar = route == Routes.Conversations || route == Routes.Translate || route == Routes.Voice
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // 状态栏交给各页面自己的 TopAppBar / safeDrawingPadding，这里只留底部导航
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    selected = section,
                    dark = false,
                    onSelect = { nav.openSection(it) },
                )
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Routes.Conversations,
            modifier = Modifier.padding(inner),
        ) {
            composable(Routes.Conversations) {
                ConversationListScreen(
                    onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                    onNavigate = { section -> nav.openSection(section) },
                    onOpenBackup = { nav.navigate(Routes.Data) },
                )
            }
            composable(Routes.Translate) {
                TranslateScreen(onNavigate = { section -> nav.openSection(section) })
            }
            composable(
                route = Routes.Chat,
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) {
                ChatScreen(
                    onBack = { nav.popBackStack() },
                    onOpenSettings = { nav.navigate(Routes.Settings) },
                )
            }
            composable(Routes.Voice) {
                VoiceChatScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.Settings) {
                SettingsHomeScreen(
                    onBack = { nav.popBackStack() },
                    onOpenProvider = { id -> nav.navigate(Routes.provider(id.name)) },
                    onOpenVoicePrefs = { nav.navigate(Routes.VoicePrefs) },
                    onOpenAppearance = { nav.navigate(Routes.Appearance) },
                    onOpenData = { nav.navigate(Routes.Data) },
                    onOpenAbout = { nav.navigate(Routes.About) },
                )
            }
            composable(
                route = Routes.Provider,
                arguments = listOf(navArgument("provider") { type = NavType.StringType }),
            ) { entry ->
                val id = entry.arguments?.getString("provider").orEmpty()
                ProviderSettingsScreen(
                    providerId = id,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.VoicePrefs) {
                VoicePrefsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.Appearance) {
                AppearanceScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.Data) {
                DataSettingsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.About) {
                AboutScreen(onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun androidx.navigation.NavHostController.openSection(section: AppSection) {
    val route = when (section) {
        AppSection.Chat -> Routes.Conversations
        AppSection.Translate -> Routes.Translate
        AppSection.VoiceChat -> Routes.Voice
        AppSection.Settings -> Routes.Settings
    }
    if (section == AppSection.Settings) {
        navigate(route)
        return
    }
    navigate(route) {
        popUpTo(Routes.Conversations) { inclusive = false }
        launchSingleTop = true
    }
}
