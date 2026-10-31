package com.example.myapplication.ui.translate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.SpeechReader
import com.example.myapplication.data.local.TranslationDao
import com.example.myapplication.data.local.TranslationEntity
import com.example.myapplication.data.remote.AstEvent
import com.example.myapplication.data.remote.DoubaoAsrClient
import com.example.myapplication.data.remote.DoubaoAstClient
import com.example.myapplication.data.remote.DoubaoTranslateClient
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.DEFAULT_TRANSLATION_MODEL
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SpeechPreferences
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.livePairError
import com.example.myapplication.ui.chat.VoicePhase
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TranslateTab { Text, Live }

enum class LivePhase { Idle, Connecting, Listening, Ended }

data class TranslateUiState(
    val tab: TranslateTab = TranslateTab.Live,
    val textSource: String = "zh",
    val textTarget: String = "en",
    val model: String = DEFAULT_TRANSLATION_MODEL,
    val input: String = "",
    val output: String = "",
    val translating: Boolean = false,
    val liveSourceLang: String = "zh",
    val liveTargetLang: String = "en",
    val liveVoice: VoiceChoice = VoiceChoice.FEMALE,
    val livePhase: LivePhase = LivePhase.Idle,
    val hearing: Boolean = true,
    val liveSaved: Boolean = false,
    val sourceCaption: String = "",
    val targetCaption: String = "",
    val sourcePartial: String = "",
    val targetPartial: String = "",
    val records: List<TranslationEntity> = emptyList(),
    val speakingKey: String? = null,
    val message: String? = null,
    val offerLocalFor: String? = null,
    val voicePhase: VoicePhase = VoicePhase.Idle,
)

