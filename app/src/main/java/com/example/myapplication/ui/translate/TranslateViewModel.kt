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
import com.example.myapplication.data.remote.DoubaoAstClient
import com.example.myapplication.data.remote.DoubaoTranslateClient
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.DEFAULT_TRANSLATION_MODEL
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SpeechPreferences
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.livePairError
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
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
)

@HiltViewModel
class TranslateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val translations: TranslationDao,
    private val translator: DoubaoTranslateClient,
    private val interpreter: DoubaoAstClient,
    private val speechReader: SpeechReader,
) : ViewModel() {
    var state by mutableStateOf(TranslateUiState())
        private set

    private var liveJob: Job? = null
    private var liveAudio: Channel<ByteArray>? = null
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
        if (tab != TranslateTab.Live) stopLive()
        state = state.copy(tab = tab, message = null)
    }

    fun leaveLive() {
        stopLive()
        if (speechReader.speaking.value?.startsWith("chat:") != true) speechReader.stop()
    }

    fun updateInput(value: String) {
        state = state.copy(input = value)
    }

    fun swapTextLanguages() {
        if (state.textSource.isEmpty()) return
        updateSpeech { it.copy(textSource = state.textTarget, textTarget = state.textSource) }
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

    fun translate() {
        val text = state.input.trim()
        if (text.isEmpty() || state.translating) return
        if (state.textTarget.isBlank() || state.textSource == state.textTarget) {
            state = state.copy(message = context.getString(R.string.choose_different_target))
            return
        }
        state = state.copy(translating = true, message = null, output = "")
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
            val key = settingsRepository.get(ProviderId.DOUBAO).speechApiKey.trim()
            if (key.isEmpty()) {
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
    }
}

private val LivePhase.active: Boolean
    get() = this == LivePhase.Connecting || this == LivePhase.Listening

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
