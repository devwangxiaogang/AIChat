package com.yuguang.lumo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.domain.model.TtsEngine
import com.yuguang.lumo.domain.model.VoiceChoice

/** 设置中心：分组列表 + 二级页，替代原来的一屏长表单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomeScreen(
    onBack: () -> Unit,
    onOpenProvider: (ProviderId) -> Unit,
    onOpenVoicePrefs: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenData: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    appearance: AppearanceViewModel = hiltViewModel(),
) {
    val configs = viewModel.configs
    val themeMode by appearance.mode.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_settings), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsGroup(stringResource(R.string.settings_section_account)) {
                configs?.get(ProviderId.DEEPSEEK)?.let { config ->
                    SettingsRow(
                        title = stringResource(ProviderId.DEEPSEEK.nameRes),
                        subtitle = stringResource(R.string.settings_provider_subtitle),
                        value = stringResource(
                            if (config.apiKey.isNotBlank()) R.string.settings_status_configured
                            else R.string.settings_status_missing,
                        ),
                        valueColor = statusColor(config.apiKey.isNotBlank()),
                        onClick = { onOpenProvider(ProviderId.DEEPSEEK) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                configs?.get(ProviderId.DOUBAO)?.let { config ->
                    SettingsRow(
                        title = stringResource(ProviderId.DOUBAO.nameRes),
                        subtitle = stringResource(R.string.settings_provider_subtitle),
                        value = stringResource(
                            if (config.apiKey.isNotBlank()) R.string.settings_status_configured
                            else R.string.settings_status_missing,
                        ),
                        valueColor = statusColor(config.apiKey.isNotBlank()),
                        onClick = { onOpenProvider(ProviderId.DOUBAO) },
                    )
                }
            }
            val speech = viewModel.speech
            SettingsGroup(stringResource(R.string.settings_section_voice)) {
                SettingsRow(
                    title = stringResource(R.string.settings_realtime_voice),
                    subtitle = stringResource(R.string.settings_realtime_subtitle),
                    value = stringResource(
                        if (speech.engine == TtsEngine.DOUBAO) R.string.settings_status_configured
                        else R.string.settings_status_missing,
                    ),
                    valueColor = statusColor(speech.engine == TtsEngine.DOUBAO),
                    onClick = onOpenVoicePrefs,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = stringResource(R.string.settings_voice_speaker),
                    value = stringResource(speech.speaker.labelRes),
                    onClick = onOpenVoicePrefs,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = stringResource(R.string.settings_voice_engine),
                    value = stringResource(speech.engine.labelRes),
                    onClick = onOpenVoicePrefs,
                )
            }
            SettingsGroup(stringResource(R.string.settings_section_appearance)) {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    value = stringResource(themeMode.labelRes),
                    onClick = onOpenAppearance,
                )
            }
            SettingsGroup(stringResource(R.string.settings_section_data)) {
                SettingsRow(
                    title = stringResource(R.string.backup_title),
                    subtitle = stringResource(R.string.settings_backup_subtitle),
                    value = "ZIP",
                    onClick = onOpenData,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = stringResource(R.string.refresh_models),
                    subtitle = stringResource(R.string.settings_models_subtitle),
                    onClick = onOpenData,
                )
            }
            SettingsGroup(stringResource(R.string.settings_section_about)) {
                SettingsRow(
                    title = stringResource(R.string.settings_version),
                    value = "1.0.0",
                    onClick = onOpenAbout,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    title = stringResource(R.string.settings_about_security),
                    value = stringResource(R.string.settings_status_ready),
                    onClick = onOpenAbout,
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

@Composable
fun SettingsGroup(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun statusColor(ok: Boolean): Color = if (ok) {
    Color(0xFF12A07A)
} else {
    MaterialTheme.colorScheme.error
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingPageScaffold(
    title: String,
    onBack: () -> Unit,
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = snackbarHost,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        content = content,
    )
}
