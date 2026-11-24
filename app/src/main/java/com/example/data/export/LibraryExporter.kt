package com.example.data.export

import com.example.domain.BookRecord
import com.example.domain.IsbnUtils
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ImportedBackup(
    val books: List<BookRecord>,
    val groups: List<String>
)

object LibraryExporter {

    private const val UTF8_BOM = "\uFEFF"

    fun todayStamp(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(Date())
    }

    fun generateLibibCsv(books: List<BookRecord>): String {
        val headers = listOf(
            "Title",
            "Authors",
            "Description",
            "Publisher",
            "Publish Date",
            "ISBN 13",
            "ISBN 10",
            "Pages",
            "Group",
            "Notes",
            "Tags"
        )
        val rows = books.map { book ->
            listOf(
                book.title,
                book.authors,
                book.description,
                book.publisher,
                book.formattedPublishDate,
                book.isbn13,
                book.isbn10,
                if (book.pages > 0) book.pages.toString() else "",
                book.group.ifBlank { "Home Library" },
                book.notes,
                book.tags.joinToString(", ")
            ).joinToString(",") { csvEscape(it) }
        }
        return UTF8_BOM + (listOf(headers.joinToString(",")) + rows).joinToString("\r\n")
    }

    fun generateCombinedMarkdown(books: List<BookRecord>): String {
        val stamp = todayStamp()
        val header = buildString {
            appendLine("# TomeScanner Library Catalog ($stamp)")
            appendLine()
            appendLine("Total Books: ${books.size}")
            appendLine()
            appendLine("---")
            appendLine()
        }
        val body = books.joinToString("\n---\n\n") { bookToMarkdown(it) }
        return header + body
    }

