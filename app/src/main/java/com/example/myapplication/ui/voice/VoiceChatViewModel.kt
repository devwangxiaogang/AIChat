package com.example.myapplication.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.SpeechReader
import com.example.myapplication.data.remote.DoubaoAsrClient
import com.example.myapplication.data.remote.DoubaoVoiceChatClient
import com.example.myapplication.data.remote.VoiceChatEvent
import com.example.myapplication.data.remote.VoiceSession
import com.example.myapplication.domain.LlmProvider
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ChatRequest
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.Role
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.domain.model.WireMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceChatUiState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val holding: Boolean = false,
    val speaking: Boolean = false,
    val userText: String = "",
    val partialUserText: String = "",
    val assistantText: String = "",
    val error: String? = null,
    val voice: VoiceChoice = VoiceChoice.FEMALE,
    val speed: Int = 0,
    val durationSec: Int = 0,
    /** 实时语音不可用（未开通/无权限）时，自动切换到识别→对话→朗读的兜底链路。 */
    val fallback: Boolean = false,
)

/**
 * 语音对话：
 * 1) 首选端到端实时语音（Seeduplex）：整段会话一个 WebSocket，按住说话上行 16k PCM；
 * 2) 若实时语音没有权限/未开通，自动降级为 豆包识别 → 对话模型 → 豆包朗读。
 */
