package com.example.myapplication.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.domain.model.Conversation
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.modelSummary
import com.example.myapplication.ui.model.ModelPickerDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 对话") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("新对话") },
            )
        },
    ) { padding ->
        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                Text("还没有会话", style = MaterialTheme.typography.titleLarge)
                Text(
                    "密钥在右上角设置里填写。",
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { showCreate = true }) { Text("开始对话") }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(conversations, key = { it.id }) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        onOpen = { onOpenChat(conversation.id) },
                        onDelete = { pendingDelete = conversation },
                    )
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("新对话") },
            text = { Text("使用 ${viewModel.starting.summary}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreate = false
                        scope.launch {
                            val id = viewModel.create(viewModel.starting.provider, viewModel.starting.model)
                            onOpenChat(id)
                        }
                    },
                ) { Text("开始") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showCreate = false
                            showModels = true
                        },
                    ) { Text("更换模型") }
                    TextButton(onClick = { showCreate = false }) { Text("取消") }
                }
            },
        )
    }

    if (showModels) {
        ModelPickerDialog(
            title = "选择模型",
            providers = ProviderId.entries,
            selectedProvider = viewModel.starting.provider,
            selectedId = viewModel.starting.model,
            onSelect = { provider, model ->
                showModels = false
                scope.launch {
                    val id = viewModel.create(provider, model)
                    onOpenChat(id)
                }
            },
            onDismiss = { showModels = false },
        )
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除会话") },
            text = { Text("删除「${conversation.title}」后，消息无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(conversation.id)
                        pendingDelete = null
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ConversationCard(
    conversation: Conversation,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    conversation.provider.modelSummary(conversation.model),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                formatTime(conversation.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除会话",
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

private fun formatTime(epoch: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(epoch))
