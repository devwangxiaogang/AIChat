package com.example.myapplication.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DismissibleDrawerSheet
import androidx.compose.material3.DismissibleNavigationDrawer
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import androidx.compose.runtime.rememberCoroutineScope
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.launch

enum class AppSection(@StringRes val labelRes: Int, val icon: ImageVector) {
    Chat(R.string.section_chat, Icons.AutoMirrored.Filled.Chat),
    Translate(R.string.section_translate, Icons.Filled.Translate),
    VoiceChat(R.string.section_voice, Icons.Filled.RecordVoiceOver),
    Settings(R.string.section_settings, Icons.Filled.Settings),
}

/** 会话列表和翻译页共用的侧边栏。打开时把主界面推出屏幕，而不是盖在上面。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScaffold(
    section: AppSection,
    title: String,
    onNavigate: (AppSection) -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    DismissibleNavigationDrawer(
        drawerState = drawerState,
        // 关闭时不能右滑打开，避免列表滑动误触发；打开后仍可左滑收起。
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            DismissibleDrawerSheet(modifier = Modifier.width(280.dp).fillMaxHeight()) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                )
                AppSection.entries.forEach { item ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(item.labelRes)) },
                        selected = item == section,
                        icon = { Icon(item.icon, contentDescription = null) },
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (item != section) onNavigate(item)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = snackbarHost,
            floatingActionButton = floatingActionButton,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.open_drawer))
                        }
                    },
                    actions = actions,
                )
            },
            content = content,
        )
    }
}
