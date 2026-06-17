package com.daex.android.database

import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Unique
import io.objectbox.annotation.HnswIndex

@Entity
data class ConversationEntity(
    @Id var id: Long = 0,
    @Unique var uuid: String = "",
    var title: String = "",
    var modelId: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var attachedFileNames: String? = ""
)

@Entity
data class MessageEntity(
    @Id var id: Long = 0,
    @Unique var uuid: String = "",
    var conversationId: String = "",
    var role: String = "",
    var content: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var tokensPerSecond: Double = 0.0,
    var thoughtContent: String? = null,
    var isPinned: Boolean = false,
    var isCompacted: Boolean = false,
    
    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
)

@Entity
data class DocumentChunkEntity(
    @Id var id: Long = 0,
    var documentId: String = "",
    var fileName: String = "",
    var chunkIndex: Int = 0,
    var content: String = "",
    var createdAt: Long = System.currentTimeMillis(),

    @HnswIndex(dimensions = 384)
    var embedding: FloatArray? = null
)