    fun writeObsidianZip(books: List<BookRecord>, outputStream: OutputStream) {
        val usedNames = mutableSetOf<String>()
        ZipOutputStream(outputStream).use { zos ->
            books.forEach { book ->
                var filename = slugifyFilename(book)
                var counter = 2
                while (!usedNames.add(filename)) {
                    val base = filename.removeSuffix(".md")
                    filename = "${base}_$counter.md"
                    counter++
                }
                val entry = ZipEntry("TomeScanner-Vault/$filename")
                zos.putNextEntry(entry)
                zos.write(bookToMarkdown(book).toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
    }

    fun generateJsonBackup(books: List<BookRecord>, groups: List<String>): String {
        val root = JSONObject().apply {
            put("app", "TomeScanner")
            put("version", 1)
            put("exported_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date()))
            put("groups", JSONArray(groups))
            val booksArr = JSONArray()
            books.forEach { b ->
                booksArr.put(
                    JSONObject().apply {
                        put("id", b.id)
                        put("title", b.title)
                        put("authors", b.authors)
                        put("description", b.description)
                        put("publisher", b.publisher)
                        put("publish_year", b.publishYear)
                        put("publish_month", b.publishMonth)
                        put("publish_day", b.publishDay)
                        put("isbn_13", b.isbn13)
                        put("isbn_10", b.isbn10)
                        put("pages", b.pages)
                        put("group", b.group)
                        put("notes", b.notes)
                        put("tags", JSONArray(b.tags))
                        put("updated_at", b.updatedAt)
                    }
                )
            }
            put("books", booksArr)
        }
        return root.toString(2)
    }

    fun parseJsonBackup(rawJson: String): ImportedBackup {
        val trimmed = rawJson.trim()
        val booksArray: JSONArray
        val groups = mutableListOf<String>()

        if (trimmed.startsWith("[")) {
            booksArray = JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            booksArray = root.optJSONArray("books")
                ?: throw IllegalArgumentException("Invalid TomeScanner backup: missing books array.")
            val groupsArr = root.optJSONArray("groups")
            if (groupsArr != null) {
                for (i in 0 until groupsArr.length()) {
                    val g = groupsArr.optString(i).trim()
                    if (g.isNotEmpty()) groups.add(g)
                }
            }
        }

        val books = buildList {
            for (i in 0 until booksArray.length()) {
                val obj = booksArray.optJSONObject(i) ?: continue
                val pair = IsbnUtils.normalizePair(
                    obj.optString("isbn_13", ""),
                    obj.optString("isbn_10", "")
                )
                val tags = buildList {
                    val tArr = obj.optJSONArray("tags")
                    if (tArr != null) {
                        for (j in 0 until tArr.length()) {
                            val tag = tArr.optString(j).trim()
                            if (tag.isNotEmpty()) add(tag)
                        }
                    }
                }
                val id = obj.optString("id", "").trim().ifEmpty { "book_${UUID.randomUUID()}" }
                add(
                    BookRecord(
                        id = id,
                        title = obj.optString("title", "").trim(),
                        authors = obj.optString("authors", "").trim(),
                        description = obj.optString("description", "").trim(),
                        publisher = obj.optString("publisher", "").trim(),
                        publishYear = obj.optString("publish_year", "").filter { it.isDigit() }.take(4),
                        publishMonth = obj.optString("publish_month", "").filter { it.isDigit() }.take(2),
                        publishDay = obj.optString("publish_day", "").filter { it.isDigit() }.take(2),
                        isbn13 = pair.isbn13,
                        isbn10 = pair.isbn10,
                        pages = obj.optInt("pages", 0).coerceAtLeast(0),
                        group = obj.optString("group", "Home Library").trim().ifEmpty { "Home Library" },
                        notes = obj.optString("notes", "").trim(),
                        tags = tags,
                        updatedAt = obj.optLong("updated_at", System.currentTimeMillis())
                    )
                )
            }
        }
        return ImportedBackup(books = books, groups = groups)
    }

    private fun bookToMarkdown(book: BookRecord): String {
        val tagsYaml = if (book.tags.isNotEmpty()) {
            "\n" + book.tags.joinToString("\n") { "  - ${yamlEscape(it)}" }
        } else {
            " []"
        }

        return buildString {
            appendLine("---")
            appendLine("title: ${yamlEscape(book.title)}")
            appendLine("authors: ${yamlEscape(book.authors)}")
            appendLine("publisher: ${yamlEscape(book.publisher)}")
            appendLine("year: ${yamlEscape(book.publishYear)}")
            appendLine("isbn_10: ${yamlEscape(book.isbn10)}")
            appendLine("isbn_13: ${yamlEscape(book.isbn13)}")
            appendLine("pages: ${book.pages}")
            appendLine("tags:$tagsYaml")
            appendLine("---")
            appendLine()
            appendLine("# ${book.title.ifBlank { "Untitled Book" }}")
            appendLine()
            appendLine("**Authors:** ${book.authors.ifBlank { "Unknown" }}  ")
            appendLine("**Publisher:** ${book.publisher.ifBlank { "Unknown" }} (${book.formattedPublishDate.ifBlank { "n.d." }})  ")
            appendLine("**Shelf:** ${book.group.ifBlank { "Home Library" }}  ")
            appendLine("**Identifiers:** ISBN-13: `${book.isbn13.ifBlank { "N/A" }}` | ISBN-10: `${book.isbn10.ifBlank { "N/A" }}`  ")
            appendLine("**Pages:** ${if (book.pages > 0) book.pages else "N/A"}")
            appendLine()
            appendLine("## Bibliographic Summary")
            appendLine(book.description.ifBlank { "_No summary available._" })
            if (book.notes.isNotBlank()) {
                appendLine()
                appendLine("## Edition & Notes")
                appendLine(book.notes)
            }
        }
    }

    private fun slugifyFilename(book: BookRecord): String {
        val normalized = Normalizer.normalize(book.title.ifBlank { "book" }, Normalizer.Form.NFKD)
        val slug = normalized
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')
            .take(60)
            .ifBlank { "book" }
        val suffix = book.isbn13.ifBlank { book.isbn10.ifBlank { book.id.takeLast(6) } }
        return "$slug-$suffix.md"
    }

    private fun csvEscape(value: String): String {
        return if (value.contains('"') || value.contains(',') || value.contains('\n') || value.contains('\r')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun yamlEscape(value: String): String {
        val sanitized = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace(Regex("\\r?\\n"), " ")
        return "\"$sanitized\""
    }
}
