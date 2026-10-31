package com.example.myapplication.ui.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
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

/** 语音页配色随主题切换：浅色暖白 + 靛紫光晕，深色深空蓝 + 靛紫光晕。 */
private data class VoicePalette(
    val bgTop: Color,
    val bgBottom: Color,
    val glow: Color,
    val card: Color,
    val cardSoft: Color,
    val accent: Color,
    val accentDeep: Color,
    val text: Color,
    val textDim: Color,
    val online: Color,
    val border: Color,
    val isDark: Boolean,
)

@Composable
private fun rememberVoicePalette(): VoicePalette {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return if (dark) {
        VoicePalette(
            bgTop = cs.background,
            bgBottom = cs.surfaceContainer,
            glow = cs.primary,
            card = cs.surface,
            cardSoft = cs.surfaceContainerHigh,
            accent = cs.primary,
            accentDeep = cs.primary,
            text = cs.onBackground,
            textDim = cs.onSurfaceVariant,
            online = Color(0xFF4ADE80),
            border = cs.outline,
            isDark = true,
        )
    } else {
        VoicePalette(
            bgTop = cs.background,
            bgBottom = lerp(cs.background, cs.primaryContainer, 0.55f),
            glow = cs.primary,
            card = cs.surface,
            cardSoft = cs.surfaceContainerHigh,
            accent = cs.primary,
            accentDeep = cs.primary,
            text = cs.onBackground,
            textDim = cs.onSurfaceVariant,
            online = Color(0xFF0E9F6E),
            border = cs.outline,
            isDark = false,
        )
    }
}

