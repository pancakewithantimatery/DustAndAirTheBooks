package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.data.export.LibraryExporter
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ScanScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.GoldAmber
import com.example.ui.theme.GoldContainer
import java.io.File

private enum class PendingCameraAction {
    NONE,
    BARCODE_SCANNER,
    SNAP_OCR_PHOTO
}

@Composable
fun TomeScannerApp(
    viewModel: TomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingCameraAction by remember { mutableStateOf(PendingCameraAction.NONE) }
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // Camera photo capture launcher (for old ISBN-10 title/copyright page OCR)
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) {
            viewModel.lookupFromPhotoUri(uri, context.contentResolver)
        }
    }

    // Zero-permission Android Photo Picker launcher (Google Play Policy compliant)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.lookupFromPhotoUri(uri, context.contentResolver)
        }
    }

    // Runtime Camera permission launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = PendingCameraAction.NONE
        if (granted) {
            when (action) {
                PendingCameraAction.BARCODE_SCANNER -> viewModel.setScannerActive(true)
                PendingCameraAction.SNAP_OCR_PHOTO -> {
                    val uri = createTempOcrUri(context)
                    pendingCaptureUri = uri
                    takePictureLauncher.launch(uri)
                }
                PendingCameraAction.NONE -> Unit
            }
        } else {
            viewModel.showSnackbar("Camera permission is needed to scan barcodes or snap title pages.")
        }
    }

    // Storage Access Framework (SAF) launchers for zero-permission exports & JSON restore
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) viewModel.exportLibibCsv(uri, context.contentResolver)
    }

    val exportMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) viewModel.exportCombinedMarkdown(uri, context.contentResolver)
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.exportObsidianZip(uri, context.contentResolver)
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportJsonBackup(uri, context.contentResolver)
    }

    val restoreJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.restoreJsonBackup(uri, context.contentResolver)
    }

    // Display snackbar notifications
    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeSnackbar()
        }
    }

    // Back handling for secondary tabs or open scanner/draft
    BackHandler(
        enabled = uiState.activeTab != TomeTab.SCAN || uiState.scannerActive || uiState.reviewDraft != null
    ) {
        when {
            uiState.scannerActive -> viewModel.setScannerActive(false)
            uiState.activeTab != TomeTab.SCAN -> viewModel.selectTab(TomeTab.SCAN)
            uiState.reviewDraft != null -> viewModel.discardDraft()
        }
    }

    fun requestOrRunCameraAction(action: PendingCameraAction) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            when (action) {
                PendingCameraAction.BARCODE_SCANNER -> viewModel.setScannerActive(true)
                PendingCameraAction.SNAP_OCR_PHOTO -> {
                    val uri = createTempOcrUri(context)
                    pendingCaptureUri = uri
                    takePictureLauncher.launch(uri)
                }
                PendingCameraAction.NONE -> Unit
            }
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TomeTopBar(
                activeModelLabel = uiState.settings.effectiveModelId,
                isUsingPaidKey = uiState.settings.isUsingPaidKey,
                onModelPillClick = { viewModel.selectTab(TomeTab.SETTINGS) }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(12.dp)
                    )
                )
            }
        },
        bottomBar = {
            TomeBottomBar(
                activeTab = uiState.activeTab,
                onTabSelected = viewModel::selectTab
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
            ) {
                when (uiState.activeTab) {
                    TomeTab.SCAN -> ScanScreen(
                        uiState = uiState,
                        onToggleScanner = {
                            if (uiState.scannerActive) {
                                viewModel.setScannerActive(false)
                            } else {
                                requestOrRunCameraAction(PendingCameraAction.BARCODE_SCANNER)
                            }
                        },
                        onBarcodeDetected = viewModel::onBarcodeDetected,
                        onScannerError = { msg ->
                            viewModel.setScannerActive(false)
                            viewModel.showSnackbar("Camera error: $msg")
                        },
                        onSnapPhotoClick = {
                            viewModel.setScannerActive(false)
                            requestOrRunCameraAction(PendingCameraAction.SNAP_OCR_PHOTO)
                        },
                        onPickPhotoClick = {
                            viewModel.setScannerActive(false)
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onManualIsbnChanged = viewModel::onManualIsbnChanged,
                        onLookupClick = { viewModel.lookupByIsbnOrQuery() },
                        onOpenBlankDraft = viewModel::openBlankDraft,
                        onDismissError = viewModel::dismissLookupError,
                        onOpenSettingsForKey = { viewModel.selectTab(TomeTab.SETTINGS) },
                        onSwitchToFlashAndRetry = viewModel::switchToFlashAndRetry,
                        onDraftChange = viewModel::updateDraft,
                        onDraftIsbn13Change = viewModel::onDraftIsbn13Changed,
                        onDraftIsbn10Change = viewModel::onDraftIsbn10Changed,
                        onSaveDraft = viewModel::attemptSaveDraft,
                        onDiscardDraft = viewModel::discardDraft,
                        onResolveDuplicate = viewModel::resolveDuplicate,
                        onDismissDuplicate = viewModel::dismissDuplicatePrompt
                    )

                    TomeTab.LIBRARY -> LibraryScreen(
                        uiState = uiState,
                        onSearchQueryChanged = viewModel::onSearchQueryChanged,
                        onShelfFilterChanged = viewModel::onShelfFilterChanged,
                        onExportCsv = {
                            if (uiState.books.isEmpty()) {
                                viewModel.showSnackbar("Library is empty.")
                            } else {
                                exportCsvLauncher.launch("tomescanner-libib-${LibraryExporter.todayStamp()}.csv")
                            }
                        },
                        onExportMdSingle = {
                            if (uiState.books.isEmpty()) {
                                viewModel.showSnackbar("Library is empty.")
                            } else {
                                exportMdLauncher.launch("tomescanner-catalog-${LibraryExporter.todayStamp()}.md")
                            }
                        },
                        onExportMdZip = {
                            if (uiState.books.isEmpty()) {
                                viewModel.showSnackbar("Library is empty.")
                            } else {
                                exportZipLauncher.launch("tomescanner-obsidian-${LibraryExporter.todayStamp()}.zip")
                            }
                        },
                        onExportJson = {
                            exportJsonLauncher.launch("tomescanner-backup-${LibraryExporter.todayStamp()}.json")
                        },
                        onRestoreJson = {
                            restoreJsonLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        onEditBook = viewModel::editBook,
                        onDeleteBook = viewModel::deleteBook,
                        onNavigateToScan = { viewModel.selectTab(TomeTab.SCAN) }
                    )

                    TomeTab.SETTINGS -> SettingsScreen(
                        uiState = uiState,
                        onSelectModel = viewModel::selectModel,
                        onSetCustomModelId = viewModel::setCustomModelId,
                        onSaveCustomApiKey = viewModel::saveCustomApiKey,
                        onClearCustomApiKey = viewModel::clearCustomApiKey,
                        onToggleUseCustomApiKey = viewModel::setUseCustomApiKey,
                        onSetDefaultGroup = viewModel::setDefaultGroup,
                        onAddGroup = viewModel::addGroup,
                        onRemoveGroup = viewModel::removeGroup,
                        onClearLibrary = viewModel::clearLibrary
                    )
                }
            }
        }
    }
}

