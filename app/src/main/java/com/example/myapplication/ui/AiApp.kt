package com.example.myapplication.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.myapplication.ui.chat.ChatScreen
import com.example.myapplication.ui.list.ConversationListScreen
import com.example.myapplication.ui.settings.SettingsScreen
import com.example.myapplication.ui.shell.AppSection
import com.example.myapplication.ui.translate.TranslateScreen
import com.example.myapplication.ui.voice.VoiceChatScreen

/** 会话列表、翻译、某个会话，以及模型与密钥设置。 */
object Routes {
    const val Conversations = "conversations"
    const val Translate = "translate"
    const val Settings = "settings"
    const val Voice = "voice"
    const val Chat = "chat/{id}"

    fun chat(id: Long) = "chat/$id"
}

@Composable
fun AiApp() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.Conversations) {
        composable(Routes.Conversations) {
            ConversationListScreen(
                onOpenChat = { id -> nav.navigate(Routes.chat(id)) },
                onNavigate = { section -> nav.openSection(section) },
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
            SettingsScreen(onBack = { nav.popBackStack() })
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