@HiltViewModel
class TranslateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val translations: TranslationDao,
    private val translator: DoubaoTranslateClient,
    private val interpreter: DoubaoAstClient,
    private val speechRecognizer: DoubaoAsrClient,
    private val speechReader: SpeechReader,
) : ViewModel() {
    var state by mutableStateOf(TranslateUiState())
        private set

    private var liveJob: Job? = null
    private var liveAudio: Channel<ByteArray>? = null
    private var autoTranslateJob: Job? = null
    private var lastTranslatedInput: String? = null
    private val player = LivePlayer()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var previousAudioMode = AudioManager.MODE_NORMAL
    private var routeChanged = false

    init {
        viewModelScope.launch {
            settingsRepository.observeSpeech().collect { preferences ->
                state = state.copy(
                    textSource = preferences.textSource,
                    textTarget = preferences.textTarget,
                    model = preferences.translationModel,
                    liveSourceLang = preferences.liveSource,
                    liveTargetLang = preferences.liveTarget,
                    liveVoice = preferences.liveVoice,
                )
            }
        }
        viewModelScope.launch {
            translations.observeAll().collect { state = state.copy(records = it) }
        }
        viewModelScope.launch {
            speechReader.speaking.collect { state = state.copy(speakingKey = it) }
        }
    }

    fun selectTab(tab: TranslateTab) {
        if (state.livePhase.active) {
            state = state.copy(message = context.getString(R.string.end_live_first))
            return
        }
        state = state.copy(tab = tab, message = null)
    }

    fun leaveLive() {
        stopLive()
        if (speechReader.speaking.value?.startsWith("chat:") != true) speechReader.stop()
    }

    fun updateInput(value: String) {
        state = state.copy(input = value)
        scheduleAutoTranslate(value)
    }

    fun clearInput() {
        autoTranslateJob?.cancel()
        lastTranslatedInput = null
        state = state.copy(input = "", output = "", message = null, offerLocalFor = null)
    }

    fun setVoicePhase(phase: VoicePhase) {
        state = state.copy(voicePhase = phase)
    }

    fun denyTextMicrophone() {
        state = state.copy(message = context.getString(R.string.need_mic_text), voicePhase = VoicePhase.Idle)
    }

    fun reportVoiceError(message: String) {
        state = state.copy(message = message, voicePhase = VoicePhase.Idle)
    }

    /** 录音识别结果：填入原文、按文字脚本匹配语种，并立即翻译。 */
    fun applyVoiceInput(spoken: String) {
        val text = spoken.trim()
        if (text.isEmpty()) return
        autoTranslateJob?.cancel()
        lastTranslatedInput = null
        state = state.copy(
            input = text,
            message = null,
            offerLocalFor = null,
            voicePhase = VoicePhase.Idle,
        )
        matchLanguagesFromText(text)
        translate(save = true)
    }

    /** 按输入文本脚本自动匹配源/目标语；目标语与源语冲突时回落到中↔英。 */
    private fun matchLanguagesFromText(text: String) {
        val sample = text.trim()
        if (sample.isEmpty() || !hasLanguageSignal(sample)) return
        val detected = detectScriptLanguage(sample)
        val target = when {
            state.textTarget.isBlank() || state.textTarget == detected ->
                if (detected.startsWith("zh")) "en" else "zh"
            else -> state.textTarget
        }
        if (detected == state.textSource && target == state.textTarget) return
        state = state.copy(textSource = detected, textTarget = target, message = null)
        updateSpeech { it.copy(textSource = detected, textTarget = target) }
    }

    fun recognize(wav: ByteArray, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                val apiKey = config.speechApiKey.trim()
                val appId = config.speechAppId.trim()
                val accessToken = config.speechAccessToken.trim()
                if (apiKey.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                    onFailure(context.getString(R.string.asr_key_missing))
                } else {
                    // 优先自动语种；服务端若要求明确语种，再回退中文识别。
                    val spoken = runCatching {
                        speechRecognizer.transcribe(
                            apiKey = apiKey,
                            wav = wav,
                            language = "",
                            appId = appId,
                            accessToken = accessToken,
                        )
                    }.getOrElse {
                        speechRecognizer.transcribe(
                            apiKey = apiKey,
                            wav = wav,
                            language = "zh-CN",
                            appId = appId,
                            accessToken = accessToken,
                        )
                    }
                    onSuccess(spoken)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.asr_failed))
            }
        }
    }

    fun swapTextLanguages() {
        if (state.textSource.isEmpty()) return
        val source = state.textSource
        val target = state.textTarget
        val hasOutput = state.output.isNotBlank()
        autoTranslateJob?.cancel()
        lastTranslatedInput = null
        state = state.copy(
            input = if (hasOutput) state.output else state.input,
            output = if (hasOutput) state.input else state.output,
            message = null,
        )
        updateSpeech { it.copy(textSource = target, textTarget = source) }
    }

    fun selectTextLanguage(source: Boolean, code: String) {
        updateSpeech {
            if (source) it.copy(textSource = code) else it.copy(textTarget = code)
        }
    }

    fun selectModel(id: String) {
        updateSpeech { it.copy(translationModel = id.trim()) }
    }

    fun selectLiveLanguage(source: Boolean, code: String) {
        if (state.livePhase.active) return
        updateSpeech {
            if (source) it.copy(liveSource = code) else it.copy(liveTarget = code)
        }
    }

    fun selectLiveVoice(voice: VoiceChoice) {
        if (state.livePhase.active) return
        updateSpeech { it.copy(liveVoice = voice) }
    }

    fun swapLiveLanguages() {
        if (state.livePhase.active) return
        updateSpeech { current ->
            current.copy(liveSource = current.liveTarget, liveTarget = current.liveSource)
        }
    }

    fun translate(save: Boolean = true) {
        val text = state.input.trim()
        if (text.isEmpty() || state.translating) return
        matchLanguagesFromText(text)
        if (state.textTarget.isBlank() || state.textSource == state.textTarget) {
            state = state.copy(message = context.getString(R.string.choose_different_target))
            return
        }
        if (save && text == lastTranslatedInput && state.output.isNotBlank()) {
            viewModelScope.launch { saveTextRecord(text, state.output) }
            return
        }
        state = state.copy(translating = true, message = null, output = if (save) "" else state.output)
        viewModelScope.launch {
            try {
                val doubao = settingsRepository.get(ProviderId.DOUBAO)
                if (doubao.apiKey.isBlank()) error(context.getString(R.string.need_ark_key))
                val translated = translator.translate(
                    apiKey = doubao.apiKey,
                    baseUrl = doubao.baseUrl,
                    model = state.model,
                    text = text,
                    sourceLanguage = state.textSource,
                    targetLanguage = state.textTarget,
                )
                lastTranslatedInput = text
                if (save) saveTextRecord(text, translated)
                state = state.copy(output = translated, translating = false)
            } catch (cancelled: CancellationException) {
                state = state.copy(translating = false)
                throw cancelled
            } catch (error: Exception) {
                state = state.copy(
                    translating = false,
                    message = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.translate_failed),
                )
            }
        }
    }

    private fun scheduleAutoTranslate(value: String) {
        autoTranslateJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            lastTranslatedInput = null
            state = state.copy(output = "")
            return
        }
        if (trimmed == lastTranslatedInput && state.output.isNotBlank()) return
        autoTranslateJob = viewModelScope.launch {
            delay(AUTO_TRANSLATE_DELAY_MS)
            if (state.input.trim() != trimmed || state.translating) return@launch
            matchLanguagesFromText(trimmed)
            translate(save = false)
        }
    }

    private suspend fun saveTextRecord(text: String, translated: String) {
        translations.insert(
            TranslationEntity(
                kind = "TEXT",
                sourceLang = state.textSource,
                targetLang = state.textTarget,
                sourceText = text,
                targetText = translated,
                option = state.model,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    fun toggleLive() {
        if (state.livePhase.active) {
            stopLive()
            return
        }
        val blocked = livePairError(state.liveSourceLang, state.liveTargetLang, state.liveVoice)
        if (blocked != null) {
            state = state.copy(message = context.getString(blocked))
            return
        }
        liveJob = viewModelScope.launch {
            val config = settingsRepository.get(ProviderId.DOUBAO)
            val key = config.speechApiKey.trim()
            val appId = config.speechAppId.trim()
            val accessToken = config.speechAccessToken.trim()
            if (key.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                state = state.copy(message = context.getString(R.string.need_speech_key))
                return@launch
            }
            val audio = Channel<ByteArray>(capacity = 32)
            liveAudio = audio
            useSpeakerForTranslation()
            state = state.copy(
                livePhase = LivePhase.Connecting,
                hearing = true,
                liveSaved = false,
                message = null,
                sourceCaption = "",
                targetCaption = "",
                sourcePartial = "",
                targetPartial = "",
            )
            val opened = kotlinx.coroutines.CompletableDeferred<AudioRecord>()
            val capture = launch(Dispatchers.IO) { capture(audio, opened) }
            try {
                val record = opened.await()
                try {
                    interpreter.interpret(
                        apiKey = key,
                        sourceLanguage = state.liveSourceLang,
                        targetLanguage = state.liveTargetLang,
                        speakerId = state.liveVoice.speakerId,
                        audio = audio,
                        appId = appId,
                        accessToken = accessToken,
                    ) { event -> applyLive(event) }
                } finally {
                    audio.close()
                    runCatching { record.stop() }
                }
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                state = state.copy(message = context.getString(R.string.live_failed))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = state.copy(message = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.live_failed))
            } finally {
                audio.close()
                capture.join()
                if (liveAudio === audio) {
                    liveAudio = null
                    liveJob = null
                    finishLiveSession()
                }
            }
        }
    }

    fun stopLive() {
        if (!state.livePhase.active) {
            liveAudio?.close()
            return
        }
        val audio = liveAudio
        liveAudio = null
        audio?.close()
        val job = liveJob
        liveJob = null
        finishLiveSession()
        job?.cancel()
    }

    private fun finishLiveSession() {
        player.stop()
        val transcript = appendLine(state.sourceCaption, state.sourcePartial).trim()
        val translation = appendLine(state.targetCaption, state.targetPartial).trim()
        val keep = transcript.isNotBlank() || translation.isNotBlank()
        if (keep) saveLiveRecord(transcript, translation)
        state = state.copy(
            livePhase = if (keep) LivePhase.Ended else LivePhase.Idle,
            liveSaved = keep,
            hearing = true,
            sourcePartial = "",
            targetPartial = "",
        )
        restoreAudioRoute()
    }

    fun reuseTextRecord(record: TranslationEntity) {
        if (record.kind != "TEXT") return
        autoTranslateJob?.cancel()
        lastTranslatedInput = record.sourceText.trim()
        state = state.copy(
            tab = TranslateTab.Text,
            input = record.sourceText,
            output = record.targetText,
            message = null,
            offerLocalFor = null,
        )
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch { translations.delete(id) }
    }

    fun speak(key: String, text: String, forceLocal: Boolean = false) {
        speechReader.toggle(viewModelScope, key, text, forceLocal) { failure ->
            state = state.copy(
                message = failure.message,
                offerLocalFor = if (failure.canUseLocal) text else null,
            )
        }
    }

    fun stopRecordSpeech() {
        if (speechReader.speaking.value?.startsWith("record:") == true) speechReader.stop()
    }

    fun denyMicrophone() {
        state = state.copy(message = context.getString(R.string.need_mic_live))
    }

    fun dismissMessage() {
        state = state.copy(message = null, offerLocalFor = null)
    }

    override fun onCleared() {
        liveAudio?.close()
        liveJob?.cancel()
        restoreAudioRoute()
        if (speechReader.speaking.value?.startsWith("chat:") != true) speechReader.stop()
        player.release()
    }

    private fun applyLive(event: AstEvent) {
        androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
            applyLiveNow(event)
        }
    }

    private fun applyLiveNow(event: AstEvent) {
        state = when (event) {
            AstEvent.Started -> state.copy(livePhase = LivePhase.Listening, hearing = true)
            AstEvent.Finished -> state
            AstEvent.Quiet -> state.copy(hearing = false)
            is AstEvent.SourcePartial -> state.copy(sourcePartial = event.text, hearing = true)
            is AstEvent.SourceFinal -> state.copy(
                sourceCaption = commitLine(state.sourceCaption, event.text),
                sourcePartial = "",
                hearing = true,
            )
            is AstEvent.TargetPartial -> state.copy(targetPartial = event.text, hearing = true)
            is AstEvent.TargetFinal -> state.copy(
                targetCaption = commitLine(state.targetCaption, event.text),
                targetPartial = "",
                hearing = true,
            )
            is AstEvent.Audio -> {
                player.write(event.pcm)
                state.copy(hearing = true)
            }
        }
    }

    private fun saveLiveRecord(source: String, target: String) {
        viewModelScope.launch {
            translations.insert(
                TranslationEntity(
                    kind = "LIVE",
                    sourceLang = state.liveSourceLang,
                    targetLang = state.liveTargetLang,
                    sourceText = source,
                    targetText = target,
                    option = state.liveVoice.name,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun updateSpeech(transform: (SpeechPreferences) -> SpeechPreferences) {
        viewModelScope.launch {
            settingsRepository.editSpeech(transform)
        }
    }

    private suspend fun capture(audio: Channel<ByteArray>, opened: kotlinx.coroutines.CompletableDeferred<AudioRecord>) {
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min.coerceAtLeast(CHUNK_BYTES * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            opened.completeExceptionally(IllegalStateException(context.getString(R.string.mic_unavailable)))
            return
        }
        opened.complete(record)
        val echo = AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        val noise = NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
        player.useSession(record.audioSessionId)
        try {
            record.startRecording()
            val buffer = ByteArray(CHUNK_BYTES)
            while (currentCoroutineContext().isActive && !audio.isClosedForSend) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val outgoing = if (player.isAudible()) ByteArray(read) else buffer.copyOf(read)
                    try {
                        audio.send(outgoing)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        break
                    }
                }
            }
        } finally {
            runCatching {
                record.stop()
                record.release()
            }
            runCatching { echo?.release() }
            runCatching { noise?.release() }
        }
    }

    private fun useSpeakerForTranslation() {
        if (routeChanged) return
        previousAudioMode = audioManager.mode
        routeChanged = true
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) audioManager.setCommunicationDevice(speaker)
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        }
    }

    private fun restoreAudioRoute() {
        if (!routeChanged) return
        routeChanged = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        }
        audioManager.mode = previousAudioMode
    }

    private fun appendLine(current: String, line: String): String {
        val sentence = line.trim()
        if (sentence.isEmpty()) return current
        val base = current.trimEnd()
        if (base.endsWith(sentence)) return base
        return if (base.isEmpty()) sentence else "$base\n$sentence"
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val CHUNK_BYTES = 2560
        const val AUTO_TRANSLATE_DELAY_MS = 600L
    }
}

