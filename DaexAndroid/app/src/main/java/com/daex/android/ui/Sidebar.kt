package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.ui.theme.DaexTheme

@Composable
fun Sidebar(
    visible: Boolean,
    onClose: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                    .padding(24.dp)
                    .clickable(enabled = false, onClick = {}) // Block touches from reaching scrim
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                Spacer(modifier = Modifier.height(48.dp))

                // Menu Items
                SidebarItem(label = "New Session", icon = "+") {
                    onNewConversation()
                }
                SidebarItem(label = "Threat Feed", icon = "»") {}
                SidebarItem(label = "Node Map", icon = "▤") {}
                
                Spacer(modifier = Modifier.weight(1f))
                
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
            .padding(vertical = 16.dp),
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
