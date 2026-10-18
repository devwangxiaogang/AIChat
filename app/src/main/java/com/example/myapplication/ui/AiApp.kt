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

/** 三个页面：会话列表、某个会话、模型与密钥设置。 */
object Routes {
    const val Conversations = "conversations"
    const val Settings = "settings"
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
                onOpenSettings = { nav.navigate(Routes.Settings) },
            )
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
        composable(Routes.Settings) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
