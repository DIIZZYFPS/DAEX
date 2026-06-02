package com.daex.android.services

import android.util.Log
import com.daex.android.database.DocumentChunkEntity
import com.daex.android.database.DocumentChunkEntity_
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

interface DaexRag {
    suspend fun initRag()
    suspend fun ingestFile(fileName: String, content: String, onProgress: (Int, Int) -> Unit = { _, _ -> })
    suspend fun queryDocuments(query: String, maxResults: Int = 5): List<String>
    suspend fun getUploadedFiles(): List<String>
    suspend fun deleteFile(documentId: String)
    suspend fun hasDocuments(): Boolean
}

class DaexRagImpl(
    private val boxStore: BoxStore,
    private val embedder: DaexEmbedder
) : DaexRag {

    private val chunkBox by lazy { boxStore.boxFor(DocumentChunkEntity::class.java) }

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

    override suspend fun queryDocuments(query: String, maxResults: Int): List<String> = withContext(Dispatchers.IO) {
        try {
            if (chunkBox.count() == 0L) return@withContext emptyList()

            val queryVector = embedder.generateEmbedding(query, isQuery = true)

            val results = chunkBox
                .query(DocumentChunkEntity_.embedding.nearestNeighbors(queryVector, maxResults))
                .build()
                .findWithScores()

            val chunks = results.map { result -> result.get().content }
            Log.d("DaexRag", "Query returned ${chunks.size} chunks for: ${query.take(50)}")
            chunks
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
                Log.d("DaexRag", "Deleted ${chunks.size} chunks for document $documentId")
            } catch (e: Exception) {
                Log.e("DaexRag", "Failed to delete file", e)
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