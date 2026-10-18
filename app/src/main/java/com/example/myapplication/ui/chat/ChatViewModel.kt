package com.example.myapplication.ui.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.example.myapplication.R
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.SpeechReader
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
    val speakingId: Long? = null,
)

data class SpeakPrompt(val message: String, val messageId: Long, val content: String)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val speechRecognizer: DoubaoAsrClient,
    private val speechReader: SpeechReader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val conversationId: Long = savedStateHandle.get<Long>("id")
        ?: savedStateHandle.get<String>("id")?.toLongOrNull()
        ?: error(context.getString(R.string.missing_conversation_id))

    private val input = MutableStateFlow("")
    private val errorsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = errorsFlow.asSharedFlow()

    private val promptsFlow = MutableSharedFlow<SpeakPrompt>(extraBufferCapacity = 1)
    val speakPrompts = promptsFlow.asSharedFlow()

    val state: StateFlow<ChatUiState> = combine(
        repository.observeConversation(conversationId),
        repository.observeMessages(conversationId),
        repository.isGenerating(conversationId),
        input,
        speechReader.speaking,
    ) { conversation, messages, generating, text, speaking ->
        val speakingId = speaking?.removePrefix("chat:")?.toLongOrNull()
        ChatUiState(conversation, messages, text, generating, speakingId)
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

    fun speakLocal(messageId: Long, content: String) {
        speechReader.toggle(
            scope = viewModelScope,
            key = "chat:$messageId",
            text = content,
            forceLocal = true,
        ) { failure ->
            errorsFlow.tryEmit(failure.message)
        }
    }

    fun toggleSpeak(message: ChatMessage, forceLocal: Boolean = false) {
        speechReader.toggle(
            scope = viewModelScope,
            key = "chat:${message.id}",
            text = message.content,
            forceLocal = forceLocal,
        ) { failure ->
            if (failure.canUseLocal) {
                promptsFlow.tryEmit(SpeakPrompt(failure.message, message.id, message.content))
            } else {
                errorsFlow.tryEmit(failure.message)
            }
        }
    }

    override fun onCleared() {
        if (speechReader.speaking.value?.startsWith("chat:") == true) speechReader.stop()
    }

    /** 语音识别只用豆包语音控制台的 Key，不用方舟对话密钥。 */
    fun recognize(wav: ByteArray, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val apiKey = settingsRepository.get(ProviderId.DOUBAO).speechApiKey.trim()
                if (apiKey.isEmpty()) {
                    onFailure(context.getString(R.string.asr_key_missing))
                } else {
                    onSuccess(speechRecognizer.transcribe(apiKey, wav))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.asr_failed))
            }
        }
    }
}
