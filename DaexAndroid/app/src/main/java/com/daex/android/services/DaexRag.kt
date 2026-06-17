package com.daex.android.services

import android.content.Context
import android.util.Log
import com.daex.android.database.DocumentChunkEntity
import com.daex.android.database.DocumentChunkEntity_
import com.daex.android.database.DaexFtsDatabaseHelper
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface DaexRag {
    suspend fun initRag()
    suspend fun ingestFile(fileName: String, content: String, onProgress: (Int, Int) -> Unit = { _, _ -> })
    suspend fun queryDocuments(query: String, maxResults: Int = 5, activeFileNames: List<String> = emptyList()): List<String>
    suspend fun getUploadedFiles(): List<String>
    suspend fun deleteFileByName(fileName: String)
    suspend fun deleteFile(documentId: String)
    suspend fun hasDocuments(): Boolean
}

class DaexRagImpl(
    private val context: Context,
    private val boxStore: BoxStore,
    private val embedder: DaexEmbedder
) : DaexRag {

    private val chunkBox by lazy { boxStore.boxFor(DocumentChunkEntity::class.java) }
    private val ftsDbHelper by lazy { DaexFtsDatabaseHelper(context) }

    override suspend fun initRag() {
        embedder.initEmbeddingContext()
    }

    override suspend fun ingestFile(fileName: String, content: String, onProgress: (Int, Int) -> Unit) {
        withContext(Dispatchers.Default) {
            val documentId = UUID.randomUUID().toString()
            val chunks = chunkText(content)
            Log.d("DaexRag", "Ingesting file '$fileName': ${chunks.size} chunks")

            chunks.forEachIndexed { index, chunkText ->
                try {
                    val vector = embedder.generateEmbedding(chunkText, isQuery = false)
                    val entity = DocumentChunkEntity(
                        documentId = documentId,
                        fileName = fileName,
                        chunkIndex = index,
                        content = chunkText,
                        embedding = vector
                    )
                    withContext(Dispatchers.IO) {
                        chunkBox.put(entity)
                        ftsDbHelper.insertChunk(documentId, fileName, index, chunkText)
                    }
                    onProgress(index + 1, chunks.size)
                    Log.d("DaexRag", "Embedded chunk ${index + 1}/${chunks.size}")
                } catch (e: Exception) {
                    Log.e("DaexRag", "Failed to embed chunk $index of '$fileName'", e)
                }
            }
            Log.d("DaexRag", "File '$fileName' ingested: $documentId")
        }
    }

    override suspend fun queryDocuments(query: String, maxResults: Int, activeFileNames: List<String>): List<String> = withContext(Dispatchers.IO) {
        try {
            if (chunkBox.count() == 0L || activeFileNames.isEmpty()) return@withContext emptyList()

            // 1. Vector search using ObjectBox
            val queryVector = embedder.generateEmbedding(query, isQuery = true)
            val vectorCond = DocumentChunkEntity_.embedding.nearestNeighbors(queryVector, 20)
            
            var fileCond: io.objectbox.query.QueryCondition<DocumentChunkEntity>? = null
            for (fileName in activeFileNames) {
                val cond = DocumentChunkEntity_.fileName.equal(fileName, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                fileCond = if (fileCond == null) cond else fileCond.or(cond)
            }
            
            val combinedCond = if (fileCond != null) {
                vectorCond.and(fileCond)
            } else {
                vectorCond
            }
            
            val vectorResults = chunkBox.query(combinedCond).build().find()

            // 2. Full-Text Search with BM25 using SQLite
            val ftsResults = ftsDbHelper.searchChunks(query, 50)
                .filter { it.fileName in activeFileNames }
                .take(20)

            // 3. Reciprocal Rank Fusion (RRF) combination
            // RRF Score formula: sum ( 1 / (60 + rank) )
            val rrfScores = mutableMapOf<String, Double>()
            val chunkContents = mutableMapOf<String, String>()

            vectorResults.forEachIndexed { index, entity ->
                val key = "${entity.fileName}_${entity.chunkIndex}"
                val rank = index + 1
                rrfScores[key] = (rrfScores[key] ?: 0.0) + 1.0 / (60.0 + rank)
                chunkContents[key] = entity.content
            }

            ftsResults.forEachIndexed { index, match ->
                val key = "${match.fileName}_${match.chunkIndex}"
                val rank = index + 1
                rrfScores[key] = (rrfScores[key] ?: 0.0) + 1.0 / (60.0 + rank)
                if (!chunkContents.containsKey(key)) {
                    chunkContents[key] = match.content
                }
            }

            // Sort results by RRF score descending
            val sortedKeys = rrfScores.entries.sortedByDescending { it.value }.map { it.key }
            val mergedResults = sortedKeys.take(maxResults).mapNotNull { chunkContents[it] }

            Log.d("DaexRag", "Hybrid search returned ${mergedResults.size} chunks for query: ${query.take(50)}")
            mergedResults
        } catch (e: Exception) {
            Log.e("DaexRag", "Document query failed", e)
            emptyList()
        }
    }

    @Suppress("DEPRECATION")
    override suspend fun getUploadedFiles(): List<String> = withContext(Dispatchers.IO) {
        try {
            chunkBox.query().build()
                .property(DocumentChunkEntity_.fileName)
                .distinct()
                .findStrings()
                .toList()
        } catch (e: Exception) {
            Log.e("DaexRag", "Failed to get uploaded files", e)
            emptyList()
        }
    }

    override suspend fun deleteFile(documentId: String) {
        withContext(Dispatchers.IO) {
            try {
                val chunks = chunkBox.query {
                    equal(DocumentChunkEntity_.documentId, documentId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                }.find()
                chunkBox.remove(chunks)
                ftsDbHelper.deleteChunksByDocumentId(documentId)
                Log.d("DaexRag", "Deleted ${chunks.size} chunks for document $documentId")
            } catch (e: Exception) {
                Log.e("DaexRag", "Failed to delete file", e)
            }
        }
    }

    override suspend fun deleteFileByName(fileName: String) {
        withContext(Dispatchers.IO) {
            try {
                val chunks = chunkBox.query {
                    equal(DocumentChunkEntity_.fileName, fileName, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                }.find()
                chunkBox.remove(chunks)
                ftsDbHelper.deleteChunksByFileName(fileName)
                Log.d("DaexRag", "Deleted ${chunks.size} chunks for file $fileName")
            } catch (e: Exception) {
                Log.e("DaexRag", "Failed to delete file by name $fileName", e)
            }
        }
    }

    override suspend fun hasDocuments(): Boolean = withContext(Dispatchers.IO) {
        try {
            chunkBox.count() > 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Splits text into overlapping chunks of ~300 characters.
     * Splits by paragraphs first, then by sentences if a paragraph is too long.
     */
    private fun chunkText(text: String, maxChunkSize: Int = 300, overlap: Int = 50): List<String> {
        val paragraphs = text.split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()

        for (paragraph in paragraphs) {
            val trimmed = paragraph.trim()
            if (trimmed.length <= maxChunkSize) {
                chunks.add(trimmed)
            } else {
                // Split long paragraphs by sentences
                val sentences = trimmed.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                val buffer = StringBuilder()
                for (sentence in sentences) {
                    if (buffer.length + sentence.length > maxChunkSize && buffer.isNotEmpty()) {
                        chunks.add(buffer.toString().trim())
                        // Overlap: keep the tail of the previous chunk
                        val overlapText = buffer.toString().takeLast(overlap)
                        buffer.clear()
                        buffer.append(overlapText)
                    }
                    buffer.append(sentence).append(" ")
                }
                if (buffer.isNotBlank()) {
                    chunks.add(buffer.toString().trim())
                }
            }
        }
        return chunks
    }
}