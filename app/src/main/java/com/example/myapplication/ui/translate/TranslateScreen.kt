package com.example.myapplication.ui.translate

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.R
import com.example.myapplication.data.local.TranslationEntity
import com.example.myapplication.domain.model.SpeechLanguage
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.liveLanguages
import com.example.myapplication.domain.model.livePairError
import com.example.myapplication.domain.model.textLanguages
import com.example.myapplication.domain.model.translationModels
import com.example.myapplication.ui.chat.SpeechInput
import com.example.myapplication.ui.chat.VoicePhase
import com.example.myapplication.ui.languageLabel
import com.example.myapplication.ui.shell.AppSection
import com.example.myapplication.ui.shell.SectionScaffold
import com.example.myapplication.ui.shortLanguageLabel
import com.example.myapplication.ui.storedVoiceLabel
import com.example.myapplication.ui.translationModelLabel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
    onNavigate: (AppSection) -> Unit,
    viewModel: TranslateViewModel = hiltViewModel(),
) {
    val state = viewModel.state
    val clipboard = LocalClipboard.current
    val clipLabel = stringResource(R.string.clip_translation)
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf<PickTarget?>(null) }
    var browse by remember { mutableStateOf<RecordBrowse?>(null) }
    val liveActive = state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening
    val recordKind = if (state.tab == TranslateTab.Live) "LIVE" else "TEXT"
    val tabRecords = state.records.filter { it.kind == recordKind }
    LaunchedEffect(state.tab) { browse = null }
    LaunchedEffect(state.livePhase) {
        if (liveActive) browse = null
    }
    BackHandler(enabled = browse != null || liveActive) {
        when {
            browse != null -> {
                browse = when (val current = browse) {
                    is RecordBrowse.Detail -> if (current.fromAll) RecordBrowse.All else null
                    else -> null
                }
            }
            liveActive -> viewModel.stopLive()
        }
    }
    val openRecordId = (browse as? RecordBrowse.Detail)?.id
    DisposableEffect(openRecordId) {
        onDispose { if (openRecordId != null) viewModel.stopRecordSpeech() }
    }
    var pendingTextMic by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        when {
            !granted && pendingTextMic -> {
                pendingTextMic = false
                viewModel.denyTextMicrophone()
            }
            !granted -> viewModel.denyMicrophone()
            pendingTextMic -> pendingTextMic = false
            else -> viewModel.toggleLive()
        }
    }
    val view = LocalView.current
    DisposableEffect(state.livePhase) {
        val window = view.context.findActivity().window
        if (liveActive) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.leaveLive() }
    }
    SectionScaffold(
        section = AppSection.Translate,
        title = stringResource(R.string.nav_translate),
        onNavigate = { section ->
            if (section != AppSection.Translate) viewModel.leaveLive()
            onNavigate(section)
        },
        actions = {
            if (browse == null) {
                IconButton(
                    onClick = { if (!liveActive) browse = RecordBrowse.All },
                    enabled = !liveActive,
                ) {
                    Icon(Icons.Filled.History, contentDescription = stringResource(R.string.history))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (browse == null) {
                TranslateModeSwitcher(
                    selected = state.tab,
                    onSelect = viewModel::selectTab,
                )
            }
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(150)),
            ) {
                if (browse != null) {
                    RecordBrowser(
                        browse = browse!!,
                        records = tabRecords,
                        speakingKey = state.speakingKey,
                        message = state.message,
                        offerLocalFor = state.offerLocalFor,
                        onBack = {
                            browse = when (val current = browse) {
                                is RecordBrowse.Detail -> if (current.fromAll) RecordBrowse.All else null
                                else -> null
                            }
                        },
                        onOpen = { record -> browse = RecordBrowse.Detail(record.id, fromAll = browse is RecordBrowse.All) },
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, text).toClipEntry())
                            }
                        },
                        onSpeak = { record -> viewModel.speak("record:${record.id}", record.targetText) },
                        onUseLocal = { text ->
                            viewModel.speak("local-once", text, forceLocal = true)
                            viewModel.dismissMessage()
                        },
                        onDelete = viewModel::deleteRecord,
                        onReuse = { record ->
                            viewModel.reuseTextRecord(record)
                            browse = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (state.tab == TranslateTab.Live) {
                    LiveStage(
                        state = state,
                        records = tabRecords,
                        onPickSource = { picking = PickTarget.LiveSource },
                        onPickTarget = { picking = PickTarget.LiveTarget },
                        onPickVoice = { picking = PickTarget.Voice },
                        onSwap = viewModel::swapLiveLanguages,
                        onToggle = {
                            if (liveActive) {
                                viewModel.stopLive()
                            } else {
                                permission.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onCopy = { text ->
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, text).toClipEntry())
                            }
                        },
                        onSpeak = { text -> viewModel.speak("live-caption", text) },
                        onOpenRecord = { record -> browse = RecordBrowse.Detail(record.id, fromAll = false) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    TextStage(
                        state = state,
                        records = tabRecords,
                        onPickSource = { picking = PickTarget.TextSource },
                        onPickTarget = { picking = PickTarget.TextTarget },
                        onSwap = viewModel::swapTextLanguages,
                        onPickModel = { picking = PickTarget.Model },
                        onInput = viewModel::updateInput,
                        onClear = viewModel::clearInput,
                        onTranslate = { viewModel.translate(save = true) },
                        onCopy = {
                            scope.launch {
                                clipboard.setClipEntry(ClipData.newPlainText(clipLabel, state.output).toClipEntry())
                            }
                        },
                        onSpeak = { viewModel.speak("text-output", state.output) },
                        onUseLocal = { text ->
                            viewModel.speak("local-once", text, forceLocal = true)
                            viewModel.dismissMessage()
                        },
                        onOpenRecord = { record -> browse = RecordBrowse.Detail(record.id, fromAll = false) },
                        onVoicePhase = viewModel::setVoicePhase,
                        onVoiceResult = viewModel::applyVoiceInput,
                        onVoiceError = viewModel::reportVoiceError,
                        recognize = viewModel::recognize,
                        onRequestMic = {
                            pendingTextMic = true
                            permission.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    picking?.let { target ->
        OptionSheet(target = target, state = state, onDismiss = { picking = null }, viewModel = viewModel)
    }
}

@Composable
private fun TranslateModeSwitcher(
    selected: TranslateTab,
    onSelect: (TranslateTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        Triple(TranslateTab.Live, R.string.tab_live, Icons.Filled.Mic),
        Triple(TranslateTab.Text, R.string.tab_text, Icons.AutoMirrored.Filled.Notes),
    )
    val selectedIndex = if (selected == TranslateTab.Live) 0 else 1
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        val segmentWidth = maxWidth / tabs.size
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            label = "modeIndicator",
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp)),
        )
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEach { (tab, labelRes, icon) ->
                ModeSegment(
                    label = stringResource(labelRes),
                    icon = icon,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun ModeSegment(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = color,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveStage(
    state: TranslateUiState,
    records: List<TranslationEntity>,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onPickVoice: () -> Unit,
    onSwap: () -> Unit,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onOpenRecord: (TranslationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening
    val blocked = livePairError(state.liveSourceLang, state.liveTargetLang, state.liveVoice)
    val scroll = rememberScrollState()
    val empty = state.sourceCaption.isBlank() && state.targetCaption.isBlank() &&
        state.sourcePartial.isBlank() && state.targetPartial.isBlank()
    LaunchedEffect(state.livePhase, state.sourceCaption, state.sourcePartial, state.targetCaption, state.targetPartial) {
        if (state.livePhase == LivePhase.Listening) scroll.animateScrollTo(scroll.maxValue)
        if (state.livePhase == LivePhase.Ended) scroll.scrollTo(0)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (active) {
            Text(
                "${languageLabel(state.liveSourceLang, liveLanguages)} → ${languageLabel(state.liveTargetLang, liveLanguages)} · ${stringResource(state.liveVoice.labelRes)}",
                style = MaterialTheme.typography.titleMedium,
            )
            LiveStatusRow(phase = state.livePhase, hearing = state.hearing)
        } else {
            LanguageRow(
                source = languageLabel(state.liveSourceLang, liveLanguages),
                target = languageLabel(state.liveTargetLang, liveLanguages),
                onSource = onPickSource,
                onTarget = onPickTarget,
                onSwap = onSwap,
            )
            SettingLink(
                label = stringResource(R.string.voice_label, stringResource(state.liveVoice.labelRes)),
                onClick = onPickVoice,
            )
            if (state.liveVoice == VoiceChoice.CLONE) {
                Text(
                    stringResource(R.string.clone_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (empty && !active) {
                LiveEmptyHint()
                LastRecordHint(records = records, onOpen = onOpenRecord)
            } else if (empty && active) {
                LiveEmptyHint(listening = true)
            } else {
                CaptionCard(stringResource(R.string.source_label), state.sourceCaption, state.sourcePartial, compact = true)
                CaptionCard(stringResource(R.string.target_label), state.targetCaption, state.targetPartial, compact = false)
                if (active) {
                    LiveWave()
                }
                if (state.livePhase == LivePhase.Ended && state.liveSaved) {
                    Text(
                        stringResource(R.string.saved),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(modifier = Modifier.align(Alignment.End)) {
                        IconButton(onClick = { onCopy(state.targetCaption) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_translation))
                        }
                        if (state.targetCaption.isNotBlank()) {
                            IconButton(onClick = { onSpeak(state.targetCaption) }) {
                                Icon(
                                    if (state.speakingKey == "live-caption") Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = stringResource(R.string.read_translation),
                                )
                            }
                        }
                    }
                }
            }
        }
        blocked?.let {
            Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        state.message?.let { message ->
            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        PrimaryActionButton(
            label = when (state.livePhase) {
                LivePhase.Connecting -> stringResource(R.string.connecting)
                LivePhase.Listening -> stringResource(R.string.end_live)
                else -> stringResource(R.string.start_live)
            },
            onClick = onToggle,
            enabled = blocked == null || active,
            stopStyle = active,
        )
    }
}

@Composable
private fun TextStage(
    state: TranslateUiState,
    records: List<TranslationEntity>,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onSwap: () -> Unit,
    onPickModel: () -> Unit,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onUseLocal: (String) -> Unit,
    onOpenRecord: (TranslationEntity) -> Unit,
    onVoicePhase: (VoicePhase) -> Unit,
    onVoiceResult: (String) -> Unit,
    onVoiceError: (String) -> Unit,
    recognize: (ByteArray, (String) -> Unit, (String) -> Unit) -> Unit,
    onRequestMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val latestVoiceResult by rememberUpdatedState(onVoiceResult)
    val latestVoicePhase by rememberUpdatedState(onVoicePhase)
    val latestVoiceError by rememberUpdatedState(onVoiceError)
    val latestRecognize by rememberUpdatedState(recognize)
    val speech = remember(context) {
        SpeechInput(
            context = context,
            onText = { latestVoiceResult(it) },
            onPhase = { latestVoicePhase(it) },
            onError = { latestVoiceError(it) },
            recognize = { wav, ok, fail -> latestRecognize(wav, ok, fail) },
        )
    }
    DisposableEffect(speech) {
        onDispose {
            speech.destroy()
            latestVoicePhase(VoicePhase.Idle)
        }
    }
    val listening = state.voicePhase == VoicePhase.Listening
    val recognizing = state.voicePhase == VoicePhase.Recognizing
    val busy = listening || recognizing || state.translating
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LanguageRow(
            source = languageLabel(state.textSource, textLanguages),
            target = languageLabel(state.textTarget, textLanguages),
            onSource = onPickSource,
            onTarget = onPickTarget,
            onSwap = onSwap,
        )
        SettingLink(
            label = stringResource(R.string.model_label, translationModelLabel(state.model)),
            onClick = onPickModel,
        )
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextSourceCard(
                input = state.input,
                voicePhase = state.voicePhase,
                onInput = onInput,
                onClear = onClear,
                onTranslate = onTranslate,
                enabled = state.voicePhase == VoicePhase.Idle,
            )
            TextTargetCard(
                output = state.output,
                translating = state.translating,
                speaking = state.speakingKey == "text-output",
                onCopy = onCopy,
                onSpeak = onSpeak,
            )
            state.message?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                if (state.offerLocalFor != null) {
                    TextButton(onClick = { onUseLocal(state.offerLocalFor) }) {
                        Text(stringResource(R.string.use_local_once))
                    }
                }
            }
            LastRecordHint(records = records, onOpen = onOpenRecord)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HoldToTalkButton(
                listening = listening,
                recognizing = recognizing,
                enabled = !state.translating,
                onRequestMic = onRequestMic,
                onStart = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    speech.start("")
                },
                onStop = { speech.stop() },
                modifier = Modifier.weight(1f),
            )
            val showTranslate = state.input.isNotBlank() || state.translating
            AnimatedVisibility(visible = showTranslate) {
                Button(
                    onClick = onTranslate,
                    enabled = state.input.isNotBlank() && !busy,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    if (state.translating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.translate), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextSourceCard(
    input: String,
    voicePhase: VoicePhase,
    onInput: (String) -> Unit,
    onClear: () -> Unit,
    onTranslate: () -> Unit,
    enabled: Boolean,
) {
    val listening = voicePhase == VoicePhase.Listening
    val recognizing = voicePhase == VoicePhase.Recognizing
    val border by animateColorAsState(
        targetValue = when {
            listening -> MaterialTheme.colorScheme.primary
            recognizing -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        },
        label = "sourceBorder",
    )
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(22.dp)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.source_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (recognizing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (input.isNotBlank() && enabled) {
                    IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.clear_input),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 112.dp)
                    .padding(top = 4.dp),
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onTranslate() }),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                when (voicePhase) {
                                    VoicePhase.Listening -> stringResource(R.string.listening_ellipsis)
                                    VoicePhase.Recognizing -> stringResource(R.string.recognizing)
                                    VoicePhase.Idle -> stringResource(R.string.text_input_hint)
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 26.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}

@Composable
private fun TextTargetCard(
    output: String,
    translating: Boolean,
    speaking: Boolean,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(22.dp)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.target_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                when {
                    translating -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    output.isNotBlank() -> {
                        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.copy_translation),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = onSpeak, modifier = Modifier.size(32.dp)) {
                            Icon(
                                if (speaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = stringResource(R.string.read_translation),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .padding(top = 4.dp),
            ) {
                when {
                    output.isNotBlank() -> {
                        Text(
                            output,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
                        )
                    }
                    translating -> {
                        Text(
                            stringResource(R.string.translating),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.output_placeholder),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HoldToTalkButton(
    listening: Boolean,
    recognizing: Boolean,
    enabled: Boolean,
    onRequestMic: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val active = listening || recognizing
    val label = when {
        recognizing -> stringResource(R.string.recognizing)
        listening -> stringResource(R.string.release_to_stop)
        else -> stringResource(R.string.hold_to_talk)
    }
    Surface(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(enabled, recognizing) {
                detectTapGestures(
                    onPress = {
                        if (!enabled || recognizing) return@detectTapGestures
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!granted) {
                            onRequestMic()
                            return@detectTapGestures
                        }
                        onStart()
                        tryAwaitRelease()
                        onStop()
                    },
                )
            },
        shape = RoundedCornerShape(28.dp),
        color = when {
            listening -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = when {
            listening -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        },
        shadowElevation = if (listening) 4.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (recognizing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveEmptyHint(listening: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (listening) R.string.hearing else R.string.live_hint_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!listening) {
                Text(
                    stringResource(R.string.live_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LiveStatusRow(phase: LivePhase, hearing: Boolean) {
    val listening = phase == LivePhase.Listening && hearing
    val pulse = if (listening) {
        val transition = rememberInfiniteTransition(label = "hear")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "hearAlpha",
        ).value
    } else {
        1f
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(pulse)
                .background(
                    color = when {
                        phase == LivePhase.Connecting -> MaterialTheme.colorScheme.outline
                        hearing -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = CircleShape,
                ),
        )
        Text(
            when {
                phase == LivePhase.Connecting -> stringResource(R.string.connecting)
                hearing -> stringResource(R.string.hearing)
                else -> stringResource(R.string.quiet)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LastRecordHint(records: List<TranslationEntity>, onOpen: (TranslationEntity) -> Unit) {
    val latest = records.firstOrNull() ?: return
    val preview = latest.targetText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
        .orEmpty()
        .ifBlank { stringResource(R.string.no_target) }
    Text(
        stringResource(R.string.last_record, preview),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(latest) }
            .padding(vertical = 4.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SettingLink(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(
            Icons.Filled.ExpandMore,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    stopStyle: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(28.dp),
        colors = if (stopStyle) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

private sealed interface RecordBrowse {
    data object All : RecordBrowse
    data class Detail(val id: Long, val fromAll: Boolean) : RecordBrowse
}

@Composable
private fun RecordBrowser(
    browse: RecordBrowse,
    records: List<TranslationEntity>,
    speakingKey: String?,
    message: String?,
    offerLocalFor: String?,
    onBack: () -> Unit,
    onOpen: (TranslationEntity) -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (TranslationEntity) -> Unit,
    onUseLocal: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onReuse: (TranslationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (browse) {
        RecordBrowse.All -> RecordListPage(records, onBack, onOpen, modifier)
        is RecordBrowse.Detail -> {
            val record = records.firstOrNull { it.id == browse.id }
            if (record == null) {
                LaunchedEffect(browse.id) { onBack() }
            } else {
                RecordDetail(
                    record = record,
                    speaking = speakingKey == "record:${record.id}",
                    message = message,
                    offerLocalFor = offerLocalFor,
                    onBack = onBack,
                    onCopy = { onCopy(record.targetText) },
                    onSpeak = { onSpeak(record) },
                    onUseLocal = onUseLocal,
                    onDelete = { onDelete(record.id) },
                    onReuse = { onReuse(record) },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun RecordListPage(
    records: List<TranslationEntity>,
    onBack: () -> Unit,
    onOpen: (TranslationEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val filtered = if (query.isBlank()) {
        records
    } else {
        records.filter { record ->
            record.sourceText.contains(query, ignoreCase = true) ||
                record.targetText.contains(query, ignoreCase = true)
        }
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium)
        }
        if (records.size > SEARCH_AFTER) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_history)) },
                singleLine = true,
            )
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    stringResource(if (query.isBlank()) R.string.no_history else R.string.no_search_hits),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            filtered.groupBy { recordDayLabel(it.createdAt) }.forEach { (day, items) ->
                Text(day, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                items.forEach { record -> RecordSummary(record, onOpen) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecordDetail(
    record: TranslationEntity,
    speaking: Boolean,
    message: String?,
    offerLocalFor: String?,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
    onUseLocal: (String) -> Unit,
    onDelete: () -> Unit,
    onReuse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirming by remember(record.id) { mutableStateOf(false) }
    val languages = if (record.kind == "LIVE") liveLanguages else textLanguages
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column {
                Text(recordWhen(record.createdAt, withClock = true), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${languageLabel(record.sourceLang, languages)} → ${languageLabel(record.targetLang, languages)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (record.kind == "LIVE") storedVoiceLabel(record.option) else translationModelLabel(record.option),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CaptionCard(stringResource(R.string.source_label), record.sourceText.ifBlank { stringResource(R.string.no_source) }, "", compact = true)
            CaptionCard(stringResource(R.string.target_label), record.targetText.ifBlank { stringResource(R.string.no_target) }, "", compact = false)
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            if (offerLocalFor != null) {
                TextButton(onClick = { onUseLocal(offerLocalFor) }) { Text(stringResource(R.string.use_local_once)) }
            }
        }
        if (confirming) {
            Text(stringResource(R.string.delete_record_confirm))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (record.kind == "TEXT") {
                    TextButton(onClick = onReuse) { Text(stringResource(R.string.fill_input)) }
                }
                TextButton(onClick = onCopy, enabled = record.targetText.isNotBlank()) {
                    Text(stringResource(R.string.copy_translation))
                }
                if (record.targetText.isNotBlank()) {
                    TextButton(onClick = onSpeak) {
                        Text(stringResource(if (speaking) R.string.stop_reading else R.string.read_translation))
                    }
                }
                TextButton(onClick = { confirming = true }) { Text(stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun RecordSummary(record: TranslationEntity, onOpen: (TranslationEntity) -> Unit) {
    val pair = "${shortLanguageLabel(record.sourceLang)} → ${shortLanguageLabel(record.targetLang)}"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onOpen(record) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            stringResource(if (record.kind == "LIVE") R.string.tab_live else R.string.tab_text),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        recordWhen(record.createdAt, withClock = false),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        pair,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    record.targetText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        .ifBlank { stringResource(R.string.no_target) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    record.sourceText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        .ifBlank { stringResource(R.string.no_source) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun recordWhen(createdAt: Long, withClock: Boolean): String {
    val locale = LocalConfiguration.current.locales[0]
    val record = Calendar.getInstance().apply { timeInMillis = createdAt }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }
    val clock = SimpleDateFormat("HH:mm", locale).format(Date(createdAt))
    val chinese = locale.language == "zh"
    return when {
        sameDay(record, today) -> clock
        sameDay(record, yesterday) -> if (withClock) stringResource(R.string.yesterday_time, clock) else stringResource(R.string.yesterday)
        withClock -> SimpleDateFormat(if (chinese) "MM-dd HH:mm" else "MMM d, HH:mm", locale).format(Date(createdAt))
        else -> SimpleDateFormat(if (chinese) "MM-dd" else "MMM d", locale).format(Date(createdAt))
    }
}

@Composable
private fun recordDayLabel(createdAt: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    val record = Calendar.getInstance().apply { timeInMillis = createdAt }
    val today = Calendar.getInstance()
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DATE, -1) }
    val chinese = locale.language == "zh"
    return when {
        sameDay(record, today) -> stringResource(R.string.today)
        sameDay(record, yesterday) -> stringResource(R.string.yesterday)
        record.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat(if (chinese) "M月d日" else "MMM d", locale).format(Date(createdAt))
        else -> SimpleDateFormat(if (chinese) "yyyy年M月d日" else "MMM d, yyyy", locale).format(Date(createdAt))
    }
}

private fun sameDay(left: Calendar, right: Calendar): Boolean =
    left.get(Calendar.YEAR) == right.get(Calendar.YEAR) &&
        left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR)

private const val SEARCH_AFTER = 6

@Composable
private fun LanguageRow(
    source: String,
    target: String,
    onSource: () -> Unit,
    onTarget: () -> Unit,
    onSwap: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageChip(source, onSource, Modifier.weight(1f))
            if (onSwap != null) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    IconButton(onClick = onSwap, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Filled.SwapHoriz,
                            contentDescription = stringResource(R.string.swap_languages),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            LanguageChip(target, onTarget, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LanguageChip(label: String, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CaptionCard(title: String, committed: String, partial: String, compact: Boolean) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (compact) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = if (compact) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            if (committed.isNotBlank()) {
                Text(
                    committed,
                    modifier = Modifier.padding(top = 8.dp),
                    style = if (compact) {
                        MaterialTheme.typography.bodyLarge
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                )
            }
            if (partial.isNotBlank()) {
                Text(
                    partial,
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

/** 同传时的动态声波。 */
@Composable
private fun LiveWave() {
    val transition = rememberInfiniteTransition(label = "liveWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "liveWavePhase",
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .padding(top = 4.dp),
    ) {
        val count = 32
        val gap = size.width / count
        for (i in 0 until count) {
            val wave = kotlin.math.abs(kotlin.math.sin(phase * 2f * Math.PI.toFloat() + i * 0.6f))
            val height = size.height * (0.2f + 0.8f * wave)
            drawRoundRect(
                color = Color(0xFF5B6CFF),
                topLeft = Offset(i * gap + gap * 0.2f, (size.height - height) / 2f),
                size = Size(gap * 0.6f, height),
                cornerRadius = CornerRadius(gap * 0.3f),
            )
        }
    }
}

private enum class PickTarget { TextSource, TextTarget, LiveSource, LiveTarget, Model, Voice }

private val preferredTextCodes = listOf("zh", "en", "ja", "ko")
private val preferredLiveCodes = listOf("zh", "en", "ja", "ko")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionSheet(
    target: PickTarget,
    state: TranslateUiState,
    viewModel: TranslateViewModel,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var custom by remember { mutableStateOf("") }
    var showCustom by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when (target) {
                PickTarget.Model -> {
                    Text(stringResource(R.string.translation_model), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    translationModels.forEach { option ->
                        OptionRow(
                            label = stringResource(option.labelRes),
                            detail = option.id,
                            selected = state.model == option.id,
                            onClick = {
                                viewModel.selectModel(option.id)
                                onDismiss()
                            },
                        )
                    }
                    TextButton(onClick = { showCustom = !showCustom }) {
                        Text(stringResource(R.string.other_model))
                    }
                    if (showCustom) {
                        Text(
                            stringResource(R.string.other_model_detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = custom,
                            onValueChange = { custom = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.other_model)) },
                        )
                        Button(
                            onClick = {
                                viewModel.selectModel(custom)
                                onDismiss()
                            },
                            enabled = custom.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.use_this_id)) }
                    }
                }
                PickTarget.Voice -> {
                    Text(stringResource(R.string.choose_voice), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    VoiceChoice.entries.forEach { voice ->
                        OptionRow(
                            label = stringResource(voice.labelRes),
                            detail = null,
                            selected = state.liveVoice == voice,
                            onClick = {
                                viewModel.selectLiveVoice(voice)
                                onDismiss()
                            },
                        )
                    }
                }
                else -> {
                    val source = target == PickTarget.TextSource || target == PickTarget.LiveSource
                    val languages = when (target) {
                        PickTarget.TextSource -> listOf(SpeechLanguage("", R.string.lang_auto)) + preferLanguages(textLanguages, preferredTextCodes)
                        PickTarget.TextTarget -> preferLanguages(textLanguages, preferredTextCodes)
                        else -> preferLanguages(
                            liveLanguages.filter { language ->
                                source || (language.code != "yue-CN" && language.code != "sh-CN")
                            },
                            preferredLiveCodes,
                        )
                    }
                    val selected = when (target) {
                        PickTarget.TextSource -> state.textSource
                        PickTarget.TextTarget -> state.textTarget
                        PickTarget.LiveSource -> state.liveSourceLang
                        else -> state.liveTargetLang
                    }
                    languages.forEach { language ->
                        OptionRow(
                            label = stringResource(language.labelRes),
                            detail = null,
                            selected = language.code == selected,
                            onClick = {
                                when (target) {
                                    PickTarget.TextSource, PickTarget.TextTarget ->
                                        viewModel.selectTextLanguage(source, language.code)
                                    else -> viewModel.selectLiveLanguage(source, language.code)
                                }
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun preferLanguages(languages: List<SpeechLanguage>, preferred: List<String>): List<SpeechLanguage> {
    val head = preferred.mapNotNull { code -> languages.firstOrNull { it.code == code } }
    val rest = languages.filter { it.code !in preferred }
    return head + rest
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error(getString(R.string.activity_missing))
}
