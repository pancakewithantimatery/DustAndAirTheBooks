package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.BookRecord
import org.json.JSONArray

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["isbn_13"]),
        Index(value = ["isbn_10"]),
        Index(value = ["updated_at"])
    ]
)
data class BookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val authors: String,
    val description: String,
    val publisher: String,
    @ColumnInfo(name = "publish_year")
    val publishYear: String,
    @ColumnInfo(name = "publish_month")
    val publishMonth: String,
    @ColumnInfo(name = "publish_day")
    val publishDay: String,
    @ColumnInfo(name = "isbn_13")
    val isbn13: String,
    @ColumnInfo(name = "isbn_10")
    val isbn10: String,
    val pages: Int,
    @ColumnInfo(name = "shelf_group")
    val group: String,
    val notes: String,
    @ColumnInfo(name = "tags_json")
    val tagsJson: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
) {
    fun toDomain(): BookRecord {
        val parsedTags = buildList {
            try {
                val arr = JSONArray(tagsJson)
                for (i in 0 until arr.length()) {
                    val tag = arr.optString(i).trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            } catch (_: Exception) {
                tagsJson.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { add(it) }
            }
        }
        return BookRecord(
            id = id,
            title = title,
            authors = authors,
            description = description,
            publisher = publisher,
            publishYear = publishYear,
            publishMonth = publishMonth,
            publishDay = publishDay,
            isbn13 = isbn13,
            isbn10 = isbn10,
            pages = pages,
            group = group.ifBlank { "Home Library" },
            notes = notes,
            tags = parsedTags,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun fromDomain(book: BookRecord): BookEntity {
            val arr = JSONArray()
            book.tags.map { it.trim() }.filter { it.isNotEmpty() }.forEach { arr.put(it) }
            return BookEntity(
                id = book.id,
                title = book.title,
                authors = book.authors,
                description = book.description,
                publisher = book.publisher,
                publishYear = book.publishYear,
                publishMonth = book.publishMonth,
                publishDay = book.publishDay,
                isbn13 = book.isbn13,
                isbn10 = book.isbn10,
                pages = book.pages.coerceAtLeast(0),
                group = book.group.ifBlank { "Home Library" },
                notes = book.notes,
                tagsJson = arr.toString(),
                updatedAt = book.updatedAt
            )
        }
    }
}