private val LivePhase.active: Boolean
    get() = this == LivePhase.Connecting || this == LivePhase.Listening

/** 按文字脚本粗判语种，用于录音/键盘输入后自动匹配翻译方向。 */
internal fun detectScriptLanguage(text: String): String {
    val sample = text.take(240)
    if (sample.isBlank()) return "zh"
    val hangul = sample.count { it in '\uac00'..'\ud7af' }
    val kana = sample.count { it in '\u3040'..'\u30ff' }
    val cjk = sample.count { it in '\u4e00'..'\u9fff' }
    val cyrillic = sample.count { it in '\u0400'..'\u04ff' }
    val arabic = sample.count { it in '\u0600'..'\u06ff' }
    val thai = sample.count { it in '\u0e00'..'\u0e7f' }
    val letters = sample.count { it.isLetter() }.coerceAtLeast(1)
    return when {
        hangul * 5 >= letters -> "ko"
        kana >= 2 -> "ja"
        arabic * 5 >= letters -> "ar"
        thai * 5 >= letters -> "th"
        cyrillic * 5 >= letters -> "ru"
        cjk >= 2 || cjk * 5 >= letters -> "zh"
        else -> "en"
    }
}

/** 过短或几乎没有字母/文字时不改语种，避免输入中途乱跳。 */
private fun hasLanguageSignal(text: String): Boolean {
    val letters = text.count { it.isLetter() }
    val cjk = text.count { it in '\u4e00'..'\u9fff' || it in '\u3040'..'\u30ff' || it in '\uac00'..'\ud7af' }
    return letters + cjk >= 2
}

