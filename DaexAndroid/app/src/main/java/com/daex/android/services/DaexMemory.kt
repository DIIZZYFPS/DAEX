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
import java.util.UUID

data class Conversation(
    val id: String,
    val title: String,
    val modelId: String,
    val createdAt: Long,
    val attachedDocumentIds: List<String> = emptyList()
)

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

    suspend fun getRecentHistory(conversationId: String, limit: Int = 500): List<Message> {
        val entities = messageBox.query {
            equal(MessageEntity_.conversationId, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
            orderDesc(MessageEntity_.timestamp)
        }.find(0, limit.toLong())
        return entities.reversed().map { it.toDomain() }
    }

    suspend fun createConversation(modelId: String, title: String = "New Execution"): String {
        val id = UUID.randomUUID().toString()
        val conversation = ConversationEntity(uuid = id, title = title, modelId = modelId)
        conversationBox.put(conversation)
        return id
    }

    suspend fun saveMessage(conversationId: String, message: Message, embedding: FloatArray? = null) {
        var entity = messageBox.query {
            equal(MessageEntity_.uuid, message.id, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst()

        if (entity == null) {
            entity = MessageEntity(
                uuid = message.id,
                conversationId = conversationId,
                role = message.role,
                content = message.content,
                tokensPerSecond = message.tokensPerSecond,
                thoughtContent = message.thoughtContent,
                embedding = embedding
            )
        } else {
            entity.content = message.content
            entity.tokensPerSecond = message.tokensPerSecond
            entity.thoughtContent = message.thoughtContent
            if (embedding != null) {
                entity.embedding = embedding
            }
        }
        messageBox.put(entity)
    }

    suspend fun deleteConversation(conversationId: String) {
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

    suspend fun updateConversationAttachments(conversationId: String, documentIds: List<String>) {
        val entity = conversationBox.query {
            equal(ConversationEntity_.uuid, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst()
        if (entity != null) {
            entity.attachedDocumentIds = documentIds.joinToString(",")
            conversationBox.put(entity)
        }
    }

    suspend fun getConversationAttachments(conversationId: String): List<String> {
        val entity = conversationBox.query {
            equal(ConversationEntity_.uuid, conversationId, io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE)
        }.findFirst()
        return entity?.attachedDocumentIds
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }

    suspend fun removeDocumentFromAllConversationAttachments(documentId: String) {
        val conversations = conversationBox.all
        val updated = conversations.mapNotNull { entity ->
            val currentIds = entity.attachedDocumentIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
            if (documentId in currentIds) {
                entity.attachedDocumentIds = currentIds.filter { it != documentId }.joinToString(",")
                entity
            } else {
                null
            }
        }
        if (updated.isNotEmpty()) {
            conversationBox.put(updated)
        }
    }

    fun searchSimilarContext(queryVector: FloatArray, maxResults: Int = 5, queryText: String = ""): List<Message> {
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

        return contextMessages.map { it.toDomain() }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = this.uuid,
        title = this.title,
        modelId = this.modelId,
        createdAt = this.createdAt,
        attachedDocumentIds = this.attachedDocumentIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    )

    private fun MessageEntity.toDomain() = Message(
        id = this.uuid,
        role = this.role,
        content = this.content,
        tokensPerSecond = this.tokensPerSecond,
        thoughtContent = this.thoughtContent
    )
}
