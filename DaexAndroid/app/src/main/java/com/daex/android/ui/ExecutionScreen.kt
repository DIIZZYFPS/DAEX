package com.daex.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.services.ModelStatus
import com.daex.android.services.PermissionRequest
import com.daex.android.services.VoiceState
import androidx.compose.animation.Crossfade
import com.daex.android.ui.components.*
import com.daex.android.services.BackendType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import com.daex.android.ui.theme.DaexTheme
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.IntOffset
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.Context

@Composable
fun ExecutionScreen(
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
    val activePermission by viewModel.activePermission.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val isReflecting by viewModel.isReflecting.collectAsState()
    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val currentModel by viewModel.currentModel.collectAsState()
    val tokenSpeed by viewModel.tokenSpeed.collectAsState()
    val hardwareState by viewModel.hardwareState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isReasoningEnabled by viewModel.isReasoningEnabled.collectAsState()
    val isVectorizing by viewModel.isVectorizing.collectAsState()
    val uploadedFiles by viewModel.uploadedFiles.collectAsState()
    val downloadedModelIds by viewModel.downloadedModelIds.collectAsState()
    val isAuraEnabled by viewModel.isAuraEnabled.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsState()
    val selectedBackend by viewModel.selectedBackend.collectAsState()
    val suggestedPrompts by viewModel.suggestedPrompts.collectAsState()
    
    val context = LocalContext.current

    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }

    // Detect if user is near the bottom
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) return@derivedStateOf true
            val lastVisibleItem = visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            // If the last item is visible, we're at the bottom
            lastVisibleItem.index >= layoutInfo.totalItemsCount - 1
        }
    }

    // Monitor scroll gestures to disable auto-scroll on upward movement
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val startOffset = listState.firstVisibleItemScrollOffset
            val startIndex = listState.firstVisibleItemIndex
            
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (index < startIndex || (index == startIndex && offset < startOffset)) {
                        // Scrolling UP
                        autoScrollEnabled = false
                    }
                }
        }
    }
    
    // Re-enable auto-scroll when user reaches the bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            autoScrollEnabled = true
        }
    }

    val isModelThinking = remember(messages, isGenerating) {
        val lastMsg = messages.lastOrNull()
        isGenerating && lastMsg != null && lastMsg.role == "model" && lastMsg.content.isEmpty() && !lastMsg.thoughtContent.isNullOrEmpty()
    }

    // Listen to accelerometer for device tilt-reactive aura
    val sensorManager = remember(context) { 
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager 
    }
    val accelerometer = remember(sensorManager) { 
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) 
    }
    
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    
    DisposableEffect(isAuraEnabled, accelerometer) {
        if (!isAuraEnabled || accelerometer == null) return@DisposableEffect onDispose {}
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val rawX = it.values[0]
                    val rawY = it.values[1]
                    // Smooth values with low-pass filter
                    tiltX = tiltX + 0.1f * (rawX - tiltX)
                    tiltY = tiltY + 0.1f * (rawY - tiltY)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    // Reactive aura background transition states
    val infiniteTransition = rememberInfiniteTransition(label = "AuraPulse")
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AuraScale"
    )

    val auraColor by animateColorAsState(
        targetValue = when {
            modelStatus == ModelStatus.ERROR -> DaexTheme.colors.error
            isModelThinking -> Color(0xFF6366F1) // Indigo for thinking state
            isGenerating -> Color(0xFFA855F7) // Purple for generating final response
            modelStatus == ModelStatus.LOADING || modelStatus == ModelStatus.DOWNLOADING || isReflecting || isVectorizing -> DaexTheme.colors.warning
            else -> DaexTheme.colors.primary
        },
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "AuraColor"
    )

    val centerAlpha by animateFloatAsState(
        targetValue = when {
            isModelThinking -> 0.28f // Increased from 0.14f
            isGenerating -> 0.22f // Increased from 0.10f
            else -> 0.16f // Increased from 0.07f
        },
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "AuraAlpha"
    )

    val isScrolling = listState.isScrollInProgress
    val scrollRadiusMultiplier by animateFloatAsState(
        targetValue = if (isScrolling) 1.15f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "AuraScrollRadius"
    )

    var inputText by remember { mutableStateOf("") }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.triggerHapticFeedback(context)
            viewModel.toggleVoiceInput { recognizedText ->
                inputText = recognizedText
            }
        }
    }

    val voiceModeProgress by animateFloatAsState(
        targetValue = if (voiceState == VoiceState.LISTENING) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "VoiceModeProgress"
    )

    val smoothedAmplitude by animateFloatAsState(
        targetValue = voiceAmplitude,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "SmoothedAmplitude"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * kotlin.math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    var sidebarVisible by remember { mutableStateOf(false) }
    var selectorVisible by remember { mutableStateOf(false) }
    var backendMenuExpanded by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "uploaded_file"
                val mimeType = context.contentResolver.getType(uri) ?: ""
                
                val textContent = if (mimeType == "application/pdf") {
                    // PDF extraction using iText
                    val inputStream = context.contentResolver.openInputStream(uri)
                    inputStream?.use { stream ->
                        val reader = com.itextpdf.kernel.pdf.PdfReader(stream)
                        val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(reader)
                        val sb = StringBuilder()
                        for (i in 1..pdfDoc.numberOfPages) {
                            val page = pdfDoc.getPage(i)
                            val text = com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor.getTextFromPage(page)
                            sb.appendLine(text)
                        }
                        pdfDoc.close()
                        sb.toString()
                    } ?: ""
                } else {
                    // Plain text files
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                }

                if (textContent.isNotBlank()) {
                    viewModel.uploadFile(fileName, textContent)
                }
            } catch (e: Exception) {
                android.util.Log.e("ExecutionScreen", "Failed to read file", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshUploadedFiles()
        viewModel.refreshDownloadedModels()
    }
    var selectedModel by remember { mutableStateOf(ModelBank.generativeModels.first()) }
    
    // Scroll tracking was moved to the top of the Composable

    val lastMessageContent = messages.lastOrNull()?.content ?: ""

    // Auto-scroll logic: scroll to bottom whenever messages list changes, 
    // generation status changes, or the content of the last message updates.
    LaunchedEffect(messages.size, isGenerating, lastMessageContent) {
        if (messages.isNotEmpty() && autoScrollEnabled) {
            if (isGenerating || isAtBottom) {
                // To avoid scrolling to the "top" of the last item (which hides long messages),
                // we use animateScrollToItem but with a VERY high index or a specific item.
                // However, LazyListState.scrollToItem(index) puts that index at the top.
                // The fix for "pinned to bottom" is to use scrollOffset to push it down.
                // But calculation is hard. Instead, we can use this trick:
                listState.animateScrollToItem(messages.size)
            }
        }
    }

    val isModelReady = modelStatus == ModelStatus.READY

    val statusBadgeText = when {
        isVectorizing -> "Vectorizing..."
        isReflecting -> "Compacting..."
        isGenerating && tokenSpeed > 0 -> "$tokenSpeed tok/s"
        isGenerating -> "Generating..."
        modelStatus == ModelStatus.LOADING -> "Loading..."
        modelStatus == ModelStatus.DOWNLOADING -> "$downloadProgress%"
        else -> hardwareState
    }

    val statusColor = when (modelStatus) {
        ModelStatus.ERROR -> DaexTheme.colors.error
        ModelStatus.DOWNLOADING, ModelStatus.LOADING -> DaexTheme.colors.warning
        ModelStatus.READY -> when {
            isVectorizing -> DaexTheme.colors.warning
            isReflecting -> DaexTheme.colors.warning
            isGenerating -> Color(0xFFA855F7)
            else -> DaexTheme.colors.success
        }
        else -> DaexTheme.colors.warning
    }

    val primaryColorVal = DaexTheme.colors.primary

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DaexTheme.colors.background)
                .drawBehind {
                    if (isAuraEnabled) {
                        // Calculate tilt shifts from accelerometer values
                        val maxShiftX = 35.dp.toPx()
                        val maxShiftY = 35.dp.toPx()
                        val shiftX = (-tiltX * 4.5f.dp.toPx()).coerceIn(-maxShiftX, maxShiftX)
                        val shiftY = (tiltY * 4.5f.dp.toPx()).coerceIn(-maxShiftY, maxShiftY)

                        // Calculate scroll wave drift
                        val scrollVal = listState.firstVisibleItemIndex * 200f + listState.firstVisibleItemScrollOffset
                        val scrollSine = kotlin.math.sin(scrollVal * 0.002f)
                        val scrollShiftY = scrollSine * 25.dp.toPx()

                        // 1. Dominant Right Reactive Aura
                        val rightCenter = Offset(
                            x = size.width * 0.85f + shiftX, 
                            y = size.height * 0.35f + shiftY + scrollShiftY
                        )
                        val rightBaseRadius = size.width * 0.8f
                        val rightRadius = rightBaseRadius * auraScale * scrollRadiusMultiplier
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    auraColor.copy(alpha = centerAlpha * (1f - voiceModeProgress)),
                                    auraColor.copy(alpha = centerAlpha * 0.4f * (1f - voiceModeProgress)),
                                    Color.Transparent
                                ),
                                center = rightCenter,
                                radius = rightRadius
                            ),
                            center = rightCenter,
                            radius = rightRadius
                        )

                        // 2. Secondary Left Calm Ambient Aura
                        val leftCenter = Offset(
                            x = size.width * 0.15f + shiftX, 
                            y = size.height * 0.75f + shiftY - scrollShiftY
                        )
                        val leftRadius = size.width * 0.6f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    primaryColorVal.copy(alpha = 0.08f * (1f - voiceModeProgress)),
                                    Color.Transparent
                                ),
                                center = leftCenter,
                                radius = leftRadius
                            ),
                            center = leftCenter,
                            radius = leftRadius
                        )


                    }
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime) // Root Column handles the IME
        ) {
            // Header
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                // Left Slot: Hamburger Menu
                BasicText(
                    text = "☰",
                    style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.primary),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable {
                            viewModel.triggerHapticFeedback(context)
                            sidebarVisible = true
                        }
                        .padding(8.dp)
                )

                // Center Slot: Brand + Engine Selector
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable {
                            if (!isGenerating && !isReflecting && !isVectorizing) {
                                viewModel.triggerHapticFeedback(context)
                                selectorVisible = true
                            }
                        }
                ) {
                    BasicText(
                        text = "DAEX", 
                        style = DaexTheme.typography.h1.copy(
                            color = DaexTheme.colors.onBackground,
                            fontSize = 16.sp,
                            letterSpacing = 2.sp
                        )
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = (currentModel?.name ?: "SELECT ENGINE").uppercase(),
                            style = DaexTheme.typography.mono.copy(
                                color = DaexTheme.colors.primary.copy(alpha = if (currentModel != null) 0.6f else 0.4f),
                                fontSize = 8.sp,
                                letterSpacing = 1.sp
                            )
                        )
                        val canChangeModel = !isGenerating && !isReflecting && !isVectorizing
                        if (canChangeModel) {
                            Spacer(modifier = Modifier.width(4.dp))
                            BasicText(
                                text = "▾",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.primary.copy(alpha = if (currentModel != null) 0.6f else 0.4f),
                                    fontSize = 8.sp
                                )
                            )
                        }
                    }
                }

                // Right Slot: Status Badge
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DaexTheme.colors.primary.copy(alpha = 0.08f))
                            .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .clickable { 
                                if (modelStatus == ModelStatus.READY && !isGenerating && !isReflecting && !isVectorizing) {
                                    backendMenuExpanded = true 
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        val showBadgeDropdown = modelStatus == ModelStatus.READY && !isGenerating && !isReflecting && !isVectorizing
                        BasicText(
                            text = statusBadgeText + if (showBadgeDropdown) " ▾" else "",
                            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onSurface, fontSize = 10.sp)
                        )
                    }

                    if (backendMenuExpanded) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(x = 0, y = 110),
                            onDismissRequest = { backendMenuExpanded = false }
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(170.dp)
                                    .height(IntrinsicSize.Min)
                            ) {
                                // Glassmorphic background
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .graphicsLayer {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                                renderEffect = android.graphics.RenderEffect
                                                    .createBlurEffect(15f, 15f, android.graphics.Shader.TileMode.DECAL)
                                                    .asComposeRenderEffect()
                                            }
                                        }
                                        .background(DaexTheme.colors.surface.copy(alpha = 0.85f))
                                        .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(6.dp)
                                ) {
                                    if (currentModel == null || currentModel!!.supportedBackends.contains(BackendType.CPU)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    backendMenuExpanded = false
                                                    viewModel.setBackend(BackendType.CPU)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (selectedBackend == BackendType.CPU) DaexTheme.colors.primary else Color.Transparent)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicText(
                                                text = "CPU Backend",
                                                style = DaexTheme.typography.body2.copy(
                                                    color = if (selectedBackend == BackendType.CPU) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.8f),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedBackend == BackendType.CPU) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        }
                                    }

                                    if (currentModel == null || currentModel!!.supportedBackends.contains(BackendType.GPU)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    backendMenuExpanded = false
                                                    viewModel.setBackend(BackendType.GPU)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (selectedBackend == BackendType.GPU) DaexTheme.colors.primary else Color.Transparent)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicText(
                                                text = "GPU Offload",
                                                style = DaexTheme.typography.body2.copy(
                                                    color = if (selectedBackend == BackendType.GPU) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.8f),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedBackend == BackendType.GPU) FontWeight.Bold else FontWeight.Normal
                                                )
                                            )
                                        }
                                    }

                                    if (currentModel == null || currentModel!!.supportedBackends.contains(BackendType.NPU)) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    backendMenuExpanded = false
                                                    viewModel.setBackend(BackendType.NPU)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(if (selectedBackend == BackendType.NPU) DaexTheme.colors.primary else Color.Transparent)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicText(
                                                text = "NPU Acceleration",
                                                style = DaexTheme.typography.body2.copy(
                                                    color = if (selectedBackend == BackendType.NPU) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.8f),
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedBackend == BackendType.NPU) FontWeight.Bold else FontWeight.Normal
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
            
            // Model Status Banner
            if (!isModelReady) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DaexTheme.colors.warning.copy(alpha = 0.08f))
                        .border(width = 0.5.dp, color = DaexTheme.colors.warning.copy(alpha = 0.2f))
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    when (modelStatus) {
                        ModelStatus.DOWNLOADING -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = DaexTheme.colors.warning
                                )
                                BasicText(
                                    text = "Downloading model... $downloadProgress%",
                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.warning, fontSize = 12.sp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(DaexTheme.colors.warning.copy(alpha = 0.15f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(downloadProgress / 100f)
                                        .fillMaxHeight()
                                        .background(DaexTheme.colors.warning)
                                )
                            }
                        }
                        ModelStatus.LOADING -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DaexLoader(size = 16.dp)
                                BasicText(
                                    text = "Loading engine into memory...",
                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.warning, fontSize = 12.sp)
                                )
                            }
                        }
                        ModelStatus.ERROR -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                BasicText(
                                    text = errorMessage ?: "Model error",
                                    modifier = Modifier.weight(1f),
                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.error, fontSize = 12.sp)
                                )
                                BasicText(
                                    text = "RETRY",
                                    modifier = Modifier.clickable { (currentModel ?: selectedModel).let { viewModel.loadModel(it) } },
                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 12.sp)
                                )
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { (currentModel ?: selectedModel).let { viewModel.loadModel(it) } },
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = "Model not loaded — tap to initialize",
                                    style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.warning, fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }
            }

            // Chat Area or Welcome
            Box(modifier = Modifier.weight(1f)) {
                if (messages.isEmpty()) {
                    SuggestedPrompts(
                        prompts = suggestedPrompts,
                        onSelectPrompt = {
                            viewModel.triggerHapticFeedback(context)
                            if (isModelReady && !isGenerating) {
                                viewModel.submitPrompt(it)
                            } else if (!isModelReady) {
                                (currentModel ?: selectedModel).let { model -> viewModel.loadModel(model) }
                            }
                        }
                    )
                } else {
                    val visibleMessages = messages.filter { !it.content.startsWith("[CONTEXT COMPACTION]:") }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp) 
                    ) {
                        itemsIndexed(visibleMessages) { index, msg ->
                            val isLastModel = msg.role == "model" && visibleMessages.subList(index + 1, visibleMessages.size).none { it.role == "model" }
                            MessageLine(
                                message = msg,
                                isLastModel = isLastModel,
                                isGenerating = isGenerating,
                                tokenSpeed = tokenSpeed,
                                activePermission = if (isLastModel) activePermission else null
                            )
                        }
                        
                        // Anchor item for robust bottom-scrolling
                        item {
                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth())
                        }
                    }
                }

                // --- LAYERED INPUT BAR (Floating Pill Style) ---
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isReasoningEnabled) DaexTheme.colors.primary.copy(alpha=0.15f) else DaexTheme.colors.onSurface.copy(alpha=0.1f))
                                .border(0.5.dp, if (isReasoningEnabled) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha=0.2f), RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.triggerHapticFeedback(context)
                                    viewModel.toggleReasoning()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isReasoningEnabled) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha=0.4f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicText(
                                    text = if (isReasoningEnabled) "REASONING" else "FAST",
                                    style = DaexTheme.typography.mono.copy(
                                        color = if (isReasoningEnabled) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha=0.6f),
                                        fontSize = 10.sp,
                                        letterSpacing = 1.sp
                                    )
                                )
                            }
                        }
                    }

                    // Uploaded file chips
                    if (uploadedFiles.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(uploadedFiles.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(DaexTheme.colors.primary.copy(alpha = 0.12f))
                                        .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    BasicText(
                                        text = uploadedFiles[index],
                                        style = DaexTheme.typography.caption.copy(
                                            color = DaexTheme.colors.primary
                                        )
                                    )
                                }
                            }
                        }
                    }
 
                    if (isVectorizing) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = DaexTheme.colors.warning,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicText(
                                text = "Vectorizing file...",
                                style = DaexTheme.typography.caption.copy(
                                    color = DaexTheme.colors.warning
                                )
                            )
                        }
                    }
 
                    // Main Floating Pill
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        // Blurred Background Pill Layer
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(28.dp))
                                .graphicsLayer {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                        renderEffect = android.graphics.RenderEffect
                                            .createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.DECAL)
                                            .asComposeRenderEffect()
                                    }
                                }
                                .background(DaexTheme.colors.background.copy(alpha = 0.85f))
                                .drawBehind {
                                    if (voiceModeProgress > 0f) {
                                        val waveAlpha = voiceModeProgress
                                        val baseLineY = size.height * 0.5f
                                        val widthF = size.width
                                        val piF = kotlin.math.PI.toFloat()

                                        // Wave 1 (Back, slow, dark)
                                        val path1 = Path()
                                        val amplitude1 = 4.dp.toPx() + (smoothedAmplitude * 10.dp.toPx())
                                        path1.moveTo(0f, size.height)
                                        for (x in 0..size.width.toInt() step 10) {
                                            val xf = x.toFloat()
                                            val envelope = kotlin.math.sin(xf / widthF * piF)
                                            val sineVal = kotlin.math.sin(xf * 0.01f + wavePhase)
                                            val y = baseLineY + sineVal * amplitude1 * 0.4f * envelope
                                            path1.lineTo(xf, y)
                                        }
                                        path1.lineTo(size.width, size.height)
                                        path1.close()
                                        drawPath(
                                            path = path1,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    auraColor.copy(alpha = waveAlpha * 0.25f),
                                                    Color.Transparent
                                                ),
                                                startY = baseLineY - amplitude1,
                                                endY = size.height
                                            )
                                        )

                                        // Wave 2 (Middle, medium speed)
                                        val path2 = Path()
                                        val amplitude2 = 6.dp.toPx() + (smoothedAmplitude * 14.dp.toPx())
                                        path2.moveTo(0f, size.height)
                                        for (x in 0..size.width.toInt() step 10) {
                                            val xf = x.toFloat()
                                            val envelope = kotlin.math.sin(xf / widthF * piF)
                                            val sineVal = kotlin.math.sin(xf * 0.015f - wavePhase * 2.0f + 1.0f)
                                            val y = baseLineY + sineVal * amplitude2 * 0.6f * envelope
                                            path2.lineTo(xf, y)
                                        }
                                        path2.lineTo(size.width, size.height)
                                        path2.close()
                                        drawPath(
                                            path = path2,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    auraColor.copy(alpha = waveAlpha * 0.40f),
                                                    Color.Transparent
                                                ),
                                                startY = baseLineY - amplitude2,
                                                endY = size.height
                                            )
                                        )

                                        // Wave 3 (Front, fast, dynamic)
                                        val path3 = Path()
                                        val amplitude3 = 8.dp.toPx() + (smoothedAmplitude * 18.dp.toPx())
                                        path3.moveTo(0f, size.height)
                                        for (x in 0..size.width.toInt() step 10) {
                                            val xf = x.toFloat()
                                            val envelope = kotlin.math.sin(xf / widthF * piF)
                                            val sineVal = kotlin.math.sin(xf * 0.02f + wavePhase * 3.0f + 2.5f)
                                            val y = baseLineY + sineVal * amplitude3 * 0.8f * envelope
                                            path3.lineTo(xf, y)
                                        }
                                        path3.lineTo(size.width, size.height)
                                        path3.close()
                                        drawPath(
                                            path = path3,
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    auraColor.copy(alpha = waveAlpha * 0.60f),
                                                    Color.Transparent
                                                ),
                                                startY = baseLineY - amplitude3,
                                                endY = size.height
                                            )
                                        )
                                    }
                                }
                                .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.15f), RoundedCornerShape(28.dp))
                        )

                        // Foreground Input Row (Pill Style)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Attachment button
                            DaexButton(
                                onClick = {
                                    viewModel.triggerHapticFeedback(context)
                                    filePickerLauncher.launch("*/*")
                                },
                                enabled = !isGenerating && !isVectorizing && isModelReady,
                                modifier = Modifier.size(36.dp),
                                backgroundColor = Color.Transparent,
                                useDefaultPadding = false,
                                shape = CircleShape
                            ) {
                                BasicText(
                                    text = "+",
                                    style = DaexTheme.typography.body1.copy(
                                        color = DaexTheme.colors.primary,
                                        fontSize = 18.sp
                                    )
                                )
                            }
                            val placeholderText = when {
                                !isModelReady -> "Engine not loaded..."
                                voiceState == VoiceState.LISTENING -> "Listening to speech..."
                                voiceState == VoiceState.PROCESSING -> "Processing speech..."
                                else -> "Initialize execution with Icarus..."
                            }
                            DaexTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = placeholderText,
                                enabled = !isGenerating && isModelReady && voiceState != VoiceState.LISTENING,
                                backgroundColor = Color.Transparent
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            DaexButton(
                                onClick = {
                                    if (isGenerating) {
                                        viewModel.triggerHapticFeedback(context)
                                        viewModel.cancelGeneration()
                                        val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                        if (lastUserMsg != null) {
                                            inputText = lastUserMsg.content
                                        }
                                    } else if (inputText.isNotEmpty()) {
                                        viewModel.triggerHapticFeedback(context)
                                        viewModel.submitPrompt(inputText)
                                        inputText = ""
                                    } else {
                                        // Voice mode mic tap
                                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(
                                            context,
                                            android.Manifest.permission.RECORD_AUDIO
                                        )
                                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                            viewModel.triggerHapticFeedback(context)
                                            viewModel.toggleVoiceInput { recognizedText ->
                                                inputText = recognizedText
                                            }
                                        } else {
                                            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                enabled = (isGenerating || isModelReady) && (isGenerating || inputText.isNotEmpty() || voiceState != VoiceState.PROCESSING),
                                modifier = Modifier.size(44.dp),
                                backgroundColor = if (isGenerating) DaexTheme.colors.primary else if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.1f) else DaexTheme.colors.primary,
                                useDefaultPadding = false,
                                shape = CircleShape
                            ) {
                                if (isGenerating) {
                                    DaexStopIcon(
                                        color = DaexTheme.colors.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else if (voiceState == VoiceState.PROCESSING) {
                                    DaexLoader(size = 28.dp)
                                } else {
                                    Crossfade(targetState = inputText.isEmpty(), label = "morph_button") { isEmpty ->
                                        if (isEmpty) {
                                            DaexMicIcon(
                                                color = if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.3f) else DaexTheme.colors.onPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else {
                                            DaexSendIcon(
                                                color = if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.3f) else DaexTheme.colors.onPrimary,
                                                modifier = Modifier.size(16.dp)
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
        
        var memoryEditorVisible by remember { mutableStateOf(false) }

        ModelSelectorModal(
            visible = selectorVisible,
            onClose = { selectorVisible = false },
            onSelect = { 
                viewModel.triggerHapticFeedback(context)
                selectedModel = it
                selectorVisible = false
                viewModel.loadModel(it)
            },
            onOpenMarketplace = {
                selectorVisible = false
                onOpenGallery()
            },
            downloadedModelIds = downloadedModelIds,
            onDelete = { viewModel.deleteModel(it) }
        )

        Sidebar(
            visible = sidebarVisible,
            onClose = { sidebarVisible = false },
            onNewConversation = {
                sidebarVisible = false
                viewModel.clearMessages()
            },
            onOpenSettings = {
                sidebarVisible = false
                onOpenSettings()
            },
            onOpenGallery = onOpenGallery,
            viewModel = viewModel
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
    }
}
