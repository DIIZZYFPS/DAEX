package com.daex.android.services

import android.util.Log
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet
import kotlinx.coroutines.runBlocking

/**
 * Agentic "recall" tool: lets the model decide on its own to search past conversations, rather
 * than every turn silently pulling in "similar" history regardless of relevance (the
 * unrelated-tangent/context-bloat problem cross-conversation search was deliberately built to
 * avoid). Kept as its own small ToolSet class rather than folded into DeviceTools, matching the
 * per-tool split item 6 introduces.
 */
class RecallMemoryTool(
    private val daexMemory: DaexMemory,
    private val onStatusUpdate: ((String?) -> Unit)? = null
) : ToolSet {

    companion object {
        private const val TAG = "RecallMemoryTool"
    }

    @Tool(description = "Search the user's past conversations (beyond what's in the current chat) for relevant context. Use this when the user references something discussed before that isn't in the visible history.")
    fun searchPastConversations(
        @ToolParam(description = "What to search for, e.g. the topic or question to recall past context about") query: String
    ): String {
Log.d(TAG, "searchPastConversations triggered")
        onStatusUpdate?.invoke("Searching past conversations...")
        val results = runBlocking { daexMemory.searchMessages(query, maxResults = 5) }
        onStatusUpdate?.invoke(null)

        if (results.isEmpty()) {
            return "No relevant past conversations found."
        }
        return results.joinToString("\n---\n") { "[${it.message.role}]: ${it.message.content}" }
    }
}
