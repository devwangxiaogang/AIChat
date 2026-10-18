package com.example.myapplication.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    var configs by mutableStateOf<Map<ProviderId, ProviderConfig>?>(null)
        private set

    private val messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = messagesFlow.asSharedFlow()

    init {
        viewModelScope.launch {
            configs = ProviderId.entries.associateWith { settingsRepository.get(it) }
        }
    }

    fun update(config: ProviderConfig) {
        val current = configs ?: return
        configs = current + (config.provider to config)
    }

    fun save(provider: ProviderId) {
        val config = configs?.get(provider) ?: return
        viewModelScope.launch {
            settingsRepository.save(config)
            messagesFlow.emit("${provider.displayName} 已保存")
        }
    }
}
