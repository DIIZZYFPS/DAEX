package com.daex.android.framework

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DaexCoreMemoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun coreMemory(): DaexCoreMemoryImpl {
        val context = mockk<Context>()
        every { context.filesDir } returns tempFolder.root
        return DaexCoreMemoryImpl(context)
    }

    @Test
    fun `getMemoryContent creates the default template when no file exists yet`() = runBlocking {
        val memory = coreMemory()

        val content = memory.getMemoryContent()

        assertTrue(content.contains("# Core Memory"))
        assertTrue(content.contains("## User Profile"))
        assertTrue(content.contains("## Key Facts & Preferences"))
    }

    @Test
    fun `overwriteMemory persists content that a later read sees`() = runBlocking {
        val memory = coreMemory()

        memory.overwriteMemory("# Core Memory\n\n## User Profile\n- Loves hiking\n")

        assertEquals("# Core Memory\n\n## User Profile\n- Loves hiking\n", memory.getMemoryContent())
    }

    @Test
    fun `compactMemory persists valid compactor output and reports the newly learned bullets`() = runBlocking {
        val memory = coreMemory()
        memory.overwriteMemory(
            "# Core Memory\n\n## User Profile\n- (No profile details recorded yet)\n"
        )
        val daexService = mockk<DaexService>()
        val compactorOutput = """
            # Core Memory

            ## User Profile
            - Works as a nurse
        """.trimIndent()
        coEvery { daexService.generateSilent(any(), any()) } returns compactorOutput

        val result = memory.compactMemory(
            recentMessages = listOf(Message(id = "1", role = "user", content = "I'm a nurse")),
            daexService = daexService
        )

        assertEquals(listOf("Works as a nurse"), result.learnedBullets)
        assertTrue(memory.getMemoryContent().contains("Works as a nurse"))
    }

    @Test
    fun `compactMemory does not overwrite when the compactor output is malformed`() = runBlocking {
        val memory = coreMemory()
        val original = "# Core Memory\n\n## User Profile\n- Works as a nurse\n"
        memory.overwriteMemory(original)
        val daexService = mockk<DaexService>()
        coEvery { daexService.generateSilent(any(), any()) } returns "Sorry, I can't help with that."

        val result = memory.compactMemory(
            recentMessages = listOf(Message(id = "1", role = "user", content = "hi")),
            daexService = daexService
        )

        assertTrue(result.learnedBullets.isEmpty())
        assertEquals(original, memory.getMemoryContent())
    }

    @Test
    fun `compactMemory recovers gracefully when the engine throws`() = runBlocking {
        val memory = coreMemory()
        val original = "# Core Memory\n\n## User Profile\n- Works as a nurse\n"
        memory.overwriteMemory(original)
        val daexService = mockk<DaexService>()
        coEvery { daexService.generateSilent(any(), any()) } throws RuntimeException("engine unavailable")

        val result = memory.compactMemory(
            recentMessages = listOf(Message(id = "1", role = "user", content = "hi")),
            daexService = daexService
        )

        assertTrue(result.learnedBullets.isEmpty())
        assertEquals(original, memory.getMemoryContent())
    }
}
