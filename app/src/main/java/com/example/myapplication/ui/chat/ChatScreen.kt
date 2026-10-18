package com.example.myapplication.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.R
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.MessageStatus
import com.example.myapplication.domain.model.Role
import com.example.myapplication.ui.isDefaultConversationTitle
import com.example.myapplication.ui.localizedSummary
import com.example.myapplication.ui.model.ModelPickerDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showModels by remember { mutableStateOf(false) }
    var voiceMode by remember { mutableStateOf(false) }
    var voicePhase by remember { mutableStateOf(VoicePhase.Idle) }
    val speech = remember {
        SpeechInput(
            context = context,
            onText = { spoken ->
                viewModel.updateInput(spoken)
                voiceMode = false
                voicePhase = VoicePhase.Idle
            },
            onPhase = { phase ->
                voicePhase = phase
                if (phase == VoicePhase.Recognizing) voiceMode = false
            },
            onError = { message ->
                voicePhase = VoicePhase.Idle
                scope.launch { snackbar.showSnackbar(message) }
            },
            recognize = viewModel::recognize,
        )
    }
    val recordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            voiceMode = true
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.mic_for_chat)) }
        }
    }
    DisposableEffect(speech) {
        onDispose { speech.destroy() }
    }
    LaunchedEffect(Unit) {
        viewModel.errors.collect { snackbar.showSnackbar(it) }
    }
    LaunchedEffect(Unit) {
        viewModel.speakPrompts.collect { prompt ->
            val result = snackbar.showSnackbar(prompt.message, actionLabel = context.getString(R.string.use_local_once))
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.speakLocal(prompt.messageId, prompt.content)
            }
        }
    }
    val tail = state.messages.lastOrNull()
    LaunchedEffect(state.messages.size, tail?.content?.length, tail?.reasoning?.length) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.conversation?.title?.let { title ->
                                if (isDefaultConversationTitle(title)) stringResource(R.string.new_chat) else title
                            } ?: stringResource(R.string.chat_fallback),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val conversation = state.conversation
                        if (conversation != null) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { showModels = true },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    conversation.provider.localizedSummary(conversation.model),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.section_settings))
                    }
                },
            )
        },
        bottomBar = {
            ChatComposer(
                input = state.input,
                generating = state.generating,
                voiceMode = voiceMode,
                voicePhase = voicePhase,
                onInput = viewModel::updateInput,
                onSend = viewModel::send,
                onStopGeneration = viewModel::stop,
                onToggleVoiceMode = {
                    if (voiceMode) {
                        if (voicePhase != VoicePhase.Idle) speech.destroy()
                        voicePhase = VoicePhase.Idle
                        voiceMode = false
                    } else if (
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        voiceMode = true
                    } else {
                        recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onHoldStart = { speech.start(viewModel.state.value.input) },
                onHoldEnd = { speech.stop() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding),
            state = listState,
            // 反向排列让最新消息贴着输入框，键盘弹出后仍停在可见区域底部。
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.messages.asReversed(), key = { it.id }) { message ->
                MessageBubble(
                    message = message,
                    speaking = state.speakingId == message.id,
                    onRetry = viewModel::retry,
                    onSpeak = { viewModel.toggleSpeak(message) },
                )
            }
        }
    }

    val conversation = state.conversation
    if (showModels && conversation != null) {
        ModelPickerDialog(
            title = stringResource(R.string.change_provider_model, stringResource(conversation.provider.nameRes)),
            providers = listOf(conversation.provider),
            selectedProvider = conversation.provider,
            selectedId = conversation.model,
            note = stringResource(R.string.change_model_note),
            onSelect = { _, model ->
                viewModel.selectModel(model)
                showModels = false
            },
            onDismiss = { showModels = false },
        )
    }
}

