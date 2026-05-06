package com.daex.android.services

import android.util.Log

interface DaexRag {
    suspend fun initRag()
    suspend fun retrieveContext(query: String): List<Message>
    suspend fun embedAndSaveMessage(conversationId: String, message: Message)
}

class DaexRagImpl(
    private val memory: DaexMemory,
    private val embedder: DaexEmbedder
) : DaexRag {

    override suspend fun initRag() {
        embedder.initEmbeddingContext()
    }

    override suspend fun retrieveContext(query: String): List<Message> {
        return try {
            Log.d("DaexRag", "Retrieving context for query: $query")
            // 1. Convert the user's query into a mathematical vector
            val queryVector = embedder.generateEmbedding(query, isQuery = true)
            
            // 2. Search ObjectBox for the closest matching messages
            val results = memory.searchSimilarContext(queryVector, maxResults = 5)
            Log.d("DaexRag", "Retrieved ${results.size} context messages")
            results
        } catch (e: Exception) {
            Log.e("DaexRag", "Context retrieval failed", e)
            // Fallback gracefully if embedding engine isn't initialized yet
            emptyList()
        }
    }

    override suspend fun embedAndSaveMessage(conversationId: String, message: Message) {
        // We only generate embeddings for messages that have actual text content
        if (message.content.isNotBlank()) {
            try {
                Log.d("DaexRag", "Embedding and saving message: ${message.id}, role=${message.role}")
                val vector = embedder.generateEmbedding(message.content, isQuery = false)
                memory.saveMessage(conversationId, message, vector)
                Log.d("DaexRag", "Message saved with embedding")
            } catch (e: Exception) {
                Log.e("DaexRag", "Message embedding failed", e)
                // Fallback gracefully to normal save if embedding engine fails or isn't ready
                memory.saveMessage(conversationId, message)
            }
        } else {
            // For empty placeholders (like when the model is just starting to type)
            memory.saveMessage(conversationId, message)
        }
    }
}