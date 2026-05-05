package com.daex.android.services

import com.daex.android.database.ConversationEntity
import com.daex.android.database.DaexDao
import com.daex.android.database.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class Conversation(
    val id: String,
    val title: String,
    val modelId: String,
    val createdAt: Long
)

class DaexMemory(private val daexDao: DaexDao) {

    fun getAllConversations(): Flow<List<Conversation>> {
        return daexDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun getMessagesForConversation(conversationId: String): Flow<List<Message>> {
        return daexDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getRecentHistory(conversationId: String, limit: Int = 500): List<Message> {
        return daexDao.getLatestMessages(conversationId, limit).reversed().map { it.toDomain() }
    }

    suspend fun createConversation(modelId: String, title: String = "New Execution"): String {
        val id = UUID.randomUUID().toString()
        val conversation = ConversationEntity(id = id, title = title, modelId = modelId)
        daexDao.insertConversation(conversation)
        return id
    }

    suspend fun saveMessage(conversationId: String, message: Message) {
        val entity = MessageEntity(
            id = message.id,
            conversationId = conversationId,
            role = message.role,
            content = message.content,
            tokensPerSecond = message.tokensPerSecond,
            thoughtContent = message.thoughtContent
        )
        daexDao.insertMessage(entity)
    }

    suspend fun deleteConversation(conversationId: String) {
        val conversation = daexDao.getConversationById(conversationId)
        if (conversation != null) {
            daexDao.deleteConversation(conversation)
        }
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        title = title,
        modelId = modelId,
        createdAt = createdAt
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        role = role,
        content = content,
        tokensPerSecond = tokensPerSecond,
        thoughtContent = thoughtContent
    )
}
