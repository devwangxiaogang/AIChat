package com.yuguang.lumo.ui.shell

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yuguang.lumo.R

enum class AppSection(@StringRes val labelRes: Int, val icon: ImageVector) {
    Chat(R.string.section_chat, Icons.AutoMirrored.Filled.Chat),
    Translate(R.string.section_translate, Icons.Filled.Translate),
    VoiceChat(R.string.section_voice, Icons.Filled.RecordVoiceOver),
    Settings(R.string.section_settings, Icons.Filled.Settings),
}

/**
 * 主界面骨架：标题栏 + 右上角「我」入口（设置中心）。
 * 一级导航已改到底部栏，抽屉不再使用。
 */
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        topBar = {
            TopAppBar(
                title = {
                    Text(title, fontWeight = FontWeight.SemiBold)
                },
                actions = {
                    actions()
                    if (section != AppSection.Settings) {
                        IconButton(onClick = { onNavigate(AppSection.Settings) }) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.section_profile),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                },
            )
        },
        content = content,
    )
}