@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val client: DoubaoVoiceChatClient,
    private val speechReader: SpeechReader,
    private val speechRecognizer: DoubaoAsrClient,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
) : ViewModel() {
    var state by mutableStateOf(VoiceChatUiState())
        private set

    private var session: VoiceSession? = null
    private var connectJob: Job? = null
    private var captureJob: Job? = null
    private var localJob: Job? = null
    private val player = PcmPlayer()

    private var turnBase = ""
    private var reply = ""
    private var tickerJob: Job? = null
    private val history = mutableListOf<WireMessage>()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.speech().liveVoice
            state = state.copy(voice = if (saved == VoiceChoice.CLONE) VoiceChoice.FEMALE else saved)
        }
    }

    fun startSession() {
        if (state.connecting || session != null) return
        state = state.copy(
            connecting = true,
            error = null,
            userText = "",
            partialUserText = "",
            assistantText = "",
            fallback = false,
        )
        turnBase = ""
        reply = ""
        history.clear()
        state = state.copy(durationSec = 0)
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                val speaker = (if (state.voice == VoiceChoice.CLONE) VoiceChoice.FEMALE else state.voice).ttsSpeakerId
                val created = client.connect(
                    apiKey = config.speechApiKey.trim(),
                    appId = config.speechAppId.trim(),
                    accessToken = config.speechAccessToken.trim(),
                    voice = speaker,
                    instructions = context.getString(R.string.assistant_prompt),
                    speed = state.speed,
                ) { event -> handle(event) }
                session = created
                state = state.copy(connecting = false, connected = true, fallback = false)
                startTicker()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // 实时语音不可用（未开通/无权限/网络）时，不挡用户：切到兜底链路
                val detail = error.message?.takeIf { it.isNotBlank() }
                state = state.copy(
                    connecting = false,
                    connected = true,
                    holding = false,
                    speaking = false,
                    fallback = true,
                    error = detail,
                )
                startTicker()
            }
        }
    }

    fun stopSession() {
        stopCapture()
        stopTicker()
        localJob?.cancel()
        localJob = null
        speechReader.stop()
        session?.close()
        session = null
        player.release()
        state = state.copy(connected = false, connecting = false, holding = false, speaking = false, durationSec = 0)
    }

    fun setVoice(voice: VoiceChoice) {
        state = state.copy(voice = if (voice == VoiceChoice.CLONE) VoiceChoice.FEMALE else voice)
    }

    fun setSpeed(speed: Int) {
        state = state.copy(speed = speed.coerceIn(-50, 100))
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = viewModelScope.launch {
            while (currentCoroutineContext().isActive) {
                kotlinx.coroutines.delay(1_000)
                if (!state.connected) break
                state = state.copy(durationSec = state.durationSec + 1)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    /** 按住：打断当前播报，开始上行音频（实时语音模式）。 */
    fun pressStart() {
        val current = session ?: return
        player.flush()
        current.cancelResponse()
        current.unmute()
        state = state.copy(holding = true, error = null)
        startCapture(current)
    }

    /** 松开：停止采集并让服务端立刻判停。 */
    fun pressEnd() {
        stopCapture()
        val current = session ?: return
        current.commitAudio()
        current.mute()
        state = state.copy(holding = false)
    }

    fun sendText(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        if (state.fallback) {
            sendLocal(content)
            return
        }
        val current = session
        if (current == null) {
            state = state.copy(error = context.getString(R.string.voice_need_start))
            return
        }
        turnBase = state.assistantText
        reply = ""
        state = state.copy(
            userText = appendLine(state.userText, content),
            partialUserText = "",
            error = null,
        )
        current.sendText(content)
    }

    /** 兜底链路：豆包识别（返回 WAV 文本）。 */
    fun recognize(wav: ByteArray, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                val apiKey = config.speechApiKey.trim()
                val appId = config.speechAppId.trim()
                val accessToken = config.speechAccessToken.trim()
                if (apiKey.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                    onFailure(context.getString(R.string.asr_key_missing))
                    return@launch
                }
                state = state.copy(partialUserText = context.getString(R.string.recognizing))
                val text = speechRecognizer.transcribe(
                    apiKey = apiKey,
                    wav = wav,
                    appId = appId,
                    accessToken = accessToken,
                )
                state = state.copy(partialUserText = "")
                onSuccess(text)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = state.copy(partialUserText = "")
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.asr_failed))
            }
        }
    }

    /** 兜底链路：文本 → 对话模型 → 朗读。 */
    private fun sendLocal(content: String) {
        localJob?.cancel()
        speechReader.stop()
        history += WireMessage(Role.USER, content)
        while (history.size > HISTORY_LIMIT) history.removeAt(0)
        val base = state.assistantText
        state = state.copy(
            userText = appendLine(state.userText, content),
            partialUserText = "",
            error = null,
            speaking = false,
        )
        reply = ""
        localJob = viewModelScope.launch {
            try {
                val selected = settingsRepository.startingModel()
                val config = settingsRepository.get(selected.provider)
                if (config.apiKey.isBlank()) {
                    state = state.copy(error = context.getString(R.string.error_api_key_invalid))
                    return@launch
                }
                val llm = providers[selected.provider]
                if (llm == null) {
                    state = state.copy(error = context.getString(R.string.voice_failed))
                    return@launch
                }
                llm.stream(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    request = ChatRequest(model = selected.model, messages = history.toList()),
                ).catch { error ->
                    state = state.copy(
                        error = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.voice_failed),
                    )
                }.collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> {
                            reply += event.text
                            val shown = if (base.isBlank()) reply else "$base\n$reply"
                            state = state.copy(assistantText = shown)
                        }
                        is StreamEvent.Failed -> state = state.copy(error = event.message)
                        else -> Unit
                    }
                }
                if (reply.isNotBlank()) {
                    history += WireMessage(Role.ASSISTANT, reply)
                    while (history.size > HISTORY_LIMIT) history.removeAt(0)
                    state = state.copy(speaking = true)
                    speechReader.toggle(viewModelScope, "voice-chat", reply) { failure ->
                        state = state.copy(error = failure.message, speaking = false)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = state.copy(
                    error = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.voice_failed),
                )
            }
        }
    }

    private fun handle(event: VoiceChatEvent) {
        when (event) {
            VoiceChatEvent.SessionStarted -> state = state.copy(connected = true, connecting = false)
            is VoiceChatEvent.UserText -> {
                if (event.final) {
                    turnBase = state.assistantText
                    reply = ""
                    state = state.copy(
                        userText = appendLine(state.userText, event.text),
                        partialUserText = "",
                    )
                } else {
                    state = state.copy(partialUserText = event.text)
                }
            }
            is VoiceChatEvent.AssistantText -> {
                reply += event.text
                val shown = if (turnBase.isBlank()) reply else "$turnBase\n$reply"
                state = state.copy(assistantText = shown)
            }
            is VoiceChatEvent.Audio -> {
                player.write(event.pcm)
                state = state.copy(speaking = true)
            }
            VoiceChatEvent.AssistantAudioDone -> state = state.copy(speaking = false)
            VoiceChatEvent.Ended -> {
                session = null
                stopTicker()
                state = state.copy(connected = false, holding = false, speaking = false)
            }
            is VoiceChatEvent.Failed -> {
                session = null
                stopTicker()
                state = state.copy(
                    connected = true,
                    connecting = false,
                    holding = false,
                    speaking = false,
                    fallback = true,
                    error = event.message,
                )
            }
        }
    }

    private fun startCapture(current: VoiceSession) {
        if (captureJob != null) return
        captureJob = viewModelScope.launch(Dispatchers.IO) {
            val min = AudioRecord.getMinBufferSize(
                INPUT_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                INPUT_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                min.coerceAtLeast(FRAME_BYTES * 4),
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                state = state.copy(error = context.getString(R.string.mic_unavailable), holding = false)
                return@launch
            }
            val echo = AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
            val noise = NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
            try {
                record.startRecording()
                val buffer = ByteArray(FRAME_BYTES)
                while (currentCoroutineContext().isActive) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0) current.appendAudio(buffer.copyOf(read))
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
    }

    private fun stopCapture() {
        captureJob?.cancel()
        captureJob = null
    }

    private fun appendLine(current: String, incoming: String): String {
        val line = incoming.trim()
        if (line.isEmpty()) return current
        return if (current.isEmpty()) line else "$current\n$line"
    }

    override fun onCleared() {
        stopCapture()
        localJob?.cancel()
        speechReader.stop()
        session?.close()
        session = null
        player.release()
    }

    /** 24kHz / 单声道 / 16bit 的 PCM 播放器，收到就写。 */
    private class PcmPlayer {
        private val lock = Any()
        private var track: AudioTrack? = null
        private var released = false

        fun write(pcm: ByteArray) {
            synchronized(lock) {
                if (released || pcm.isEmpty()) return
                val current = track ?: AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(OUTPUT_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(
                        AudioTrack.getMinBufferSize(
                            OUTPUT_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ).coerceAtLeast(OUTPUT_RATE),
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                    .also {
                        it.play()
                        track = it
                    }
                current.write(pcm, 0, pcm.size)
            }
        }

        fun flush() {
            synchronized(lock) {
                runCatching {
                    track?.pause()
                    track?.flush()
                    track?.play()
                }
            }
        }

        fun release() {
            synchronized(lock) {
                if (released) return
                released = true
                runCatching {
                    track?.pause()
                    track?.flush()
                    track?.stop()
                }
                runCatching { track?.release() }
                track = null
            }
        }
    }

    private companion object {
        const val INPUT_RATE = 16_000
        const val OUTPUT_RATE = 24_000
        const val HISTORY_LIMIT = 12

        /** 20ms 一包：16000 × 0.02 × 2 字节。 */
        const val FRAME_BYTES = 640
    }
}
