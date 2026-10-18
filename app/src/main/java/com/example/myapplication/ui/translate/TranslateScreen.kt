package com.example.myapplication.ui.translate

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.data.local.TranslationEntity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.domain.model.SpeechLanguage
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.liveLanguages
import com.example.myapplication.domain.model.livePairError
import com.example.myapplication.domain.model.textLanguages
import com.example.myapplication.domain.model.translationModels
import com.example.myapplication.ui.languageLabel
import com.example.myapplication.ui.shortLanguageLabel
import com.example.myapplication.ui.storedVoiceLabel
import com.example.myapplication.ui.translationModelLabel
import com.example.myapplication.ui.shell.AppSection
import com.example.myapplication.ui.shell.SectionScaffold
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val recordKind = if (state.tab == TranslateTab.Live) "LIVE" else "TEXT"
    val tabRecords = state.records.filter { it.kind == recordKind }
    LaunchedEffect(state.tab) { browse = null }
    LaunchedEffect(state.livePhase) {
        if (state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening) browse = null
    }
    BackHandler(enabled = browse != null) {
        browse = when (val current = browse) {
            is RecordBrowse.Detail -> if (current.fromAll) RecordBrowse.All else null
            else -> null
        }
    }
    val openRecordId = (browse as? RecordBrowse.Detail)?.id
    DisposableEffect(openRecordId) {
        onDispose { if (openRecordId != null) viewModel.stopRecordSpeech() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleLive() else viewModel.denyMicrophone()
    }
    val view = LocalView.current
    DisposableEffect(state.livePhase) {
        val window = view.context.findActivity().window
        val awake = state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening
        if (awake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.leaveLive() }
    }
    SectionScaffold(
        section = AppSection.Translate,
        title = stringResource(R.string.section_translate),
        onNavigate = { section ->
            if (section != AppSection.Translate) viewModel.leaveLive()
            onNavigate(section)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = if (state.tab == TranslateTab.Live) 0 else 1) {
                Tab(selected = state.tab == TranslateTab.Live, onClick = { viewModel.selectTab(TranslateTab.Live) }, text = { Text(stringResource(R.string.tab_live)) })
                Tab(selected = state.tab == TranslateTab.Text, onClick = { viewModel.selectTab(TranslateTab.Text) }, text = { Text(stringResource(R.string.tab_text)) })
            }
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
                    modifier = Modifier.weight(1f),
                )
            } else if (state.tab == TranslateTab.Live) {
                LiveStage(
                    state = state,
                    records = tabRecords,
                    onPickSource = { picking = PickTarget.LiveSource },
                    onPickTarget = { picking = PickTarget.LiveTarget },
                    onVoice = viewModel::selectLiveVoice,
                    onSwap = viewModel::swapLiveLanguages,
                    onToggle = {
                        if (state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening) {
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
                    onOpenAll = { browse = RecordBrowse.All },
                )
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        TextPane(
                            state = state,
                            onPickSource = { picking = PickTarget.TextSource },
                            onPickTarget = { picking = PickTarget.TextTarget },
                            onSwap = viewModel::swapTextLanguages,
                            onPickModel = { picking = PickTarget.Model },
                            onInput = viewModel::updateInput,
                            onTranslate = viewModel::translate,
                            onCopy = {
                                scope.launch {
                                    clipboard.setClipEntry(ClipData.newPlainText(clipLabel, state.output).toClipEntry())
                                }
                            },
                            onSpeak = { viewModel.speak("text-output", state.output) },
                        )
                        state.message?.let { message ->
                            Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            if (state.offerLocalFor != null) {
                                TextButton(onClick = {
                                    viewModel.speak("local-once", state.offerLocalFor, forceLocal = true)
                                    viewModel.dismissMessage()
                                }) { Text(stringResource(R.string.use_local_once)) }
                            }
                        }
                    }
                    HistoryPreview(
                        records = tabRecords,
                        onOpen = { record -> browse = RecordBrowse.Detail(record.id, fromAll = false) },
                        onOpenAll = { browse = RecordBrowse.All },
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
private fun androidx.compose.foundation.layout.ColumnScope.LiveStage(
    state: TranslateUiState,
    records: List<TranslationEntity>,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onVoice: (VoiceChoice) -> Unit,
    onSwap: () -> Unit,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onSpeak: (String) -> Unit,
    onOpenRecord: (TranslationEntity) -> Unit,
    onOpenAll: () -> Unit,
) {
    val active = state.livePhase == LivePhase.Connecting || state.livePhase == LivePhase.Listening
    val blocked = livePairError(state.liveSourceLang, state.liveTargetLang, state.liveVoice)
    val scroll = rememberScrollState()
    LaunchedEffect(state.livePhase, state.sourceCaption, state.sourcePartial, state.targetCaption, state.targetPartial) {
        if (state.livePhase == LivePhase.Listening) scroll.animateScrollTo(scroll.maxValue)
        if (state.livePhase == LivePhase.Ended) scroll.scrollTo(0)
    }
    if (active) {
        Text(
            "${languageLabel(state.liveSourceLang, liveLanguages)} → ${languageLabel(state.liveTargetLang, liveLanguages)} · ${stringResource(state.liveVoice.labelRes)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            when {
                state.livePhase == LivePhase.Connecting -> stringResource(R.string.connecting)
                state.hearing -> stringResource(R.string.hearing)
                else -> stringResource(R.string.quiet)
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        LanguageRow(
            source = languageLabel(state.liveSourceLang, liveLanguages),
            target = languageLabel(state.liveTargetLang, liveLanguages),
            onSource = onPickSource,
            onTarget = onPickTarget,
            onSwap = onSwap,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(VoiceChoice.FEMALE, VoiceChoice.MALE, VoiceChoice.CLONE).forEach { voice ->
                FilterChip(
                    selected = state.liveVoice == voice,
                    onClick = { onVoice(voice) },
                    label = { Text(stringResource(voice.labelRes)) },
                )
            }
        }
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
        if (state.sourceCaption.isBlank() && state.targetCaption.isBlank() && state.sourcePartial.isBlank() && state.targetPartial.isBlank()) {
            Text(
                stringResource(R.string.live_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CaptionCard(stringResource(R.string.source_label), state.sourceCaption, state.sourcePartial, compact = true)
            CaptionCard(stringResource(R.string.target_label), state.targetCaption, state.targetPartial, compact = false)
            if (state.livePhase == LivePhase.Ended && state.liveSaved) {
                Text(stringResource(R.string.saved), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
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
    if (!active) {
        HistoryPreview(records = records, onOpen = onOpenRecord, onOpenAll = onOpenAll)
    }
    blocked?.let {
        Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
    state.message?.let { message ->
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = onToggle, enabled = blocked == null || active, modifier = Modifier.fillMaxWidth()) {
        Text(
            when (state.livePhase) {
                LivePhase.Connecting -> stringResource(R.string.connecting)
                LivePhase.Listening -> stringResource(R.string.end_live)
                else -> stringResource(R.string.start_live)
            },
        )
    }
}

private sealed interface RecordBrowse {
    data object All : RecordBrowse
    data class Detail(val id: Long, val fromAll: Boolean) : RecordBrowse
}

@Composable
private fun HistoryPreview(
    records: List<TranslationEntity>,
    onOpen: (TranslationEntity) -> Unit,
    onOpenAll: () -> Unit,
) {
    if (records.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = onOpenAll) { Text(stringResource(R.string.see_all)) }
    }
    records.take(3).forEach { record ->
        RecordSummary(record, onOpen)
    }
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
                TextButton(onClick = onCopy, enabled = record.targetText.isNotBlank()) { Text(stringResource(R.string.copy_translation)) }
                if (record.targetText.isNotBlank()) {
                    TextButton(onClick = onSpeak) { Text(stringResource(if (speaking) R.string.stop_reading else R.string.read_translation)) }
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
        modifier = Modifier.fillMaxWidth().clickable { onOpen(record) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    record.targetText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank { stringResource(R.string.no_target) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$pair · ${record.sourceText.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank { stringResource(R.string.no_source) }}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                recordWhen(record.createdAt, withClock = false),
                modifier = Modifier.padding(start = 12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun TextPane(
    state: TranslateUiState,
    onPickSource: () -> Unit,
    onPickTarget: () -> Unit,
    onSwap: () -> Unit,
    onPickModel: () -> Unit,
    onInput: (String) -> Unit,
    onTranslate: () -> Unit,
    onCopy: () -> Unit,
    onSpeak: () -> Unit,
) {
    LanguageRow(
        source = languageLabel(state.textSource, textLanguages),
        target = languageLabel(state.textTarget, textLanguages),
        onSource = onPickSource,
        onTarget = onPickTarget,
        onSwap = onSwap,
    )
    Surface(
        modifier = Modifier.clickable(onClick = onPickModel),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            stringResource(R.string.doubao_model_chip, translationModelLabel(state.model)),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    OutlinedTextField(
        value = state.input,
        onValueChange = onInput,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.source_label)) },
        minLines = 4,
    )
    Button(onClick = onTranslate, enabled = !state.translating, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (state.translating) R.string.translating else R.string.translate))
    }
    if (state.output.isNotBlank()) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(state.output, style = MaterialTheme.typography.bodyLarge)
                Row(modifier = Modifier.align(Alignment.End)) {
                    IconButton(onClick = onCopy) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.copy_translation))
                    }
                    IconButton(onClick = onSpeak) {
                        Icon(
                            if (state.speakingKey == "text-output") Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = stringResource(R.string.read_translation),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    source: String,
    target: String,
    onSource: () -> Unit,
    onTarget: () -> Unit,
    onSwap: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LanguageChip(source, onSource, Modifier.weight(1f))
        if (onSwap != null) {
            IconButton(onClick = onSwap) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.swap_languages))
            }
        }
        LanguageChip(target, onTarget, Modifier.weight(1f))
    }
}

@Composable
private fun LanguageChip(label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
    }
}

@Composable
private fun CaptionCard(title: String, committed: String, partial: String, compact: Boolean) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (committed.isNotBlank()) {
                Text(committed, modifier = Modifier.padding(top = 8.dp), style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
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

private enum class PickTarget { TextSource, TextTarget, LiveSource, LiveTarget, Model }

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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (target == PickTarget.Model) {
                Text(stringResource(R.string.translation_model), style = MaterialTheme.typography.titleMedium)
                translationModels.forEach { option ->
                    Text(
                        stringResource(option.labelRes),
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.selectModel(option.id)
                            onDismiss()
                        }.padding(vertical = 12.dp),
                    )
                    Text(option.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = custom, onValueChange = { custom = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.other_model)) })
                Button(
                    onClick = {
                        viewModel.selectModel(custom)
                        onDismiss()
                    },
                    enabled = custom.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.use_this_id)) }
            } else {
                val source = target == PickTarget.TextSource || target == PickTarget.LiveSource
                val languages = when (target) {
                    PickTarget.TextSource -> listOf(SpeechLanguage("", R.string.lang_auto)) + textLanguages
                    PickTarget.TextTarget -> textLanguages
                    else -> liveLanguages.filter { language ->
                        source || (language.code != "yue-CN" && language.code != "sh-CN")
                    }
                }
                languages.forEach { language ->
                    Text(
                        stringResource(language.labelRes),
                        modifier = Modifier.fillMaxWidth().clickable {
                            when (target) {
                                PickTarget.TextSource, PickTarget.TextTarget ->
                                    viewModel.selectTextLanguage(source, language.code)
                                else -> viewModel.selectLiveLanguage(source, language.code)
                            }
                            onDismiss()
                        }.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("找不到页面窗口")
}
