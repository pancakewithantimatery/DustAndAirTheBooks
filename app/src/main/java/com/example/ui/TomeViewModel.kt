package com.example.ui

import android.app.Application
import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.BookAiRepository
import com.example.data.export.LibraryExporter
import com.example.data.local.AppSettings
import com.example.data.local.BookRepository
import com.example.data.local.SettingsRepository
import com.example.data.local.TomeDatabase
import com.example.domain.AiModelOption
import com.example.domain.ArchivistProgress
import com.example.domain.ArchivistRankEngine
import com.example.domain.BookRecord
import com.example.domain.DiscoveredGeminiModel
import com.example.domain.GroundingSource
import com.example.domain.IsbnUtils
import com.example.domain.ParsedIsbn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "TomeViewModel"

enum class TomeTab {
    SCAN,
    LIBRARY,
    SETTINGS
}

sealed interface LookupUiState {
    data object Idle : LookupUiState
    data class Loading(val title: String, val subtitle: String) : LookupUiState
    data class Error(val message: String, val lastQuery: String? = null) : LookupUiState
}

sealed interface ModelDiscoveryState {
    data object Idle : ModelDiscoveryState
    data object Loading : ModelDiscoveryState
    data class Loaded(val models: List<DiscoveredGeminiModel>) : ModelDiscoveryState
    data class Error(val message: String) : ModelDiscoveryState
}

