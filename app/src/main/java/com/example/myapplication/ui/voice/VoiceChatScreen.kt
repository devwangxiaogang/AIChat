package com.example.myapplication.ui.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.R
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.ui.chat.SpeechInput
import com.example.myapplication.ui.chat.VoicePhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val BgTop = Color(0xFF0B1220)
private val BgBottom = Color(0xFF16233F)
private val CardBg = Color(0xFF1B2440)
private val CardBgSoft = Color(0xFF212B4A)
private val Accent = Color(0xFF7C87FF)
private val AccentDeep = Color(0xFF5B6CFF)
private val TextMain = Color(0xFFF2F5FF)
private val TextDim = Color(0xFF9AA6C8)
private val Online = Color(0xFF34D399)

/**
 * 语音对话（融合版）：暗色沉浸背景 + 卡片式对话流 + 声波/光环动效。
 * 支持实时语音（Seeduplex）、自动降级、音色/语速、长按复制、通话时长。
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
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
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
    val speakingNow = state.holding || state.speaking || voicePhase == VoicePhase.Listening
    LaunchedEffect(state.userText, state.assistantText, state.partialUserText, state.error) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 18.dp),
        ) {
            // 顶栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = TextMain)
                }
                Text(
                    stringResource(R.string.voice_chat_title),
                    color = TextMain,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                StatusChip(connected = state.connected, connecting = state.connecting)
                if (state.connected) {
                    IconButton(onClick = viewModel::stopSession) {
                        Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.voice_stop), tint = TextDim)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            // 状态头：光环 + 状态 + 通话时长 + 设置
            Row(verticalAlignment = Alignment.CenterVertically) {
                VoiceOrb(active = speakingNow, connected = state.connected)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = statusText(state, voicePhase),
                        color = TextMain,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = if (state.connected) {
                            stringResource(R.string.voice_duration, formatDuration(state.durationSec))
                        } else {
                            stringResource(R.string.voice_offline_hint)
                        },
                        color = TextDim,
                        fontSize = 12.sp,
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.voice_settings_title), tint = TextDim)
                }
            }
            if (state.fallback) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF3A2E1A),
                ) {
                    Text(
                        stringResource(R.string.voice_fallback_hint),
                        color = Color(0xFFFFC46B),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            // 对话流
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.userText.isNotBlank()) {
                    item { Bubble(stringResource(R.string.voice_you), state.userText, mine = true) }
                }
                if (state.partialUserText.isNotBlank()) {
                    item { Bubble(stringResource(R.string.voice_you), state.partialUserText, mine = true, streaming = true) }
                }
                if (state.assistantText.isNotBlank()) {
                    item { Bubble(stringResource(R.string.voice_assistant), state.assistantText, mine = false) }
                }
                if (state.connecting) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Accent)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.voice_connecting), color = TextDim, fontSize = 13.sp)
                        }
                    }
                }
                val error = state.error ?: localError
                if (error != null) {
                    item {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFF3A1F26), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                error,
                                color = Color(0xFFFF9AA8),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(4.dp)) }
            }
            // 声波（仅通话中显示）
            if (state.connected) Waveform(active = speakingNow)
            // 打招呼输入
            if (state.connected) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(if (state.fallback) R.string.voice_type_hint else R.string.voice_greeting_hint),
                            color = TextDim,
                            fontSize = 13.sp,
                        )
                    },
                    maxLines = 2,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextMain,
                        unfocusedTextColor = TextMain,
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF2A3556),
                        cursorColor = Accent,
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                viewModel.sendText(input)
                                input = ""
                            },
                            enabled = input.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = Accent)
                        }
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
            // 主操作
            if (state.connected) {
                HoldButton(
                    listening = state.holding || voicePhase == VoicePhase.Listening,
                    recognizing = voicePhase == VoicePhase.Recognizing,
                    onPress = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                            false
                        } else {
                            localError = null
                            if (state.fallback) speech.start("") else viewModel.pressStart()
                            true
                        }
                    },
                    onRelease = {
                        if (state.fallback) speech.stop() else viewModel.pressEnd()
                    },
                )
            } else {
                Button(
                    onClick = viewModel::startSession,
                    enabled = !state.connecting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(30.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentDeep, contentColor = TextMain),
                ) {
                    if (state.connecting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = TextMain)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        stringResource(if (state.connecting) R.string.voice_connecting else R.string.voice_start),
                        fontSize = 16.sp,
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }
    }

    if (showSettings) {
        VoiceSettingsSheet(
            voice = state.voice,
            speed = state.speed,
            connected = state.connected,
            onVoice = viewModel::setVoice,
            onSpeed = viewModel::setSpeed,
            onApply = viewModel::applyAudioSettings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StatusChip(connected: Boolean, connecting: Boolean) {
    val color = when {
        connected -> Color(0xFF16352C)
        connecting -> Color(0xFF2A2F1C)
        else -> Color(0xFF252C45)
    }
    val dot = when {
        connected -> Online
        connecting -> Color(0xFFFFC46B)
        else -> TextDim
    }
    val label = when {
        connected -> stringResource(R.string.voice_status_connected)
        connecting -> stringResource(R.string.voice_status_connecting)
        else -> stringResource(R.string.voice_status_offline)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(dot, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, color = dot, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VoiceOrb(active: Boolean, connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Restart),
        label = "pulse",
    )
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            if (active && connected) {
                drawCircle(Accent.copy(alpha = 0.30f * (1f - pulse)), radius = r * (0.55f + 0.45f * pulse))
                drawCircle(Accent.copy(alpha = 0.18f * (1f - pulse)), radius = r * (0.70f + 0.30f * pulse))
            }
            drawCircle(if (connected) Color(0xFF6C7BFF) else Color(0xFF2A3556), radius = r * 0.62f)
        }
        Text("豆包", color = TextMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Waveform(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "phase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        val count = 30
        val gap = size.width / count
        for (i in 0 until count) {
            val wave = abs(sin(phase * 2f * PI.toFloat() + i * 0.55f))
            val amp = if (active) 0.22f + 0.78f * wave else 0.10f + 0.12f * wave
            val h = size.height * amp
            drawRoundRect(
                color = Accent.copy(alpha = if (active) 0.92f else 0.32f),
                topLeft = Offset(i * gap + gap * 0.20f, (size.height - h) / 2f),
                size = Size(gap * 0.6f, h),
                cornerRadius = CornerRadius(gap * 0.3f),
            )
        }
    }
}

@Composable
private fun Bubble(
    label: String,
    text: String,
    mine: Boolean,
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .pointerInput(label, text, mine) {
                    detectTapGestures(
                        onLongPress = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("voice-chat", text))
                            Toast.makeText(context, R.string.voice_copy_done, Toast.LENGTH_SHORT).show()
                        },
                    )
                },
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (mine) 20.dp else 6.dp,
                bottomEnd = if (mine) 6.dp else 20.dp,
            ),
            color = if (mine) AccentDeep else CardBg,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    label,
                    color = if (mine) Color(0xFFC9D0FF) else Accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (streaming) "$text ▍" else text,
                    color = TextMain,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HoldButton(
    listening: Boolean,
    recognizing: Boolean,
    onPress: () -> Boolean,
    onRelease: () -> Unit,
) {
    val gradient = when {
        listening -> Brush.horizontalGradient(listOf(Accent, Color(0xFF9B6BFF)))
        recognizing -> Brush.horizontalGradient(listOf(Color(0xFF7C5CFF), Color(0xFF6C7BFF)))
        else -> Brush.horizontalGradient(listOf(AccentDeep, Accent))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clip(RoundedCornerShape(33.dp))
            .background(gradient)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        if (!onPress()) return@detectTapGestures
                        tryAwaitRelease()
                        onRelease()
                    },
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = null, tint = TextMain, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                when {
                    listening -> R.string.release_to_stop
                    recognizing -> R.string.recognizing
                    else -> R.string.hold_to_talk
                },
            ),
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceSettingsSheet(
    voice: VoiceChoice,
    speed: Int,
    connected: Boolean,
    onVoice: (VoiceChoice) -> Unit,
    onSpeed: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardBgSoft) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.voice_settings_title), color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.voice_settings_tone), color = TextDim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoiceChoice.entries.filter { it != VoiceChoice.CLONE }.forEach { choice ->
                    val selected = voice == choice
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) Accent else CardBg,
                        modifier = Modifier.clickable { onVoice(choice) },
                    ) {
                        Text(
                            stringResource(choice.labelRes),
                            color = if (selected) TextMain else TextDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            Text(
                stringResource(R.string.voice_settings_speed, speed),
                color = TextDim,
                fontSize = 13.sp,
            )
            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeed(it.toInt()) },
                onValueChangeFinished = { onApply() },
                valueRange = -50f..100f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = CardBg,
                ),
            )
            if (connected) {
                Text(stringResource(R.string.voice_settings_applied), color = Online, fontSize = 12.sp)
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentDeep, contentColor = TextMain),
            ) {
                Text(stringResource(R.string.voice_settings_done))
            }
        }
    }
}

private fun statusText(state: VoiceChatUiState, voicePhase: VoicePhase): String {
    return when {
        state.connecting -> "正在连接…"
        state.holding || voicePhase == VoicePhase.Listening -> "正在听你说…"
        state.speaking -> "豆包正在回答…"
        state.fallback -> "基础语音对话 · 按住说话"
        state.connected -> "按住说话，豆包会语音回答"
        else -> "点开始，和豆包聊两句"
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
