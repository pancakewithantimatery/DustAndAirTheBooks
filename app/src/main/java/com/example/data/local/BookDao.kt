package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY updated_at DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY updated_at DESC")
    suspend fun getAllBooksOnce(): List<BookEntity>

    @Query(
        "SELECT * FROM books WHERE " +
            "(:isbn13 != '' AND isbn_13 = :isbn13) OR " +
            "(:isbn10 != '' AND isbn_10 = :isbn10) LIMIT 1"
    )
    suspend fun findByIsbn(isbn13: String, isbn10: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(books: List<BookEntity>)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM books")
    suspend fun clearAll()
}
