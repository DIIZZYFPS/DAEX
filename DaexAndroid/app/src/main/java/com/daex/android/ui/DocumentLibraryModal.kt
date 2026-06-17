package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.ui.theme.DaexTheme

@Composable
fun DocumentLibraryModal(
    visible: Boolean,
    onClose: () -> Unit,
    uploadedFiles: List<String>,
    attachedFiles: List<String>,
    onToggleAttachment: (String) -> Unit,
    onDeleteFromLibrary: (String) -> Unit,
    onUploadNew: () -> Unit
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
                            text = "DOCUMENT LIBRARY",
                            style = DaexTheme.typography.h2.copy(
                                color = DaexTheme.colors.primary,
                                letterSpacing = 2.sp,
                                fontSize = 16.sp
                            )
                        )
                        BasicText(
                            text = "Attach or detach files from the current session",
                            style = DaexTheme.typography.mono.copy(
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (uploadedFiles.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "No documents in library.",
                            style = DaexTheme.typography.mono.copy(color = Color.White.copy(alpha = 0.4f))
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(uploadedFiles) { _, fileName ->
                            val isAttached = attachedFiles.contains(fileName)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isAttached) DaexTheme.colors.primary.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.02f))
                                    .border(
                                        width = 0.5.dp,
                                        color = if (isAttached) DaexTheme.colors.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Row click toggles attachment status
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onToggleAttachment(fileName) }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(if (isAttached) DaexTheme.colors.primary else Color.Transparent)
                                                .border(1.dp, if (isAttached) DaexTheme.colors.primary else Color.White.copy(alpha = 0.3f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isAttached) {
                                                BasicText(
                                                    text = "✓",
                                                    style = DaexTheme.typography.caption.copy(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        BasicText(
                                            text = fileName,
                                            style = DaexTheme.typography.body2.copy(
                                                color = if (isAttached) Color.White else Color.White.copy(alpha = 0.7f),
                                                fontWeight = if (isAttached) FontWeight.Bold else FontWeight.Normal
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Action delete button
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 14.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(DaexTheme.colors.error.copy(alpha = 0.12f))
                                            .border(0.5.dp, DaexTheme.colors.error.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .clickable { onDeleteFromLibrary(fileName) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "DELETE",
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

                // Upload Trigger Button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaexTheme.colors.primary.copy(alpha = 0.1f))
                        .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onUploadNew() }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "UPLOAD NEW DOCUMENT +",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }
            }
        }
    }
}
