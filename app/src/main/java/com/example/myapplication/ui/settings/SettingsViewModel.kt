package com.example.myapplication.ui.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.R
import com.example.myapplication.domain.ModelCatalogRepository
import com.example.myapplication.domain.SettingsRepository
import com.example.myapplication.domain.model.ModelChoice
import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SpeechPreferences
import com.example.myapplication.domain.model.TtsEngine
import com.example.myapplication.domain.model.VoiceChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val modelCatalog: ModelCatalogRepository,
) : ViewModel() {
    var configs by mutableStateOf<Map<ProviderId, ProviderConfig>?>(null)
        private set

    var speech by mutableStateOf(SpeechPreferences())
        private set

    var refreshing by mutableStateOf(false)
        private set

    private val messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = messagesFlow.asSharedFlow()

    val catalogs: StateFlow<Map<ProviderId, List<ModelChoice>>> = combine(
        modelCatalog.observe(ProviderId.DEEPSEEK),
        modelCatalog.observe(ProviderId.DOUBAO),
    ) { deepseek, doubao ->
        mapOf(ProviderId.DEEPSEEK to deepseek, ProviderId.DOUBAO to doubao)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            configs = ProviderId.entries.associateWith { settingsRepository.get(it) }
            speech = settingsRepository.speech()
        }
    }

    fun setEngine(engine: TtsEngine) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(engine = engine) }
            speech = settingsRepository.speech()
        }
    }

    fun setSpeaker(speaker: VoiceChoice) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(speaker = speaker) }
            speech = settingsRepository.speech()
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
            messagesFlow.emit(context.getString(R.string.saved_provider, context.getString(provider.nameRes)))
        }
    }

    fun refreshModels() {
        viewModelScope.launch {
            refreshing = true
            val error = modelCatalog.refreshAll()
            refreshing = false
            messagesFlow.emit(
                if (error == null) context.getString(R.string.models_refreshed)
                else error,
            )
        }
    }
}