data class BookDraft(
    val existingId: String? = null,
    val title: String = "",
    val authors: String = "",
    val description: String = "",
    val publisher: String = "",
    val publishYear: String = "",
    val publishMonth: String = "",
    val publishDay: String = "",
    val isbn13: String = "",
    val isbn10: String = "",
    val pagesText: String = "",
    val group: String = "Home Library",
    val notes: String = "",
    val tagsText: String = "",
    val groundingSources: List<GroundingSource> = emptyList()
) {
    val isEdit: Boolean get() = existingId != null

    fun toBookRecord(defaultGroup: String, overrideId: String? = null): BookRecord {
        val pair = IsbnUtils.normalizePair(isbn13, isbn10)
        val parsedTags = tagsText
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return BookRecord(
            id = overrideId ?: existingId ?: "book_${UUID.randomUUID()}",
            title = title.trim(),
            authors = authors.trim(),
            description = description.trim(),
            publisher = publisher.trim(),
            publishYear = publishYear.filter { it.isDigit() }.take(4),
            publishMonth = publishMonth.filter { it.isDigit() }.take(2),
            publishDay = publishDay.filter { it.isDigit() }.take(2),
            isbn13 = pair.isbn13,
            isbn10 = pair.isbn10,
            pages = pagesText.filter { it.isDigit() }.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            group = group.trim().ifEmpty { defaultGroup },
            notes = notes.trim(),
            tags = parsedTags,
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun fromBookRecord(
            record: BookRecord,
            sources: List<GroundingSource> = emptyList(),
            isEdit: Boolean = false
        ): BookDraft {
            val pair = IsbnUtils.normalizePair(record.isbn13, record.isbn10)
            return BookDraft(
                existingId = if (isEdit) record.id else null,
                title = record.title,
                authors = record.authors,
                description = record.description,
                publisher = record.publisher,
                publishYear = record.publishYear,
                publishMonth = record.publishMonth,
                publishDay = record.publishDay,
                isbn13 = pair.isbn13,
                isbn10 = pair.isbn10,
                pagesText = if (record.pages > 0) record.pages.toString() else "",
                group = record.group.ifBlank { "Home Library" },
                notes = record.notes,
                tagsText = record.tags.joinToString(", "),
                groundingSources = sources
            )
        }
    }
}

data class DuplicateBookPrompt(
    val existingBook: BookRecord,
    val pendingDraft: BookDraft
)

data class TomeUiState(
    val activeTab: TomeTab = TomeTab.SCAN,
    val books: List<BookRecord> = emptyList(),
    val filteredBooks: List<BookRecord> = emptyList(),
    val archivistProgress: ArchivistProgress = ArchivistRankEngine.calculate(0, 0),
    val settings: AppSettings = AppSettings(),
    val modelDiscoveryState: ModelDiscoveryState = ModelDiscoveryState.Idle,
    val alternateModelSuggestion: String = AiModelOption.LITE_BATCH.modelId,
    val manualIsbnInput: String = "",
    val parsedManualIsbn: ParsedIsbn = IsbnUtils.parseSingle(""),
    val scannerActive: Boolean = false,
    val lookupState: LookupUiState = LookupUiState.Idle,
    val reviewDraft: BookDraft? = null,
    val duplicatePrompt: DuplicateBookPrompt? = null,
    val searchQuery: String = "",
    val selectedShelfFilter: String = "ALL",
    val snackbarMessage: String? = null
)

private data class TransientScanState(
    val activeTab: TomeTab = TomeTab.SCAN,
    val manualIsbnInput: String = "",
    val scannerActive: Boolean = false,
    val lookupState: LookupUiState = LookupUiState.Idle,
    val modelDiscoveryState: ModelDiscoveryState = ModelDiscoveryState.Idle,
    val reviewDraft: BookDraft? = null,
    val duplicatePrompt: DuplicateBookPrompt? = null,
    val searchQuery: String = "",
    val selectedShelfFilter: String = "ALL",
    val snackbarMessage: String? = null
)

class TomeViewModel(application: Application) : AndroidViewModel(application) {

    private val bookRepository = BookRepository(
        TomeDatabase.getInstance(application).bookDao()
    )
    private val settingsRepository = SettingsRepository(application)
    private val aiRepository = BookAiRepository()

    private val transientState = MutableStateFlow(TransientScanState())

    val uiState: StateFlow<TomeUiState> = combine(
        bookRepository.allBooks,
        settingsRepository.settings,
        transientState
    ) { books, settings, transient ->
        val totalPages = books.sumOf { it.pages.coerceAtLeast(0) }
        val progress = ArchivistRankEngine.calculate(books.size, totalPages)
        val shelfFilter = if (transient.selectedShelfFilter == "ALL" ||
            settings.groups.contains(transient.selectedShelfFilter)
        ) {
            transient.selectedShelfFilter
        } else {
            "ALL"
        }
        val query = transient.searchQuery.trim().lowercase()
        val filtered = books.filter { book ->
            val shelfMatch = shelfFilter == "ALL" || book.group == shelfFilter
            if (!shelfMatch) return@filter false
            if (query.isEmpty()) return@filter true
            val haystack = buildString {
                append(book.title).append(' ')
                append(book.authors).append(' ')
                append(book.publisher).append(' ')
                append(book.isbn13).append(' ')
                append(book.isbn10).append(' ')
                append(book.notes).append(' ')
                append(book.group).append(' ')
                append(book.tags.joinToString(" "))
            }.lowercase()
            haystack.contains(query)
        }

        val alternateModel = resolveAlternateModel(
            currentModelId = settings.effectiveModelId,
            isUsingPaidKey = settings.isUsingPaidKey,
            discoveryState = transient.modelDiscoveryState
        )

        TomeUiState(
            activeTab = transient.activeTab,
            books = books,
            filteredBooks = filtered,
            archivistProgress = progress,
            settings = settings,
            modelDiscoveryState = transient.modelDiscoveryState,
            alternateModelSuggestion = alternateModel,
            manualIsbnInput = transient.manualIsbnInput,
            parsedManualIsbn = IsbnUtils.parseSingle(transient.manualIsbnInput),
            scannerActive = transient.scannerActive,
            lookupState = transient.lookupState,
            reviewDraft = transient.reviewDraft,
            duplicatePrompt = transient.duplicatePrompt,
            searchQuery = transient.searchQuery,
            selectedShelfFilter = shelfFilter,
            snackbarMessage = transient.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TomeUiState()
    )

    init {
        viewModelScope.launch {
            settingsRepository.settings
                .map { Triple(it.isUsingPaidKey, it.customApiKey, it.effectiveModelId) }
                .distinctUntilChanged { old, new -> old.first == new.first && old.second == new.second }
                .collect { (usingPaidKey, apiKey, currentModelId) ->
                    if (usingPaidKey && apiKey.isNotBlank()) {
                        discoverModelsForKey(apiKey = apiKey, currentModelId = currentModelId, autoSelectIfMissing = true)
                    } else {
                        transientState.update { it.copy(modelDiscoveryState = ModelDiscoveryState.Idle) }
                    }
                }
        }
    }

    private fun resolveAlternateModel(
        currentModelId: String,
        isUsingPaidKey: Boolean,
        discoveryState: ModelDiscoveryState
    ): String {
        if (isUsingPaidKey && discoveryState is ModelDiscoveryState.Loaded) {
            val discoveredAlt = discoveryState.models.firstOrNull {
                !it.id.equals(currentModelId, ignoreCase = true)
            }
            if (discoveredAlt != null) return discoveredAlt.id
        }
        return if (currentModelId.equals(AiModelOption.FAST_FLASH.modelId, ignoreCase = true)) {
            AiModelOption.LITE_BATCH.modelId
        } else {
            AiModelOption.FAST_FLASH.modelId
        }
    }

    fun refreshAvailableModels() {
        val currentSettings = uiState.value.settings
        if (!currentSettings.isUsingPaidKey) {
            showSnackbar("Add and enable a Paid API key to query models.list.")
            return
        }
        viewModelScope.launch {
            discoverModelsForKey(
                apiKey = currentSettings.customApiKey,
                currentModelId = currentSettings.effectiveModelId,
                autoSelectIfMissing = true
            )
        }
    }

    private suspend fun discoverModelsForKey(
        apiKey: String,
        currentModelId: String,
        autoSelectIfMissing: Boolean
    ) {
        transientState.update { it.copy(modelDiscoveryState = ModelDiscoveryState.Loading) }
        try {
            val models = aiRepository.fetchAvailableModels(apiKey)
            transientState.update { it.copy(modelDiscoveryState = ModelDiscoveryState.Loaded(models)) }

            if (autoSelectIfMissing && models.isNotEmpty()) {
                val currentSupported = models.any { it.id.equals(currentModelId, ignoreCase = true) }
                if (!currentSupported) {
                    val bestModel = models.first().id
                    applyModelId(bestModel)
                    showSnackbar("Verified via models.list — auto-selected $bestModel")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "models.list discovery failed", e)
            transientState.update {
                it.copy(
                    modelDiscoveryState = ModelDiscoveryState.Error(
                        e.localizedMessage ?: "Failed to query models.list."
                    )
                )
            }
        }
    }

    private suspend fun applyModelId(modelId: String) {
        val preset = AiModelOption.presetMatchingId(modelId)
        if (preset != null) {
            settingsRepository.setModel(preset)
        } else {
            settingsRepository.setCustomModelId(modelId)
            settingsRepository.setModel(AiModelOption.CUSTOM)
        }
    }

    fun selectTab(tab: TomeTab) {
        transientState.update {
            it.copy(
                activeTab = tab,
                scannerActive = if (tab != TomeTab.SCAN) false else it.scannerActive
            )
        }
    }

    fun setScannerActive(active: Boolean) {
        transientState.update { it.copy(scannerActive = active) }
    }

    fun onManualIsbnChanged(input: String) {
        transientState.update { it.copy(manualIsbnInput = input) }
    }

    fun onBarcodeDetected(rawCode: String) {
        val parsed = IsbnUtils.parseSingle(rawCode)
        if (!parsed.isValid) return
        val preferred = parsed.isbn13.ifEmpty { parsed.isbn10 }
        transientState.update {
            it.copy(
                scannerActive = false,
                manualIsbnInput = preferred
            )
        }
        lookupByIsbnOrQuery(preferred)
    }

    fun lookupByIsbnOrQuery(rawQuery: String = transientState.value.manualIsbnInput) {
        val query = rawQuery.trim()
        if (query.isEmpty()) {
            showSnackbar("Enter an ISBN-10, ISBN-13, or book title first.")
            return
        }
        val currentSettings = uiState.value.settings
        val modelId = currentSettings.effectiveModelId
        val paidKey = if (currentSettings.isUsingPaidKey) currentSettings.customApiKey else null
        val backendTag = if (paidKey != null) "Paid API Key" else "Firebase AI Logic"
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    scannerActive = false,
                    lookupState = LookupUiState.Loading(
                        title = "Searching bibliographic records…",
                        subtitle = "$modelId · $backendTag · Google Search"
                    )
                )
            }
            try {
                val result = aiRepository.lookupByIsbnOrQuery(
                    query = query,
                    modelId = modelId,
                    defaultGroup = currentSettings.defaultGroup,
                    customApiKey = paidKey
                )
                transientState.update {
                    it.copy(
                        lookupState = LookupUiState.Idle,
                        reviewDraft = BookDraft.fromBookRecord(
                            record = result.record,
                            sources = result.sources,
                            isEdit = false
                        ),
                        snackbarMessage = "Bibliographic record ready for review."
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "ISBN lookup timed out", e)
                openManualFallbackIfValidIsbn(
                    query = query,
                    defaultGroup = currentSettings.defaultGroup,
                    errorMessage = "Lookup timed out after 60 seconds. Check your connection and try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "ISBN lookup failed", e)
                openManualFallbackIfValidIsbn(
                    query = query,
                    defaultGroup = currentSettings.defaultGroup,
                    errorMessage = e.localizedMessage ?: "Bibliographic lookup failed. Please try again."
                )
            }
        }
    }

    fun lookupFromPhotoUri(uri: Uri, contentResolver: ContentResolver) {
        val currentSettings = uiState.value.settings
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    scannerActive = false,
                    lookupState = LookupUiState.Loading(
                        title = "Analyzing photo with Gemini Vision…",
                        subtitle = "Extracting ISBN / title page & grounding metadata"
                    )
                )
            }
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    decodeBitmapFromUri(uri, contentResolver)
                } ?: throw IllegalStateException("Could not decode the selected image.")

                lookupFromBitmapInternal(bitmap, currentSettings)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Photo URI lookup failed", e)
                transientState.update {
                    it.copy(
                        lookupState = LookupUiState.Error(
                            message = e.localizedMessage ?: "Failed to analyze photo."
                        )
                    )
                }
            }
        }
    }

    fun lookupFromBitmap(bitmap: Bitmap) {
        val currentSettings = uiState.value.settings
        viewModelScope.launch {
            transientState.update {
                it.copy(
                    scannerActive = false,
                    lookupState = LookupUiState.Loading(
                        title = "Analyzing photo with Gemini Vision…",
                        subtitle = "Extracting ISBN / title page & grounding metadata"
                    )
                )
            }
            lookupFromBitmapInternal(bitmap, currentSettings)
        }
    }

    private suspend fun lookupFromBitmapInternal(bitmap: Bitmap, currentSettings: AppSettings) {
        val modelId = currentSettings.effectiveModelId
        val paidKey = if (currentSettings.isUsingPaidKey) currentSettings.customApiKey else null
        try {
            val result = aiRepository.lookupByPhoto(
                bitmap = bitmap,
                modelId = modelId,
                defaultGroup = currentSettings.defaultGroup,
                customApiKey = paidKey
            )
            transientState.update {
                it.copy(
                    lookupState = LookupUiState.Idle,
                    reviewDraft = BookDraft.fromBookRecord(
                        record = result.record,
                        sources = result.sources,
                        isEdit = false
                    ),
                    snackbarMessage = "Photo analyzed. Review details before saving."
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Photo OCR timed out", e)
            transientState.update {
                it.copy(
                    lookupState = LookupUiState.Error(
                        message = "Vision OCR timed out after 60 seconds. Please try again."
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Photo OCR failed", e)
            transientState.update {
                it.copy(
                    lookupState = LookupUiState.Error(
                        message = e.localizedMessage ?: "Vision OCR analysis failed."
                    )
                )
            }
        }
    }

    private fun openManualFallbackIfValidIsbn(
        query: String,
        defaultGroup: String,
        errorMessage: String
    ) {
        val parsed = IsbnUtils.parseSingle(query)
        transientState.update {
            it.copy(
                lookupState = LookupUiState.Error(message = errorMessage, lastQuery = query),
                reviewDraft = if (parsed.isValid) {
                    BookDraft(
                        isbn13 = parsed.isbn13,
                        isbn10 = parsed.isbn10,
                        group = defaultGroup
                    )
                } else {
                    it.reviewDraft
                }
            )
        }
    }

    fun openBlankDraft() {
        val parsed = IsbnUtils.parseSingle(transientState.value.manualIsbnInput)
        val defaultGroup = uiState.value.settings.defaultGroup
        transientState.update {
            it.copy(
                lookupState = LookupUiState.Idle,
                reviewDraft = BookDraft(
                    isbn13 = if (parsed.isValid) parsed.isbn13 else "",
                    isbn10 = if (parsed.isValid) parsed.isbn10 else "",
                    group = defaultGroup
                )
            )
        }
    }

    fun updateDraft(transform: (BookDraft) -> BookDraft) {
        transientState.update { state ->
            val current = state.reviewDraft ?: return@update state
            state.copy(reviewDraft = transform(current))
        }
    }

    fun onDraftIsbn13Changed(raw: String) {
        updateDraft { draft ->
            val cleaned = IsbnUtils.clean(raw).take(13)
            val converted10 = if (IsbnUtils.isValidIsbn13(cleaned)) {
                IsbnUtils.toIsbn10(cleaned)
            } else {
                draft.isbn10
            }
            draft.copy(isbn13 = cleaned, isbn10 = converted10)
        }
    }

    fun onDraftIsbn10Changed(raw: String) {
        updateDraft { draft ->
            val cleaned = IsbnUtils.clean(raw).take(10)
            val converted13 = if (IsbnUtils.isValidIsbn10(cleaned)) {
                IsbnUtils.toIsbn13(cleaned)
            } else {
                draft.isbn13
            }
            draft.copy(isbn10 = cleaned, isbn13 = converted13)
        }
    }

    fun discardDraft() {
        transientState.update {
            it.copy(
                reviewDraft = null,
                lookupState = LookupUiState.Idle,
                duplicatePrompt = null
            )
        }
    }

    fun dismissLookupError() {
        transientState.update { it.copy(lookupState = LookupUiState.Idle) }
    }

    fun attemptSaveDraft() {
        val draft = transientState.value.reviewDraft ?: return
        if (draft.title.trim().isEmpty()) {
            showSnackbar("Book title is required before saving.")
            return
        }
        val defaultGroup = uiState.value.settings.defaultGroup
        viewModelScope.launch {
            val pair = IsbnUtils.normalizePair(draft.isbn13, draft.isbn10)
            if (!draft.isEdit && (pair.isbn13.isNotEmpty() || pair.isbn10.isNotEmpty())) {
                val existing = bookRepository.findDuplicateByIsbn(pair.isbn13, pair.isbn10)
                if (existing != null) {
                    transientState.update {
                        it.copy(duplicatePrompt = DuplicateBookPrompt(existing, draft))
                    }
                    return@launch
                }
            }
            persistDraft(draft, defaultGroup, overrideId = null, isUpdate = draft.isEdit)
        }
    }

    fun resolveDuplicate(updateExisting: Boolean) {
        val prompt = transientState.value.duplicatePrompt ?: return
        val defaultGroup = uiState.value.settings.defaultGroup
        viewModelScope.launch {
            persistDraft(
                draft = prompt.pendingDraft,
                defaultGroup = defaultGroup,
                overrideId = if (updateExisting) prompt.existingBook.id else null,
                isUpdate = updateExisting
            )
        }
    }

    fun dismissDuplicatePrompt() {
        transientState.update { it.copy(duplicatePrompt = null) }
    }

    private suspend fun persistDraft(
        draft: BookDraft,
        defaultGroup: String,
        overrideId: String?,
        isUpdate: Boolean
    ) {
        val record = draft.toBookRecord(defaultGroup = defaultGroup, overrideId = overrideId)
        bookRepository.saveBook(record)
        settingsRepository.addGroup(record.group)
        transientState.update {
            it.copy(
                reviewDraft = null,
                duplicatePrompt = null,
                lookupState = LookupUiState.Idle,
                manualIsbnInput = "",
                snackbarMessage = if (isUpdate) "Book updated in library." else "Book saved to library!"
            )
        }
    }

    fun editBook(book: BookRecord) {
        transientState.update {
            it.copy(
                activeTab = TomeTab.SCAN,
                scannerActive = false,
                lookupState = LookupUiState.Idle,
                reviewDraft = BookDraft.fromBookRecord(book, emptyList(), isEdit = true)
            )
        }
    }

    fun deleteBook(book: BookRecord) {
        viewModelScope.launch {
            bookRepository.deleteBook(book.id)
            showSnackbar("Deleted \"${book.title.ifBlank { "Untitled" }}\".")
        }
    }

    fun clearLibrary() {
        viewModelScope.launch {
            bookRepository.clearAll()
            showSnackbar("All saved books cleared.")
        }
    }

    fun onSearchQueryChanged(query: String) {
        transientState.update { it.copy(searchQuery = query) }
    }

    fun onShelfFilterChanged(shelf: String) {
        transientState.update { it.copy(selectedShelfFilter = shelf) }
    }

    fun selectModel(option: AiModelOption) {
        viewModelScope.launch {
            settingsRepository.setModel(option)
            showSnackbar("Model switched to ${option.label}")
        }
    }

    fun setCustomModelId(modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.setCustomModelId(trimmed)
            settingsRepository.setModel(AiModelOption.CUSTOM)
            showSnackbar("Custom model set to $trimmed")
        }
    }

    fun saveCustomApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            showSnackbar("Paste a valid Google AI Studio API key first.")
            return
        }
        viewModelScope.launch {
            settingsRepository.saveCustomApiKey(trimmed)
            showSnackbar("Paid API key saved locally & enabled.")
        }
    }

    fun clearCustomApiKey() {
        viewModelScope.launch {
            settingsRepository.clearCustomApiKey()
            showSnackbar("Custom API key removed. Using Firebase AI Logic.")
        }
    }

    fun setUseCustomApiKey(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setUseCustomApiKey(enabled)
            showSnackbar(
                if (enabled) "Switched to Paid API Key (BYOK)."
                else "Switched to built-in Firebase AI Logic."
            )
        }
    }

    fun selectDiscoveredModel(modelId: String) {
        val trimmed = modelId.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            applyModelId(trimmed)
            showSnackbar("Model switched to $trimmed")
        }
    }

    fun switchToAlternateModelAndRetry() {
        val state = uiState.value
        val targetModelId = state.alternateModelSuggestion
        val lastQuery = (transientState.value.lookupState as? LookupUiState.Error)?.lastQuery
            ?: transientState.value.manualIsbnInput
        viewModelScope.launch {
            applyModelId(targetModelId)
            if (lastQuery.isNotBlank()) {
                val settings = uiState.value.settings
                val paidKey = if (settings.isUsingPaidKey) settings.customApiKey else null
                val backendTag = if (paidKey != null) "Paid API Key" else "Firebase AI Logic"
                transientState.update {
                    it.copy(
                        lookupState = LookupUiState.Loading(
                            title = "Retrying with $targetModelId…",
                            subtitle = "$backendTag · Google Search Grounding"
                        )
                    )
                }
                try {
                    val result = aiRepository.lookupByIsbnOrQuery(
                        query = lastQuery,
                        modelId = targetModelId,
                        defaultGroup = settings.defaultGroup,
                        customApiKey = paidKey
                    )
                    transientState.update {
                        it.copy(
                            lookupState = LookupUiState.Idle,
                            reviewDraft = BookDraft.fromBookRecord(
                                record = result.record,
                                sources = result.sources,
                                isEdit = false
                            ),
                            snackbarMessage = "Switched to $targetModelId. Record ready."
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Alternate model retry failed", e)
                    openManualFallbackIfValidIsbn(
                        query = lastQuery,
                        defaultGroup = settings.defaultGroup,
                        errorMessage = e.localizedMessage ?: "Lookup failed."
                    )
                }
            }
        }
    }

    fun setDefaultGroup(group: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultGroup(group)
            showSnackbar("Default shelf set to $group")
        }
    }

    fun addGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            settingsRepository.addGroup(trimmed)
            showSnackbar("Added shelf \"$trimmed\".")
        }
    }

    fun removeGroup(name: String) {
        viewModelScope.launch {
            settingsRepository.removeGroup(name)
            showSnackbar("Removed shelf \"$name\".")
        }
    }

    // --- Export & Restore via Android Storage Access Framework ---

    fun exportLibibCsv(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val books = bookRepository.getAllBooksOnce()
                if (books.isEmpty()) {
                    showSnackbar("Library is empty.")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val csv = LibraryExporter.generateLibibCsv(books)
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output file.")
                }
                showSnackbar("Libib CSV exported (${books.size} books).")
            } catch (e: Exception) {
                Log.e(TAG, "CSV export failed", e)
                showSnackbar("Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun exportCombinedMarkdown(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val books = bookRepository.getAllBooksOnce()
                if (books.isEmpty()) {
                    showSnackbar("Library is empty.")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    val md = LibraryExporter.generateCombinedMarkdown(books)
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(md.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output file.")
                }
                showSnackbar("NotebookLM Markdown catalog exported.")
            } catch (e: Exception) {
                Log.e(TAG, "Markdown export failed", e)
                showSnackbar("Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun exportObsidianZip(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val books = bookRepository.getAllBooksOnce()
                if (books.isEmpty()) {
                    showSnackbar("Library is empty.")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        LibraryExporter.writeObsidianZip(books, out)
                    } ?: throw IllegalStateException("Could not open output file.")
                }
                showSnackbar("Obsidian Markdown ZIP exported (${books.size} notes).")
            } catch (e: Exception) {
                Log.e(TAG, "Obsidian ZIP export failed", e)
                showSnackbar("Export failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun exportJsonBackup(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val books = bookRepository.getAllBooksOnce()
                val groups = uiState.value.settings.groups
                withContext(Dispatchers.IO) {
                    val json = LibraryExporter.generateJsonBackup(books, groups)
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    } ?: throw IllegalStateException("Could not open output file.")
                }
                showSnackbar("JSON backup saved (${books.size} books).")
            } catch (e: Exception) {
                Log.e(TAG, "JSON backup failed", e)
                showSnackbar("Backup failed: ${e.localizedMessage ?: "Unknown error"}")
            }
        }
    }

    fun restoreJsonBackup(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val raw = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: throw IllegalStateException("Could not read backup file.")
                    LibraryExporter.parseJsonBackup(raw)
                }
                if (imported.groups.isNotEmpty()) {
                    settingsRepository.mergeGroups(imported.groups)
                }
                val extraGroups = imported.books.map { it.group }.distinct()
                if (extraGroups.isNotEmpty()) {
                    settingsRepository.mergeGroups(extraGroups)
                }
                bookRepository.saveBooks(imported.books)
                showSnackbar("Restored ${imported.books.size} books from JSON backup.")
            } catch (e: Exception) {
                Log.e(TAG, "JSON restore failed", e)
                showSnackbar("Restore failed: ${e.localizedMessage ?: "Invalid backup file"}")
            }
        }
    }

    fun showSnackbar(message: String) {
        transientState.update { it.copy(snackbarMessage = message) }
    }

    fun consumeSnackbar() {
        transientState.update { it.copy(snackbarMessage = null) }
    }

    private fun decodeBitmapFromUri(uri: Uri, contentResolver: ContentResolver): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
            }
        } else {
            @Suppress("DEPRECATION")
            contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }
    }
}
