package com.example.myapplication.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.myapplication.R

/** 底部三栏导航：对话 / 翻译 / 语音。语音页用深色版本，避免打断沉浸感。 */
@Composable
fun AppBottomBar(
    selected: AppSection,
    dark: Boolean = false,
    onSelect: (AppSection) -> Unit,
) {
    val indicator = if (dark) Color(0xFF2C3670) else MaterialTheme.colorScheme.primaryContainer
    val active = if (dark) Color(0xFFB9C1FF) else MaterialTheme.colorScheme.primary
    val inactive = if (dark) Color(0xFF8B96B8) else MaterialTheme.colorScheme.onSurfaceVariant
    val items = listOf(
        Triple(AppSection.Chat, Icons.AutoMirrored.Filled.Chat, R.string.nav_chat),
        Triple(AppSection.Translate, Icons.Filled.Translate, R.string.nav_translate),
        Triple(AppSection.VoiceChat, Icons.Filled.RecordVoiceOver, R.string.nav_voice),
    )
    Surface(
        color = if (dark) Color(0xFF101A2E) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (dark) 0.dp else 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEach { (section, icon, labelRes) ->
                val isActive = section == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { if (!isActive) onSelect(section) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isActive) indicator else Color.Transparent)
                            .padding(horizontal = 18.dp, vertical = 5.dp),
                    ) {
                        Icon(
                            icon,
                            contentDescription = stringResource(labelRes),
                            tint = if (isActive) active else inactive,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(
                        stringResource(labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isActive) active else inactive,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
