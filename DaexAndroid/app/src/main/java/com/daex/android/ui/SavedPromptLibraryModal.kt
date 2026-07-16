package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.framework.Message
import com.daex.android.ui.theme.DaexTheme

@Composable
fun SavedPromptLibraryModal(
    visible: Boolean,
    onClose: () -> Unit,
    pinnedMessages: List<Message>,
    onUsePrompt: (Message) -> Unit,
    onUnpin: (Message) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onClose() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.6f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(DaexTheme.colors.background.copy(alpha = 0.95f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(enabled = false, onClick = {})
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .align(Alignment.CenterHorizontally)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText(
                            text = "SAVED PROMPTS",
                            style = DaexTheme.typography.h2.copy(
                                color = DaexTheme.colors.primary,
                                letterSpacing = 2.sp,
                                fontSize = 16.sp
                            )
                        )
                        BasicText(
                            text = "Pinned messages, ready to reuse",
                            style = DaexTheme.typography.mono.copy(
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (pinnedMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "No pinned messages yet.\nTap \"PIN\" on any message to save it here.",
                            style = DaexTheme.typography.mono.copy(
                                color = Color.White.copy(alpha = 0.4f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(pinnedMessages, key = { _, msg -> msg.id }) { _, msg ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.02f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onUsePrompt(msg) }
                                            .padding(14.dp)
                                    ) {
                                        BasicText(
                                            text = if (msg.role == "user") "YOU" else "ICARUS",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.primary.copy(alpha = 0.6f),
                                                fontSize = 9.sp,
                                                letterSpacing = 1.sp
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        BasicText(
                                            text = msg.content,
                                            style = DaexTheme.typography.body2.copy(
                                                color = Color.White.copy(alpha = 0.85f)
                                            ),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .padding(end = 14.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DaexTheme.colors.error.copy(alpha = 0.12f))
                                            .border(0.5.dp, DaexTheme.colors.error.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .clickable { onUnpin(msg) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "UNPIN",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.error,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
