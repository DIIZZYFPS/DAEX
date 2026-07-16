package com.daex.android.data

import android.content.Context
import com.daex.android.framework.DaexEmbedder
import com.daex.android.framework.Message
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import io.objectbox.kotlin.toFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class Conversation(
    val id: String,
    val title: String,
    val modelId: String,
    val createdAt: Long,
    val attachedFileNames: List<String> = emptyList()
)

/** A message-level search hit, carrying which conversation it belongs to for navigation. */
data class MessageSearchResult(
    val conversationId: String,
    val message: Message
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DaexMemory(
    private val boxStore: BoxStore,
    private val embedder: DaexEmbedder? = null,
    context: Context? = null
) {
    private val conversationBox = boxStore.boxFor(ConversationEntity::class.java)
    private val messageBox = boxStore.boxFor(MessageEntity::class.java)
    private val saveMutex = Mutex()
    private val messageFtsHelper = context?.let { DaexMessageFtsHelper(it) }

    // Only user/model messages with real content are worth searching - system logs, tool
    // status noise, and empty streaming placeholders would just pollute results.
    private fun isSearchable(message: Message): Boolean =
        (message.role == "user" || message.role == "model") &&
            message.content.isNotBlank() &&
            !message.content.startsWith("[SYSTEM_LOG]") &&
            !message.content.startsWith("[CONTEXT COMPACTION]")

    suspend fun getAllConversationsList(): List<Conversation> = withContext(Dispatchers.IO) {
        android.util.Log.d("DaexMemory", "getAllConversationsList called")
        try {
            val entities = conversationBox.query {
                orderDesc(ConversationEntity_.createdAt)
            }.find()
            android.util.Log.d("DaexMemory", "Found ${entities.size} conversations")
            entities.map { it.toDomain() }
        } catch (e: Exception) {
            android.util.Log.e("DaexMemory", "Error in getAllConversationsList", e)
            emptyList()
        }
    }

    suspend fun getMessagesForConversationList(conversationId: String): List<Message> = withContext(Dispatchers.IO) {
        android.util.Log.d("DaexMemory", "getMessagesForConversationList called for $conversationId")
        try {
            val entities = messageBox.query {
                equal(MessageEntity_.conversationId, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                order(MessageEntity_.timestamp)
            }.find()
            entities.map { it.toDomain() }
        } catch (e: Exception) {
            android.util.Log.e("DaexMemory", "Error in getMessagesForConversationList", e)
            emptyList()
        }
    }

    suspend fun getRecentHistory(conversationId: String, limit: Int = 500): List<Message> = withContext(Dispatchers.IO) {
        val entities = messageBox.query {
            equal(MessageEntity_.conversationId, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            orderDesc(MessageEntity_.timestamp)
        }.find(0, limit.toLong())
        entities.reversed().map { it.toDomain() }
    }

    suspend fun createConversation(modelId: String, title: String = "New Execution"): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        android.util.Log.d("DaexMemory", "createConversation uuid=$id, title=$title, modelId=$modelId")
        try {
            val conversation = ConversationEntity(uuid = id, title = title, modelId = modelId)
            conversationBox.put(conversation)
            android.util.Log.d("DaexMemory", "Successfully created and put conversation uuid=$id")
        } catch (e: Exception) {
            android.util.Log.e("DaexMemory", "Failed to put conversation uuid=$id", e)
            throw e
        }
        id
    }

    suspend fun saveMessage(conversationId: String, message: Message, embedding: FloatArray? = null) = withContext(Dispatchers.IO) {
        android.util.Log.d("DaexMemory", "saveMessage convId=$conversationId, messageId=${message.id}, role=${message.role}")
        saveMutex.withLock {
            var entity = messageBox.query {
                equal(MessageEntity_.uuid, message.id, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            }.findFirst()

            if (entity == null) {
                entity = MessageEntity(
                    uuid = message.id,
                    conversationId = conversationId,
                    role = message.role,
                    content = message.content,
                    timestamp = message.timestamp,
                    tokensPerSecond = message.tokensPerSecond,
                    thoughtContent = message.thoughtContent,
                    isPinned = message.isPinned,
                    isCompacted = message.isCompacted,
                    audioPath = message.audioPath,
                    embedding = embedding
                )
            } else {
                entity.content = message.content
                entity.timestamp = message.timestamp
                entity.tokensPerSecond = message.tokensPerSecond
                entity.thoughtContent = message.thoughtContent
                entity.isPinned = message.isPinned
                entity.isCompacted = message.isCompacted
                entity.audioPath = message.audioPath
                if (embedding != null) {
                    entity.embedding = embedding
                }
            }
            messageBox.put(entity)
        }
    }

    /**
     * Like [saveMessage], but also computes and stores an embedding for cross-conversation
     * search (see [searchMessages]) - intended for the *final*, stable save of a message (the
     * user's own message, or a model reply once generation is complete), not intermediate
     * streaming updates or system-log noise, since embedding is comparatively expensive and
     * would otherwise get recomputed on every partial save.
     */
    suspend fun saveMessageWithEmbedding(conversationId: String, message: Message) {
        val searchable = isSearchable(message)
        val vector = if (embedder != null && searchable) {
            try {
                embedder.generateEmbedding(message.content, isQuery = false)
            } catch (e: Exception) {
                android.util.Log.w("DaexMemory", "Failed to embed message ${message.id}, saving without it", e)
                null
            }
        } else null
        saveMessage(conversationId, message, vector)
        if (searchable) {
            messageFtsHelper?.indexMessage(conversationId, message.id, message.content)
        }
    }

    /**
     * Hybrid vector+BM25 search across every conversation's messages (RRF fusion, same approach
     * as DaexRagImpl.queryDocuments for RAG chunks). Purely user-initiated (Sidebar search box,
     * or the agentic recall tool) - never called automatically per-turn, so nothing from this
     * ever gets silently injected into a conversation's context.
     */
    suspend fun searchMessages(query: String, maxResults: Int = 8): List<MessageSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val vectorResults: List<MessageEntity> = if (embedder != null && messageBox.count() > 0) {
                try {
                    val queryVector = embedder.generateEmbedding(query, isQuery = true)
                    messageBox
                        .query(MessageEntity_.embedding.nearestNeighbors(queryVector, (maxResults * 10).coerceAtLeast(50)))
                        .build()
                        .findWithScores()
                        .map { it.get() }
                } catch (e: Exception) {
                    android.util.Log.w("DaexMemory", "Vector search failed, falling back to keyword-only", e)
                    emptyList()
                }
            } else emptyList()

            val ftsResults = messageFtsHelper?.searchMessages(query, 50) ?: emptyList()

            // Reciprocal Rank Fusion, same formula/weights as DaexRagImpl.queryDocuments.
            val rrfScores = mutableMapOf<String, Double>()
            val byMessageId = mutableMapOf<String, MessageEntity>()

            vectorResults.forEachIndexed { index, entity ->
                val key = entity.uuid
                rrfScores[key] = (rrfScores[key] ?: 0.0) + 1.0 / (60.0 + index + 1)
                byMessageId.putIfAbsent(key, entity)
            }

            if (ftsResults.isNotEmpty()) {
                var idCond: io.objectbox.query.QueryCondition<MessageEntity>? = null
                for (match in ftsResults) {
                    val cond = MessageEntity_.uuid.equal(match.messageId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                    idCond = if (idCond == null) cond else idCond.or(cond)
                }
                val ftsEntities = if (idCond != null) {
                    messageBox.query(idCond).build().find().associateBy { it.uuid }
                } else emptyMap()

                ftsResults.forEachIndexed { index, match ->
                    val entity = ftsEntities[match.messageId] ?: return@forEachIndexed
                    val key = entity.uuid
                    rrfScores[key] = (rrfScores[key] ?: 0.0) + 2.0 / (60.0 + index + 1)
                    byMessageId.putIfAbsent(key, entity)
                }
            }

            rrfScores.entries
                .sortedByDescending { it.value }
                .take(maxResults)
                .mapNotNull { (key, _) ->
                    byMessageId[key]?.let { entity ->
                        MessageSearchResult(conversationId = entity.conversationId, message = entity.toDomain())
                    }
                }
        } catch (e: Exception) {
            android.util.Log.e("DaexMemory", "searchMessages failed", e)
            emptyList()
        }
    }

    /**
     * One-time background backfill for messages saved before cross-conversation search shipped
     * (no embedding/FTS entry yet). Safe to call repeatedly - already-embedded messages are
     * skipped by the isNull() filter, so re-running it is a cheap no-op.
     */
    suspend fun backfillMissingEmbeddings() = withContext(Dispatchers.IO) {
        if (embedder == null) return@withContext
        try {
            val candidates = messageBox.query {
                isNull(MessageEntity_.embedding)
            }.find().filter { isSearchable(it.toDomain()) }

            android.util.Log.d("DaexMemory", "Backfilling embeddings for ${candidates.size} existing messages")
            for (entity in candidates) {
                try {
                    val vector = embedder.generateEmbedding(entity.content, isQuery = false)
                    entity.embedding = vector
                    messageBox.put(entity)
                    messageFtsHelper?.indexMessage(entity.conversationId, entity.uuid, entity.content)
                } catch (e: Exception) {
                    android.util.Log.w("DaexMemory", "Failed to backfill embedding for message ${entity.uuid}", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DaexMemory", "Embedding backfill failed", e)
        }
    }

    /** All pinned messages across every conversation, newest first - backs the saved-prompt library. */
    suspend fun getPinnedMessages(): List<Message> = withContext(Dispatchers.IO) {
        try {
            messageBox.query {
                equal(MessageEntity_.isPinned, true)
                orderDesc(MessageEntity_.timestamp)
            }.find().map { it.toDomain() }
        } catch (e: Exception) {
            android.util.Log.e("DaexMemory", "Failed to get pinned messages", e)
            emptyList()
        }
    }

    suspend fun setPinned(messageId: String, isPinned: Boolean) = withContext(Dispatchers.IO) {
        saveMutex.withLock {
            val entity = messageBox.query {
                equal(MessageEntity_.uuid, messageId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            }.findFirst()
            if (entity != null) {
                entity.isPinned = isPinned
                messageBox.put(entity)
            }
        }
    }

    suspend fun updateAttachedFiles(conversationId: String, files: List<String>) = withContext(Dispatchers.IO) {
        val entity = conversationBox.query {
            equal(ConversationEntity_.uuid, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst()
        if (entity != null) {
            entity.attachedFileNames = files.joinToString("|")
            conversationBox.put(entity)
        }
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
        boxStore.runInTx {
            val conversation = conversationBox.query {
                equal(ConversationEntity_.uuid, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            }.findFirst()

            if (conversation != null) {
                conversationBox.remove(conversation)

                val messages = messageBox.query {
                    equal(MessageEntity_.conversationId, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                }.find()
                messageBox.remove(messages)
            }
        }
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        conversationBox.removeAll()
        messageBox.removeAll()
    }

    suspend fun searchSimilarContext(queryVector: FloatArray, maxResults: Int = 5, queryText: String = ""): List<Message> = withContext(Dispatchers.IO) {
        val count = messageBox.count()
        android.util.Log.d("DaexMemory", "Searching across $count messages")

        val results = messageBox
            .query(MessageEntity_.embedding.nearestNeighbors(queryVector, maxResults))
            .build()
            .findWithScores()
        
        val contextMessages = mutableListOf<MessageEntity>()
        val seenIds = mutableSetOf<Long>()

        results.forEach { result ->
            val entity = result.get()
            
            // Skip exact duplicate questions to avoid useless self-referencing context
            val isExactMatch = queryText.isNotBlank() && entity.content.trim().equals(queryText.trim(), ignoreCase = true)
            
            if (!isExactMatch && seenIds.add(entity.id)) {
                contextMessages.add(entity)
            }

            // CONTEXT EXPANSION:
            // If the user's current question matched a previous question (even if skipped above), 
            // the actually useful information is the answer that followed it!
            // Fetch the very next message in that conversation.
            val nextMessage = messageBox.query {
                equal(MessageEntity_.conversationId, entity.conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
                greater(MessageEntity_.id, entity.id)
                order(MessageEntity_.id)
            }.findFirst()
                
            if (nextMessage != null && seenIds.add(nextMessage.id)) {
                contextMessages.add(nextMessage)
            }
        }

        contextMessages.map { it.toDomain() }
    }

    private fun ConversationEntity.toDomain(): Conversation {
        android.util.Log.d("DaexMemory", "toDomain for conversation uuid=${this.uuid}, title=${this.title}")
        return Conversation(
            id = this.uuid,
            title = this.title,
            modelId = this.modelId,
            createdAt = this.createdAt,
            attachedFileNames = (this.attachedFileNames ?: "").split("|").filter { it.isNotBlank() }
        )
    }

    private fun MessageEntity.toDomain() = Message(
        id = this.uuid,
        role = this.role,
        content = this.content,
        tokensPerSecond = this.tokensPerSecond,
        thoughtContent = this.thoughtContent,
        isPinned = this.isPinned,
        isCompacted = this.isCompacted,
        audioPath = this.audioPath,
        timestamp = this.timestamp
    )
}
