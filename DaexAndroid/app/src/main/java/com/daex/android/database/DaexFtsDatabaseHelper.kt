package com.daex.android.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DaexFtsDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "daex_fts4.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_FTS = "document_chunks_fts"
        
        // FTS4 virtual table fields
        private const val COLUMN_DOC_ID = "documentId"
        private const val COLUMN_FILE_NAME = "fileName"
        private const val COLUMN_CHUNK_INDEX = "chunkIndex"
        private const val COLUMN_CONTENT = "content"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE VIRTUAL TABLE $TABLE_FTS USING fts4(
                $COLUMN_DOC_ID,
                $COLUMN_FILE_NAME,
                $COLUMN_CHUNK_INDEX,
                $COLUMN_CONTENT,
                tokenize=unicode61
            )
        """.trimIndent()
        
        Log.d("DaexFtsDatabaseHelper", "Creating FTS4 Virtual Table: $createTableQuery")
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS")
        onCreate(db)
    }

    fun insertChunk(documentId: String, fileName: String, chunkIndex: Int, content: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_DOC_ID, documentId)
            put(COLUMN_FILE_NAME, fileName)
            put(COLUMN_CHUNK_INDEX, chunkIndex)
            put(COLUMN_CONTENT, content)
        }
        db.insert(TABLE_FTS, null, values)
    }

    fun deleteChunksByDocumentId(documentId: String) {
        val db = writableDatabase
        db.delete(TABLE_FTS, "$COLUMN_DOC_ID = ?", arrayOf(documentId))
    }

    fun deleteChunksByFileName(fileName: String) {
        val db = writableDatabase
        db.delete(TABLE_FTS, "$COLUMN_FILE_NAME = ?", arrayOf(fileName))
    }

    fun clearAll() {
        val db = writableDatabase
        db.delete(TABLE_FTS, null, null)
    }

    data class FtsMatch(
        val fileName: String,
        val chunkIndex: Int,
        val content: String,
        val documentId: String,
        var score: Double // higher is better for our custom BM25
    )

    fun searchChunks(queryText: String, limit: Int): List<FtsMatch> {
        val sanitizedQuery = sanitizeFtsQuery(queryText)
        if (sanitizedQuery.isBlank()) return emptyList()

        // Extract individual query terms (lowercased) for BM25 calculation
        val queryTerms = queryText.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .distinct()

        if (queryTerms.isEmpty()) return emptyList()

        val results = mutableListOf<FtsMatch>()
        val db = readableDatabase
        
        val sql = """
            SELECT $COLUMN_DOC_ID, $COLUMN_FILE_NAME, $COLUMN_CHUNK_INDEX, $COLUMN_CONTENT 
            FROM $TABLE_FTS 
            WHERE $COLUMN_CONTENT MATCH ?
        """.trimIndent()

        try {
            // Get total document count for IDF calculation
            val totalDocs = try {
                val stmt = db.compileStatement("SELECT count(*) FROM $TABLE_FTS")
                stmt.simpleQueryForLong().coerceAtLeast(1L)
            } catch (e: Exception) {
                1L
            }

            // Get document frequency (DF) for each query term
            val docFreqs = queryTerms.associateWith { term ->
                try {
                    val stmt = db.compileStatement("SELECT count(*) FROM $TABLE_FTS WHERE $COLUMN_CONTENT MATCH ?")
                    stmt.bindString(1, "\"$term\"*")
                    stmt.simpleQueryForLong().toInt().coerceAtLeast(1)
                } catch (e: Exception) {
                    1
                }
            }

            db.rawQuery(sql, arrayOf(sanitizedQuery)).use { cursor ->
                if (cursor.moveToFirst()) {
                    val idxDocId = cursor.getColumnIndexOrThrow(COLUMN_DOC_ID)
                    val idxFileName = cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME)
                    val idxChunkIndex = cursor.getColumnIndexOrThrow(COLUMN_CHUNK_INDEX)
                    val idxContent = cursor.getColumnIndexOrThrow(COLUMN_CONTENT)

                    do {
                        val docId = cursor.getString(idxDocId)
                        val fileName = cursor.getString(idxFileName)
                        val chunkIndex = cursor.getInt(idxChunkIndex)
                        val content = cursor.getString(idxContent)

                        val score = computeBm25Score(content, queryTerms, totalDocs, docFreqs)

                        results.add(
                            FtsMatch(
                                documentId = docId,
                                fileName = fileName,
                                chunkIndex = chunkIndex,
                                content = content,
                                score = score
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e("DaexFtsDatabaseHelper", "FTS4 search query failed", e)
        }

        // Return top results sorted by BM25 score descending
        return results.sortedByDescending { it.score }.take(limit)
    }

    private fun computeBm25Score(
        content: String,
        queryTerms: List<String>,
        totalDocs: Long,
        docFreqs: Map<String, Int>
    ): Double {
        val words = content.lowercase().split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }
        val docLen = words.size
        if (docLen == 0) return 0.0

        var score = 0.0
        val k1 = 1.2
        val b = 0.75
        val avgDocLen = 50.0 // Approximate average chunk length

        for (term in queryTerms) {
            val tf = words.count { it == term }
            if (tf > 0) {
                val df = docFreqs[term] ?: 1
                // BM25 IDF formulation
                val idf = kotlin.math.log10((totalDocs - df + 0.5) / (df + 0.5) + 1.0).coerceAtLeast(0.0001)
                val numerator = tf * (k1 + 1)
                val denominator = tf + k1 * (1 - b + b * (docLen / avgDocLen))
                score += idf * (numerator / denominator)
            }
        }
        return score
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
