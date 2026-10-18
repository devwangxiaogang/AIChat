package com.example.myapplication.ui.model

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.groupedModels

/**
 * 新建对话、设置和对话页共用的模型选择器。
 * 点目录项立即选中；「其他模型」展开后填写 ep- 接入点或目录外的 ID。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerDialog(
    title: String,
    providers: List<ProviderId>,
    selectedProvider: ProviderId,
    selectedId: String,
    note: String? = null,
    onSelect: (ProviderId, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val known = providers.flatMap { it.models }.any { it.id == selectedId }
    var customProvider by remember(selectedProvider) { mutableStateOf(selectedProvider) }
    var customOpen by remember(selectedId) { mutableStateOf(!known && selectedId.isNotBlank()) }
    var customId by remember(selectedId) { mutableStateOf(if (known) "" else selectedId) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.72f)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (note != null) {
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                providers.forEach { provider ->
                    if (providers.size > 1) {
                        Text(
                            provider.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    provider.groupedModels().forEach { (group, options) ->
                        Text(
                            group,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        options.forEach { option ->
                            ModelRow(
                                label = option.label,
                                detail = option.id,
                                selected = provider == selectedProvider && option.id == selectedId && !customOpen,
                                onClick = { onSelect(provider, option.id) },
                            )
                        }
                    }
                    ModelRow(
                        label = "其他模型",
                        detail = "填写接入点或控制台给出的模型 ID",
                        selected = customOpen && customProvider == provider,
                        onClick = {
                            customProvider = provider
                            customOpen = true
                        },
                    )
                }
                if (customOpen) {
                    OutlinedTextField(
                        value = customId,
                        onValueChange = { customId = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        label = { Text("${customProvider.displayName}模型 ID") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSelect(customProvider, customId.trim()) },
                        enabled = customId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("使用这个 ID") }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ModelRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