/**
 * 语音对话页（光球 · 电话感）：
 * 顶部状态 + 中央光球 + 字幕层 + 底部按住说话，主题自适应。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceChatScreen(
    onBack: () -> Unit,
    viewModel: VoiceChatViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val palette = rememberVoicePalette()
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var voicePhase by remember { mutableStateOf(VoicePhase.Idle) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }
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
    val level by animateFloatAsState(
        targetValue = state.amplitude,
        animationSpec = tween(120),
        label = "voiceLevel",
    )
    BackHandler {
        when {
            showTextInput -> showTextInput = false
            state.connected || state.connecting -> viewModel.stopSession()
            else -> onBack()
        }
    }
    LaunchedEffect(state.userText, state.assistantText, state.partialUserText, state.error) {
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(palette.bgTop, palette.bgBottom))),
    ) {
        // 顶部光晕，给光球一个落点
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(palette.glow.copy(alpha = if (palette.isDark) 0.16f else 0.10f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.52f),
                    radius = size.width * 0.62f,
                ),
                radius = size.width * 0.62f,
                center = Offset(size.width / 2f, size.height * 0.52f),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .padding(horizontal = 18.dp),
        ) {
            // 顶栏：标题 + 状态 + 结束
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.voice_chat_title),
                    color = palette.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                StatusChip(connected = state.connected, connecting = state.connecting, palette = palette)
                if (state.connected) {
                    IconButton(onClick = viewModel::stopSession) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = stringResource(R.string.voice_stop),
                            tint = Color(0xFFFF6B81),
                        )
                    }
                }
            }

            if (!state.connected) {
                Spacer(Modifier.weight(1f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VoiceOrb(active = false, connected = false, size = 148.dp, amplitude = level, palette = palette)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.voice_idle_title),
                        color = palette.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.voice_idle_subtitle),
                        color = palette.textDim,
                        fontSize = 13.sp,
                    )
                }
                (state.error ?: localError)?.let { error ->
                    Spacer(Modifier.height(14.dp))
                    com.example.myapplication.ui.components.ErrorCard(
                        message = error,
                        onRetry = {
                            localError = null
                            viewModel.startSession()
                        },
                    )
                }
                Spacer(Modifier.weight(1f))
                StartButton(connecting = state.connecting, palette = palette) { viewModel.startSession() }
                Spacer(Modifier.height(12.dp))
                QuickChips(
                    state = state,
                    palette = palette,
                    onOpenSettings = { showSettings = true },
                    onToggleText = { showTextInput = !showTextInput },
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    VoiceOrb(active = speakingNow, connected = true, size = 96.dp, amplitude = level, palette = palette)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        statusText(state, voicePhase),
                        color = palette.text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.voice_duration, formatDuration(state.durationSec)),
                        color = palette.textDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (state.fallback) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (palette.isDark) Color(0xFF3A2E1A) else Color(0xFFFFF4E0),
                    ) {
                        Text(
                            stringResource(R.string.voice_fallback_hint),
                            color = if (palette.isDark) Color(0xFFFFC46B) else Color(0xFF8A5A00),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val blank = state.userText.isBlank() && state.assistantText.isBlank() &&
                        state.partialUserText.isBlank() && !state.connecting && !state.holding &&
                        !state.speaking && voicePhase == VoicePhase.Idle
                    if (blank) {
                        item {
                            Text(
                                stringResource(R.string.voice_hold_hint_short),
                                color = palette.textDim,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    if (state.userText.isNotBlank()) {
                        item { Bubble(stringResource(R.string.voice_you), state.userText, mine = true, palette = palette) }
                    }
                    if (state.partialUserText.isNotBlank()) {
                        item {
                            Bubble(
                                stringResource(R.string.voice_you),
                                state.partialUserText,
                                mine = true,
                                streaming = true,
                                palette = palette,
                            )
                        }
                    }
                    if (state.assistantText.isNotBlank()) {
                        item { Bubble(stringResource(R.string.voice_assistant), state.assistantText, mine = false, streaming = state.speaking, palette = palette) }
                    }
                    if (state.connecting) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = palette.accent)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.voice_connecting), color = palette.textDim, fontSize = 13.sp)
                            }
                        }
                    }
                    val error = state.error ?: localError
                    if (error != null) {
                        item {
                            com.example.myapplication.ui.components.ErrorCard(
                                message = error,
                                onRetry = { localError = null },
                            )
                        }
                    }
                    item { Spacer(Modifier.height(2.dp)) }
                }
                Waveform(active = speakingNow, amplitude = level, palette = palette)
                if (showTextInput) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(if (state.fallback) R.string.voice_type_hint else R.string.voice_greeting_hint),
                                color = palette.textDim,
                                fontSize = 13.sp,
                            )
                        },
                        maxLines = 2,
                        shape = RoundedCornerShape(20.dp),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Send),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = palette.text,
                            unfocusedTextColor = palette.text,
                            focusedBorderColor = palette.accent,
                            unfocusedBorderColor = palette.border,
                            cursorColor = palette.accent,
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    viewModel.sendText(input)
                                    input = ""
                                },
                                enabled = input.isNotBlank(),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.send), tint = palette.accent)
                            }
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                HoldButton(
                    listening = state.holding || voicePhase == VoicePhase.Listening,
                    recognizing = voicePhase == VoicePhase.Recognizing,
                    palette = palette,
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
                Text(
                    stringResource(
                        if (state.holding || voicePhase == VoicePhase.Listening) R.string.voice_hold_hint
                        else R.string.voice_interrupt_hint,
                    ),
                    color = palette.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                QuickChips(
                    state = state,
                    palette = palette,
                    onOpenSettings = { showSettings = true },
                    onToggleText = { showTextInput = !showTextInput },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSettings) {
        VoiceSettingsSheet(
            voice = state.voice,
            speed = state.speed,
            connected = state.connected,
            palette = palette,
            onVoice = viewModel::setVoice,
            onSpeed = viewModel::setSpeed,
            onApply = viewModel::applyAudioSettings,
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun StatusChip(connected: Boolean, connecting: Boolean, palette: VoicePalette) {
    val dot = when {
        connected -> palette.online
        connecting -> Color(0xFFFFC46B)
        else -> palette.textDim
    }
    val bg = when {
        connected -> palette.online.copy(alpha = 0.14f)
        connecting -> Color(0xFFFFC46B).copy(alpha = 0.16f)
        else -> palette.cardSoft
    }
    val label = when {
        connected -> stringResource(R.string.voice_status_connected)
        connecting -> stringResource(R.string.voice_status_connecting)
        else -> stringResource(R.string.voice_status_offline)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(dot, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = dot, fontSize = 11.sp)
        }
    }
}

@Composable
private fun VoiceOrb(active: Boolean, connected: Boolean, size: Dp, amplitude: Float, palette: VoicePalette) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400), repeatMode = RepeatMode.Restart),
        label = "pulse",
    )
    val barPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "barPhase",
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            if (active && connected) {
                val boost = 1f + amplitude * 0.22f
                drawCircle(palette.accent.copy(alpha = 0.28f * (1f - pulse)), radius = r * (0.55f + 0.45f * pulse) * boost)
                drawCircle(palette.accent.copy(alpha = 0.16f * (1f - pulse)), radius = r * (0.72f + 0.28f * pulse) * boost)
                drawCircle(palette.accent.copy(alpha = 0.10f + 0.18f * amplitude), radius = r * (0.98f + 0.06f * amplitude))
            } else {
                drawCircle(palette.accent.copy(alpha = 0.10f), radius = r * 0.95f)
            }
            drawCircle(palette.accent, radius = r * 0.64f)
            drawCircle(Color.White.copy(alpha = 0.14f), radius = r * 0.60f)
            // 音量条
            val bars = 7
            val centerY = this.size.height * 0.66f
            for (i in 0 until bars) {
                val wave = abs(sin(barPhase * 2f * PI.toFloat() + i * 0.9f))
                val energy = if (active) (0.45f + amplitude * 1.8f) else 0.9f
                val h = r * (0.10f + 0.22f * wave) * energy
                val x = this.size.width / 2f + (i - (bars - 1) / 2f) * r * 0.16f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.92f),
                    topLeft = Offset(x - r * 0.035f, centerY - h / 2f),
                    size = androidx.compose.ui.geometry.Size(r * 0.07f, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r * 0.035f),
                )
            }
        }
        Text(
            stringResource(R.string.voice_assistant),
            color = Color.White,
            fontSize = if (size > 120.dp) 20.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.offset(y = -(size * 0.10f)),
        )
    }
}

@Composable
private fun Waveform(active: Boolean, amplitude: Float, palette: VoicePalette) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1300, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "phase",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp),
    ) {
        val count = 30
        val gap = size.width / count
        for (i in 0 until count) {
            val wave = abs(sin(phase * 2f * PI.toFloat() + i * 0.55f))
            val amp = if (active) (0.10f + 0.90f * wave) * (0.45f + amplitude * 1.6f) else 0.08f + 0.10f * wave
            val h = size.height * amp
            drawRoundRect(
                color = palette.accent.copy(alpha = if (active) 0.92f else 0.32f),
                topLeft = Offset(i * gap + gap * 0.2f, (size.height - h) / 2f),
                size = androidx.compose.ui.geometry.Size(gap * 0.6f, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(gap * 0.3f),
            )
        }
    }
}

@Composable
private fun Bubble(
    label: String,
    text: String,
    mine: Boolean,
    palette: VoicePalette,
    streaming: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (mine) 0.84f else 0.9f)
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
            color = if (mine) palette.accentDeep else palette.card,
            border = if (mine) null else androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    label,
                    color = if (mine) Color.White.copy(alpha = 0.75f) else palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (streaming) "$text ▍" else text,
                    color = if (mine) Color.White else palette.text,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StartButton(connecting: Boolean, palette: VoicePalette, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(29.dp))
            .pointerInput(connecting) {
                detectTapGestures { if (!connecting) onClick() }
            },
        shape = RoundedCornerShape(29.dp),
        color = palette.accentDeep,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (connecting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(if (connecting) R.string.voice_connecting else R.string.voice_start),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun QuickChips(
    state: VoiceChatUiState,
    palette: VoicePalette,
    onOpenSettings: () -> Unit,
    onToggleText: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(
            label = stringResource(R.string.voice_settings_tone) + " · " + stringResource(state.voice.labelRes),
            palette = palette,
            onClick = onOpenSettings,
        )
        Chip(
            label = stringResource(R.string.voice_settings_speed, state.speed),
            palette = palette,
            onClick = onOpenSettings,
        )
        Chip(
            label = stringResource(R.string.voice_typing),
            palette = palette,
            onClick = onToggleText,
        )
    }
}

@Composable
private fun Chip(label: String, palette: VoicePalette, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = palette.card,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.border.copy(alpha = 0.6f)),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .pointerInput(label) { detectTapGestures { onClick() } },
    ) {
        Text(
            label,
            color = palette.accent,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun HoldButton(
    listening: Boolean,
    recognizing: Boolean,
    palette: VoicePalette,
    onPress: () -> Boolean,
    onRelease: () -> Unit,
) {
    val gradient = if (listening) {
        Brush.horizontalGradient(listOf(palette.accent, palette.accentDeep))
    } else {
        Brush.horizontalGradient(
            listOf(
                palette.accent.copy(alpha = if (palette.isDark) 0.30f else 0.16f),
                palette.accent.copy(alpha = if (palette.isDark) 0.22f else 0.12f),
            ),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
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
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            tint = if (listening) Color.White else palette.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(
                when {
                    listening -> R.string.release_to_stop
                    recognizing -> R.string.recognizing
                    else -> R.string.hold_to_talk
                },
            ),
            color = if (listening) Color.White else palette.accent,
            fontSize = 17.sp,
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
    palette: VoicePalette,
    onVoice: (VoiceChoice) -> Unit,
    onSpeed: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = palette.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.voice_settings_title), color = palette.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.voice_settings_tone), color = palette.textDim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VoiceChoice.entries.filter { it != VoiceChoice.CLONE }.forEach { choice ->
                    val selected = voice == choice
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) palette.accent else palette.cardSoft,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .pointerInput(choice) {
                                detectTapGestures {
                                    onVoice(choice)
                                    if (connected) onApply()
                                }
                            },
                    ) {
                        Text(
                            stringResource(choice.labelRes),
                            color = if (selected) Color.White else palette.textDim,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        )
                    }
                }
            }
            Text(stringResource(R.string.voice_settings_speed, speed), color = palette.textDim, fontSize = 13.sp)
            Slider(
                value = speed.toFloat(),
                onValueChange = { onSpeed(it.toInt()) },
                onValueChangeFinished = { if (connected) onApply() },
                valueRange = -50f..100f,
                steps = 14,
                colors = SliderDefaults.colors(
                    thumbColor = palette.accent,
                    activeTrackColor = palette.accent,
                    inactiveTrackColor = palette.cardSoft,
                ),
            )
            if (connected) {
                Text(stringResource(R.string.voice_settings_applied), color = palette.online, fontSize = 12.sp)
            }
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = palette.accentDeep,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.voice_settings_done), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun statusText(state: VoiceChatUiState, voicePhase: VoicePhase): String = when {
    state.connecting -> stringResource(R.string.voice_connecting)
    state.holding || voicePhase == VoicePhase.Listening -> stringResource(R.string.voice_listening)
    state.speaking -> stringResource(R.string.voice_speaking)
    state.fallback -> stringResource(R.string.voice_status_fallback)
    else -> stringResource(R.string.voice_status_connected_hint)
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