@Composable
private fun TomeTopBar(
    activeModelLabel: String,
    isUsingPaidKey: Boolean,
    onModelPillClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.96f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .shadow(8.dp, CircleShape, ambientColor = MaterialTheme.colorScheme.primary, spotColor = MaterialTheme.colorScheme.primary)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isUsingPaidKey) GoldContainer else MaterialTheme.colorScheme.primaryContainer
                    )
                    .border(
                        1.dp,
                        if (isUsingPaidKey) GoldAmber.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        RoundedCornerShape(999.dp)
                    )
                    .clickable(onClick = onModelPillClick)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("active_model_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (isUsingPaidKey) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = "Paid API key active",
                        tint = GoldAmber,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = activeModelLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUsingPaidKey) GoldAmber else MaterialTheme.colorScheme.primary
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun TomeBottomBar(
    activeTab: TomeTab,
    onTabSelected: (TomeTab) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets.navigationBars
        ) {
            NavigationBarItem(
                selected = activeTab == TomeTab.SCAN,
                onClick = { onTabSelected(TomeTab.SCAN) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == TomeTab.SCAN) Icons.Filled.QrCodeScanner else Icons.Outlined.QrCodeScanner,
                        contentDescription = stringResource(R.string.tab_scan)
                    )
                },
                label = { Text(stringResource(R.string.tab_scan)) },
                colors = tomeNavItemColors(),
                modifier = Modifier.testTag("nav_tab_scan")
            )
            NavigationBarItem(
                selected = activeTab == TomeTab.LIBRARY,
                onClick = { onTabSelected(TomeTab.LIBRARY) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == TomeTab.LIBRARY) Icons.AutoMirrored.Filled.MenuBook else Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = stringResource(R.string.tab_library)
                    )
                },
                label = { Text(stringResource(R.string.tab_library)) },
                colors = tomeNavItemColors(),
                modifier = Modifier.testTag("nav_tab_library")
            )
            NavigationBarItem(
                selected = activeTab == TomeTab.SETTINGS,
                onClick = { onTabSelected(TomeTab.SETTINGS) },
                icon = {
                    Icon(
                        imageVector = if (activeTab == TomeTab.SETTINGS) Icons.Filled.Settings else Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.tab_settings)
                    )
                },
                label = { Text(stringResource(R.string.tab_settings)) },
                colors = tomeNavItemColors(),
                modifier = Modifier.testTag("nav_tab_settings")
            )
        }
    }
}

@Composable
private fun tomeNavItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
)

private fun createTempOcrUri(context: Context): Uri {
    val ocrDir = File(context.cacheDir, "ocr").apply { mkdirs() }
    val imageFile = File(ocrDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}
