package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daex.android.services.Model
import com.daex.android.services.ModelStatus
import com.daex.android.ui.components.DaexSwitch
import com.daex.android.ui.theme.DaexTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsModal(
    visible: Boolean,
    onClose: () -> Unit,
    modelStatus: ModelStatus,
    selectedModel: Model,
    useGPU: Boolean,
    isDark: Boolean,
    primaryColor: Color,
    onToggleGPU: (Boolean) -> Unit,
    onToggleDark: (Boolean) -> Unit,
    onSelectColor: (Color) -> Unit,
    onDownloadModel: () -> Unit,
    onChangeModel: () -> Unit,
    onDeleteModel: () -> Unit,
    onClearConversations: () -> Unit,
    onEditMemory: () -> Unit,
    onShareLogs: () -> Unit,
    deviceSpecs: com.daex.android.services.DeviceSpecs?,
    isSpeculativeDecodingEnabled: Boolean,
    onToggleSpeculativeDecoding: (Boolean) -> Unit,
    inferenceTemperature: Float,
    onTemperatureChange: (Float) -> Unit,
    inferenceTopK: Int,
    onTopKChange: (Int) -> Unit,
    inferenceTopP: Float,
    onTopPChange: (Float) -> Unit,
    customSystemPrompt: String,
    onCustomSystemPromptChange: (String) -> Unit,
    isToolCallingEnabled: Boolean,
    onToggleToolCalling: (Boolean) -> Unit,
    uploadedFiles: List<String> = emptyList(),
    onDeleteFile: (String) -> Unit = {}
) {
    if (!visible) return

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val configuration = LocalConfiguration.current
        val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }
        
        val coroutineScope = rememberCoroutineScope()
        val offsetY = remember { Animatable(screenHeightPx) }
        val scrimOpacity = remember { Animatable(0f) }
        var devOptionsExpanded by remember { mutableStateOf(false) }

        val springSpec = spring<Float>(dampingRatio = 0.75f, stiffness = 300f)
        val timingSpec = tween<Float>(durationMillis = 260, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))

        LaunchedEffect(visible) {
            if (visible) {
                launch { scrimOpacity.animateTo(1f, animationSpec = tween(280)) }
                launch { offsetY.animateTo(0f, animationSpec = springSpec) }
            }
        }

        val dismiss = {
            coroutineScope.launch {
                launch { scrimOpacity.animateTo(0f, animationSpec = tween(220)) }
                launch { 
                    offsetY.animateTo(screenHeightPx, animationSpec = timingSpec)
                    onClose()
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f * scrimOpacity.value))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                        onClick = { dismiss() }
                    )
            )

            // Sheet
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(DaexTheme.colors.surface)
                    .border(
                        width = 0.5.dp,
                        color = DaexTheme.colors.onSurface.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    )
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (offsetY.value > screenHeightPx * 0.3f) {
                                    dismiss()
                                } else {
                                    coroutineScope.launch {
                                        offsetY.animateTo(0f, animationSpec = springSpec)
                                    }
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val newOffset = (offsetY.value + dragAmount).coerceAtLeast(0f)
                                coroutineScope.launch {
                                    offsetY.snapTo(newOffset)
                                    scrimOpacity.snapTo((1f - (newOffset / (screenHeightPx * 0.5f))).coerceIn(0f, 1f))
                                }
                            }
                        )
                    }
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Column {
                    // Handle
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(DaexTheme.colors.onSurface.copy(alpha = 0.18f))
                            .align(Alignment.CenterHorizontally)
                    )
                    
                    Spacer(modifier = Modifier.height(20.dp))

                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "Settings",
                            style = DaexTheme.typography.h2.copy(
                                color = DaexTheme.colors.onBackground,
                                letterSpacing = 1.sp
                            )
                        )
                        BasicText(
                            text = "✕",
                            style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.onBackground.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { dismiss() }
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            // Model Info Section
                            SectionHeader("MODEL")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DaexTheme.colors.primary.copy(alpha = 0.06f))
                                    .border(1.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = selectedModel.name,
                                                style = DaexTheme.typography.body1.copy(
                                                    color = DaexTheme.colors.primary,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                                )
                                            )
                                            val sizeGb = String.format("%.1f", selectedModel.size / (1024.0 * 1024.0 * 1024.0))
                                            val ramGb = String.format("%.1f", selectedModel.requiredRAM / (1024.0 * 1024.0 * 1024.0))
                                            BasicText(
                                                text = "${sizeGb}GB • ${ramGb}GB RAM req.",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val statusColor = when (modelStatus) {
                                                ModelStatus.READY -> DaexTheme.colors.success
                                                ModelStatus.LOADING, ModelStatus.DOWNLOADING -> DaexTheme.colors.warning
                                                ModelStatus.ERROR -> DaexTheme.colors.error
                                                else -> DaexTheme.colors.onSurface.copy(alpha = 0.4f)
                                            }
                                            val statusText = when (modelStatus) {
                                                ModelStatus.READY -> "Loaded • Active"
                                                ModelStatus.LOADING -> "Loading..."
                                                ModelStatus.DOWNLOADING -> "Downloading..."
                                                ModelStatus.ERROR -> "Error"
                                                else -> "Not Downloaded"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(statusColor)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            BasicText(
                                                text = statusText,
                                                style = DaexTheme.typography.mono.copy(color = statusColor, fontSize = 11.sp)
                                            )
                                        }
                                    }

                                    if (modelStatus == ModelStatus.NOT_DOWNLOADED) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DaexTheme.colors.primary)
                                                .clickable { onDownloadModel() }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = "↓ DOWNLOAD MODEL",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onPrimary,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DaexTheme.colors.onSurface.copy(alpha = 0.1f))
                                            .clickable { onChangeModel() }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = "CHANGE MODEL",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.onSurface,
                                                fontSize = 13.sp
                                            )
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // Memory Section
                            SectionHeader("GLOBAL MEMORY")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { 
                                        dismiss()
                                        onEditMemory() 
                                    }
                                    .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        BasicText(
                                            text = "Edit Core Memory",
                                            style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.onSurface)
                                        )
                                        BasicText(
                                            text = "Review and edit the markdown file manually.",
                                            style = DaexTheme.typography.body2.copy(
                                                color = DaexTheme.colors.onSurface.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                    BasicText(
                                        text = "→",
                                        style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.primary)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Inference Section
                            SectionHeader("INFERENCE")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (useGPU) Color(0xFFA855F7).copy(alpha = 0.08f) else Color.Transparent)
                                    .border(
                                        0.5.dp, 
                                        if (useGPU) Color(0xFFA855F7).copy(alpha = 0.4f) else DaexTheme.colors.onSurface.copy(alpha = 0.1f), 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = "GPU Offload (Vulkan)",
                                        style = DaexTheme.typography.body1.copy(
                                            color = if (useGPU) Color(0xFFA855F7) else DaexTheme.colors.onBackground,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = if (useGPU) "Offloading to GPU • Faster but uses more power" else "CPU only • Stable and power efficient",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = useGPU,
                                    onCheckedChange = onToggleGPU,
                                    checkedColor = Color(0xFFA855F7)
                                )
                            }
                            Spacer(modifier = Modifier.height(32.dp))

                            // Theme Section
                            SectionHeader("APPEARANCE")
                            
                            // Dark Mode Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DaexTheme.colors.onSurface.copy(alpha = 0.03f))
                                    .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    BasicText(
                                        text = "Dark Mode",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "High contrast OLED optimized",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isDark,
                                    onCheckedChange = onToggleDark
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Primary Color Selector
                            BasicText(
                                text = "PRIMARY COLOR",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
                            )
                            
                            val themeColors = listOf(
                                Color(0xFF00FFFF), // Cyan
                                Color(0xFFA855F7), // Purple
                                Color(0xFF4ADE80), // Green
                                Color(0xFF3B82F6), // Blue
                                Color(0xFFF59E0B), // Amber
                                Color(0xFFFF003C)  // Cyber Red
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(themeColors) { color ->
                                    val isSelected = color == primaryColor
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isDark) Color.White else Color.Black,
                                                shape = CircleShape
                                            )
                                            .clickable { onSelectColor(color) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            BasicText(
                                                text = "✓",
                                                style = DaexTheme.typography.body1.copy(
                                                    color = if (color == Color.White) Color.Black else Color.White,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            // Developer Options Section
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { devOptionsExpanded = !devOptionsExpanded }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SectionHeader("DEVELOPER OPTIONS", modifier = Modifier.padding(bottom = 0.dp))
                                BasicText(
                                    text = if (devOptionsExpanded) "▼" else "▶",
                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary)
                                )
                            }

                            AnimatedVisibility(visible = devOptionsExpanded) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Device Info Diagnostics Block
                                    deviceSpecs?.let { specs ->
                                        BasicText(
                                            text = "DEVICE DIAGNOSTICS",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                                fontSize = 10.sp,
                                                letterSpacing = 1.sp
                                            ),
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DaexTheme.colors.onSurface.copy(alpha = 0.04f))
                                                .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                .padding(12.dp)
                                        ) {
                                            Column {
                                                val ramGb = String.format(java.util.Locale.US, "%.1f", specs.totalRAM / (1024.0 * 1024.0 * 1024.0))
                                                val freeStorageGb = String.format(java.util.Locale.US, "%.1f", specs.freeStorage / (1024.0 * 1024.0 * 1024.0))
                                                
                                                DiagnosticRow("Phone Model", "${specs.manufacturer} ${specs.model}")
                                                DiagnosticRow("SoC Board", specs.board)
                                                DiagnosticRow("SoC Hardware", specs.hardware)
                                                DiagnosticRow("Total Memory", "${ramGb} GB RAM")
                                                DiagnosticRow("Free Storage", "${freeStorageGb} GB")
                                                DiagnosticRow("Vulkan API", if (specs.hasVulkan) "SUPPORTED" else "UNSUPPORTED")
                                                DiagnosticRow("LiteRT NPU Support", if (specs.npuSupported) "READY (Dispatch Lib Present)" else "UNAVAILABLE (No Dispatch Lib)")
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    // Speculative Decoding Switch
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DaexTheme.colors.onSurface.copy(alpha = 0.03f))
                                            .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = "Speculative Decoding (MTP)",
                                                style = DaexTheme.typography.body1.copy(
                                                    color = DaexTheme.colors.onBackground,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                                )
                                            )
                                            BasicText(
                                                text = "Draft models accelerate generation speed",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                        DaexSwitch(
                                            checked = isSpeculativeDecodingEnabled,
                                            onCheckedChange = onToggleSpeculativeDecoding
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Native Tool Calling Switch
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(DaexTheme.colors.onSurface.copy(alpha = 0.03f))
                                            .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = "Native Tool Calling",
                                                style = DaexTheme.typography.body1.copy(
                                                    color = DaexTheme.colors.onBackground,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                                )
                                            )
                                            BasicText(
                                                text = "Expose battery, storage, time, specs (and optionally launch apps) directly to model",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                                    fontSize = 11.sp
                                                ),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                            )
                                        }
                                        DaexSwitch(
                                            checked = isToolCallingEnabled,
                                            onCheckedChange = onToggleToolCalling
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Custom System Prompt
                                    BasicText(
                                        text = "CUSTOM SYSTEM PROMPT",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 10.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                                    )
                                    com.daex.android.ui.components.DaexTextField(
                                        value = customSystemPrompt,
                                        onValueChange = onCustomSystemPromptChange,
                                        placeholder = "Override instructions (e.g. You are a cowboy...)",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Temperature Slider
                                    SliderParameter(
                                        label = "Temperature",
                                        value = inferenceTemperature,
                                        valueRange = 0f..2f,
                                        valueFormatter = { String.format(java.util.Locale.US, "%.2f", it) },
                                        onValueChange = onTemperatureChange,
                                        primaryColor = primaryColor
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Top-K Slider
                                    SliderParameter(
                                        label = "Top-K",
                                        value = inferenceTopK.toFloat(),
                                        valueRange = 1f..100f,
                                        valueFormatter = { it.toInt().toString() },
                                        onValueChange = { onTopKChange(it.toInt()) },
                                        primaryColor = primaryColor
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Top-P Slider
                                    SliderParameter(
                                        label = "Top-P",
                                        value = inferenceTopP,
                                        valueRange = 0f..1f,
                                        valueFormatter = { String.format(java.util.Locale.US, "%.2f", it) },
                                        onValueChange = onTopPChange,
                                        primaryColor = primaryColor
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))

                            // Knowledge Base Section (RAG Files)
                            SectionHeader("KNOWLEDGE BASE")
                            if (uploadedFiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DaexTheme.colors.onSurface.copy(alpha = 0.02f))
                                        .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicText(
                                        text = "No offline documents ingested",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.3f),
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uploadedFiles.forEach { fileName ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(DaexTheme.colors.onSurface.copy(alpha = 0.03f))
                                                .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            BasicText(
                                                text = fileName,
                                                style = DaexTheme.typography.body2.copy(
                                                    color = DaexTheme.colors.onSurface
                                                ),
                                                modifier = Modifier.weight(1f)
                                            )
                                            BasicText(
                                                text = "[REMOVE]",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.error,
                                                    fontSize = 11.sp,
                                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                                ),
                                                modifier = Modifier
                                                    .clickable { onDeleteFile(fileName) }
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Data Management Section
                            SectionHeader("DATA MANAGEMENT")
                            if (modelStatus == ModelStatus.READY || modelStatus == ModelStatus.NOT_DOWNLOADED) {
                                val sizeGb = String.format("%.1f", selectedModel.size / (1024.0 * 1024.0 * 1024.0))
                                ActionButton(
                                    text = "Delete model file (~${sizeGb}GB)",
                                    color = DaexTheme.colors.error,
                                    onClick = onDeleteModel
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            ActionButton(
                                text = "Clear all conversations",
                                color = DaexTheme.colors.error,
                                onClick = onClearConversations
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            ActionButton(
                                text = "Share system logs",
                                color = DaexTheme.colors.primary,
                                onClick = onShareLogs
                            )
                            
                            Spacer(modifier = Modifier.height(40.dp))
                            
                            // Footer
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DaexTheme.colors.onSurface.copy(alpha = 0.08f)))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    BasicText(
                                        text = "v0.1.0 • Powered by llama.cpp",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.25f),
                                            fontSize = 11.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text = text,
        style = DaexTheme.typography.mono.copy(
            color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
            fontSize = 11.sp,
            letterSpacing = 1.sp
        ),
        modifier = modifier.padding(bottom = 12.dp)
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(
            text = label,
            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onSurface.copy(alpha = 0.5f), fontSize = 11.sp)
        )
        BasicText(
            text = value,
            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onSurface, fontSize = 11.sp)
        )
    }
}

@Composable
private fun SliderParameter(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    primaryColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = label.uppercase(),
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            )
            BasicText(
                text = valueFormatter(value),
                style = DaexTheme.typography.mono.copy(
                    color = primaryColor,
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = primaryColor,
                activeTrackColor = primaryColor,
                inactiveTrackColor = primaryColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.05f))
            .border(0.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        BasicText(
            text = text,
            style = DaexTheme.typography.body2.copy(color = color)
        )
    }
}
