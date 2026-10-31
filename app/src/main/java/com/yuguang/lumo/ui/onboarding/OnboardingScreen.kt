package com.yuguang.lumo.ui.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yuguang.lumo.R
import com.yuguang.lumo.domain.SettingsRepository
import com.yuguang.lumo.domain.model.ProviderId
import com.yuguang.lumo.ui.prefs.UiPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val uiPreferences: UiPreferences,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    var deepSeekKey by mutableStateOf("")
        private set
    var doubaoKey by mutableStateOf("")
        private set
    var speechKey by mutableStateOf("")
        private set

    fun setDeepSeek(value: String) { deepSeekKey = value }
    fun setDoubao(value: String) { doubaoKey = value }
    fun setSpeech(value: String) { speechKey = value }

    fun save() {
        viewModelScope.launch {
            if (deepSeekKey.isNotBlank()) {
                val config = settingsRepository.get(ProviderId.DEEPSEEK)
                settingsRepository.save(config.copy(apiKey = deepSeekKey.trim()))
            }
            if (doubaoKey.isNotBlank() || speechKey.isNotBlank()) {
                val config = settingsRepository.get(ProviderId.DOUBAO)
                settingsRepository.save(
                    config.copy(
                        apiKey = doubaoKey.trim().ifBlank { config.apiKey },
                        speechApiKey = speechKey.trim().ifBlank { config.speechApiKey },
                    ),
                )
            }
            uiPreferences.markOnboarded()
        }
    }

    fun skip() {
        uiPreferences.markOnboarded()
    }
}

/** 首次启动引导：填好密钥就可以开始，也可以先跳过。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(48.dp))
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = viewModel.deepSeekKey,
                onValueChange = viewModel::setDeepSeek,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_deepseek)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            OutlinedTextField(
                value = viewModel.doubaoKey,
                onValueChange = viewModel::setDoubao,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_doubao)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            OutlinedTextField(
                value = viewModel.speechKey,
                onValueChange = viewModel::setSpeech,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.onboarding_speech)) },
                supportingText = { Text(stringResource(R.string.onboarding_note)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(stringResource(R.string.onboarding_start), style = MaterialTheme.typography.titleMedium)
            }
            TextButton(
                onClick = viewModel::skip,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
