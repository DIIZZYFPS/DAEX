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
import com.daex.android.services.Model
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.ui.theme.DaexTheme

@Composable
fun ModelSelectorModal(
    visible: Boolean,
    onClose: () -> Unit,
    onSelect: (Model) -> Unit,
    onOpenMarketplace: () -> Unit,
    modelManager: ModelManager?
) {
    val allModels = ModelBank.generativeModels
    var downloadedModels by remember { mutableStateOf<List<Model>>(emptyList()) }

    LaunchedEffect(visible) {
        if (!visible || modelManager == null) return@LaunchedEffect
        downloadedModels = allModels.filter { modelManager.isModelDownloaded(it) }
    }

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
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        BasicText(
                            text = "HOT SWAP",
                            style = DaexTheme.typography.h2.copy(
                                color = DaexTheme.colors.primary,
                                letterSpacing = 2.sp,
                                fontSize = 18.sp
                            )
                        )
                        BasicText(
                            text = "Switch between downloaded engines",
                            style = DaexTheme.typography.mono.copy(
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                if (downloadedModels.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "No models downloaded yet.",
                            style = DaexTheme.typography.mono.copy(color = Color.White.copy(alpha = 0.4f))
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(downloadedModels) { _, model ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                    .clickable { onSelect(model) }
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(DaexTheme.colors.success)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    BasicText(
                                        text = model.name,
                                        style = DaexTheme.typography.body1.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                // Marketplace Link
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaexTheme.colors.primary.copy(alpha = 0.1f))
                        .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { onOpenMarketplace() }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        text = "BROWSE MARKETPLACE →",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
    }
}
