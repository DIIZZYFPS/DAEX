package com.daex.android.database

import android.content.Context
import android.util.Log
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class DaexFtsDatabaseHelper(private val context: Context) {

    companion object {
        private const val DATABASE_NAME = "daex_fts5.db"
        private const val TABLE_FTS = "document_chunks_fts"
        
        // FTS5 virtual table fields
        private const val COLUMN_DOC_ID = "documentId"
        private const val COLUMN_FILE_NAME = "fileName"
        private const val COLUMN_CHUNK_INDEX = "chunkIndex"
        private const val COLUMN_CONTENT = "content"
    }

    private val driver = BundledSQLiteDriver()
    private val dbFile: File by lazy { context.getDatabasePath(DATABASE_NAME) }
    private val mutex = Mutex()
    private var connection: SQLiteConnection? = null

    private fun getOrOpenConnection(): SQLiteConnection {
        var conn = connection
        if (conn == null) {
            dbFile.parentFile?.mkdirs()
            conn = driver.open(dbFile.absolutePath)
            
            // Create FTS5 virtual table if it doesn't exist
            conn.prepare("""
                CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts5(
                    $COLUMN_DOC_ID,
                    $COLUMN_FILE_NAME,
                    $COLUMN_CHUNK_INDEX,
                    $COLUMN_CONTENT,
                    tokenize='unicode61'
                )
            """).use { it.step() }
            
            connection = conn
            Log.d("DaexFtsDatabaseHelper", "Successfully opened and initialized Bundled SQLite connection at: ${dbFile.absolutePath}")
        }
        return conn
    }

    suspend fun insertChunk(documentId: String, fileName: String, chunkIndex: Int, content: String) = mutex.withLock {
        try {
            val conn = getOrOpenConnection()
            conn.prepare("""
                INSERT INTO $TABLE_FTS ($COLUMN_DOC_ID, $COLUMN_FILE_NAME, $COLUMN_CHUNK_INDEX, $COLUMN_CONTENT)
                VALUES (?, ?, ?, ?)
            """).use { stmt ->
                stmt.bindText(1, documentId)
                stmt.bindText(2, fileName)
                stmt.bindLong(3, chunkIndex.toLong())
                stmt.bindText(4, content)
                stmt.step()
            }
        } catch (e: Exception) {
            Log.e("DaexFtsDatabaseHelper", "Failed to insert chunk into FTS5 for document '$documentId'", e)
        }
    }

    suspend fun deleteChunksByDocumentId(documentId: String) = mutex.withLock {
        try {
            val conn = getOrOpenConnection()
            conn.prepare("DELETE FROM $TABLE_FTS WHERE $COLUMN_DOC_ID = ?").use { stmt ->
                stmt.bindText(1, documentId)
                stmt.step()
            }
        } catch (e: Exception) {
            Log.e("DaexFtsDatabaseHelper", "Failed to delete FTS5 chunks for documentId '$documentId'", e)
        }
    }

    suspend fun deleteChunksByFileName(fileName: String) = mutex.withLock {
        try {
            val conn = getOrOpenConnection()
            conn.prepare("DELETE FROM $TABLE_FTS WHERE $COLUMN_FILE_NAME = ?").use { stmt ->
                stmt.bindText(1, fileName)
                stmt.step()
            }
        } catch (e: Exception) {
            Log.e("DaexFtsDatabaseHelper", "Failed to delete FTS5 chunks for fileName '$fileName'", e)
        }
    }

    suspend fun clearAll() = mutex.withLock {
        try {
            val conn = getOrOpenConnection()
            conn.prepare("DELETE FROM $TABLE_FTS").use { it.step() }
        } catch (e: Exception) {
            Log.e("DaexFtsDatabaseHelper", "Failed to clear FTS5 database", e)
        }
    }

    data class FtsMatch(
        val fileName: String,
        val chunkIndex: Int,
        val content: String,
        val documentId: String,
        var score: Double // higher relevance means lower score in FTS5 bm25()
    )

    suspend fun searchChunks(queryText: String, limit: Int): List<FtsMatch> = mutex.withLock {
        val sanitizedQuery = sanitizeFtsQuery(queryText)
        if (sanitizedQuery.isBlank()) return@withLock emptyList()

        val results = mutableListOf<FtsMatch>()
        try {
            val conn = getOrOpenConnection()
            conn.prepare("""
                SELECT $COLUMN_DOC_ID, $COLUMN_FILE_NAME, $COLUMN_CHUNK_INDEX, $COLUMN_CONTENT, bm25($TABLE_FTS)
                FROM $TABLE_FTS
                WHERE $TABLE_FTS MATCH ?
                ORDER BY bm25($TABLE_FTS) ASC
                LIMIT ?
            """).use { stmt ->
                stmt.bindText(1, sanitizedQuery)
                stmt.bindLong(2, limit.toLong())

                while (stmt.step()) {
                    val docId = stmt.getText(0)
                    val fileName = stmt.getText(1)
                    val chunkIndex = stmt.getLong(2).toInt()
                    val content = stmt.getText(3)
                    val score = stmt.getDouble(4)

                    results.add(
                        FtsMatch(
                            fileName = fileName,
                            chunkIndex = chunkIndex,
                            content = content,
                            documentId = docId,
                            score = score
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DaexFtsDatabaseHelper", "FTS5 search query failed", e)
        }

        return@withLock results
    }

    private fun sanitizeFtsQuery(query: String): String {
        val clean = query.replace(Regex("[^a-zA-Z0-9\\s]"), " ").trim()
        if (clean.isBlank()) return ""
        // Split terms, wrap in double quotes, and join with space.
        // Append prefix query suffix * to each term for prefix matching.
        return clean.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ") { "\"$it\"*" }
    }
}
