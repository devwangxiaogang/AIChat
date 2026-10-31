package com.example.myapplication.ui.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.remote.DoubaoVoiceChatClient
import com.example.myapplication.data.remote.VoiceChatEvent
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.VoiceChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceChatUiState(
    val running: Boolean = false,
    val connecting: Boolean = false,
    val userText: String = "",
    val userPartial: String = "",
    val assistantText: String = "",
    val error: String? = null,
    val speaking: Boolean = false,
)

@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val client: DoubaoVoiceChatClient,
) : ViewModel() {
    private val _state = MutableStateFlow(VoiceChatUiState())
    val state: StateFlow<VoiceChatUiState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private val player = PcmPlayer()

    fun toggle() {
        if (_state.value.running) stop() else start()
    }

    fun start() {
        if (_state.value.running) return
        viewModelScope.launch {
            val config = settingsRepository.get(ProviderId.DOUBAO)
            val apiKey = config.speechApiKey.trim()
            val appId = config.speechAppId.trim()
            val accessToken = config.speechAccessToken.trim()
            if (apiKey.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                _state.update { it.copy(error = context.getString(R.string.need_speech_key)) }
                return@launch
            }
            val voice = settingsRepository.speech().liveVoice
            val speaker = (if (voice == VoiceChoice.CLONE) VoiceChoice.FEMALE else voice).speakerId
                ?: VoiceChoice.FEMALE.speakerId
                ?: "zh_female_vv_uranus_bigtts"
            val channel = Channel<ByteArray>(capacity = 32)
            _state.update {
                it.copy(
                    running = true,
                    connecting = true,
                    error = null,
                    userText = "",
                    userPartial = "",
                    assistantText = "",
                )
            }
            sessionJob = viewModelScope.launch(Dispatchers.IO) {
                val captureJob = launch(Dispatchers.IO) {
                    runCatching { capture(channel) }
                        .onFailure { channel.close() }
                }
                try {
                    client.runSession(
                        apiKey = apiKey,
                        appId = appId,
                        accessToken = accessToken,
                        speaker = speaker,
                        botName = context.getString(R.string.voice_bot_name),
                        audio = channel,
                    ) { event -> handle(event) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            error = error.message?.takeIf { msg -> msg.isNotBlank() }
                                ?: context.getString(R.string.voice_failed),
                        )
                    }
                } finally {
                    channel.close()
                    captureJob.cancel()
                    player.release()
                    _state.update { it.copy(running = false, connecting = false, speaking = false) }
                }
            }
        }
    }

    fun stop() {
        sessionJob?.cancel()
        sessionJob = null
        player.release()
        _state.update { it.copy(running = false, connecting = false, speaking = false) }
    }

    private fun handle(event: VoiceChatEvent) {
        when (event) {
            VoiceChatEvent.Connected, VoiceChatEvent.SessionStarted -> {
                _state.update { it.copy(connecting = false) }
            }
            is VoiceChatEvent.UserText -> {
                if (event.final) {
                    _state.update {
                        it.copy(
                            userText = appendLine(it.userText, event.text),
                            userPartial = "",
                        )
                    }
                } else {
                    _state.update { it.copy(userPartial = event.text) }
                }
            }
            is VoiceChatEvent.AssistantText -> {
                _state.update { it.copy(assistantText = appendLine(it.assistantText, event.text)) }
            }
            is VoiceChatEvent.Audio -> {
                player.write(event.pcm)
                _state.update { it.copy(speaking = true) }
            }
            VoiceChatEvent.UserSpeaking -> {
                // 用户插话：立刻停止本地播报，交给服务端重新生成。
                player.flush()
                _state.update { it.copy(speaking = false, userPartial = "") }
            }
            VoiceChatEvent.Ended -> {
                _state.update { it.copy(running = false, connecting = false, speaking = false) }
            }
            is VoiceChatEvent.Failed -> {
                _state.update {
                    it.copy(running = false, connecting = false, speaking = false, error = event.message)
                }
            }
        }
    }

    private fun appendLine(current: String, incoming: String): String {
        val line = incoming.trim()
        if (line.isEmpty()) return current
        return if (current.isEmpty()) line else "$current\n$line"
    }

    private suspend fun capture(channel: Channel<ByteArray>) {
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
            min.coerceAtLeast(FRAME_BYTES * 2),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            _state.update { it.copy(error = context.getString(R.string.mic_unavailable)) }
            return
        }
        val echo = AcousticEchoCanceler.create(record.audioSessionId)?.also { it.enabled = true }
        val noise = NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }
        try {
            record.startRecording()
            val buffer = ByteArray(FRAME_BYTES)
            while (currentCoroutineContext().isActive && !channel.isClosedForSend) {
                val read = record.read(buffer, 0, buffer.size)
                if (read > 0) {
                    val chunk = buffer.copyOf(read)
                    try {
                        channel.send(chunk)
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

    override fun onCleared() {
        sessionJob?.cancel()
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
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build(),
                    )
                    .setBufferSizeInBytes(
                        AudioTrack.getMinBufferSize(
                            SAMPLE_RATE,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                        ).coerceAtLeast(SAMPLE_RATE),
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
        const val SAMPLE_RATE = 16_000
        /** 20ms 一帧：16000 × 0.02 × 2 字节。 */
        const val FRAME_BYTES = 640
    }
}
