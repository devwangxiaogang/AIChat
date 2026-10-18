package com.example.myapplication.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.myapplication.domain.model.ProviderConfig
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.SpeechPreferences
import com.example.myapplication.domain.model.TtsEngine
import com.example.myapplication.domain.model.VoiceChoice
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.ui.localizedModel
import com.example.myapplication.ui.model.ModelPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    val configs = viewModel.configs
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (configs == null) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .windowInsetsPadding(WindowInsets.ime.exclude(WindowInsets.navigationBars))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    stringResource(R.string.settings_intro),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                configs.values.forEach { config ->
                    ProviderForm(
                        config = config,
                        speech = viewModel.speech,
                        onChange = viewModel::update,
                        onEngine = viewModel::setEngine,
                        onSpeaker = viewModel::setSpeaker,
                        onSave = { viewModel.save(config.provider) },
                    )
                }
            }
        }
    }
}

/** 每个服务商单独保存。默认模型只在没有上次成功发送记录时用于新对话。 */
@Composable
private fun ProviderForm(
    config: ProviderConfig,
    speech: SpeechPreferences,
    onChange: (ProviderConfig) -> Unit,
    onEngine: (TtsEngine) -> Unit,
    onSpeaker: (VoiceChoice) -> Unit,
    onSave: () -> Unit,
) {
    var pickingModel by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(config.provider.nameRes), style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickingModel = true }
                    .padding(vertical = 4.dp),
            ) {
                Text(stringResource(R.string.default_model_label), style = MaterialTheme.typography.labelLarge)
                Text(config.provider.localizedModel(config.model), style = MaterialTheme.typography.bodyLarge)
                Text(
                    config.model,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { onChange(config.copy(apiKey = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.api_key)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            if (config.provider == ProviderId.DOUBAO) {
                OutlinedTextField(
                    value = config.speechApiKey,
                    onValueChange = { onChange(config.copy(speechApiKey = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.speech_api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.speech_api_key_help)) },
                )
                Text(stringResource(R.string.read_aloud), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TtsEngine.entries.forEach { engine ->
                        FilterChip(
                            selected = speech.engine == engine,
                            onClick = { onEngine(engine) },
                            label = { Text(stringResource(engine.labelRes)) },
                        )
                    }
                }
                if (speech.engine == TtsEngine.DOUBAO) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(VoiceChoice.FEMALE, VoiceChoice.MALE).forEach { voice ->
                            FilterChip(
                                selected = speech.speaker == voice,
                                onClick = { onSpeaker(voice) },
                                label = { Text(stringResource(voice.labelRes)) },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = { showUrl = !showUrl }) {
                Text(stringResource(if (showUrl) R.string.hide_endpoint else R.string.show_endpoint))
            }
            if (showUrl) {
                OutlinedTextField(
                    value = config.baseUrl,
                    onValueChange = { onChange(config.copy(baseUrl = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.base_url)) },
                    singleLine = true,
                )
            }
            Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.save_provider, stringResource(config.provider.nameRes)))
            }
        }
    }
    if (pickingModel) {
        ModelPickerDialog(
            title = stringResource(R.string.provider_default_model, stringResource(config.provider.nameRes)),
            providers = listOf(config.provider),
            selectedProvider = config.provider,
            selectedId = config.model,
            note = stringResource(R.string.default_model_note),
            onSelect = { _, model ->
                onChange(config.copy(model = model))
                pickingModel = false
            },
            onDismiss = { pickingModel = false },
        )
    }
}