@Composable
private fun ChatComposer(
    input: String,
    generating: Boolean,
    voiceMode: Boolean,
    voicePhase: VoicePhase,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    onStopGeneration: () -> Unit,
    onToggleVoiceMode: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val listening = voicePhase == VoicePhase.Listening
    val recognizing = voicePhase == VoicePhase.Recognizing
    val borderColor by animateColorAsState(
        targetValue = when {
            listening -> MaterialTheme.colorScheme.primary
            recognizing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
        },
        label = "composerBorder",
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .then(if (voiceMode) Modifier.height(52.dp) else Modifier.heightIn(min = 48.dp)),
            verticalAlignment = if (voiceMode) Alignment.CenterVertically else Alignment.Bottom,
        ) {
            IconButton(
                onClick = onToggleVoiceMode,
                enabled = voicePhase == VoicePhase.Idle,
            ) {
                Icon(
                    if (voiceMode) Icons.Filled.Keyboard else Icons.Filled.Mic,
                    contentDescription = stringResource(
                        if (voiceMode) R.string.switch_to_keyboard else R.string.switch_to_voice,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (voiceMode) {
                ChatHoldPad(
                    listening = listening,
                    recognizing = recognizing,
                    onHoldStart = onHoldStart,
                    onHoldEnd = onHoldEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            } else {
                BasicTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    enabled = !recognizing,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 4,
                    decorationBox = { inner ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    stringResource(
                                        when {
                                            recognizing -> R.string.recognizing
                                            listening -> R.string.listening_ellipsis
                                            else -> R.string.input_hint
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            when {
                recognizing -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(12.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }
                generating -> {
                    IconButton(onClick = onStopGeneration) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.stop_generation))
                    }
                }
                else -> {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = input.isNotBlank() && voicePhase == VoicePhase.Idle,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatHoldPad(
    listening: Boolean,
    recognizing: Boolean,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = when {
            listening -> MaterialTheme.colorScheme.primary
            recognizing -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
        },
        label = "holdPad",
    )
    val content = when {
        listening -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    val label = when {
        recognizing -> stringResource(R.string.recognizing)
        listening -> stringResource(R.string.release_to_stop)
        else -> stringResource(R.string.hold_to_talk)
    }
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .pointerInput(recognizing) {
                detectTapGestures(
                    onPress = {
                        if (recognizing) return@detectTapGestures
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onHoldStart()
                        tryAwaitRelease()
                        onHoldEnd()
                    },
                )
            },
        shape = RoundedCornerShape(22.dp),
        color = container,
        contentColor = content,
        shadowElevation = if (listening) 3.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (!recognizing) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (listening) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    speaking: Boolean,
    onRetry: () -> Unit,
    onSpeak: () -> Unit,
) {
    val isUser = message.role == Role.USER
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val container = when {
        message.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.status == MessageStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isUser) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        if (!isUser && message.reasoning.isNotBlank()) {
            ReasoningBlock(message.reasoning)
        }
        Surface(
            color = container,
            contentColor = contentColor,
            shape = shape,
            modifier = Modifier.fillMaxWidth(0.78f),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                val body = when {
                    message.content.isNotBlank() && message.status == MessageStatus.STREAMING -> "${message.content}▍"
                    message.content.isNotBlank() -> message.content
                    message.status == MessageStatus.STREAMING -> "▍"
                    else -> stringResource(R.string.empty_reply)
                }
                Text(body, style = MaterialTheme.typography.bodyLarge)
                if (message.content.isNotBlank()) {
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier
                            .align(Alignment.End)
                            .size(28.dp),
                    ) {
                        Icon(
                            if (speaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(if (speaking) R.string.stop_reading else R.string.read_message),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        if (message.status == MessageStatus.FAILED) {
            if (message.error.isNotBlank()) {
                Text(
                    message.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun ReasoningBlock(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth(0.78f).padding(bottom = 4.dp)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.hide_reasoning else R.string.show_reasoning))
        }
        if (expanded) {
            Text(
                reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
