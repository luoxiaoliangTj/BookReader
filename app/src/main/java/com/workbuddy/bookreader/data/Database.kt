package com.workbuddy.bookreader.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/* ---------- 实体 ---------- */

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,              // 文件路径或 uri 的 hash
    val title: String,
    val path: String,
    val format: String,                      // TXT / EPUB / MOBI / PDF / PDF_OCR
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = System.currentTimeMillis(),
    val progressPercent: Int = 0,
    val coverColor: Long = 0xFF4A6DA7
)

@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val color: Long = 0xFFFFF176,            // 划线颜色
    val note: String = "",                   // 笔记内容（可空）
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: String,
    val position: Int,                       // 章节/页索引
    val label: String,
    val createdAt: Long = System.currentTimeMillis()
)

/* ---------- DAO ---------- */

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC")
    fun all(): Flow<List<Book>>

    @Upsert suspend fun upsert(book: Book)

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun byId(id: String): Book?

    @Query("UPDATE books SET progressPercent = :p, lastReadAt = :t WHERE id = :id")
    suspend fun updateProgress(id: String, p: Int, t: Long)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY chapterIndex, startOffset")
    fun forBook(bookId: String): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId")
    suspend fun forBookSync(bookId: String): List<Highlight>

    @Upsert suspend fun upsert(h: Highlight): Long

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY position")
    fun forBook(bookId: String): Flow<List<Bookmark>>

    @Insert suspend fun insert(b: Bookmark)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)
}

/* ---------- 数据库 ---------- */

@Database(entities = [Book::class, Highlight::class, Bookmark::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun highlightDao(): HighlightDao
    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDatabase::class.java, "bookreader.db")
                .fallbackToDestructiveMigration().build().also { inst = it }
        }
    }
}
