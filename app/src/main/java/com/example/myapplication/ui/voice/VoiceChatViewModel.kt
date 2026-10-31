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
import com.example.myapplication.data.remote.DoubaoVoiceChatClient
import com.example.myapplication.data.remote.VoiceChatEvent
import com.example.myapplication.data.remote.VoiceSession
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.VoiceChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
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
)

/**
 * 端到端实时语音（Seeduplex）：整段会话是一个 WebSocket，按住说话时上行 16k PCM，
 * 服务端直接返回模型语音（24k PCM）与识别/回复文本。
 */
@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val client: DoubaoVoiceChatClient,
) : ViewModel() {
    var state by mutableStateOf(VoiceChatUiState())
        private set

    private var session: VoiceSession? = null
    private var connectJob: Job? = null
    private var captureJob: Job? = null
    private val player = PcmPlayer()

    private var turnBase = ""
    private var reply = ""

    fun startSession() {
        if (state.connecting || session != null) return
        state = state.copy(connecting = true, error = null, userText = "", partialUserText = "", assistantText = "")
        turnBase = ""
        reply = ""
        connectJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                val voice = settingsRepository.speech().liveVoice
                val speaker = (if (voice == VoiceChoice.CLONE) VoiceChoice.FEMALE else voice).ttsSpeakerId
                val created = client.connect(
                    apiKey = config.speechApiKey.trim(),
                    appId = config.speechAppId.trim(),
                    accessToken = config.speechAccessToken.trim(),
                    voice = speaker,
                    instructions = context.getString(R.string.assistant_prompt),
                ) { event -> handle(event) }
                session = created
                state = state.copy(connecting = false, connected = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = state.copy(
                    connecting = false,
                    connected = false,
                    error = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.voice_failed),
                )
            }
        }
    }

    fun stopSession() {
        stopCapture()
        session?.close()
        session = null
        player.release()
        state = state.copy(connected = false, connecting = false, holding = false, speaking = false)
    }

    /** 按住：打断当前播报，开始上行音频。 */
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
                state = state.copy(connected = false, holding = false, speaking = false)
            }
            is VoiceChatEvent.Failed -> state = state.copy(
                connected = false,
                connecting = false,
                holding = false,
                speaking = false,
                error = event.message,
            )
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
        /** 20ms 一包：16000 × 0.02 × 2 字节。 */
        const val FRAME_BYTES = 640
    }
}
