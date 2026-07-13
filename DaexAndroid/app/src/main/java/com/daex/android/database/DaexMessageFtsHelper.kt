package com.daex.android.database

import android.content.Context
import android.util.Log
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * FTS5/BM25 keyword index for message content, used alongside vector search
 * (MessageEntity.embedding) for cross-conversation search - mirrors DaexFtsDatabaseHelper's
 * structure for RAG document chunks.
 */
class DaexMessageFtsHelper(private val context: Context) {

    companion object {
        private const val DATABASE_NAME = "daex_message_fts5.db"
        private const val TABLE_FTS = "message_fts"

        private const val COLUMN_CONVERSATION_ID = "conversationId"
        private const val COLUMN_MESSAGE_ID = "messageId"
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

            conn.prepare("""
                CREATE VIRTUAL TABLE IF NOT EXISTS $TABLE_FTS USING fts5(
                    $COLUMN_CONVERSATION_ID UNINDEXED,
                    $COLUMN_MESSAGE_ID UNINDEXED,
                    $COLUMN_CONTENT,
                    tokenize='unicode61'
                )
            """).use { it.step() }

            connection = conn
            Log.d("DaexMessageFts", "Successfully opened and initialized message FTS5 connection at: ${dbFile.absolutePath}")
        }
        return conn
    }

    suspend fun indexMessage(conversationId: String, messageId: String, content: String) = mutex.withLock {
        if (content.isBlank()) return@withLock
        try {
            val conn = getOrOpenConnection()
            // Remove any prior row for this message first (an edited/regenerated message keeps
            // the same id but its content changes) so a stale copy doesn't linger in the index.
            conn.prepare("DELETE FROM $TABLE_FTS WHERE $COLUMN_MESSAGE_ID = ?").use { stmt ->
                stmt.bindText(1, messageId)
                stmt.step()
            }
            conn.prepare("""
                INSERT INTO $TABLE_FTS ($COLUMN_CONVERSATION_ID, $COLUMN_MESSAGE_ID, $COLUMN_CONTENT)
                VALUES (?, ?, ?)
            """).use { stmt ->
                stmt.bindText(1, conversationId)
                stmt.bindText(2, messageId)
                stmt.bindText(3, content)
                stmt.step()
            }
        } catch (e: Exception) {
            Log.e("DaexMessageFts", "Failed to index message '$messageId'", e)
        }
    }

    data class FtsMatch(
        val conversationId: String,
        val messageId: String,
        val content: String,
        var score: Double // higher relevance means lower score in FTS5 bm25()
    )

    suspend fun searchMessages(queryText: String, limit: Int): List<FtsMatch> = mutex.withLock {
        val sanitizedQuery = FtsQuerySanitizer.sanitize(queryText)
        if (sanitizedQuery.isBlank()) return@withLock emptyList()

        val results = mutableListOf<FtsMatch>()
        try {
            val conn = getOrOpenConnection()
            conn.prepare("""
                SELECT $COLUMN_CONVERSATION_ID, $COLUMN_MESSAGE_ID, $COLUMN_CONTENT, bm25($TABLE_FTS)
                FROM $TABLE_FTS
                WHERE $TABLE_FTS MATCH ?
                ORDER BY bm25($TABLE_FTS) ASC
                LIMIT ?
            """).use { stmt ->
                stmt.bindText(1, sanitizedQuery)
                stmt.bindLong(2, limit.toLong())

                while (stmt.step()) {
                    results.add(
                        FtsMatch(
                            conversationId = stmt.getText(0),
                            messageId = stmt.getText(1),
                            content = stmt.getText(2),
                            score = stmt.getDouble(3)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("DaexMessageFts", "Message FTS5 search query failed", e)
        }

        return@withLock results
    }

    suspend fun hasAnyIndexed(): Boolean = mutex.withLock {
        try {
            val conn = getOrOpenConnection()
            conn.prepare("SELECT COUNT(*) FROM $TABLE_FTS LIMIT 1").use { stmt ->
                if (stmt.step()) stmt.getLong(0) > 0 else false
            }
        } catch (e: Exception) {
            false
        }
    }
}
