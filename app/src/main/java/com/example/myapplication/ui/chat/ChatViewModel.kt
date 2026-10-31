package com.example.myapplication.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.data.SpeechReader
import com.example.myapplication.data.local.ChatMediaStore
import com.example.myapplication.data.remote.DoubaoAsrClient
import com.example.myapplication.domain.ChatRepository
import com.example.myapplication.domain.ModelCatalogRepository
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ChatMessage
import com.example.myapplication.domain.model.Conversation
import com.example.myapplication.domain.model.ModelChoice
import com.example.myapplication.domain.model.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val conversation: Conversation? = null,
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val pendingImages: List<Uri> = emptyList(),
    val drawMode: Boolean = false,
    val generating: Boolean = false,
    val speakingId: Long? = null,
    val catalog: List<ModelChoice> = emptyList(),
)

data class SpeakPrompt(val message: String, val messageId: Long, val content: String)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
    private val speechRecognizer: DoubaoAsrClient,
    private val speechReader: SpeechReader,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val conversationId: Long = savedStateHandle.get<Long>("id")
        ?: savedStateHandle.get<String>("id")?.toLongOrNull()
        ?: error(context.getString(R.string.missing_conversation_id))

    private val input = MutableStateFlow("")
    private val pendingImages = MutableStateFlow<List<Uri>>(emptyList())
    private val drawMode = MutableStateFlow(false)
    private val errorsFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors = errorsFlow.asSharedFlow()

    private val promptsFlow = MutableSharedFlow<SpeakPrompt>(extraBufferCapacity = 1)
    val speakPrompts = promptsFlow.asSharedFlow()

    private val conversationFlow = repository.observeConversation(conversationId)
    private val catalogFlow = conversationFlow.flatMapLatest { conversation ->
        if (conversation == null) flowOf(emptyList())
        else modelCatalog.observe(conversation.provider)
    }

    private val core = combine(
        conversationFlow,
        repository.observeMessages(conversationId),
        repository.isGenerating(conversationId),
        input,
        pendingImages,
    ) { conversation, messages, generating, text, images ->
        CoreSlice(conversation, messages, generating, text, images)
    }

    val state: StateFlow<ChatUiState> = combine(
        core,
        drawMode,
        speechReader.speaking,
        catalogFlow,
    ) { slice, drawing, speaking, catalog ->
        val speakingId = speaking?.removePrefix("chat:")?.toLongOrNull()
        ChatUiState(
            conversation = slice.conversation,
            messages = slice.messages,
            input = slice.input,
            pendingImages = slice.images,
            drawMode = drawing,
            generating = slice.generating,
            speakingId = speakingId,
            catalog = catalog,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    private data class CoreSlice(
        val conversation: Conversation?,
        val messages: List<ChatMessage>,
        val generating: Boolean,
        val input: String,
        val images: List<Uri>,
    )

    init {
        viewModelScope.launch {
            var lastProvider: ProviderId? = null
            conversationFlow.collect { conversation ->
                val provider = conversation?.provider ?: return@collect
                if (provider == lastProvider) return@collect
                lastProvider = provider
                modelCatalog.refreshIfStale(provider)
            }
        }
    }

    fun updateInput(value: String) {
        input.value = value
    }

    fun toggleDrawMode() {
        drawMode.update { !it }
    }

    fun addImages(uris: List<Uri>) {
        pendingImages.update { current ->
            (current + uris).distinct().take(ChatMediaStore.MAX_ATTACHMENTS)
        }
    }

    fun removeImage(uri: Uri) {
        pendingImages.update { it - uri }
    }

    fun clearImages() {
        pendingImages.value = emptyList()
    }

    fun send() {
        val text = input.value
        val images = pendingImages.value
        val drawing = drawMode.value
        viewModelScope.launch {
            val reason = repository.send(conversationId, text, images, drawing)
            if (reason == null) {
                input.value = ""
                pendingImages.value = emptyList()
                if (drawing) drawMode.value = false
            } else {
                errorsFlow.emit(reason)
            }
        }
    }

    fun applySuggestion(text: String, draw: Boolean = false) {
        input.value = text
        if (draw) drawMode.value = true
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

    fun updatePersona(prompt: String) {
        viewModelScope.launch {
            val reason = repository.updateSystemPrompt(conversationId, prompt)
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

    fun recognize(wav: ByteArray, onSuccess: (String) -> Unit, onFailure: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                val apiKey = config.speechApiKey.trim()
                val appId = config.speechAppId.trim()
                val accessToken = config.speechAccessToken.trim()
                if (apiKey.isEmpty() && (appId.isEmpty() || accessToken.isEmpty())) {
                    onFailure(context.getString(R.string.asr_key_missing))
                } else {
                    onSuccess(
                        speechRecognizer.transcribe(
                            apiKey = apiKey,
                            wav = wav,
                            appId = appId,
                            accessToken = accessToken,
                        ),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                onFailure(error.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.asr_failed))
            }
        }
    }
}
