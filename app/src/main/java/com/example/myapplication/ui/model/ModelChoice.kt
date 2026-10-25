package com.example.myapplication.ui.model

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.domain.model.ModelChoice
import com.example.myapplication.domain.model.ProviderId
import com.example.myapplication.domain.model.modelChoices

/**
 * 新建对话、设置和对话页共用的模型选择器。
 * 优先展示远程目录；没有时回退内置目录。「其他模型」可手填。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelPickerDialog(
    title: String,
    providers: List<ProviderId>,
    selectedProvider: ProviderId,
    selectedId: String,
    catalogByProvider: Map<ProviderId, List<ModelChoice>> = emptyMap(),
    /** 对话选择器默认隐藏纯生图模型，避免误选 Seedream 当聊天模型。 */
    includeImageGen: Boolean = false,
    note: String? = null,
    onSelect: (ProviderId, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val known = providers.any { provider ->
        catalogFor(provider, catalogByProvider, includeImageGen).any { it.id == selectedId }
    }
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
                            stringResource(provider.nameRes),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    val choices = catalogFor(provider, catalogByProvider, includeImageGen)
                    choices.groupBy { it.groupRes }.forEach { (group, options) ->
                        Text(
                            stringResource(group),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                        )
                        options.forEach { option ->
                            ModelRow(
                                label = option.label,
                                detail = option.id,
                                vision = option.vision,
                                imageGen = option.imageGen,
                                selected = provider == selectedProvider && option.id == selectedId && !customOpen,
                                onClick = { onSelect(provider, option.id) },
                            )
                        }
                    }
                    ModelRow(
                        label = stringResource(R.string.other_model),
                        detail = stringResource(R.string.other_model_detail),
                        vision = false,
                        imageGen = false,
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
                        label = { Text(stringResource(R.string.provider_model_id, stringResource(customProvider.nameRes))) },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSelect(customProvider, customId.trim()) },
                        enabled = customId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.use_this_id)) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun catalogFor(
    provider: ProviderId,
    catalogByProvider: Map<ProviderId, List<ModelChoice>>,
    includeImageGen: Boolean,
): List<ModelChoice> {
    val base = catalogByProvider[provider]?.takeIf { it.isNotEmpty() } ?: provider.modelChoices()
    return if (includeImageGen) base else base.filterNot { it.imageGen }
}

@Composable
private fun ModelRow(
    label: String,
    detail: String,
    vision: Boolean,
    imageGen: Boolean,
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (vision) {
                Text(
                    stringResource(R.string.capability_vision),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (imageGen) {
                Text(
                    stringResource(R.string.capability_draw),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
