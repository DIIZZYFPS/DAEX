package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.Model
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.services.ModelStatus
import com.daex.android.services.HapticType
import com.daex.android.ui.components.DaexSwitch
import com.daex.android.ui.theme.DaexTheme

@Composable
fun SettingsScreen(
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val context = LocalContext.current

    val modelStatus by viewModel.modelStatus.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val selectedModel = currentModel ?: ModelBank.generativeModels.first()

    val useGPU by viewModel.useGPU.collectAsState()
    val isDark by viewModel.isDarkMode.collectAsState()
    val primaryColor by viewModel.primaryColor.collectAsState()
    val isSpeculativeDecodingEnabled by viewModel.isSpeculativeDecodingEnabled.collectAsState()
    val inferenceTemperature by viewModel.inferenceTemperature.collectAsState()
    val inferenceTopK by viewModel.inferenceTopK.collectAsState()
    val inferenceTopP by viewModel.inferenceTopP.collectAsState()
    val customSystemPrompt by viewModel.customSystemPrompt.collectAsState()
    val isToolCallingEnabled by viewModel.isToolCallingEnabled.collectAsState()
    val uploadedFiles by viewModel.uploadedFiles.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val maxTokens by viewModel.maxTokens.collectAsState()
    val isHapticEnabled by viewModel.isHapticEnabled.collectAsState()
    val isAuraEnabled by viewModel.isAuraEnabled.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("TUNING", "THEME", "SYSTEM")

    var memoryEditorVisible by remember { mutableStateOf(false) }
    var changelogVisible by remember { mutableStateOf(false) }

    val downloadedModelIds by viewModel.downloadedModelIds.collectAsState()

    val packageInfo = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    val versionName = packageInfo?.versionName ?: "0.3.0"

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DaexTheme.colors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = "←",
                style = DaexTheme.typography.h1.copy(color = DaexTheme.colors.primary, fontSize = 24.sp),
                modifier = Modifier
                    .clickable { onBack() }
                    .padding(8.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            BasicText(
                text = "SETTINGS",
                style = DaexTheme.typography.h1.copy(
                    color = DaexTheme.colors.onBackground,
                    letterSpacing = 2.sp,
                    fontSize = 18.sp
                )
            )
        }

        // Horizontal Tabs selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 0.5.dp, color = DaexTheme.colors.onSurface.copy(alpha = 0.08f))
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            tabs.forEachIndexed { index, label ->
                val isActive = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            viewModel.triggerHapticFeedback(context)
                            selectedTab = index
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(
                            text = label,
                            style = DaexTheme.typography.mono.copy(
                                color = if (isActive) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .background(if (isActive) DaexTheme.colors.primary else Color.Transparent)
                        )
                    }
                }
            }
        }

        // Content panel based on selected tab
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> { // TUNING / INFERENCE
                    item {
                        SectionHeader("INFERENCE HYPERPARAMETERS")
                        SettingsCard {
                            SliderParameter(
                                label = "Temperature",
                                value = inferenceTemperature,
                                valueRange = 0f..2f,
                                valueFormatter = { String.format(java.util.Locale.US, "%.2f", it) },
                                onValueChange = { viewModel.setInferenceTemperature(it) },
                                primaryColor = DaexTheme.colors.primary,
                                subtitle = "Strict (0.0) ─── Creative (2.0)",
                                viewModel = viewModel
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderParameter(
                                label = "Top-K",
                                value = inferenceTopK.toFloat(),
                                valueRange = 1f..100f,
                                valueFormatter = { it.toInt().toString() },
                                onValueChange = { viewModel.setInferenceTopK(it.toInt()) },
                                primaryColor = DaexTheme.colors.primary,
                                viewModel = viewModel
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderParameter(
                                label = "Top-P",
                                value = inferenceTopP,
                                valueRange = 0f..1f,
                                valueFormatter = { String.format(java.util.Locale.US, "%.2f", it) },
                                onValueChange = { viewModel.setInferenceTopP(it) },
                                primaryColor = DaexTheme.colors.primary,
                                viewModel = viewModel
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderParameter(
                                label = "Verbosity (Max Tokens)",
                                value = maxTokens.toFloat(),
                                valueRange = 128f..4096f,
                                valueFormatter = { it.toInt().toString() + " tokens" },
                                onValueChange = { viewModel.setMaxTokens(it.toInt()) },
                                primaryColor = DaexTheme.colors.primary,
                                subtitle = "Concise (128) ─── Detailed (4096)",
                                viewModel = viewModel
                            )
                        }
                    }

                    item {
                        SectionHeader("SYSTEM PROMPT OVERRIDE")
                        SettingsCard {
                            BasicText(
                                text = "CUSTOM SYSTEM PROMPT",
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
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DaexTheme.colors.background)
                                    .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                BasicTextField(
                                    value = customSystemPrompt,
                                    onValueChange = { viewModel.setCustomSystemPrompt(it) },
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = TextStyle(
                                        color = DaexTheme.colors.onBackground,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(DaexTheme.colors.primary)
                                )
                                if (customSystemPrompt.isEmpty()) {
                                    BasicText(
                                        text = "Override default instruction set...",
                                        style = DaexTheme.typography.body2.copy(color = DaexTheme.colors.onSurface.copy(alpha = 0.3f))
                                    )
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader("CORE MEMORY BANK")
                        SettingsCard {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        viewModel.loadCoreMemory()
                                        memoryEditorVisible = true 
                                    },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    BasicText(
                                        text = "Global Core Memory",
                                        style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.onSurface)
                                    )
                                    BasicText(
                                        text = "Review and edit the persistent system instruction file.",
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
                    }

                    item {
                        SectionHeader("AGENT OPTIONS")
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = "Speculative Decoding (MTP)",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "Draft models accelerate token output speeds",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isSpeculativeDecodingEnabled,
                                    onCheckedChange = { viewModel.setSpeculativeDecodingEnabled(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = "Native Tool Calling",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "Expose hardware sensors and app services directly to Icarus",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isToolCallingEnabled,
                                    onCheckedChange = { viewModel.setToolCallingEnabled(it) }
                                )
                            }
                        }
                    }
                }

                1 -> { // APPEARANCE & THEME
                    item {
                        SectionHeader("THEME SELECTION")
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    BasicText(
                                        text = "Dark Mode",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "OLED contrast-optimized UI profile",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isDark,
                                    onCheckedChange = { viewModel.setDarkMode(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            BasicText(
                                text = "PRIMARY COLOR SCHEME",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 12.dp)
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(themeColors) { color ->
                                    val isSelected = color == primaryColor
                                    val displayColor = DaexTheme.getAdjustedColor(color, isDark)
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(displayColor)
                                            .border(
                                                width = if (isSelected) 3.dp else 0.dp,
                                                color = if (isDark) Color.White else Color.Black,
                                                shape = CircleShape
                                            )
                                            .clickable { viewModel.setThemeColor(color) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            BasicText(
                                                text = "✓",
                                                style = DaexTheme.typography.body1.copy(
                                                    color = if (displayColor == Color.White) Color.Black else Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            BasicText(
                                text = "AMBIENT EFFECTS",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = "Ambient Reactive Aura",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "Draw dynamic, state-reactive gradients in the background",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isAuraEnabled,
                                    onCheckedChange = { viewModel.setAuraEnabled(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            BasicText(
                                text = "TACTILE HAPTIC OPTIONS",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = "Haptic Tap Pulses",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "Tactile feedback clicks on key buttons",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isHapticEnabled,
                                    onCheckedChange = { viewModel.setHapticEnabled(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            ActionButton(
                                text = "Test tactile haptic pulse",
                                color = DaexTheme.colors.primary,
                                onClick = { viewModel.triggerHapticFeedback(context, force = true) }
                            )
                        }
                    }
                }

                2 -> { // SYSTEM DIAGNOSTICS & ACTIONS
                    item {
                        SectionHeader("DEVICE METRICS DIAGNOSTICS")
                        viewModel.deviceSpecs?.let { specs ->
                            SettingsCard {
                                val ramGb = String.format(java.util.Locale.US, "%.1f", specs.totalRAM / (1024.0 * 1024.0 * 1024.0))
                                val freeStorageGb = String.format(java.util.Locale.US, "%.1f", specs.freeStorage / (1024.0 * 1024.0 * 1024.0))

                                DiagnosticRow("Phone Model", "${specs.manufacturer} ${specs.model}")
                                DiagnosticRow("SoC Board", specs.board)
                                DiagnosticRow("SoC Hardware", specs.hardware)
                                DiagnosticRow("Total Memory", "${ramGb} GB RAM")
                                DiagnosticRow("Free Storage", "${freeStorageGb} GB")
                                DiagnosticRow("Vulkan API Support", if (specs.hasVulkan) "SUPPORTED" else "UNSUPPORTED")
                                DiagnosticRow("LiteRT NPU Library", if (specs.npuSupported) "READY" else "UNAVAILABLE")
                            }
                        }
                    }

                    item {
                        SectionHeader("OFFLINE KNOWLEDGE BASE (RAG)")
                        SettingsCard {
                            if (uploadedFiles.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
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
                                                text = "REMOVE",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.error,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                modifier = Modifier
                                                    .clickable { viewModel.deleteUploadedFile(fileName) }
                                                    .padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader("DATA MANAGEMENT OPERATIONS")
                        SettingsCard {
                            ActionButton(
                                text = "Clear conversation history",
                                color = DaexTheme.colors.error,
                                onClick = { 
                                    viewModel.deleteAllConversations()
                                    onBack() 
                                }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionButton(
                                text = "Share application debugging logs",
                                color = DaexTheme.colors.primary,
                                onClick = { LogShareHelper.shareAppLogs(context) }
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionButton(
                                text = "Display changelog history",
                                color = DaexTheme.colors.primary,
                                onClick = { changelogVisible = true }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DaexTheme.colors.onSurface.copy(alpha = 0.08f)))
                                Spacer(modifier = Modifier.height(12.dp))
                                BasicText(
                                    text = "v$versionName • Powered by LiteRT",
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.onSurface.copy(alpha = 0.25f),
                                        fontSize = 11.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    ModelSelectorModal(
        visible = false,
        onClose = {},
        onSelect = {},
        onOpenMarketplace = {},
        downloadedModelIds = downloadedModelIds,
        onDelete = { viewModel.deleteModel(it) }
    )

    MemoryEditorModal(
        visible = memoryEditorVisible,
        onClose = { memoryEditorVisible = false },
        initialContent = viewModel.coreMemoryText.collectAsState().value,
        onSave = { 
            viewModel.saveCoreMemory(it)
            memoryEditorVisible = false
        }
    )

    ChangelogModal(
        visible = changelogVisible,
        onClose = { changelogVisible = false }
    )
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
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DaexTheme.colors.surface)
            .border(
                width = 0.5.dp,
                color = DaexTheme.colors.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SliderParameter(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueFormatter: (Float) -> String,
    onValueChange: (Float) -> Unit,
    primaryColor: Color,
    subtitle: String? = null,
    viewModel: DaexInferenceViewModel? = null
) {
    val context = LocalContext.current
    var lastFormattedValue by remember(value) { mutableStateOf(valueFormatter(value)) }

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
                    fontWeight = FontWeight.Bold
                )
            )
        }
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.35f),
                    fontSize = 10.sp
                )
            )
        }
        Slider(
            value = value,
            onValueChange = { newValue ->
                val formatted = valueFormatter(newValue)
                if (formatted != lastFormattedValue) {
                    lastFormattedValue = formatted
                    viewModel?.triggerHapticFeedback(context, type = HapticType.TICK)
                }
                onValueChange(newValue)
            },
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
