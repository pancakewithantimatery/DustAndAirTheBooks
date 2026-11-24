package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.domain.BookRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Database(entities = [BookEntity::class], version = 1, exportSchema = false)
abstract class TomeDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao

    companion object {
        @Volatile
        private var instance: TomeDatabase? = null

        fun getInstance(context: Context): TomeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TomeDatabase::class.java,
                    "tomescanner.db"
                ).build().also { instance = it }
            }
        }
    }
}

class BookRepository(private val bookDao: BookDao) {

    val allBooks: Flow<List<BookRecord>> = bookDao.observeAllBooks()
        .map { entities -> entities.map { it.toDomain() } }

    suspend fun getAllBooksOnce(): List<BookRecord> =
        bookDao.getAllBooksOnce().map { it.toDomain() }

    suspend fun findDuplicateByIsbn(isbn13: String, isbn10: String): BookRecord? {
        if (isbn13.isBlank() && isbn10.isBlank()) return null
        return bookDao.findByIsbn(isbn13, isbn10)?.toDomain()
    }

    suspend fun saveBook(book: BookRecord) {
        bookDao.upsertBook(BookEntity.fromDomain(book.copy(updatedAt = System.currentTimeMillis())))
    }

    suspend fun saveBooks(books: List<BookRecord>) {
        bookDao.upsertBooks(books.map { BookEntity.fromDomain(it) })
    }

    suspend fun deleteBook(id: String) {
        bookDao.deleteById(id)
    }

    suspend fun clearAll() {
        bookDao.clearAll()
    }
}
