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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.Model
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.ui.theme.DaexTheme

data class ModelSupportStatus(
    val hasEnoughRAM: Boolean,
    val hasEnoughStorage: Boolean,
    val isDownloaded: Boolean,
    val checked: Boolean
)

@Composable
fun ModelSelectorModal(
    visible: Boolean,
    onClose: () -> Unit,
    onSelect: (Model) -> Unit,
    modelManager: ModelManager?
) {
    val models = ModelBank.models
    val supportMap = remember { mutableStateMapOf<String, ModelSupportStatus>() }

    LaunchedEffect(visible) {
        if (!visible || modelManager == null) return@LaunchedEffect
        models.forEach { model ->
            try {
                val spec = modelManager.checkSpecSupport(model)
                val isDownloaded = modelManager.isModelDownloaded(model)
                supportMap[model.id] = ModelSupportStatus(
                    hasEnoughRAM = spec.hasEnoughRAM,
                    hasEnoughStorage = spec.hasEnoughStorage,
                    isDownloaded = isDownloaded,
                    checked = true
                )
            } catch (e: Exception) {
                supportMap[model.id] = ModelSupportStatus(false, false, false, true)
            }
        }
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
            // Main Sheet
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(DaexTheme.colors.background.copy(alpha = 0.95f))
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(enabled = false, onClick = {}) // Block clickthrough
            ) {
                // Header Handle
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
                            text = "ENGINE REPOSITORY",
                            style = DaexTheme.typography.h2.copy(
                                color = DaexTheme.colors.primary,
                                letterSpacing = 2.sp,
                                fontSize = 18.sp
                            )
                        )
                        BasicText(
                            text = "${models.size} local models available",
                            style = DaexTheme.typography.mono.copy(
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp
                            )
                        )
                    }
                    
                    BasicText(
                        text = "✕",
                        style = DaexTheme.typography.h2.copy(color = Color.White.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .clickable { onClose() }
                            .padding(8.dp)
                    )
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    itemsIndexed(models) { _, model ->
                        val status = supportMap[model.id]
                        val isSupported = status?.hasEnoughRAM ?: true
                        val isDownloaded = status?.isDownloaded ?: false
                        
                        ModelCard(
                            model = model,
                            isSupported = isSupported,
                            isDownloaded = isDownloaded,
                            modelManager = modelManager,
                            onClick = { onSelect(model) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelCard(
    model: Model,
    isSupported: Boolean,
    isDownloaded: Boolean,
    modelManager: ModelManager?,
    onClick: () -> Unit
) {
    val opacity = if (isSupported) 1f else 0.4f
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(
                width = 0.5.dp,
                color = if (isSupported) Color.White.copy(alpha = 0.1f) else Color.Red.copy(alpha = 0.2f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = isSupported, onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status Glow
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDownloaded) DaexTheme.colors.success.copy(alpha = 0.1f)
                        else if (isSupported) DaexTheme.colors.primary.copy(alpha = 0.05f)
                        else Color.Red.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (isDownloaded) DaexTheme.colors.success
                            else if (isSupported) DaexTheme.colors.primary
                            else Color.Red
                        )
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        text = model.name,
                        style = DaexTheme.typography.body1.copy(
                            color = Color.White.copy(alpha = opacity),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DaexTheme.colors.success.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            BasicText(
                                text = "READY",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.success,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                BasicText(
                    text = model.description,
                    style = DaexTheme.typography.body2.copy(
                        color = Color.White.copy(alpha = 0.4f * opacity),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2
                )

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    InfoTag(label = modelManager?.formatBytes(model.size) ?: "0 B")
                    InfoTag(label = "${modelManager?.formatBytes(model.requiredRAM)} RAM")
                }

                if (!isSupported) {
                    BasicText(
                        text = "INSUFFICIENT HARDWARE",
                        style = DaexTheme.typography.mono.copy(
                            color = Color.Red.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoTag(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        BasicText(
            text = label,
            style = DaexTheme.typography.mono.copy(
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp
            )
        )
    }
}
