package com.yuguang.lumo.ui.settings

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.R
import com.yuguang.lumo.data.ChatBackupManager
import com.yuguang.lumo.domain.ModelCatalogRepository
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ModelChoice
import com.yuguang.lumo.domain.model.ProviderConfig
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.SpeechPreferences
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.domain.model.VoiceChoice
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
    private val backupManager: ChatBackupManager,
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

    fun setLiveVoice(voice: VoiceChoice) {
        viewModelScope.launch {
            settingsRepository.editSpeech { it.copy(liveVoice = voice) }
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

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            messagesFlow.emit(
                runCatching { backupManager.exportTo(uri) }.fold(
                    onSuccess = { context.getString(R.string.backup_export_done, it) },
                    onFailure = { context.getString(R.string.backup_failed, it.message.orEmpty()) },
                ),
            )
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            messagesFlow.emit(
                runCatching { backupManager.importFrom(uri) }.fold(
                    onSuccess = { context.getString(R.string.backup_import_done, it) },
                    onFailure = { context.getString(R.string.backup_failed, it.message.orEmpty()) },
                ),
            )
        }
    }
}
