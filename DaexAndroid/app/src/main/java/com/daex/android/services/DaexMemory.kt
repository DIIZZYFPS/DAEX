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
    val createdAt: Long
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
    fun searchSimilarContext(queryVector: FloatArray, maxResults: Int = 5): List<Message> {
        val count = messageBox.count()
        android.util.Log.d("DaexMemory", "Searching across $count messages")

        val results = messageBox.query()
            .nearestNeighbors(MessageEntity_.embedding, queryVector, maxResults)
            .build()
            .find()
        
        android.util.Log.d("DaexMemory", "Search vector length: ${queryVector.size}")
        results.forEach { 
            android.util.Log.d("DaexMemory", "Match found: id=${it.uuid}, role=${it.role}, content=${it.content.take(30)}...")
        }

        return results.map { it.toDomain() }
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
        thoughtContent = this.thoughtContent
    )
}
