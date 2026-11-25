package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IsbnUtils
import com.example.ui.BookDraft
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewBookCard(
    draft: BookDraft,
    availableGroups: List<String>,
    onDraftChange: ((BookDraft) -> BookDraft) -> Unit,
    onIsbn13Change: (String) -> Unit,
    onIsbn10Change: (String) -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    var shelfExpanded by remember { mutableStateOf(false) }
    val shelfOptions = remember(availableGroups, draft.group) {
        if (draft.group.isNotBlank() && !availableGroups.contains(draft.group)) {
            availableGroups + draft.group
        } else {
            availableGroups
        }
    }

    val pairValid = IsbnUtils.isValidIsbn13(draft.isbn13) && IsbnUtils.isValidIsbn10(draft.isbn10)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("review_book_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (draft.isEdit) R.string.review_heading_edit else R.string.review_heading_new
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = onDiscard,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("discard_review_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_discard),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 1: Basic Info
            FormSectionContainer(title = stringResource(R.string.section_basic_info)) {
                TomeTextField(
                    value = draft.title,
                    onValueChange = { value -> onDraftChange { it.copy(title = value) } },
                    label = "Title *",
                    placeholder = "Book title",
                    modifier = Modifier.testTag("field_title")
                )
                TomeTextField(
                    value = draft.authors,
                    onValueChange = { value -> onDraftChange { it.copy(authors = value) } },
                    label = "Authors (comma-separated)",
                    placeholder = "Author One, Author Two",
                    modifier = Modifier.testTag("field_authors")
                )
                TomeTextField(
                    value = draft.description,
                    onValueChange = { value -> onDraftChange { it.copy(description = value) } },
                    label = "Description",
                    placeholder = "Concise bibliographic summary & subject",
                    minLines = 3,
                    singleLine = false,
                    modifier = Modifier.testTag("field_description")
                )
            }

            // Section 2: Published (Publisher + Year only)
            FormSectionContainer(title = stringResource(R.string.section_published)) {
                TomeTextField(
                    value = draft.publisher,
                    onValueChange = { value -> onDraftChange { it.copy(publisher = value) } },
                    label = "Publisher",
                    placeholder = "Publisher (City)",
                    modifier = Modifier.testTag("field_publisher")
                )
                TomeTextField(
                    value = draft.publishYear,
                    onValueChange = { value ->
                        onDraftChange {
                            it.copy(
                                publishYear = value.filter { c -> c.isDigit() }.take(4),
                                publishMonth = "",
                                publishDay = ""
                            )
                        }
                    },
                    label = "Year",
                    placeholder = "YYYY",
                    keyboardType = KeyboardType.Number,
                    monospace = true,
                    modifier = Modifier.testTag("field_year")
                )
            }

            // Section 3: Identifiers (Auto-Synced ISBN-13 <-> ISBN-10)
            FormSectionContainer(
                title = stringResource(R.string.section_identifiers),
                trailingBadge = if (pairValid) "Auto-synced" else null
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TomeTextField(
                        value = draft.isbn13,
                        onValueChange = onIsbn13Change,
                        label = "ISBN-13",
                        placeholder = "978…",
                        keyboardType = KeyboardType.Number,
                        monospace = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("field_isbn13")
                    )
                    TomeTextField(
                        value = draft.isbn10,
                        onValueChange = onIsbn10Change,
                        label = "ISBN-10",
                        placeholder = "014…",
                        monospace = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("field_isbn10")
                    )
                }
            }

            // Section 4: Additional Info
            FormSectionContainer(title = stringResource(R.string.section_additional)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    TomeTextField(
                        value = draft.pagesText,
                        onValueChange = { value ->
                            onDraftChange { it.copy(pagesText = value.filter { c -> c.isDigit() }.take(5)) }
                        },
                        label = "Pages",
                        placeholder = "0",
                        keyboardType = KeyboardType.Number,
                        monospace = true,
                        modifier = Modifier
                            .weight(0.85f)
                            .testTag("field_pages")
                    )

                    ExposedDropdownMenuBox(
                        expanded = shelfExpanded,
                        onExpandedChange = { shelfExpanded = !shelfExpanded },
                        modifier = Modifier.weight(1.35f)
                    ) {
                        OutlinedTextField(
                            value = draft.group,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Shelf / Group") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = shelfExpanded)
                            },
                            colors = tomeTextFieldColors(),
                            shape = MaterialTheme.shapes.small,
                            singleLine = true,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("field_shelf_dropdown")
                        )
                        ExposedDropdownMenu(
                            expanded = shelfExpanded,
                            onDismissRequest = { shelfExpanded = false },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            shelfOptions.forEach { groupName ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = groupName,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onDraftChange { it.copy(group = groupName) }
                                        shelfExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Section 5: Tags & Notes
            FormSectionContainer(title = stringResource(R.string.section_tags_notes)) {
                TomeTextField(
                    value = draft.tagsText,
                    onValueChange = { value -> onDraftChange { it.copy(tagsText = value) } },
                    label = "Tags (comma-separated)",
                    placeholder = "history, philosophy, sci-fi",
                    modifier = Modifier.testTag("field_tags")
                )
                TomeTextField(
                    value = draft.notes,
                    onValueChange = { value -> onDraftChange { it.copy(notes = value) } },
                    label = "Notes (edition / reprint details)",
                    placeholder = "Edition, translation, condition, or lore notes",
                    minLines = 2,
                    singleLine = false,
                    modifier = Modifier.testTag("field_notes")
                )
            }

            // Grounding Sources
            if (draft.groundingSources.isNotEmpty()) {
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
                        text = "GOOGLE SEARCH GROUNDING SOURCES",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        draft.groundingSources.forEach { source ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        try {
                                            uriHandler.openUri(source.uri)
                                        } catch (_: Exception) {
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = source.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Filled.OpenInNew,
                                    contentDescription = "Open source ${source.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Save Action Button
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_book_button"),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.BookmarkAdded,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(
                        if (draft.isEdit) R.string.action_update_book else R.string.action_save_to_library
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun FormSectionContainer(
    title: String,
    trailingBadge: String? = null,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            if (trailingBadge != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SyncAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = trailingBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun TomeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    monospace: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = {
            Text(
                text = placeholder,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        },
        textStyle = if (monospace) {
            MaterialTheme.typography.bodyLarge.copy(fontFamily = JetBrainsMonoFontFamily)
        } else {
            MaterialTheme.typography.bodyLarge
        },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small,
        colors = tomeTextFieldColors(),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun tomeTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.background,
    unfocusedContainerColor = MaterialTheme.colorScheme.background,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.primary
)
