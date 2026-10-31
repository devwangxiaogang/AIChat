package com.example.myapplication.ui.voice

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.SpeechReader
import com.example.myapplication.data.remote.DoubaoAsrClient
import com.example.myapplication.domain.LlmProvider
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ChatRequest
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.Role
import com.example.myapplication.domain.model.StreamEvent
import com.example.myapplication.domain.model.WireMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VoiceChatUiState(
    val busy: Boolean = false,
    val userText: String = "",
    val partialUserText: String = "",
    val assistantText: String = "",
    val error: String? = null,
)

/**
 * 语音对话（兜底链路）：豆包识别 → DeepSeek/方舟对话 → 豆包朗读。
 * 端到端实时语音（volc.speech.dialog）需要控制台单独开通，暂时用这条链路保证功能可用。
 */
@HiltViewModel
class VoiceChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val speechReader: SpeechReader,
    private val speechRecognizer: DoubaoAsrClient,
    private val providers: Map<ProviderId, @JvmSuppressWildcards LlmProvider>,
) : ViewModel() {
    private companion object {
        const val HISTORY_LIMIT = 12
    }
    var state by mutableStateOf(VoiceChatUiState())
        private set

    private var chatJob: Job? = null

    /** 多轮上下文，只保留最近若干条。 */
    private val history = mutableListOf<WireMessage>()

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
                onSuccess(
                    speechRecognizer.transcribe(
                        apiKey = apiKey,
                        wav = wav,
                        appId = appId,
                        accessToken = accessToken,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.asr_failed))
            } finally {
                state = state.copy(partialUserText = "")
            }
        }
    }

    /** 录音/手输的一段话，走「对话模型 + 朗读」。 */
    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        history += WireMessage(Role.USER, content)
        while (history.size > HISTORY_LIMIT) history.removeAt(0)
        chatJob?.cancel()
        speechReader.stop()
        state = state.copy(
            busy = true,
            error = null,
            assistantText = "",
            userText = appendLine(state.userText, content),
        )
        val previousReply = state.assistantText
        chatJob = viewModelScope.launch {
            try {
                val selected = settingsRepository.startingModel()
                val config = settingsRepository.get(selected.provider)
                if (config.apiKey.isBlank()) {
                    state = state.copy(busy = false, error = context.getString(R.string.error_api_key_invalid))
                    return@launch
                }
                val llm = providers[selected.provider]
                if (llm == null) {
                    state = state.copy(busy = false, error = context.getString(R.string.voice_failed))
                    return@launch
                }
                var reply = ""
                llm.stream(
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl,
                    request = ChatRequest(
                        model = selected.model,
                        messages = history.toList(),
                    ),
                ).catch { error ->
                    state = state.copy(
                        busy = false,
                        error = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.voice_failed),
                    )
                }.collect { event ->
                    when (event) {
                        is StreamEvent.Delta -> {
                            reply += event.text
                            val shown = if (previousReply.isBlank()) reply else "$previousReply\n$reply"
                            state = state.copy(assistantText = shown)
                        }
                        is StreamEvent.Failed -> {
                            state = state.copy(busy = false, error = event.message)
                        }
                        else -> Unit
                    }
                }
                state = state.copy(busy = false)
                if (reply.isNotBlank()) {
                    history += WireMessage(Role.ASSISTANT, reply)
                    while (history.size > HISTORY_LIMIT) history.removeAt(0)
                    speechReader.toggle(viewModelScope, "voice-chat", reply) { failure ->
                        state = state.copy(error = failure.message)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state = state.copy(
                    busy = false,
                    error = error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.voice_failed),
                )
            }
        }
    }

    fun stop() {
        chatJob?.cancel()
        chatJob = null
        speechReader.stop()
        state = state.copy(busy = false)
    }

    fun clear() {
        stop()
        history.clear()
        state = VoiceChatUiState()
    }

    private fun appendLine(current: String, incoming: String): String {
        val line = incoming.trim()
        if (line.isEmpty()) return current
        return if (current.isEmpty()) line else "$current\n$line"
    }

    override fun onCleared() {
        chatJob?.cancel()
        speechReader.stop()
    }
}
