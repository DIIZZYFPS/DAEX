package com.daex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.Model
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.services.ModelStatus
import com.daex.android.ui.components.DaexLogo
import com.daex.android.ui.theme.DaexTheme

@Composable
fun GalleryScreen(
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    onBack: () -> Unit
) {
    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaexTheme.colors.background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "←",
                style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.primary),
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                BasicText(
                    text = "ENGINE MARKETPLACE",
                    style = DaexTheme.typography.h1.copy(
                        color = DaexTheme.colors.onBackground,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    )
                )
                BasicText(
                    text = "Browse and deploy localized intelligence",
                    style = DaexTheme.typography.mono.copy(
                        color = DaexTheme.colors.onBackground.copy(alpha = 0.4f),
                        fontSize = 10.sp
                    )
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(ModelBank.generativeModels) { model ->
                GalleryModelCard(
                    model = model,
                    viewModel = viewModel,
                    modelManager = modelManager,
                    isCurrent = currentModel?.id == model.id
                )
            }
        }
    }
}

@Composable
private fun GalleryModelCard(
    model: Model,
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    isCurrent: Boolean
) {
    var isDownloaded by remember { mutableStateOf(false) }
    var isHardwareCapable by remember { mutableStateOf(true) }
    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val isThisDownloading = isCurrent && modelStatus == ModelStatus.DOWNLOADING

    LaunchedEffect(Unit) {
        isDownloaded = modelManager.isModelDownloaded(model)
        isHardwareCapable = modelManager.checkSpecSupport(model).supported
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DaexTheme.colors.onSurface.copy(alpha = 0.03f))
            .border(
                width = 1.dp,
                color = if (isCurrent) DaexTheme.colors.primary.copy(alpha = 0.4f) else DaexTheme.colors.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = model.name,
                        style = DaexTheme.typography.body1.copy(
                            color = DaexTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isHardwareCapable) DaexTheme.colors.success else Color.Red)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        BasicText(
                            text = if (isHardwareCapable) "COMPATIBLE" else "INCOMPATIBLE",
                            style = DaexTheme.typography.mono.copy(
                                color = if (isHardwareCapable) DaexTheme.colors.success.copy(alpha = 0.6f) else Color.Red.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
                
                if (isDownloaded) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(DaexTheme.colors.success.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = "DEPLOYED",
                            style = DaexTheme.typography.mono.copy(
                                color = DaexTheme.colors.success,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            BasicText(
                text = model.description,
                style = DaexTheme.typography.body2.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MarketTag(label = modelManager.formatBytes(model.size))
                    MarketTag(label = "${modelManager.formatBytes(model.requiredRAM)} RAM")
                }

                if (isThisDownloading) {
                    BasicText(
                        text = "$downloadProgress%",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                } else if (!isDownloaded) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isHardwareCapable) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                            .clickable(enabled = isHardwareCapable) { viewModel.loadModel(model) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = "DOWNLOAD",
                            style = DaexTheme.typography.mono.copy(
                                color = if (isHardwareCapable) DaexTheme.colors.onPrimary else DaexTheme.colors.onSurface.copy(alpha = 0.2f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .clickable { viewModel.loadModel(model) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        BasicText(
                            text = "RE-INITIALIZE",
                            style = DaexTheme.typography.mono.copy(
                                color = DaexTheme.colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
            
            if (isThisDownloading) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(CircleShape)
                        .background(DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(downloadProgress / 100f)
                            .fillMaxHeight()
                            .background(DaexTheme.colors.primary)
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketTag(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DaexTheme.colors.onSurface.copy(alpha = 0.05f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        BasicText(
            text = label,
            style = DaexTheme.typography.mono.copy(
                color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        )
    }
}
