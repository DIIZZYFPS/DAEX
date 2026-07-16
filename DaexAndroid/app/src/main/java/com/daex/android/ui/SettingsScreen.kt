package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.ui.viewmodels.ChatViewModel
import com.daex.android.ui.viewmodels.SettingsViewModel
import com.daex.android.ui.viewmodels.TtsViewModel
import com.daex.android.domain.Model
import com.daex.android.domain.ModelBank
import com.daex.android.data.ModelManager
import com.daex.android.ui.viewmodels.ModelStatus
import com.daex.android.ui.viewmodels.HapticType
import com.daex.android.framework.ToolRegistry
import com.daex.android.ui.components.ConfirmDialog
import com.daex.android.ui.components.DaexSwitch
import com.daex.android.ui.theme.DaexTheme

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    ttsViewModel: TtsViewModel,
    chatViewModel: ChatViewModel,
    modelManager: ModelManager,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onReplayOnboarding: () -> Unit
) {
    val context = LocalContext.current

    val modelStatus by settingsViewModel.modelStatus.collectAsState()
    val currentModel by settingsViewModel.currentModel.collectAsState()
    val selectedModel = currentModel ?: ModelBank.generativeModels.first()

    val useGPU by settingsViewModel.useGPU.collectAsState()
    val isDark by settingsViewModel.isDarkMode.collectAsState()
    val primaryColor by settingsViewModel.primaryColor.collectAsState()
    val isSpeculativeDecodingEnabled by settingsViewModel.isSpeculativeDecodingEnabled.collectAsState()
    val inferenceTemperature by settingsViewModel.inferenceTemperature.collectAsState()
    val inferenceTopK by settingsViewModel.inferenceTopK.collectAsState()
    val inferenceTopP by settingsViewModel.inferenceTopP.collectAsState()
    val customSystemPrompt by settingsViewModel.customSystemPrompt.collectAsState()
    val isToolCallingEnabled by settingsViewModel.isToolCallingEnabled.collectAsState()
    val disabledToolIds by settingsViewModel.disabledToolIds.collectAsState()
    val uploadedFiles by chatViewModel.uploadedFiles.collectAsState()
    val downloadProgress by settingsViewModel.downloadProgress.collectAsState()
    val maxTokens by settingsViewModel.maxTokens.collectAsState()
    val isHapticEnabled by settingsViewModel.isHapticEnabled.collectAsState()
    val isAuraEnabled by settingsViewModel.isAuraEnabled.collectAsState()
    val isTtsEnabled by ttsViewModel.isTtsEnabled.collectAsState()
    val ttsVoiceId by ttsViewModel.ttsVoiceId.collectAsState()
    val isTtsDownloaded by ttsViewModel.isTtsDownloaded.collectAsState()
    val isTtsDownloading by ttsViewModel.isTtsDownloading.collectAsState()
    val ttsDownloadProgress by ttsViewModel.ttsDownloadProgress.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("TUNING", "THEME", "SYSTEM")

    var memoryEditorVisible by remember { mutableStateOf(false) }
    var changelogVisible by remember { mutableStateOf(false) }
    var clearHistoryConfirmVisible by remember { mutableStateOf(false) }

    val downloadedModelIds by settingsViewModel.downloadedModelIds.collectAsState()

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
                            settingsViewModel.triggerHapticFeedback(context)
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
                                onValueChange = { settingsViewModel.setInferenceTemperature(it) },
                                primaryColor = DaexTheme.colors.primary,
                                subtitle = "Strict (0.0) ─── Creative (2.0)",
                                settingsViewModel = settingsViewModel
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderParameter(
                                label = "Top-K",
                                value = inferenceTopK.toFloat(),
                                valueRange = 1f..100f,
                                valueFormatter = { it.toInt().toString() },
                                onValueChange = { settingsViewModel.setInferenceTopK(it.toInt()) },
                                primaryColor = DaexTheme.colors.primary,
                                settingsViewModel = settingsViewModel
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderParameter(
                                label = "Top-P",
                                value = inferenceTopP,
                                valueRange = 0f..1f,
                                valueFormatter = { String.format(java.util.Locale.US, "%.2f", it) },
                                onValueChange = { settingsViewModel.setInferenceTopP(it) },
                                primaryColor = DaexTheme.colors.primary,
                                settingsViewModel = settingsViewModel
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            SliderParameter(
                                label = "Verbosity (Max Tokens)",
                                value = maxTokens.toFloat(),
                                valueRange = 128f..4096f,
                                valueFormatter = { it.toInt().toString() + " tokens" },
                                onValueChange = { settingsViewModel.setMaxTokens(it.toInt()) },
                                primaryColor = DaexTheme.colors.primary,
                                subtitle = "Concise (128) ─── Detailed (4096)",
                                settingsViewModel = settingsViewModel
                            )
                        }
                    }

                    item {
                        SectionHeader("YOUR INSTRUCTIONS")
                        SettingsCard {
                            BasicText(
                                text = "ADD TO ICARUS'S PERSONALITY",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp
                                ),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            BasicText(
                                text = "Icarus has a fixed core personality — instructions here are appended on top, not a replacement.",
                                style = DaexTheme.typography.body2.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
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
                                    onValueChange = { settingsViewModel.setCustomSystemPrompt(it) },
                                    modifier = Modifier.fillMaxSize(),
                                    textStyle = TextStyle(
                                        color = DaexTheme.colors.onBackground,
                                        fontSize = 13.sp
                                    ),
                                    cursorBrush = SolidColor(DaexTheme.colors.primary)
                                )
                                if (customSystemPrompt.isEmpty()) {
                                    BasicText(
                                        text = "e.g. \"Always answer in bullet points\"",
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
                                        chatViewModel.loadCoreMemory()
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
                                    onCheckedChange = { settingsViewModel.setSpeculativeDecodingEnabled(it) }
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
                                    onCheckedChange = { settingsViewModel.setToolCallingEnabled(it) }
                                )
                            }

                            if (isToolCallingEnabled) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(DaexTheme.colors.onSurface.copy(alpha = 0.08f)))
                                Spacer(modifier = Modifier.height(12.dp))

                                ToolRegistry.ALL.forEach { toolEntry ->
                                    val enabled = toolEntry.id !in disabledToolIds
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            BasicText(
                                                text = toolEntry.label,
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onSurface.copy(alpha = if (enabled) 0.9f else 0.4f),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            )
                                            BasicText(
                                                text = toolEntry.description,
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.35f),
                                                    fontSize = 10.sp
                                                ),
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        DaexSwitch(
                                            checked = enabled,
                                            onCheckedChange = { settingsViewModel.setToolEnabled(toolEntry.id, it) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        SectionHeader("OFFLINE VOICE OUTPUT (TTS)")
                        SettingsCard {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    BasicText(
                                        text = "Read Aloud Responses",
                                        style = DaexTheme.typography.body1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    )
                                    BasicText(
                                        text = "Synthesizes responses using local Kokoro TTS",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                            fontSize = 11.sp
                                        ),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                DaexSwitch(
                                    checked = isTtsEnabled,
                                    onCheckedChange = { ttsViewModel.setTtsEnabled(it) }
                                )
                            }

                            if (isTtsEnabled) {
                                if (isTtsDownloaded) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BasicText(
                                            text = "SELECT SPEAKER PROFILE",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                                fontSize = 10.sp,
                                                letterSpacing = 1.sp
                                            )
                                        )
                                        BasicText(
                                            text = "DELETE ENGINE",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.error,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            modifier = Modifier
                                                .clickable { ttsViewModel.deleteTtsModel() }
                                                .padding(vertical = 4.dp, horizontal = 8.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    val voiceProfiles = remember {
                                        listOf(
                                            VoiceProfile(0, "af_alloy", "Alloy", "Female ♀", "US"),
                                            VoiceProfile(1, "af_bella", "Bella", "Female ♀", "US"),
                                            VoiceProfile(2, "af_nicole", "Nicole", "Female ♀", "US"),
                                            VoiceProfile(3, "af_sarah", "Sarah", "Female ♀", "US"),
                                            VoiceProfile(4, "af_sky", "Sky", "Female ♀", "US"),
                                            VoiceProfile(5, "am_adam", "Adam", "Male ♂", "US"),
                                            VoiceProfile(6, "am_michael", "Michael", "Male ♂", "US"),
                                            VoiceProfile(7, "bf_emma", "Emma", "Female ♀", "UK"),
                                            VoiceProfile(8, "bf_isabella", "Isabella", "Female ♀", "UK"),
                                            VoiceProfile(9, "bm_george", "George", "Male ♂", "UK"),
                                            VoiceProfile(10, "bm_lewis", "Lewis", "Male ♂", "UK")
                                        )
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(voiceProfiles.size) { index ->
                                            val profile = voiceProfiles[index]
                                            val isSelected = ttsVoiceId == profile.id
                                            val cardBg = if (isSelected) DaexTheme.colors.primary.copy(alpha = 0.15f) else DaexTheme.colors.background
                                            val borderCl = if (isSelected) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.15f)
                                            
                                            Column(
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(cardBg)
                                                    .border(1.dp, borderCl, RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        ttsViewModel.setTtsVoiceId(profile.id)
                                                        settingsViewModel.triggerHapticFeedback(context, force = true, type = com.daex.android.ui.viewmodels.HapticType.TICK)
                                                    }
                                                    .padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                BasicText(
                                                    text = profile.displayName,
                                                    style = DaexTheme.typography.body2.copy(
                                                        color = DaexTheme.colors.onBackground,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                BasicText(
                                                    text = "${profile.region} • ${if (profile.gender.contains("Female")) "♀" else "♂"}",
                                                    style = DaexTheme.typography.mono.copy(
                                                        color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                                                        fontSize = 9.sp
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (isTtsDownloading) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                BasicText(
                                                    text = "Downloading model assets...",
                                                    style = DaexTheme.typography.body2.copy(color = DaexTheme.colors.onBackground)
                                                )
                                                BasicText(
                                                    text = "$ttsDownloadProgress%",
                                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontWeight = FontWeight.Bold)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(6.dp)
                                                    .clip(RoundedCornerShape(3.dp))
                                                    .background(Color.White.copy(alpha = 0.05f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(ttsDownloadProgress / 100f)
                                                        .fillMaxHeight()
                                                        .background(DaexTheme.colors.primary)
                                                )
                                            }
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(DaexTheme.colors.primary.copy(alpha = 0.1f))
                                                .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                                .clickable { ttsViewModel.downloadTtsModel() }
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            BasicText(
                                                text = "DOWNLOAD TTS VOICE ENGINE (102 MB)",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.primary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    letterSpacing = 1.sp
                                                )
                                            )
                                        }
                                    }
                                }
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
                                    onCheckedChange = { settingsViewModel.setDarkMode(it) }
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
                                Color(0xFFEDAEC0), // Rose
                                Color(0xFFF59E0B), // Amber
                                Color(0xFFFF003C)  // Cyber Red
                            )
                            val isCustomActive = themeColors.none { it == primaryColor }
                            var customPickerExpanded by remember { mutableStateOf(false) }
                            val initialHsv = remember {
                                FloatArray(3).also { android.graphics.Color.colorToHSV(primaryColor.toArgb(), it) }
                            }
                            var hue by remember { mutableStateOf(initialHsv[0]) }
                            var saturation by remember { mutableStateOf(initialHsv[1]) }
                            var brightness by remember { mutableStateOf(initialHsv[2]) }
                            val pickedColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

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
                                            .clickable {
                                                settingsViewModel.setThemeColor(color)
                                                val hsv = FloatArray(3)
                                                android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                                hue = hsv[0]
                                                saturation = hsv[1]
                                                brightness = hsv[2]
                                            },
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
                                item {
                                    val customDisplayColor = if (isCustomActive) DaexTheme.getAdjustedColor(primaryColor, isDark) else Color.Transparent
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(customDisplayColor)
                                            .border(
                                                width = if (isCustomActive) 3.dp else 1.dp,
                                                color = if (isCustomActive) {
                                                    if (isDark) Color.White else Color.Black
                                                } else {
                                                    DaexTheme.colors.onSurface.copy(alpha = 0.3f)
                                                },
                                                shape = CircleShape
                                            )
                                            .clickable { customPickerExpanded = !customPickerExpanded },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        BasicText(
                                            text = if (isCustomActive) "✓" else "+",
                                            style = DaexTheme.typography.body1.copy(
                                                color = if (isCustomActive) {
                                                    if (customDisplayColor == Color.White) Color.Black else Color.White
                                                } else {
                                                    DaexTheme.colors.onSurface.copy(alpha = 0.5f)
                                                },
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = customPickerExpanded,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                Column(modifier = Modifier.padding(top = 16.dp)) {
                                    BasicText(
                                        text = "#%06X".format(0xFFFFFF and pickedColor.toArgb()),
                                        style = DaexTheme.typography.mono.copy(
                                            color = pickedColor,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        ),
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )

                                    fun applyPickedColor() {
                                        settingsViewModel.setThemeColor(
                                            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
                                        )
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            BasicText(
                                                text = "HUE",
                                                style = DaexTheme.typography.mono.copy(
                                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(24.dp)
                                                .padding(top = 6.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        listOf(
                                                            Color.Red, Color.Yellow, Color.Green,
                                                            Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                                                        )
                                                    )
                                                )
                                        ) {
                                            Slider(
                                                value = hue,
                                                onValueChange = {
                                                    hue = it
                                                    applyPickedColor()
                                                },
                                                valueRange = 0f..360f,
                                                colors = SliderDefaults.colors(
                                                    thumbColor = Color.White,
                                                    activeTrackColor = Color.Transparent,
                                                    inactiveTrackColor = Color.Transparent
                                                ),
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    SliderParameter(
                                        label = "Saturation",
                                        value = saturation,
                                        valueRange = 0f..1f,
                                        valueFormatter = { "${(it * 100).toInt()}%" },
                                        onValueChange = {
                                            saturation = it
                                            applyPickedColor()
                                        },
                                        primaryColor = pickedColor,
                                        settingsViewModel = settingsViewModel
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))
                                    SliderParameter(
                                        label = "Brightness",
                                        value = brightness,
                                        valueRange = 0f..1f,
                                        valueFormatter = { "${(it * 100).toInt()}%" },
                                        onValueChange = {
                                            brightness = it
                                            applyPickedColor()
                                        },
                                        primaryColor = pickedColor,
                                        settingsViewModel = settingsViewModel
                                    )
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
                                    onCheckedChange = { settingsViewModel.setAuraEnabled(it) }
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
                                    onCheckedChange = { settingsViewModel.setHapticEnabled(it) }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            ActionButton(
                                text = "Test tactile haptic pulse",
                                color = DaexTheme.colors.primary,
                                onClick = { settingsViewModel.triggerHapticFeedback(context, force = true) }
                            )
                        }
                    }
                }

                2 -> { // SYSTEM DIAGNOSTICS & ACTIONS
                    item {
                        SectionHeader("DEVICE METRICS DIAGNOSTICS")
                        settingsViewModel.deviceSpecs?.let { specs ->
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
                                                    .clickable { chatViewModel.deleteUploadedFile(fileName) }
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
                                onClick = { clearHistoryConfirmVisible = true }
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
                            Spacer(modifier = Modifier.height(10.dp))
                            ActionButton(
                                text = "Replay onboarding tour",
                                color = DaexTheme.colors.primary,
                                onClick = onReplayOnboarding
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
        onDelete = { settingsViewModel.deleteModel(it) }
    )

    MemoryEditorModal(
        visible = memoryEditorVisible,
        onClose = { memoryEditorVisible = false },
        initialContent = chatViewModel.coreMemoryText.collectAsState().value,
        onSave = { 
            chatViewModel.saveCoreMemory(it)
            memoryEditorVisible = false
        }
    )

    ChangelogModal(
        visible = changelogVisible,
        onClose = { changelogVisible = false }
    )

    ConfirmDialog(
        visible = clearHistoryConfirmVisible,
        title = "CLEAR ALL HISTORY?",
        message = "This permanently deletes every conversation. This cannot be undone.",
        confirmLabel = "CLEAR ALL",
        onConfirm = {
            chatViewModel.deleteAllConversations()
            onBack()
        },
        onDismiss = { clearHistoryConfirmVisible = false }
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
    settingsViewModel: SettingsViewModel? = null
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
                    settingsViewModel?.triggerHapticFeedback(context, type = HapticType.TICK)
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

data class VoiceProfile(
    val id: Int,
    val name: String,
    val displayName: String,
    val gender: String,
    val region: String
)

