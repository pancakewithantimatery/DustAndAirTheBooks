package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AiModelOption
import com.example.ui.ModelDiscoveryState
import com.example.ui.TomeUiState
import com.example.ui.components.tomeTextFieldColors
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    uiState: TomeUiState,
    onSelectModel: (AiModelOption) -> Unit,
    onSetCustomModelId: (String) -> Unit,
    onSelectDiscoveredModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onSaveCustomApiKey: (String) -> Unit,
    onClearCustomApiKey: () -> Unit,
    onToggleUseCustomApiKey: (Boolean) -> Unit,
    onSetDefaultGroup: (String) -> Unit,
    onAddGroup: (String) -> Unit,
    onRemoveGroup: (String) -> Unit,
    onClearLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var apiKeyInput by remember { mutableStateOf(uiState.settings.customApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var customModelInput by remember { mutableStateOf(uiState.settings.customModelId) }
    var newGroupText by remember { mutableStateOf("") }
    var defaultShelfExpanded by remember { mutableStateOf(false) }
    var confirmClearOpen by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.settings.customApiKey) {
        if (apiKeyInput.isEmpty() && uiState.settings.customApiKey.isNotEmpty()) {
            apiKeyInput = uiState.settings.customApiKey
        } else if (uiState.settings.customApiKey.isEmpty() && apiKeyInput.isNotEmpty()) {
            apiKeyInput = ""
        }
    }

    LaunchedEffect(uiState.settings.customModelId) {
        customModelInput = uiState.settings.customModelId
    }

    val usingPaidKey = uiState.settings.isUsingPaidKey
    val hasSavedKey = uiState.settings.customApiKey.isNotBlank()
    val discoveryState = uiState.modelDiscoveryState
    val discoveredList = (discoveryState as? ModelDiscoveryState.Loaded)?.models.orEmpty()
    val discoveredIdSet = remember(discoveredList) {
        discoveredList.map { it.id.lowercase() }.toSet()
    }

    // Filter visible preset cards dynamically based on active API key permissions
    val visiblePresets = remember(usingPaidKey, discoveredIdSet, uiState.settings.selectedModel) {
        AiModelOption.entries.filter { option ->
            when {
                option == AiModelOption.CUSTOM -> usingPaidKey || uiState.settings.selectedModel == AiModelOption.CUSTOM
                !usingPaidKey -> true // Show all non-custom presets on Free Tier with clear Free / Paid Key badges
                discoveredIdSet.isEmpty() -> true // Before/if models.list hasn't loaded yet
                else -> discoveredIdSet.contains(option.modelId.lowercase()) || uiState.settings.selectedModel == option
            }
        }
    }

    val extraDiscoveredModels = remember(discoveredList, visiblePresets) {
        val shownPresetIds = visiblePresets.filter { it != AiModelOption.CUSTOM }.map { it.modelId.lowercase() }.toSet()
        discoveredList.filter { it.id.lowercase() !in shownPresetIds }.take(12)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. AI Model Selection Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_model_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (usingPaidKey) GoldContainer
                                else EmeraldSuccess.copy(alpha = 0.14f)
                            )
                            .border(
                                1.dp,
                                if (usingPaidKey) GoldAmber.copy(alpha = 0.45f)
                                else EmeraldSuccess.copy(alpha = 0.4f),
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (usingPaidKey) Icons.Filled.Key else Icons.Filled.VerifiedUser,
                            contentDescription = null,
                            tint = if (usingPaidKey) GoldAmber else EmeraldSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = if (usingPaidKey) "Paid API Key" else "Firebase Free",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (usingPaidKey) GoldAmber else EmeraldSuccess,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Dynamic models.list verification strip when BYOK Paid API Key is active
                if (usingPaidKey) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            when (discoveryState) {
                                is ModelDiscoveryState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Checking available models via models.list…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                is ModelDiscoveryState.Loaded -> {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "${discoveryState.models.size} models available for your key",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                is ModelDiscoveryState.Error -> {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = discoveryState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                ModelDiscoveryState.Idle -> {
                                    Text(
                                        text = "Tap refresh to check models.list",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onRefreshModels,
                            enabled = discoveryState !is ModelDiscoveryState.Loading,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("refresh_models_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh available models",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Preset Model Cards (no mid-word badge wrapping!)
                visiblePresets.forEach { option ->
                    val selected = uiState.settings.selectedModel == option
                    val lockedOnFreeTier = !usingPaidKey && option.requiresPaidTier
                    val isVerifiedInKey = usingPaidKey && discoveredIdSet.isNotEmpty() &&
                        option != AiModelOption.CUSTOM &&
                        discoveredIdSet.contains(option.modelId.lowercase())
                    val isMissingFromKey = usingPaidKey && discoveredIdSet.isNotEmpty() &&
                        option != AiModelOption.CUSTOM &&
                        !discoveredIdSet.contains(option.modelId.lowercase())

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (lockedOnFreeTier || isMissingFromKey) 0.72f else 1f)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.background
                            )
                            .border(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                MaterialTheme.shapes.small
                            )
                            .clickable { onSelectModel(option) }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("model_option_${option.modelId}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { onSelectModel(option) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary,
                                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    if (option.badge != null) {
                                        ModelBadgePill(
                                            text = option.badge,
                                            containerColor = if (option.requiresPaidTier) GoldContainer
                                            else MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = if (option.requiresPaidTier) GoldAmber
                                            else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    }
                                    if (isVerifiedInKey) {
                                        ModelBadgePill(
                                            text = "Verified",
                                            containerColor = EmeraldSuccess.copy(alpha = 0.14f),
                                            contentColor = EmeraldSuccess,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    } else if (isMissingFromKey) {
                                        ModelBadgePill(
                                            text = "Unavailable",
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.align(Alignment.CenterVertically)
                                        )
                                    } else if (lockedOnFreeTier) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.CenterVertically)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(GoldContainer)
                                                .padding(horizontal = 7.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = null,
                                                tint = GoldAmber,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Paid Key",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = GoldAmber,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = option.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (option == AiModelOption.CUSTOM && selected) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 10.dp, end = 4.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customModelInput,
                                    onValueChange = { customModelInput = it },
                                    placeholder = {
                                        Text(
                                            text = "e.g. gemini-3.8-flash",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    shape = MaterialTheme.shapes.small,
                                    colors = tomeTextFieldColors(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("custom_model_id_input")
                                )
                                Button(
                                    onClick = { onSetCustomModelId(customModelInput) },
                                    enabled = customModelInput.isNotBlank(),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("save_custom_model_button"),
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp)
                                ) {
                                    Text("Apply", maxLines = 1, softWrap = false)
                                }
                            }
                        }
                    }
                }

                // Live discovered models from models.list for one-tap selection
                if (usingPaidKey && extraDiscoveredModels.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "VERIFIED MODELS ON YOUR KEY (MODELS.LIST)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            extraDiscoveredModels.forEach { model ->
                                val isActive = uiState.settings.effectiveModelId.equals(model.id, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isActive) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            1.dp,
                                            if (isActive) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSelectDiscoveredModel(model.id) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("discovered_model_${model.id}")
                                ) {
                                    Text(
                                        text = model.id,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isActive) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Helpful hint if user selects a paid-tier model without a paid key active
                if (uiState.settings.selectedModel.requiresPaidTier && !usingPaidKey) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(GoldContainer)
                            .border(1.dp, GoldAmber.copy(alpha = 0.4f), MaterialTheme.shapes.small)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = GoldAmber,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(16.dp)
                        )
                        Text(
                            text = "This model has 0 Free Tier quota on Firebase. Paste your paid Google AI Studio API key below to unlock it and auto-verify available models.",
                            style = MaterialTheme.typography.bodySmall,
                            color = GoldAmber
                        )
                    }
                }
            }
        }

        // 2. Paid Gemini API Key (BYOK) Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(
                1.dp,
                if (usingPaidKey) GoldAmber.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = if (usingPaidKey) GoldAmber else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_paid_key_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = stringResource(R.string.settings_paid_key_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text(stringResource(R.string.settings_paid_key_label)) },
                    placeholder = {
                        Text(
                            text = "AIza...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            )
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    visualTransformation = if (showApiKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showApiKey = !showApiKey },
                            modifier = Modifier.testTag("toggle_api_key_visibility")
                        ) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showApiKey) "Hide API key" else "Show API key",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = tomeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("paid_api_key_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onSaveCustomApiKey(apiKeyInput) },
                        enabled = apiKeyInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("save_paid_api_key_button"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_save_api_key),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    if (hasSavedKey) {
                        OutlinedButton(
                            onClick = {
                                apiKeyInput = ""
                                onClearCustomApiKey()
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clear_paid_api_key_button"),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Remove")
                        }
                    }
                }

                if (hasSavedKey) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 10.dp)
                        ) {
                            Text(
                                text = "Use Paid API Key for Requests",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (usingPaidKey) {
                                    "Routing directly to Gemini API with your paid quota"
                                } else {
                                    "Paused — using built-in Firebase AI Logic"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = usingPaidKey,
                            onCheckedChange = onToggleUseCustomApiKey,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("use_paid_key_switch")
                        )
                    }
                }
            }
        }

        // 3. Shelves & Group Presets Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_shelves_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ExposedDropdownMenuBox(
                    expanded = defaultShelfExpanded,
                    onExpandedChange = { defaultShelfExpanded = !defaultShelfExpanded }
                ) {
                    OutlinedTextField(
                        value = uiState.settings.defaultGroup,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.settings_default_shelf)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = defaultShelfExpanded)
                        },
                        shape = MaterialTheme.shapes.small,
                        colors = tomeTextFieldColors(),
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                            .testTag("settings_default_shelf_dropdown")
                    )
                    ExposedDropdownMenu(
                        expanded = defaultShelfExpanded,
                        onDismissRequest = { defaultShelfExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        uiState.settings.groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group) },
                                onClick = {
                                    onSetDefaultGroup(group)
                                    defaultShelfExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newGroupText,
                        onValueChange = { newGroupText = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.settings_new_shelf_placeholder),
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        colors = tomeTextFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("new_shelf_input")
                    )

                    OutlinedButton(
                        onClick = {
                            if (newGroupText.isNotBlank()) {
                                onAddGroup(newGroupText)
                                newGroupText = ""
                            }
                        },
                        enabled = newGroupText.isNotBlank(),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("add_shelf_button"),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add shelf",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val groups = uiState.settings.groups
                    groups.forEach { group ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    1.dp,
                                    if (group == uiState.settings.defaultGroup) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(
                                    start = 12.dp,
                                    end = if (groups.size > 1) 4.dp else 12.dp,
                                    top = 4.dp,
                                    bottom = 4.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (groups.size > 1) {
                                IconButton(
                                    onClick = { onRemoveGroup(group) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Remove shelf $group",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Offline Storage & Maintenance Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_storage_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Local Room SQLite database (Offline Ready) · ${uiState.books.size} books stored on device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedButton(
                    onClick = { confirmClearOpen = true },
                    enabled = uiState.books.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("clear_library_button"),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_clear_library),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (confirmClearOpen) {
        AlertDialog(
            onDismissRequest = { confirmClearOpen = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete All Books?") },
            text = {
                Text("This permanently deletes all ${uiState.books.size} cataloged books from this device. Export a JSON Backup first if you want to keep a copy.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onClearLibrary()
                        confirmClearOpen = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun ModelBadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            softWrap = false
        )
    }
}
