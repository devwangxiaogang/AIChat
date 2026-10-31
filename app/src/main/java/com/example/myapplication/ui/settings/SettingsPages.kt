package com.example.myapplication.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.myapplication.R
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.TtsEngine
import com.example.myapplication.domain.model.VoiceChoice
import com.example.myapplication.ui.localizedModel
import com.example.myapplication.ui.model.ModelPickerDialog

/** 单个服务商的接入信息与默认模型。 */
@Composable
fun ProviderSettingsScreen(
    providerId: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val provider = ProviderId.entries.firstOrNull { it.name == providerId } ?: ProviderId.DEEPSEEK
    val catalogs by viewModel.catalogs.collectAsStateWithLifecycle()
    val config = viewModel.configs?.get(provider)
    var pickingModel by remember { mutableStateOf(false) }
    var showUrl by remember { mutableStateOf(false) }

    SettingPageScaffold(
        title = stringResource(provider.nameRes),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (config == null) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
            return@SettingPageScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_provider_default_model)) {
                SettingsRow(
                    title = stringResource(R.string.default_model_label),
                    subtitle = "${provider.localizedModel(config.model)} · ${config.model}",
                    onClick = { pickingModel = true },
                )
            }
            SettingsGroup(stringResource(R.string.settings_provider_keys)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = config.apiKey,
                        onValueChange = { viewModel.update(config.copy(apiKey = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.api_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                    TextButton(onClick = { showUrl = !showUrl }) {
                        Text(stringResource(if (showUrl) R.string.hide_endpoint else R.string.show_endpoint))
                    }
                    if (showUrl) {
                        OutlinedTextField(
                            value = config.baseUrl,
                            onValueChange = { viewModel.update(config.copy(baseUrl = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.base_url)) },
                            singleLine = true,
                        )
                    }
                }
            }
            Button(
                onClick = { viewModel.save(provider) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.save_provider, stringResource(provider.nameRes)))
            }
            Text(
                stringResource(R.string.settings_about_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (pickingModel) {
        ModelPickerDialog(
            title = stringResource(R.string.provider_default_model, stringResource(provider.nameRes)),
            providers = listOf(provider),
            selectedProvider = provider,
            selectedId = config?.model.orEmpty(),
            catalogByProvider = mapOf(provider to catalogs[provider].orEmpty()),
            note = stringResource(R.string.default_model_note),
            onSelect = { _, model ->
                config?.let { viewModel.update(it.copy(model = model)) }
                pickingModel = false
            },
            onDismiss = { pickingModel = false },
        )
    }
}

/** 朗读引擎、音色与实时语音凭证。 */
@Composable
fun VoicePrefsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val speech = viewModel.speech
    val config = viewModel.configs?.get(ProviderId.DOUBAO)

    SettingPageScaffold(
        title = stringResource(R.string.settings_section_voice),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_voice_reading)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.settings_voice_engine), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TtsEngine.entries.forEach { engine ->
                            FilterChip(
                                selected = speech.engine == engine,
                                onClick = { viewModel.setEngine(engine) },
                                label = { Text(stringResource(engine.labelRes)) },
                            )
                        }
                    }
                    if (speech.engine == TtsEngine.DOUBAO) {
                        Text(stringResource(R.string.settings_voice_speaker), style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(VoiceChoice.FEMALE, VoiceChoice.MALE).forEach { voice ->
                                FilterChip(
                                    selected = speech.speaker == voice,
                                    onClick = { viewModel.setSpeaker(voice) },
                                    label = { Text(stringResource(voice.labelRes)) },
                                )
                            }
                        }
                    }
                }
            }
            SettingsGroup(stringResource(R.string.settings_realtime_voice)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.settings_realtime_keys_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (config == null) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        OutlinedTextField(
                            value = config.speechApiKey,
                            onValueChange = { viewModel.update(config.copy(speechApiKey = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.speech_api_key)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.speech_api_key_help)) },
                        )
                        OutlinedTextField(
                            value = config.speechAppId,
                            onValueChange = { viewModel.update(config.copy(speechAppId = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.speech_app_id)) },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = config.speechAccessToken,
                            onValueChange = { viewModel.update(config.copy(speechAccessToken = it)) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.speech_access_token)) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            supportingText = { Text(stringResource(R.string.speech_legacy_help)) },
                        )
                        Button(onClick = { viewModel.save(ProviderId.DOUBAO) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.save_provider, stringResource(ProviderId.DOUBAO.nameRes)))
                        }
                    }
                    Text(stringResource(R.string.settings_live_voice), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceChoice.entries.forEach { voice ->
                            FilterChip(
                                selected = speech.liveVoice == voice,
                                onClick = { viewModel.setLiveVoice(voice) },
                                label = { Text(stringResource(voice.labelRes)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 外观：主题目前跟随系统，深色模式在 P4 阶段接入。 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
) {
    SettingPageScaffold(
        title = stringResource(R.string.settings_section_appearance),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_theme)) {
                SettingsRow(
                    title = stringResource(R.string.settings_theme_follow_system),
                    value = stringResource(R.string.settings_theme_current),
                    valueColor = MaterialTheme.colorScheme.primary,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(title = stringResource(R.string.settings_theme_light), value = stringResource(R.string.settings_soon))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(title = stringResource(R.string.settings_theme_dark), value = stringResource(R.string.settings_soon))
            }
            Text(
                stringResource(R.string.settings_theme_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** 模型目录刷新与会话备份。 */
@Composable
fun DataSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importBackup(uri) }

    SettingPageScaffold(
        title = stringResource(R.string.settings_section_data),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.refresh_models)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.refresh_models_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = viewModel::refreshModels,
                        enabled = !viewModel.refreshing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (viewModel.refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(stringResource(R.string.refresh_models))
                    }
                }
            }
            SettingsGroup(stringResource(R.string.backup_title)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.backup_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { exportLauncher.launch("chat-backup.zip") },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.backup_export)) }
                        Button(
                            onClick = { importLauncher.launch(arrayOf("application/zip")) },
                            modifier = Modifier.weight(1f),
                        ) { Text(stringResource(R.string.backup_import)) }
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    SettingPageScaffold(
        title = stringResource(R.string.settings_section_about),
        onBack = onBack,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_section_about)) {
                SettingsRow(title = stringResource(R.string.settings_version), value = "1.0.0")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = stringResource(R.string.settings_about_security),
                    subtitle = stringResource(R.string.settings_about_note),
                    value = stringResource(R.string.settings_status_ready),
                )
            }
            Text(
                stringResource(R.string.settings_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
