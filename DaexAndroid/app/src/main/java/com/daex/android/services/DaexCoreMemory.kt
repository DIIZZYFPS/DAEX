package com.daex.android.services

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface DaexCoreMemory {
    suspend fun getMemoryContent(): String
    suspend fun overwriteMemory(content: String)
    suspend fun compactMemory(recentMessages: List<Message>, daexService: DaexService)
}

class DaexCoreMemoryImpl(private val context: Context) : DaexCoreMemory {

    private val memoryFile: File
        get() = File(context.filesDir, "core_memory.md")

    override suspend fun getMemoryContent(): String = withContext(Dispatchers.IO) {
        if (!memoryFile.exists()) {
            val defaultTemplate = """
                # Core Memory

                ## User Profile
                - (No profile details recorded yet)

                ## Project & Environment
                - (No project details recorded yet)

                ## Durable Learnings
                - (No learnings recorded yet)
            """.trimIndent()
            memoryFile.writeText(defaultTemplate)
            return@withContext defaultTemplate
        }
        return@withContext memoryFile.readText()
    }

    override suspend fun overwriteMemory(content: String) = withContext(Dispatchers.IO) {
        try {
            memoryFile.writeText(content)
            Log.d("DaexCoreMemory", "Core memory explicitly overwritten")
        } catch (e: Exception) {
            Log.e("DaexCoreMemory", "Failed to overwrite core memory", e)
        }
        Unit
    }

    override suspend fun compactMemory(recentMessages: List<Message>, daexService: DaexService) {
        try {
            val currentMemory = getMemoryContent()

            val conversationBlock = recentMessages.joinToString("\n") { msg ->
                "${msg.role}: ${msg.content}"
            }

            val compactorPrompt = buildString {
                append("<bos><|turn>system\n")
                append("You are a memory curator. Your ONLY job is to maintain a clean markdown file of important facts.\n")
                append("You will receive the current memory file and a recent conversation segment.\n")
                append("Output ONLY the updated markdown file — nothing else. No preamble, no explanation, no commentary.\n")
                append("Rules:\n")
                append("- Keep all existing facts that are still relevant.\n")
                append("- Classify new facts into the correct sections:\n")
                append("  * '## User Profile' for name, personal traits, communication habits, and persistent preferences.\n")
                append("  * '## Project & Environment' for active directories, build tools, setup details, and system specifications.\n")
                append("  * '## Durable Learnings' for code workarounds, rules, instructions, or methods that succeeded/failed.\n")
                append("- Remove placeholder bullets like '(No... recorded yet)' once real facts exist in a section.\n")
                append("- Merge duplicate facts and keep information highly concise.\n")
                append("- Keep the entire markdown file strictly under a 2,000-character budget. If the list is getting long, aggressively merge related bullets and prune older/completed or less relevant details.\n")
                append("- Maintain the exact headers '# Core Memory', '## User Profile', '## Project & Environment', and '## Durable Learnings'.\n")
                append("<turn|>\n")
                append("<|turn>user\n")
                append("CURRENT MEMORY FILE:\n")
                append("```\n")
                append(currentMemory)
                append("\n```\n\n")
                append("RECENT CONVERSATION:\n")
                append("```\n")
                append(conversationBlock)
                append("\n```\n")
                append("<turn|>\n")
                append("<|turn>model\n")
            }

            val result = daexService.generateSilent(compactorPrompt, maxTokens = 512)
            val cleaned = result.trim()

            // Only overwrite if the compactor actually produced markdown content with correct headers
            if (cleaned.contains("# Core Memory") && (cleaned.contains("## User Profile") || cleaned.contains("## Project & Environment") || cleaned.contains("## Durable Learnings"))) {
                overwriteMemory(cleaned)
                Log.d("DaexCoreMemory", "Memory compacted successfully (${cleaned.length} chars)")
            } else {
                Log.w("DaexCoreMemory", "Compactor output didn't look like valid markdown structure, skipping overwrite. Output: ${cleaned.take(200)}")
            }
        } catch (e: Exception) {
            Log.e("DaexCoreMemory", "Memory compaction failed", e)
        }
    }
}
