package com.daex.android.services

import android.util.Log
import com.daex.android.database.DocumentChunkEntity
import com.daex.android.database.DocumentChunkEntity_
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import java.util.UUID

data class VaultDocument(
    val documentId: String,
    val fileName: String,
    val displayName: String,
    val chunkCount: Int
)

interface DaexRag {
    suspend fun initRag()
    suspend fun ingestFile(fileName: String, content: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): String
    suspend fun queryDocuments(query: String, allowedDocumentIds: List<String>, maxResults: Int = 5): List<String>
    suspend fun getVaultDocuments(): List<VaultDocument>
    suspend fun renameDocument(documentId: String, newName: String)
    suspend fun deleteDocument(documentId: String)
    fun hasDocuments(documentIds: List<String>): Boolean
}

class DaexRagImpl(
    private val boxStore: BoxStore,
    private val embedder: DaexEmbedder
) : DaexRag {

    private val chunkBox by lazy { boxStore.boxFor(DocumentChunkEntity::class.java) }

    override suspend fun initRag() {
        embedder.initEmbeddingContext()
    }

    override suspend fun ingestFile(fileName: String, content: String, onProgress: (Int, Int) -> Unit): String {
        val documentId = UUID.randomUUID().toString()
        val chunks = chunkText(content)
        Log.d("DaexRag", "Ingesting file '$fileName': ${chunks.size} chunks")

        chunks.forEachIndexed { index, chunkText ->
            try {
                val vector = embedder.generateEmbedding(chunkText, isQuery = false)
                val entity = DocumentChunkEntity(
                    documentId = documentId,
                    fileName = fileName,
                    displayName = fileName,
                    chunkIndex = index,
                    content = chunkText,
                    embedding = vector
                )
                chunkBox.put(entity)
                onProgress(index + 1, chunks.size)
                Log.d("DaexRag", "Embedded chunk ${index + 1}/${chunks.size}")
            } catch (e: Exception) {
                Log.e("DaexRag", "Failed to embed chunk $index of '$fileName'", e)
            }
        }
        Log.d("DaexRag", "File '$fileName' ingested: $documentId")
        return documentId
    }

    override suspend fun queryDocuments(query: String, allowedDocumentIds: List<String>, maxResults: Int): List<String> {
        return try {
            if (allowedDocumentIds.isEmpty()) return emptyList()

            val queryVector = embedder.generateEmbedding(query, isQuery = true)

            // Get nearest neighbors across all chunks, then filter to allowed documents
            val results = chunkBox
                .query(DocumentChunkEntity_.embedding.nearestNeighbors(queryVector, maxResults * 3))
                .build()
                .findWithScores()

            val chunks = results
                .map { it.get() }
                .filter { it.documentId != null && it.documentId!! in allowedDocumentIds }
                .take(maxResults)
                .mapNotNull { it.content }

            Log.d("DaexRag", "Targeted query returned ${chunks.size} chunks for: ${query.take(50)}")
            chunks
        } catch (e: Exception) {
            Log.e("DaexRag", "Document query failed", e)
            emptyList()
        }
    }

    override suspend fun getVaultDocuments(): List<VaultDocument> {
        return try {
            val allChunks = chunkBox.all
            allChunks.filter { it.documentId != null && it.fileName != null }
                .groupBy { it.documentId }.map { (docId, chunks) ->
                    val firstChunk = chunks.first()
                    VaultDocument(
                        documentId = docId!!,
                        fileName = firstChunk.fileName ?: "unknown",
                        displayName = (firstChunk.displayName ?: "").ifBlank { firstChunk.fileName ?: "unknown" },
                        chunkCount = chunks.size
                    )
                }
        } catch (e: Exception) {
            Log.e("DaexRag", "Failed to get vault documents", e)
            emptyList()
        }
    }

    override suspend fun renameDocument(documentId: String, newName: String) {
        try {
            val chunks = chunkBox.query {
                equal(DocumentChunkEntity_.documentId, documentId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            }.find()
            chunks.forEach { it.displayName = newName }
            chunkBox.put(chunks)
            Log.d("DaexRag", "Renamed document $documentId to '$newName'")
        } catch (e: Exception) {
            Log.e("DaexRag", "Failed to rename document", e)
        }
    }

    override suspend fun deleteDocument(documentId: String) {
        try {
            val chunks = chunkBox.query {
                equal(DocumentChunkEntity_.documentId, documentId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            }.find()
            chunkBox.remove(chunks)
            Log.d("DaexRag", "Deleted ${chunks.size} chunks for document $documentId")
        } catch (e: Exception) {
            Log.e("DaexRag", "Failed to delete document", e)
        }
    }

    override fun hasDocuments(documentIds: List<String>): Boolean {
        if (documentIds.isEmpty()) return false
        return try {
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