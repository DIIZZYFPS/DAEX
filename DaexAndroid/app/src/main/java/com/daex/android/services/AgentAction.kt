package com.daex.android.services

import kotlinx.coroutines.CompletableDeferred

sealed interface AgentAction

data class ToolProgress(
    val label: String,
    val inProgress: Boolean
) : AgentAction

data class PermissionRequest(
    val toolName: String,
    val description: String,
    val callback: CompletableDeferred<Boolean>
) : AgentAction
