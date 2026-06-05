package com.daex.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.Message
import com.daex.android.services.PermissionRequest
import com.daex.android.ui.theme.DaexTheme
import com.mikepenz.markdown.m3.Markdown

@Composable
fun MessageLine(
    message: Message,
    isLastModel: Boolean = false,
    isGenerating: Boolean = false,
    tokenSpeed: Double = 0.0,
    activePermission: PermissionRequest? = null
) {
    if (message.role == "system" || message.content.startsWith("[SYSTEM_LOG]:")) {
        val logText = message.content.removePrefix("[SYSTEM_LOG]:").trim()
        val isInProgress = logText.endsWith("...")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isInProgress) {
                DaexLoader(size = 10.dp)
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                BasicText(
                    text = "▲",
                    style = DaexTheme.typography.mono.copy(
                        color = DaexTheme.colors.primary,
                        fontSize = 10.sp
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            BasicText(
                text = logText,
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            )
        }
        return
    }

    val isUser = message.role == "user"

    if (isUser) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 0.dp))
                    .background(DaexTheme.colors.primary.copy(alpha = 0.15f))
                    .border(
                        width = 1.dp,
                        color = DaexTheme.colors.primary.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 0.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                BasicText(
                    text = message.content,
                    style = DaexTheme.typography.body1.copy(
                        color = DaexTheme.colors.primary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Label
            BasicText(
                text = "ICARUS",
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.primary.copy(alpha = 0.6f),
                    fontSize = 11.sp
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            if (!message.thoughtContent.isNullOrEmpty()) {
                var isExpanded by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                        .border(1.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!message.toolStatus.isNullOrEmpty() && isGenerating) {
                                    DaexLoader(size = 12.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(DaexTheme.colors.primary.copy(alpha = if (isGenerating) 1f else 0.5f))
                                    )
                                }
                                val headerText = when {
                                    !message.toolStatus.isNullOrEmpty() -> message.toolStatus.uppercase()
                                    isGenerating && message.content.isEmpty() -> "THINKING..."
                                    else -> "THOUGHT PROCESS"
                                }
                                BasicText(
                                    text = headerText,
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.primary.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                            BasicText(
                                text = if (isExpanded) "▲" else "▼",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp
                                )
                            )
                        }
                        
                        AnimatedVisibility(visible = isExpanded) {
                            Column {
                                Spacer(modifier = Modifier.height(8.dp))
                                BasicText(
                                    text = message.thoughtContent,
                                    style = DaexTheme.typography.body1.copy(
                                        color = DaexTheme.colors.onSurface.copy(alpha = 0.7f),
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (message.content.isEmpty() && isGenerating && message.thoughtContent.isNullOrEmpty()) {
                BasicText(
                    text = "▊",
                    style = DaexTheme.typography.body1.copy(
                        color = DaexTheme.colors.onSurface.copy(alpha = 0.3f),
                        fontSize = 14.sp
                    )
                )
            } else if (message.content.isNotEmpty()) {
                Markdown(
                    content = message.content,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            activePermission?.let { request ->
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(1.dp, DaexTheme.colors.primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DaexTheme.colors.warning)
                                )
                                BasicText(
                                    text = "TOOL CALL REQUEST",
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.warning,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            BasicText(
                                text = request.toolName,
                                style = DaexTheme.typography.mono.copy(
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 8.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        BasicText(
                            text = request.description,
                            style = DaexTheme.typography.body2.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .clickable { request.callback.complete(false) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "DENY",
                                style = DaexTheme.typography.mono.copy(
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DaexTheme.colors.primary.copy(alpha = 0.2f))
                                .border(0.5.dp, DaexTheme.colors.primary, RoundedCornerShape(6.dp))
                                .clickable { request.callback.complete(true) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "APPROVE",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.primary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }

            if (isLastModel && tokenSpeed > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DaexTheme.colors.onSurface.copy(alpha = 0.06f)))
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BasicText(
                        text = "$tokenSpeed tok/s",
                        style = DaexTheme.typography.mono.copy(
                            color = if (isGenerating) Color(0xFFA855F7) else DaexTheme.colors.onSurface.copy(alpha = 0.35f),
                            fontSize = 10.sp
                        )
                    )
                    if (isGenerating) {
                        BasicText(
                            text = "generating...",
                            style = DaexTheme.typography.mono.copy(
                                color = Color(0xFFA855F7).copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
