package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.ui.theme.DaexTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Sidebar(
    visible: Boolean,
    onClose: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGallery: () -> Unit,
    viewModel: DaexInferenceViewModel
) {
    val conversations by viewModel.conversations.collectAsState()
    val currentConvId by viewModel.currentConversationId.collectAsState()

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(300)
        ),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Scrim layer to dismiss
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onClose() }
            )

            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(280.dp)
                    .background(DaexTheme.colors.surface)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(vertical = 24.dp, horizontal = 16.dp)
                    .clickable(enabled = false, onClick = {}) // Block touches from reaching scrim
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = "D A E X",
                        style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.primary, letterSpacing = 4.sp)
                    )
                    BasicText(
                        text = "X",
                        style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.onSurface),
                        modifier = Modifier
                            .clickable { onClose() }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Menu Items
                SidebarItem(label = "New Session", icon = "+") {
                    onNewConversation()
                    onClose()
                }

                Spacer(modifier = Modifier.height(24.dp))

                BasicText(
                    text = "RECENT EXECUTIONS",
                    style = DaexTheme.typography.mono.copy(
                        color = DaexTheme.colors.onSurface.copy(alpha = 0.3f),
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    ),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                )

                // Conversation List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(conversations) { conv ->
                        val isSelected = conv.id == currentConvId
                        var showMenu by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) DaexTheme.colors.onSurface.copy(alpha = 0.05f) else Color.Transparent)
                                .combinedClickable(
                                    onClick = { 
                                        viewModel.selectConversation(conv.id)
                                        onClose()
                                    },
                                    onLongClick = {
                                        showMenu = true
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                        ) {
                            Column {
                                BasicText(
                                    text = conv.title,
                                    style = DaexTheme.typography.body1.copy(
                                        color = if (isSelected) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.7f),
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BasicText(
                                    text = conv.modelId.uppercase(),
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.onSurface.copy(alpha = 0.2f),
                                        fontSize = 8.sp
                                    )
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(DaexTheme.colors.surface)
                            ) {
                                DropdownMenuItem(onClick = { showMenu = false }) {
                                    BasicText("Pin (Coming Soon)", style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.onSurface.copy(alpha=0.5f)))
                                }
                                DropdownMenuItem(onClick = { 
                                    viewModel.deleteConversation(conv.id)
                                    showMenu = false 
                                }) {
                                    BasicText("Delete Session", style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.error))
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                SidebarItem(label = "Marketplace", icon = "▤") {
                    onOpenGallery()
                    onClose()
                }
                
                SidebarItem(label = "Settings", icon = "⚙") {
                    onOpenSettings()
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(label: String, icon: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = icon,
            style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.primary),
            modifier = Modifier.width(32.dp)
        )
        BasicText(
            text = label,
            style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.onSurface)
        )
    }
}
