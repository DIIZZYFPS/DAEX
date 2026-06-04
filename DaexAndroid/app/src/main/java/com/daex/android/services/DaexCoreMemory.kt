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
                
                This file contains persistent facts about the user and the environment.
                
                ## User Facts
                - (No facts recorded yet)
                
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
                append("You are a memory curator. Your ONLY job is to maintain a clean markdown file of important facts about the user.\n")
                append("You will receive the current memory file and a recent conversation.\n")
                append("Output ONLY the updated markdown file — nothing else. No preamble, no explanation, no commentary.\n")
                append("Rules:\n")
                append("- Keep all existing facts that are still relevant.\n")
                append("- Add any new important facts from the conversation (preferences, personal info, project details, etc).\n")
                append("- Remove the placeholder '(No facts recorded yet)' once real facts exist.\n")
                append("- Merge duplicate facts.\n")
                append("- Remove trivial or irrelevant information.\n")
                append("- Keep the markdown format with '# Core Memory' header and '## User Facts' section.\n")
                append("- Be concise. Each fact should be a single bullet point.\n")
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

            // Only overwrite if the compactor actually produced markdown content
            if (cleaned.contains("# Core Memory") || cleaned.contains("## User Facts") || cleaned.startsWith("- ")) {
                overwriteMemory(cleaned)
                Log.d("DaexCoreMemory", "Memory compacted successfully (${cleaned.length} chars)")
            } else {
                Log.w("DaexCoreMemory", "Compactor output didn't look like valid markdown, skipping overwrite. Output: ${cleaned.take(200)}")
            }
        } catch (e: Exception) {
            Log.e("DaexCoreMemory", "Memory compaction failed", e)
        }
    }
}
