package com.example.myapplication.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import com.example.myapplication.R
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import java.io.ByteArrayOutputStream

/** 麦克风采集 16 kHz 单声道 PCM，停止后封装成 WAV 交给豆包识别。 */
class SpeechInput(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onPhase: (VoicePhase) -> Unit,
    private val onError: (String) -> Unit,
    private val recognize: (ByteArray, (String) -> Unit, (String) -> Unit) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recorder: AudioRecord? = null
    private var capture: Thread? = null
    private var pcm = ByteArrayOutputStream()
    private var prefix = ""

    @Volatile
    private var capturing = false

    fun start(currentText: String) {
        if (capturing) return
        prefix = currentText
        val audio = openMic()
        if (audio == null) {
            onError(context.getString(R.string.mic_open_failed))
            return
        }
        pcm = ByteArrayOutputStream()
        capturing = true
        recorder = audio
        audio.startRecording()
        onPhase(VoicePhase.Listening)
        capture = Thread {
            val chunk = ByteArray(audio.bufferSizeInFrames.coerceAtLeast(320))
            while (capturing) {
                val count = audio.read(chunk, 0, chunk.size)
                if (count > 0) pcm.write(chunk, 0, count)
            }
        }.also { it.start() }
    }

    fun stop() {
        if (!capturing) return
        capturing = false
        capture?.join(1_000)
        capture = null
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        val wav = pcmToWav(pcm.toByteArray())
        if (wav.size <= WAV_HEADER + MIN_PCM) {
            onPhase(VoicePhase.Idle)
            onError(context.getString(R.string.asr_empty))
            return
        }
        onPhase(VoicePhase.Recognizing)
        recognize(
            wav,
            { spoken ->
                main.post {
                    onText(if (prefix.isBlank()) spoken else prefix + spoken)
                    onPhase(VoicePhase.Idle)
                }
            },
            { message ->
                main.post {
                    onPhase(VoicePhase.Idle)
                    onError(message)
                }
            },
        )
    }

    fun destroy() {
        capturing = false
        capture?.join(500)
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
    }

    @SuppressLint("MissingPermission")
    private fun openMic(): AudioRecord? {
        val min = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (min <= 0) return null
        val audio = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            min * 2,
        )
        return audio.takeIf { it.state == AudioRecord.STATE_INITIALIZED }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val WAV_HEADER = 44
        /** 短于 0.1 秒的录音视为没有说话。 */
        const val MIN_PCM = 3_200
    }
}

enum class VoicePhase {
    Idle,
    Listening,
    Recognizing,
}

internal fun pcmToWav(pcm: ByteArray, sampleRate: Int = 16_000): ByteArray {
    val out = ByteArrayOutputStream(44 + pcm.size)
    fun ascii(value: String) = out.write(value.toByteArray(Charsets.US_ASCII))
    fun int32(value: Int) {
        out.write(value and 0xff)
        out.write(value shr 8 and 0xff)
        out.write(value shr 16 and 0xff)
        out.write(value shr 24 and 0xff)
    }
    fun int16(value: Int) {
        out.write(value and 0xff)
        out.write(value shr 8 and 0xff)
    }
    ascii("RIFF")
    int32(36 + pcm.size)
    ascii("WAVE")
    ascii("fmt ")
    int32(16)
    int16(1)
    int16(1)
    int32(sampleRate)
    int32(sampleRate * 2)
    int16(2)
    int16(16)
    ascii("data")
    int32(pcm.size)
    out.write(pcm)
    return out.toByteArray()
}
