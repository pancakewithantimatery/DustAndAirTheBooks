package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AiModelOption
import com.example.ui.BookDraft
import com.example.ui.LookupUiState
import com.example.ui.TomeUiState
import com.example.ui.components.BarcodeScannerSection
import com.example.ui.components.ReviewBookCard
import com.example.ui.components.tomeTextFieldColors
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.GoldContainer
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(
    uiState: TomeUiState,
    onToggleScanner: () -> Unit,
    onBarcodeDetected: (String) -> Unit,
    onScannerError: (String) -> Unit,
    onSnapPhotoClick: () -> Unit,
    onPickPhotoClick: () -> Unit,
    onManualIsbnChanged: (String) -> Unit,
    onLookupClick: () -> Unit,
    onOpenBlankDraft: () -> Unit,
    onDismissError: () -> Unit,
    onOpenSettingsForKey: () -> Unit,
    onSwitchToFlashAndRetry: () -> Unit,
    onDraftChange: ((BookDraft) -> BookDraft) -> Unit,
    onDraftIsbn13Change: (String) -> Unit,
    onDraftIsbn10Change: (String) -> Unit,
    onSaveDraft: () -> Unit,
    onDiscardDraft: () -> Unit,
    onResolveDuplicate: (Boolean) -> Unit,
    onDismissDuplicate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    val isLoading = uiState.lookupState is LookupUiState.Loading

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Capture Book Card
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
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.capture_book_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.capture_book_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Collapsible Live Barcode Scanner
                AnimatedVisibility(
                    visible = uiState.scannerActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    BarcodeScannerSection(
                        onIsbnDetected = onBarcodeDetected,
                        onScannerError = onScannerError
                    )
                }

                // Primary Scan Barcode Button
                Button(
                    onClick = onToggleScanner,
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("toggle_barcode_scanner_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.scannerActive) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (uiState.scannerActive) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (uiState.scannerActive) Icons.Filled.Close else Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (uiState.scannerActive) R.string.action_stop_scanner else R.string.action_scan_barcode
                        ),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                // Vision OCR Row (Snap Camera Photo + Zero-Permission Photo Library)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnapPhotoClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("snap_ocr_photo_button"),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_snap_ocr),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }

                    OutlinedButton(
                        onClick = onPickPhotoClick,
                        enabled = !isLoading,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("pick_ocr_photo_button"),
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.action_pick_photo),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1
                        )
                    }
                }

                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = stringResource(R.string.manual_input_divider).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // Manual ISBN / Title Input + Lookup Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.manualIsbnInput,
                        onValueChange = onManualIsbnChanged,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.manual_isbn_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onLookupClick()
                            }
                        ),
                        shape = MaterialTheme.shapes.small,
                        colors = tomeTextFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("manual_isbn_input")
                    )

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onLookupClick()
                        },
                        enabled = !isLoading && uiState.manualIsbnInput.isNotBlank(),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("manual_lookup_button"),
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.action_lookup),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Live ISBN-10 <-> ISBN-13 Preview Pill
                if (uiState.manualIsbnInput.isNotBlank()) {
                    val parsed = uiState.parsedManualIsbn
                    val pillBg = if (parsed.isValid) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                    val pillFg = if (parsed.isValid) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(pillBg)
                            .border(
                                1.dp,
                                if (parsed.isValid) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("isbn_live_preview")
                    ) {
                        Text(
                            text = if (parsed.isValid) {
                                "${parsed.inputType} valid  ·  ISBN-13: ${parsed.isbn13.ifEmpty { "—" }}  ·  ISBN-10: ${parsed.isbn10.ifEmpty { "—" }}"
                            } else {
                                "Not a valid ISBN-10/13 check digit yet — you can still run an AI title/edition lookup."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = pillFg
                        )
                    }
                }

                // Subtle Manual Form shortcut for offline rare books
                if (uiState.reviewDraft == null && !isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onOpenBlankDraft,
                            modifier = Modifier.testTag("open_blank_draft_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Catalog Manually (Offline)",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        // Loading Status Card
        AnimatedVisibility(
            visible = uiState.lookupState is LookupUiState.Loading,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val loading = uiState.lookupState as? LookupUiState.Loading
            if (loading != null) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lookup_loading_card"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = loading.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = loading.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Authentic Error Surface Card
        AnimatedVisibility(
            visible = uiState.lookupState is LookupUiState.Error,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val error = uiState.lookupState as? LookupUiState.Error
            if (error != null) {
                val isQuotaError = error.message.contains("quota", ignoreCase = true) ||
                    error.message.contains("limit: 0", ignoreCase = true) ||
                    error.message.contains("429", ignoreCase = true) ||
                    error.message.contains("rate-limit", ignoreCase = true)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lookup_error_card"),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isQuotaError) "Quota Limit Reached" else "Lookup Error",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        if (isQuotaError) {
                            Text(
                                text = "Free Tier quota for ${uiState.settings.effectiveModelId} is exhausted or disabled. Add your own Paid Gemini API key in Settings to bypass this limit, or switch to gemini-3.8-flash.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onOpenSettingsForKey,
                                    modifier = Modifier
                                        .height(42.dp)
                                        .testTag("error_add_paid_key_button"),
                                    shape = MaterialTheme.shapes.small,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldContainer,
                                        contentColor = GoldAmber
                                    ),
                                    border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.5f)),
                                    contentPadding = PaddingValues(horizontal = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Key,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Add Paid API Key",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                if (uiState.settings.selectedModel != AiModelOption.FAST_FLASH) {
                                    OutlinedButton(
                                        onClick = onSwitchToFlashAndRetry,
                                        modifier = Modifier
                                            .height(42.dp)
                                            .testTag("error_switch_flash_button"),
                                        shape = MaterialTheme.shapes.small,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bolt,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Use Flash & Retry",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = error.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismissError) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (!error.lastQuery.isNullOrBlank()) {
                                TextButton(onClick = onLookupClick) {
                                    Text(
                                        text = stringResource(R.string.action_retry),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Editable Review & Save Card (Libib Layout)
        AnimatedVisibility(
            visible = uiState.reviewDraft != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            val draft = uiState.reviewDraft
            if (draft != null) {
                ReviewBookCard(
                    draft = draft,
                    availableGroups = uiState.settings.groups,
                    onDraftChange = onDraftChange,
                    onIsbn13Change = onDraftIsbn13Change,
                    onIsbn10Change = onDraftIsbn10Change,
                    onSave = onSaveDraft,
                    onDiscard = onDiscardDraft
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Duplicate ISBN Confirmation Dialog
    val duplicate = uiState.duplicatePrompt
    if (duplicate != null) {
        AlertDialog(
            onDismissRequest = onDismissDuplicate,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Duplicate ISBN Found") },
            text = {
                Text(
                    "\"${duplicate.existingBook.title.ifBlank { "Untitled" }}\" already exists in your library with this ISBN. Would you like to update the existing entry or save this as a separate copy?"
                )
            },
            confirmButton = {
                Button(
                    onClick = { onResolveDuplicate(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Update Existing")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onDismissDuplicate) {
                        Text(stringResource(R.string.action_cancel))
                    }
                    TextButton(onClick = { onResolveDuplicate(false) }) {
                        Text("Save Copy", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        )
    }
}
