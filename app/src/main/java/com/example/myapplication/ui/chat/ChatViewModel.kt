package com.example.myapplication.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.remote.DoubaoAsrClient
import com.example.myapplication.domain.ChatRepository
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.Conversation
import com.example.myapplication.domain.model.ProviderId
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val generating: Boolean = false,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val speechRecognizer: DoubaoAsrClient,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val conversationId: Long = savedStateHandle.get<Long>("id")
        ?: savedStateHandle.get<String>("id")?.toLongOrNull()
        ?: error("缺少会话 id")

    private val input = MutableStateFlow("")
    private val errorsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = errorsFlow.asSharedFlow()

    val state: StateFlow<ChatUiState> = combine(
        repository.observeConversation(conversationId),
        repository.observeMessages(conversationId),
        repository.isGenerating(conversationId),
        input,
    ) { conversation, messages, generating, text ->
        ChatUiState(conversation, messages, text, generating)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun updateInput(value: String) {
        input.value = value
    }

    fun send() {
        val text = input.value
        viewModelScope.launch {
            val reason = repository.send(conversationId, text)
            if (reason == null) input.value = "" else errorsFlow.emit(reason)
        }
    }

    fun retry() {
        viewModelScope.launch {
            val reason = repository.retry(conversationId)
            if (reason != null) errorsFlow.emit(reason)
        }
    }

    fun stop() {
        repository.stop(conversationId)
    }

    fun selectModel(model: String) {
        viewModelScope.launch {
            val reason = repository.updateModel(conversationId, model)
            if (reason != null) errorsFlow.emit(reason)
        }
    }

    /** 语音识别只用豆包语音控制台的 Key，不用方舟对话密钥。 */
    fun recognize(wav: ByteArray, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val apiKey = settingsRepository.get(ProviderId.DOUBAO).speechApiKey.trim()
                if (apiKey.isEmpty()) {
                    onFailure("请在设置中填写豆包语音识别 API Key")
                } else {
                    onSuccess(speechRecognizer.transcribe(apiKey, wav))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: "语音识别失败")
            }
        }
    }
}
