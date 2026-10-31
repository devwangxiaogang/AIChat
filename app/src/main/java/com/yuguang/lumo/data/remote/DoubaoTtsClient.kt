package com.yuguang.lumo.data.remote

import android.content.Context
import android.util.Base64
import com.yuguang.lumo.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 豆包语音合成 2.0。一次送入全文，按行返回 base64 PCM。
 * 音色必须属于 seed-tts-2.0，否则服务会拒绝资源不匹配。
 */
@Singleton
class DoubaoTtsClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val http: OkHttpClient,
    private val json: Json,
) {
    suspend fun synthesize(
        apiKey: String,
        speaker: String,
        text: String,
        appId: String = "",
        accessToken: String = "",
        onPcm: (ByteArray) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            putJsonObject("user") { put("uid", "aidemo") }
            putJsonObject("req_params") {
                put("text", text)
                put("speaker", speaker)
                putJsonObject("audio_params") {
                    put("format", "pcm")
                    put("sample_rate", SAMPLE_RATE)
                }
            }
        }
        val requestBuilder = Request.Builder()
            .url(ENDPOINT)
            .header("X-Api-Resource-Id", RESOURCE_ID)
            .header("X-Api-Request-Id", UUID.randomUUID().toString())
        if (appId.isNotBlank() && accessToken.isNotBlank()) {
            // 旧版控制台的应用凭证：账号里开通的语音服务通常只认这种鉴权。
            requestBuilder
                .header("X-Api-App-Key", appId.trim())
                .header("X-Api-Access-Key", accessToken.trim())
        } else {
            requestBuilder.header("X-Api-Key", apiKey.trim())
        }
        val request = requestBuilder
            .post(body.toString().toRequestBody(JSON))
            .build()
        val call = http.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) call.cancel()
        }
        call.execute().use { response ->
            val payload = response.body ?: error(context.getString(R.string.tts_no_audio))
            if (!response.isSuccessful) {
                val detail = payload.string().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                error(context.getString(R.string.tts_failed_detail, detail.ifBlank { "HTTP ${response.code}" }))
            }
            var heard = false
            val source = payload.source()
            while (!source.exhausted()) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val obj = runCatching { json.parseToJsonElement(line).jsonObject }.getOrNull() ?: continue
                val code = obj["code"]?.jsonPrimitive?.intOrNull ?: 0
                if (code != 0 && code != SUCCESS) {
                    val message = obj["message"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    error(context.getString(R.string.tts_failed_detail, message.ifBlank { code.toString() }))
                }
                val audio = obj["data"]?.jsonPrimitive?.contentOrNull
                if (!audio.isNullOrBlank()) {
                    val pcm = Base64.decode(audio, Base64.DEFAULT)
                    if (pcm.isNotEmpty()) {
                        onPcm(pcm)
                        heard = true
                    }
                }
            }
            if (!heard) error(context.getString(R.string.tts_no_audio))
        }
    }

    private companion object {
        const val ENDPOINT = "https://openspeech.bytedance.com/api/v3/tts/unidirectional"
        const val RESOURCE_ID = "seed-tts-2.0"
        const val SAMPLE_RATE = 24_000
        const val SUCCESS = 20_000_000
        val JSON = "application/json".toMediaType()
    }
}
