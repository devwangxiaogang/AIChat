package com.example.myapplication.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.example.myapplication.R
import com.example.myapplication.data.remote.DoubaoTtsClient
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.TtsEngine
import com.example.myapplication.domain.model.VoiceChoice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SpeakFailure(val message: String, val canUseLocal: Boolean)

/**
 * 朗读文字。默认走豆包语音合成，设置里可以改成本机朗读。
 * 同声翻译进行中的实时播报不经过这里。
 */
@Singleton
class SpeechReader @Inject constructor(
    @ApplicationContext context: Context,
    private val settingsRepository: SettingsRepository,
    private val ttsClient: DoubaoTtsClient,
) {
    private val context = context
    private val local = LocalSpeaker(context)
    private val speakingState = MutableStateFlow<String?>(null)
    val speaking: StateFlow<String?> = speakingState.asStateFlow()
    private var job: Job? = null
    private var player: PcmPlayer? = null

    fun toggle(
        scope: CoroutineScope,
        key: String,
        text: String,
        forceLocal: Boolean = false,
        onFailure: (SpeakFailure) -> Unit = {},
    ) {
        if (speakingState.value == key) {
            stop()
            return
        }
        stop()
        val content = text.trim()
        if (content.isEmpty()) return
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            speakingState.value = key
            try {
                val preferences = settingsRepository.speech()
                val useLocal = forceLocal || preferences.engine == TtsEngine.LOCAL
                if (useLocal) {
                    local.speak(content)
                } else {
                    val config = settingsRepository.get(ProviderId.DOUBAO)
                    val apiKey = config.speechApiKey.trim()
                    val appId = config.speechAppId.trim()
                    val accessToken = config.speechAccessToken.trim()
                    if (apiKey.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                        onFailure(SpeakFailure(context.getString(R.string.need_speech_key_or_local), canUseLocal = true))
                        return@launch
                    }
                    playDoubao(apiKey, appId, accessToken, preferences.speaker, content)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(
                    SpeakFailure(
                        error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.tts_failed),
                        canUseLocal = true,
                    ),
                )
            } finally {
                synchronized(this@SpeechReader) {
                    if (job === launched) {
                        job = null
                        val current = player
                        player = null
                        current?.release()
                    }
                }
                if (speakingState.value == key) speakingState.value = null
            }
        }
        job = launched
        launched.start()
    }

    fun stop() {
        val cancelled = synchronized(this) {
            val currentJob = job
            job = null
            val current = player
            player = null
            current?.release()
            currentJob
        }
        cancelled?.cancel()
        local.stop()
        speakingState.value = null
    }

    private suspend fun playDoubao(
        apiKey: String,
        appId: String,
        accessToken: String,
        speaker: VoiceChoice,
        text: String,
    ) {
        val voice = (if (speaker == VoiceChoice.CLONE) VoiceChoice.FEMALE else speaker).ttsSpeakerId
        val playback = PcmPlayer(SAMPLE_RATE)
        synchronized(this) { player = playback }
        playback.start()
        for (chunk in speechChunks(text, DOUBAO_CHUNK)) {
            ttsClient.synthesize(
                apiKey = apiKey,
                speaker = voice,
                text = chunk,
                appId = appId,
                accessToken = accessToken,
            ) { pcm -> playback.write(pcm) }
        }
    }

    private fun speechChunks(text: String, limit: Int): List<String> {
        if (text.length <= limit) return listOf(text)
        val parts = mutableListOf<String>()
        var rest = text
        while (rest.isNotEmpty()) {
            if (rest.length <= limit) {
                parts += rest
                break
            }
            val window = rest.substring(0, limit)
            val cut = listOf('。', '！', '？', '\n', '.', '!', '?').maxOf { window.lastIndexOf(it) }
            val end = if (cut >= 20) cut + 1 else limit
            parts += rest.substring(0, end).trim()
            rest = rest.substring(end).trimStart()
        }
        return parts.filter { it.isNotBlank() }
    }

    private class PcmPlayer(sampleRate: Int) {
        private val lock = Any()
        private var closed = false
        private val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(
                AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                ).coerceAtLeast(sampleRate),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        fun start() {
            track.play()
        }

        fun write(pcm: ByteArray) {
            synchronized(lock) {
                if (closed) return
                var offset = 0
                while (offset < pcm.size) {
                    val written = track.write(pcm, offset, pcm.size - offset)
                    if (written <= 0) break
                    offset += written
                }
            }
        }

        fun release() {
            synchronized(lock) {
                if (closed) return
                closed = true
                runCatching {
                    track.pause()
                    track.flush()
                    track.stop()
                }
                track.release()
            }
        }
    }

    private class LocalSpeaker(context: Context) {
        private val app = context.applicationContext
        private val main = Handler(Looper.getMainLooper())
        private var ready = false
        private var failed = false
        private var pending: String? = null
        private var done: (() -> Unit)? = null
        private var fail: (() -> Unit)? = null
        private val resumed = java.util.concurrent.atomic.AtomicBoolean(false)
        private val tts: TextToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val preferred = tts.setLanguage(Locale.getDefault())
                if (preferred == TextToSpeech.LANG_MISSING_DATA || preferred == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
                ready = true
                pending?.let { speakNow(it) }
                pending = null
            } else {
                failed = true
                pending = null
                val onFail = fail
                fail = null
                done = null
                onFail?.invoke()
            }
        }

        init {
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == LAST) main.post { done?.invoke() }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    if (utteranceId == LAST) main.post { done?.invoke() }
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    if (utteranceId == LAST) main.post { done?.invoke() }
                }
            })
        }

        suspend fun speak(text: String) {
            suspendCancellableCoroutine { continuation ->
                if (failed) {
                    continuation.resumeWithException(
                        IllegalStateException(app.getString(R.string.local_tts_failed)),
                    )
                    return@suspendCancellableCoroutine
                }
                resumed.set(false)
                done = { if (resumed.compareAndSet(false, true)) continuation.resume(Unit) }
                fail = {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWithException(
                            IllegalStateException(app.getString(R.string.local_tts_failed)),
                        )
                    }
                }
                continuation.invokeOnCancellation { stop() }
                if (ready) speakNow(text) else pending = text
            }
        }

        fun stop() {
            tts.stop()
            pending = null
            fail = null
            done?.invoke()
            done = null
        }

        private fun speakNow(text: String) {
            val chunks = speechChunks(text)
            if (chunks.isEmpty()) {
                done?.invoke()
                return
            }
            chunks.forEachIndexed { index, chunk ->
                val utterance = if (index == chunks.lastIndex) LAST else "$index"
                val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                tts.speak(chunk, mode, null, utterance)
            }
        }

        private fun speechChunks(text: String): List<String> {
            if (text.length <= LOCAL_CHUNK) return listOf(text)
            val parts = mutableListOf<String>()
            var rest = text
            while (rest.isNotEmpty()) {
                if (rest.length <= LOCAL_CHUNK) {
                    parts += rest
                    break
                }
                val window = rest.substring(0, LOCAL_CHUNK)
                val cut = listOf('。', '！', '？', '\n', '.', '!', '?').maxOf { window.lastIndexOf(it) }
                val end = if (cut >= 80) cut + 1 else LOCAL_CHUNK
                parts += rest.substring(0, end)
                rest = rest.substring(end).trimStart()
            }
            return parts
        }

        private companion object {
            const val LOCAL_CHUNK = 3000
            const val LAST = "last"
        }
    }

    private companion object {
        const val SAMPLE_RATE = 24_000
        const val DOUBAO_CHUNK = 300
    }
}
