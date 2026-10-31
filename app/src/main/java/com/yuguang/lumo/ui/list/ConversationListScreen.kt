package com.yuguang.lumo.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.Conversation
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.ui.localizedSummary
import com.yuguang.lumo.ui.model.ModelPickerDialog
import com.yuguang.lumo.ui.shell.AppSection
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 对话首页：搜索、快捷入口、置顶/今天/更早分组，长按管理会话。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenChat: (Long) -> Unit,
    onNavigate: (AppSection) -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: ConversationListViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Conversation?>(null) }
    var renameTarget by remember { mutableStateOf<Conversation?>(null) }
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refreshStarting() }

    val filtered = conversations.filter { conversation ->
        if (query.isBlank()) return@filter true
        val needle = query.trim()
        conversation.title.contains(needle, ignoreCase = true) ||
            conversation.preview?.contains(needle, ignoreCase = true) == true ||
            conversation.provider.localizedSummary(conversation.model).contains(needle, ignoreCase = true)
    }
    val pinned = filtered.filter { it.pinned }
    val unpinned = filtered.filter { !it.pinned }
    val today = unpinned.filter { isToday(it.updatedAt) }
    val earlier = unpinned.filterNot { isToday(it.updatedAt) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_chat)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // 标题 + 设置入口
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.nav_chat),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onNavigate(AppSection.Settings) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.section_profile),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                // 搜索
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(14.dp))
                // 快捷入口
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(
                        icon = Icons.Filled.Add,
                        label = stringResource(R.string.new_chat),
                        modifier = Modifier.weight(1f),
                    ) { showCreate = true }
                    QuickAction(
                        icon = Icons.Filled.Translate,
                        label = stringResource(R.string.nav_translate),
                        modifier = Modifier.weight(1f),
                    ) { onNavigate(AppSection.Translate) }
                    QuickAction(
                        icon = Icons.Filled.RecordVoiceOver,
                        label = stringResource(R.string.nav_voice),
                        modifier = Modifier.weight(1f),
                    ) { onNavigate(AppSection.VoiceChat) }
                    QuickAction(
                        icon = Icons.Filled.Archive,
                        label = stringResource(R.string.quick_backup),
                        modifier = Modifier.weight(1f),
                    ) { onOpenBackup() }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (conversations.isEmpty()) {
                item { EmptyState(onStartChat = { showCreate = true }, onOpenSettings = { onNavigate(AppSection.Settings) }) }
            } else if (filtered.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            if (conversations.isNotEmpty()) {
                if (pinned.isNotEmpty()) {
                    item { GroupHeader(stringResource(R.string.conversation_group_pinned)) }
                    items(pinned, key = { "pinned-${it.id}" }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            menuOpen = menuFor == conversation.id,
                            onOpen = { onOpenChat(conversation.id) },
                            onLongPress = { menuFor = conversation.id },
                            onDismissMenu = { menuFor = null },
                            onTogglePin = {
                                viewModel.setPinned(conversation.id, !conversation.pinned)
                                menuFor = null
                            },
                            onRename = {
                                renameTarget = conversation
                                menuFor = null
                            },
                            onDelete = {
                                pendingDelete = conversation
                                menuFor = null
                            },
                        )
                    }
                }
                if (today.isNotEmpty()) {
                    item { GroupHeader(stringResource(R.string.conversation_group_today)) }
                    items(today, key = { "today-${it.id}" }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            menuOpen = menuFor == conversation.id,
                            onOpen = { onOpenChat(conversation.id) },
                            onLongPress = { menuFor = conversation.id },
                            onDismissMenu = { menuFor = null },
                            onTogglePin = {
                                viewModel.setPinned(conversation.id, !conversation.pinned)
                                menuFor = null
                            },
                            onRename = {
                                renameTarget = conversation
                                menuFor = null
                            },
                            onDelete = {
                                pendingDelete = conversation
                                menuFor = null
                            },
                        )
                    }
                }
                if (earlier.isNotEmpty()) {
                    item { GroupHeader(stringResource(R.string.conversation_group_earlier)) }
                    items(earlier, key = { "earlier-${it.id}" }) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            menuOpen = menuFor == conversation.id,
                            onOpen = { onOpenChat(conversation.id) },
                            onLongPress = { menuFor = conversation.id },
                            onDismissMenu = { menuFor = null },
                            onTogglePin = {
                                viewModel.setPinned(conversation.id, !conversation.pinned)
                                menuFor = null
                            },
                            onRename = {
                                renameTarget = conversation
                                menuFor = null
                            },
                            onDelete = {
                                pendingDelete = conversation
                                menuFor = null
                            },
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

        renameTarget?.let { conversation ->
            RenameDialog(
                initial = conversation.title,
                onConfirm = { title ->
                    viewModel.rename(conversation.id, title)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null },
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: Conversation,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (conversation.pinned) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = stringResource(R.string.conversation_group_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        conversation.title.ifBlank { stringResource(R.string.new_chat) },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        formatTime(conversation.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (conversation.preview?.isNotBlank() == true) {
                    Text(
                        conversation.preview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            conversation.provider.localizedSummary(conversation.model),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (conversation.pinned) R.string.unpin else R.string.pin))
                },
                onClick = onTogglePin,
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = onRename)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun EmptyState(onStartChat: () -> Unit, onOpenSettings: () -> Unit) {
    com.yuguang.lumo.ui.components.EmptyState(
        icon = Icons.AutoMirrored.Filled.Chat,
        title = stringResource(R.string.empty_title),
        body = stringResource(R.string.empty_body),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onStartChat) { Text(stringResource(R.string.start_chat)) }
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.keys_in_settings)) }
        }
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun isToday(epoch: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = epoch }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun formatTime(epoch: Long): String {
    val locale = Locale.getDefault()
    val pattern = if (isToday(epoch)) "HH:mm" else "MM-dd"
    return SimpleDateFormat(pattern, locale).format(Date(epoch))
}