private fun commitLine(existing: String, incoming: String): String {
    val line = incoming.trim()
    if (line.isBlank()) return existing
    val lines = existing.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    if (lines.isEmpty()) return line
    val last = lines.last()
    if (sameUtterance(last, line)) lines[lines.lastIndex] = line else lines += line
    return lines.joinToString("\n")
}

private fun sameUtterance(previous: String, next: String): Boolean {
    val left = previous.trimEnd { it in SENTENCE_END }
    val right = next.trimEnd { it in SENTENCE_END }
    if (left.isEmpty() || right.isEmpty()) return false
    if (left == right) return true
    if (minOf(left.length, right.length) < 4) return false
    return left.startsWith(right) || right.startsWith(left) || left.contains(right) || right.contains(left)
}

private const val SENTENCE_END = "。！？.!?，, "

/** 同传译文是 16 kHz、16 bit 的 PCM，收到就播放。播放期间记下结束时间，避免麦克风把译文再送回去。 */
private class LivePlayer {
    private var track: AudioTrack? = null
    private var released = false
    private var sessionId = AudioManager.AUDIO_SESSION_ID_GENERATE
    @Volatile private var audibleUntil = 0L

    fun useSession(id: Int) {
        synchronized(this) {
            if (sessionId == id) return
            sessionId = id
            track?.release()
            track = null
        }
    }

    fun isAudible(): Boolean = SystemClock.elapsedRealtime() < audibleUntil

    fun write(pcm: ByteArray) {
        synchronized(this) {
            if (released || pcm.isEmpty()) return
            val current = track ?: AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16_000)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(
                    AudioTrack.getMinBufferSize(
                        16_000,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    ).coerceAtLeast(16_000),
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setSessionId(sessionId)
                .build()
                .also {
                    it.play()
                    track = it
                }
            current.write(pcm, 0, pcm.size)
            val durationMs = pcm.size * 1000L / (16_000 * 2)
            val now = SystemClock.elapsedRealtime()
            audibleUntil = maxOf(audibleUntil, now) + durationMs + PLAYBACK_TAIL_MS
        }
    }

    fun stop() {
        synchronized(this) {
            audibleUntil = 0L
            pauseTrack()
            track?.release()
            track = null
        }
    }

    fun release() {
        synchronized(this) {
            if (released) return
            released = true
            audibleUntil = 0L
            pauseTrack()
            track?.release()
            track = null
        }
    }

    private fun pauseTrack() {
        runCatching {
            track?.pause()
            track?.flush()
        }
    }

    private companion object {
        const val PLAYBACK_TAIL_MS = 350L
    }
}
