package com.example.myapplication.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.myapplication.domain.ChatRepository
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.Conversation
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SelectedModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationListViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** 新建对话对话框里「开始」使用的模型。 */
    var starting by mutableStateOf(SelectedModel(ProviderId.DOUBAO, ProviderId.DOUBAO.defaultModel))
        private set

    init {
        viewModelScope.launch { starting = settingsRepository.startingModel() }
    }
    val conversations: StateFlow<List<Conversation>> = repository.observeConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun create(provider: ProviderId, model: String): Long =
        repository.createConversation(provider, model)

    fun delete(id: Long) {
        viewModelScope.launch { repository.deleteConversation(id) }
    }
}
