package com.example.myapplication.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.R
import com.example.myapplication.ui.chat.SpeechInput
import com.example.myapplication.ui.chat.VoicePhase

/**
 * 语音对话：优先端到端实时语音（Seeduplex）；未开通时自动降级为
 * 豆包识别 → 对话模型 → 豆包朗读，页面功能保持一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    viewModel: VoiceChatViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var voicePhase by remember { mutableStateOf(VoicePhase.Idle) }
    var localError by remember { mutableStateOf<String?>(null) }
    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) localError = context.getString(R.string.need_mic_live)
    }
    val speech = remember {
        SpeechInput(
            context = context,
            onText = { spoken -> viewModel.sendText(spoken) },
            onPhase = { phase -> voicePhase = phase },
            onError = { message -> localError = message },
            recognize = viewModel::recognize,
        )
    }
    DisposableEffect(speech) {
        onDispose { speech.destroy() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.voice_chat_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state.connected || state.connecting) {
                        IconButton(onClick = viewModel::stopSession) {
                            Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.voice_stop))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (state.fallback) {
                    stringResource(R.string.voice_fallback_hint)
                } else {
                    stringResource(R.string.voice_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.userText.isNotBlank()) {
                    Caption(title = stringResource(R.string.voice_you), text = state.userText)
                }
                if (state.partialUserText.isNotBlank()) {
                    Caption(title = stringResource(R.string.voice_you), text = state.partialUserText, muted = true)
                }
                if (state.assistantText.isNotBlank()) {
                    Caption(title = stringResource(R.string.voice_assistant), text = state.assistantText)
                }
                if (state.connecting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.voice_connecting), style = MaterialTheme.typography.bodySmall)
                    }
                }
                val error = state.error ?: localError
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(if (state.fallback) R.string.voice_type_hint else R.string.voice_greeting_hint)) },
                maxLines = 3,
                enabled = state.connected,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            viewModel.sendText(input)
                            input = ""
                        },
                        enabled = input.isNotBlank() && state.connected,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send))
                    }
                },
            )
            if (state.connected) {
                val listening = state.holding || voicePhase == VoicePhase.Listening
                val recognizing = voicePhase == VoicePhase.Recognizing
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .pointerInput(state.connected, state.fallback) {
                            detectTapGestures(
                                onPress = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        permission.launch(Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    localError = null
                                    if (state.fallback) speech.start("") else viewModel.pressStart()
                                    tryAwaitRelease()
                                    if (state.fallback) speech.stop() else viewModel.pressEnd()
                                },
                            )
                        },
                    shape = RoundedCornerShape(24.dp),
                    color = when {
                        listening -> MaterialTheme.colorScheme.primary
                        recognizing -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                when {
                                    listening -> R.string.release_to_stop
                                    recognizing -> R.string.recognizing
                                    else -> R.string.hold_to_talk
                                },
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                Button(
                    onClick = viewModel::startSession,
                    enabled = !state.connecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(stringResource(R.string.voice_start), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Caption(title: String, text: String, muted: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
