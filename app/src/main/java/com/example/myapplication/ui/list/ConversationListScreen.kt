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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.myapplication.R
import com.example.myapplication.ui.localizedSummary
import com.example.myapplication.ui.model.ModelPickerDialog
import androidx.compose.ui.res.stringResource
import com.example.myapplication.ui.shell.AppSection
import com.example.myapplication.ui.shell.SectionScaffold
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onNavigate: (AppSection) -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    val scope = rememberCoroutineScope()
    // 从对话页返回时刷新一次，「新建对话」才会用上最近一次成功发送的模型。
    LaunchedEffect(Unit) { viewModel.refreshStarting() }

    SectionScaffold(
        section = AppSection.Chat,
        title = stringResource(R.string.section_chat),
        onNavigate = onNavigate,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_chat)) },
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
                Text(stringResource(R.string.no_conversations), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.keys_in_settings),
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { showCreate = true }) { Text(stringResource(R.string.start_chat)) }
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
            title = { Text(stringResource(R.string.new_chat)) },
            text = { Text(stringResource(R.string.use_model, viewModel.starting.provider.localizedSummary(viewModel.starting.model))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCreate = false
                        scope.launch {
                            val id = viewModel.create(viewModel.starting.provider, viewModel.starting.model)
                            onOpenChat(id)
                        }
                    },
                ) { Text(stringResource(R.string.start)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showCreate = false
                            showModels = true
                        },
                    ) { Text(stringResource(R.string.change_model)) }
                    TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        )
    }

    if (showModels) {
        ModelPickerDialog(
            title = stringResource(R.string.choose_model),
            providers = ProviderId.entries,
            selectedProvider = viewModel.starting.provider,
            selectedId = viewModel.starting.model,
            catalogByProvider = catalogs,
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
            title = { Text(stringResource(R.string.delete_conversation)) },
            text = {
                val title = conversation.title.ifBlank { stringResource(R.string.new_chat) }
                Text(stringResource(R.string.delete_conversation_body, title))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(conversation.id)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
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
                Text(
                    conversation.title.ifBlank { stringResource(R.string.new_chat) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    conversation.provider.localizedSummary(conversation.model),
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
                    contentDescription = stringResource(R.string.delete_conversation),
                    tint = MaterialTheme.colorScheme.secondary,
                )
            }
        }
    }
}

@Composable
private fun formatTime(epoch: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val pattern = if (locale.language == "zh") "MM-dd HH:mm" else "MMM d, HH:mm"
    return SimpleDateFormat(pattern, locale).format(Date(epoch))
}
