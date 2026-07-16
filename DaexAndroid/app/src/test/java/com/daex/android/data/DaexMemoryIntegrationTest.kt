package com.daex.android.data

import android.content.Context
import com.daex.android.framework.Message
import io.mockk.every
import io.mockk.mockk
import io.objectbox.BoxStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Integration test: a real ObjectBox [BoxStore] (desktop-native binary, see the testImplementation
 * dependencies in app/build.gradle.kts) and a real SQLite FTS5 index via [DaexMessageFtsHelper],
 * wired together exactly as in production - no mocking at the repository boundary. This is what
 * catches the class of bug a mocked-boundary ViewModel unit test structurally cannot: two
 * genuinely real components correctly implemented individually but wired together wrong.
 *
 * No embedder is supplied, so [DaexMemory.searchMessages] exercises only its FTS5 half - the
 * vector/HNSW half would need the embedding model itself, which isn't available off-device.
 */
class DaexMemoryIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var boxStore: BoxStore
    private lateinit var memory: DaexMemory

    @Before
    fun setup() {
        boxStore = MyObjectBox.builder().directory(tempFolder.newFolder("objectbox")).build()
        val context = mockk<Context>()
        every { context.getDatabasePath(any()) } answers {
            File(tempFolder.root, "databases/${firstArg<String>()}")
        }
        memory = DaexMemory(boxStore = boxStore, embedder = null, context = context)
    }

    @After
    fun tearDown() {
        boxStore.close()
    }

    @Test
    fun `creating a conversation makes it show up in the full list`() = runBlocking {
        val id = memory.createConversation(modelId = "test-model", title = "My chat")

        val conversations = memory.getAllConversationsList()

        assertEquals(1, conversations.size)
        assertEquals(id, conversations[0].id)
        assertEquals("My chat", conversations[0].title)
    }

    @Test
    fun `messages round-trip through a real BoxStore in timestamp order`() = runBlocking {
        val convId = memory.createConversation("test-model")
        memory.saveMessage(convId, Message(id = "m1", role = "user", content = "first", timestamp = 100))
        memory.saveMessage(convId, Message(id = "m2", role = "model", content = "second", timestamp = 200))

        val history = memory.getRecentHistory(convId)

        assertEquals(listOf("first", "second"), history.map { it.content })
    }

    @Test
    fun `saving the same message id again updates it in place instead of duplicating`() = runBlocking {
        val convId = memory.createConversation("test-model")
        memory.saveMessage(convId, Message(id = "m1", role = "model", content = "", timestamp = 100))
        memory.saveMessage(convId, Message(id = "m1", role = "model", content = "final answer", timestamp = 100))

        val history = memory.getMessagesForConversationList(convId)

        assertEquals(1, history.size)
        assertEquals("final answer", history[0].content)
    }

    @Test
    fun `searchMessages finds a message via the real FTS5 index across conversations`() = runBlocking {
        val convA = memory.createConversation("test-model", title = "Cooking chat")
        val convB = memory.createConversation("test-model", title = "Space chat")
        val breadMsg = Message(id = "m1", role = "user", content = "How do I bake sourdough bread?")
        val moonMsg = Message(id = "m2", role = "user", content = "How far away is the moon?")
        memory.saveMessageWithEmbedding(convA, breadMsg)
        memory.saveMessageWithEmbedding(convB, moonMsg)

        val results = memory.searchMessages("sourdough bread")

        assertEquals(1, results.size)
        assertEquals(convA, results[0].conversationId)
        assertEquals("m1", results[0].message.id)
    }

    @Test
    fun `searchMessages ignores system log and compaction noise`() = runBlocking {
        val convId = memory.createConversation("test-model")
        val logMsg = Message(id = "m1", role = "system", content = "[SYSTEM_LOG]: searching for widgets")
        val userMsg = Message(id = "m2", role = "user", content = "tell me about widgets")
        memory.saveMessageWithEmbedding(convId, logMsg)
        memory.saveMessageWithEmbedding(convId, userMsg)

        val results = memory.searchMessages("widgets")

        assertEquals(listOf("m2"), results.map { it.message.id })
    }

    @Test
    fun `pinning a message surfaces it in getPinnedMessages`() = runBlocking {
        val convId = memory.createConversation("test-model")
        memory.saveMessage(convId, Message(id = "m1", role = "user", content = "pin me"))

        memory.setPinned("m1", true)

        val pinned = memory.getPinnedMessages()
        assertEquals(listOf("m1"), pinned.map { it.id })

        memory.setPinned("m1", false)
        assertTrue(memory.getPinnedMessages().isEmpty())
    }

    @Test
    fun `updateAttachedFiles persists and round-trips the file list`() = runBlocking {
        val convId = memory.createConversation("test-model")

        memory.updateAttachedFiles(convId, listOf("doc1.pdf", "doc2.txt"))

        val conversations = memory.getAllConversationsList()
        assertEquals(listOf("doc1.pdf", "doc2.txt"), conversations[0].attachedFileNames)
    }

    @Test
    fun `deleteConversation removes the conversation and all its messages`() = runBlocking {
        val convId = memory.createConversation("test-model")
        memory.saveMessage(convId, Message(id = "m1", role = "user", content = "hello"))

        memory.deleteConversation(convId)

        assertTrue(memory.getAllConversationsList().isEmpty())
        assertTrue(memory.getMessagesForConversationList(convId).isEmpty())
    }

    @Test
    fun `deleteAllConversations clears every conversation and message`() = runBlocking {
        val convId = memory.createConversation("test-model")
        memory.saveMessage(convId, Message(id = "m1", role = "user", content = "hello"))
        memory.createConversation("test-model", title = "second")

        memory.deleteAllConversations()

        assertTrue(memory.getAllConversationsList().isEmpty())
        assertNull(memory.getMessagesForConversationList(convId).firstOrNull())
    }
}
