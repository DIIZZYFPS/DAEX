package com.daex.android.services

import com.daex.android.database.ConversationEntity
import com.daex.android.database.ConversationEntity_
import com.daex.android.database.MessageEntity
import com.daex.android.database.MessageEntity_
import io.objectbox.BoxStore
import io.objectbox.kotlin.query
import io.objectbox.kotlin.flow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class Conversation(
    val id: String,
    val title: String,
    val modelId: String,
    val createdAt: Long
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DaexMemory(private val boxStore: BoxStore) {
    private val conversationBox = boxStore.boxFor(ConversationEntity::class.java)
    private val messageBox = boxStore.boxFor(MessageEntity::class.java)

    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationBox.query {
            orderDesc(ConversationEntity_.createdAt)
        }.flow().map { entities -> 
            entities.map { it.toDomain() } 
        }
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> {
        return messageBox.query {
            equal(MessageEntity_.conversationId, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            order(MessageEntity_.timestamp)
        }.flow().map { entities -> 
            entities.map { it.toDomain() } 
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
        val conversation = ConversationEntity(uuid = id, title = title, modelId = modelId)
        conversationBox.put(conversation)
        id
    }

    suspend fun saveMessage(conversationId: String, message: Message, embedding: FloatArray? = null) = withContext(Dispatchers.IO) {
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
                embedding = embedding
            )
        } else {
            entity.content = message.content
            entity.timestamp = message.timestamp
            entity.tokensPerSecond = message.tokensPerSecond
            entity.thoughtContent = message.thoughtContent
            entity.isPinned = message.isPinned
            entity.isCompacted = message.isCompacted
            if (embedding != null) {
                entity.embedding = embedding
            }
        }
        messageBox.put(entity)
    }

    suspend fun deleteConversation(conversationId: String) = withContext(Dispatchers.IO) {
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

    private fun ConversationEntity.toDomain() = Conversation(
        id = this.uuid,
        title = this.title,
        modelId = this.modelId,
        createdAt = this.createdAt
    )

    private fun MessageEntity.toDomain() = Message(
        id = this.uuid,
        role = this.role,
        content = this.content,
        tokensPerSecond = this.tokensPerSecond,
        thoughtContent = this.thoughtContent,
        isPinned = this.isPinned,
        isCompacted = this.isCompacted,
        timestamp = this.timestamp
    )
}
