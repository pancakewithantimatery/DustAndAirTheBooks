package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ArchivistProgress
import com.example.domain.BookRecord
import com.example.ui.TomeUiState
import com.example.ui.components.tomeTextFieldColors
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanDeep
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    uiState: TomeUiState,
    onSearchQueryChanged: (String) -> Unit,
    onShelfFilterChanged: (String) -> Unit,
    onExportCsv: () -> Unit,
    onExportMdSingle: () -> Unit,
    onExportMdZip: () -> Unit,
    onExportJson: () -> Unit,
    onRestoreJson: () -> Unit,
    onEditBook: (BookRecord) -> Unit,
    onDeleteBook: (BookRecord) -> Unit,
    onNavigateToScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shelfMenuExpanded by remember { mutableStateOf(false) }
    var bookPendingDelete by remember { mutableStateOf<BookRecord?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_list"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Compact Gamified Archivist Rank Header
        item(key = "archivist_rank") {
            ArchivistRankCard(progress = uiState.archivistProgress)
        }

        // 2. Search, Shelf Filter & Export Toolbar
        item(key = "library_toolbar") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = onSearchQueryChanged,
                            placeholder = {
                                Text(
                                    text = stringResource(R.string.search_library_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            singleLine = true,
                            shape = MaterialTheme.shapes.small,
                            colors = tomeTextFieldColors(),
                            modifier = Modifier
                                .weight(1.35f)
                                .testTag("library_search_input")
                        )

                        ExposedDropdownMenuBox(
                            expanded = shelfMenuExpanded,
                            onExpandedChange = { shelfMenuExpanded = !shelfMenuExpanded },
                            modifier = Modifier.weight(0.95f)
                        ) {
                            val label = if (uiState.selectedShelfFilter == "ALL") {
                                stringResource(R.string.all_shelves)
                            } else {
                                uiState.selectedShelfFilter
                            }
                            OutlinedTextField(
                                value = label,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = shelfMenuExpanded)
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = tomeTextFieldColors(),
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    .testTag("library_shelf_filter")
                            )
                            ExposedDropdownMenu(
                                expanded = shelfMenuExpanded,
                                onDismissRequest = { shelfMenuExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.all_shelves)) },
                                    onClick = {
                                        onShelfFilterChanged("ALL")
                                        shelfMenuExpanded = false
                                    }
                                )
                                uiState.settings.groups.forEach { group ->
                                    DropdownMenuItem(
                                        text = { Text(group) },
                                        onClick = {
                                            onShelfFilterChanged(group)
                                            shelfMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Export & Backup Bar
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExportActionButton(
                            label = stringResource(R.string.export_libib_csv),
                            icon = Icons.Filled.TableView,
                            onClick = onExportCsv,
                            tag = "export_csv_button"
                        )
                        ExportActionButton(
                            label = stringResource(R.string.export_notebooklm_md),
                            icon = Icons.Filled.Description,
                            onClick = onExportMdSingle,
                            tag = "export_md_button"
                        )
                        ExportActionButton(
                            label = stringResource(R.string.export_obsidian_zip),
                            icon = Icons.Filled.Archive,
                            onClick = onExportMdZip,
                            tag = "export_zip_button"
                        )
                        ExportActionButton(
                            label = stringResource(R.string.export_json_backup),
                            icon = Icons.Filled.DataObject,
                            onClick = onExportJson,
                            tag = "export_json_button"
                        )
                        ExportActionButton(
                            label = stringResource(R.string.import_json_restore),
                            icon = Icons.Filled.Restore,
                            onClick = onRestoreJson,
                            tag = "restore_json_button"
                        )
                    }
                }
            }
        }

        // 3. Empty State or Book List
        if (uiState.filteredBooks.isEmpty()) {
            item(key = "empty_state") {
                val hasAnyBooks = uiState.books.isNotEmpty()
                EmptyLibraryCard(
                    isFilteredEmpty = hasAnyBooks,
                    onScanClick = onNavigateToScan
                )
            }
        } else {
            items(
                items = uiState.filteredBooks,
                key = { it.id }
            ) { book ->
                BookCatalogCard(
                    book = book,
                    onEdit = { onEditBook(book) },
                    onDelete = { bookPendingDelete = book }
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    // Delete Confirmation Dialog
    val targetBook = bookPendingDelete
    if (targetBook != null) {
        AlertDialog(
            onDismissRequest = { bookPendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete Book") },
            text = {
                Text("Remove \"${targetBook.title.ifBlank { "Untitled" }}\" from your local library?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteBook(targetBook)
                        bookPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { bookPendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchivistRankCard(progress: ArchivistProgress) {
    val formatter = remember { NumberFormat.getIntegerInstance(Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archivist_rank_card"),
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
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.archivist_rank_label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = progress.currentRank,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    StatCounter(
                        value = formatter.format(progress.totalBooks),
                        label = stringResource(R.string.stat_books_label)
                    )
                    StatCounter(
                        value = formatter.format(progress.totalPages),
                        label = stringResource(R.string.stat_pages_label)
                    )
                }
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(999.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.progressFraction)
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(CyanDeep, CyanAccent)))
                )
            }

            Text(
                text = progress.nextRankHint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Unlockable Archivist Rank Badges
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                progress.tiers.forEach { (tier, unlocked) ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (unlocked) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.background
                            )
                            .border(
                                1.dp,
                                if (unlocked) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.outline,
                                RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (unlocked) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                        Text(
                            text = "${tier.name} (${tier.minBooks})",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unlocked) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCounter(value: String, label: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExportActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .height(40.dp)
            .testTag(tag),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCatalogCard(
    book: BookRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("book_item_card_${book.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = book.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = book.authors.ifBlank { "Unknown Author" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Monospace bibliographic metadata strip
            val metaItems = remember(book) {
                buildList {
                    if (book.publisher.isNotBlank()) add(book.publisher)
                    if (book.formattedPublishDate.isNotBlank()) add(book.formattedPublishDate)
                    if (book.pages > 0) add("${book.pages} pp")
                    if (book.isbn13.isNotBlank()) add("ISBN-13: ${book.isbn13}")
                    if (book.isbn10.isNotBlank()) add("ISBN-10: ${book.isbn10}")
                }
            }
            if (metaItems.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    metaItems.forEach { item ->
                        Text(
                            text = item,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (book.description.isNotBlank()) {
                Text(
                    text = book.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Shelf chip + topic tags
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = book.group.ifBlank { "Home Library" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                book.tags.forEach { tag ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Edit & Delete Action Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("edit_book_${book.id}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.action_edit),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .height(40.dp)
                        .testTag("delete_book_${book.id}"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    isFilteredEmpty: Boolean,
    onScanClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_library_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_empty_archive_1795506304538),
                contentDescription = "Minimalist illuminated dark bookshelf illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(155.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            )
            Text(
                text = stringResource(
                    if (isFilteredEmpty) R.string.empty_search_title else R.string.empty_library_title
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(
                    if (isFilteredEmpty) R.string.empty_search_subtitle else R.string.empty_library_subtitle
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!isFilteredEmpty) {
                Button(
                    onClick = onScanClick,
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("empty_scan_first_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_scan_first_book),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
