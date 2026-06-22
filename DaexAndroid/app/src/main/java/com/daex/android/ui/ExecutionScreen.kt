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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.ui.draw.alpha
import com.daex.android.ui.components.*
import com.daex.android.services.BackendType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun TelemetryTicker(systemState: String, textColor: Color) {
    AnimatedContent(
        targetState = systemState,
        transitionSpec = {
            (slideInVertically { height -> height } + fadeIn(tween(300))).togetherWith(
                slideOutVertically { height -> -height } + fadeOut(tween(300))
            )
        },
        label = "telemetry_ticker"
    ) { stateText ->
        BasicText(
            text = stateText.uppercase(),
            style = DaexTheme.typography.mono.copy(
                color = textColor.copy(alpha = 0.6f),
                fontSize = 11.sp,
                letterSpacing = 2.sp
            ),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

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
    val attachedFiles by viewModel.attachedFiles.collectAsState()
    val downloadedModelIds by viewModel.downloadedModelIds.collectAsState()
    var documentLibraryVisible by remember { mutableStateOf(false) }
    val isAuraEnabled by viewModel.isAuraEnabled.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val voiceAmplitude by viewModel.voiceAmplitude.collectAsState()
    val selectedBackend by viewModel.selectedBackend.collectAsState()
    val suggestedPrompts by viewModel.suggestedPrompts.collectAsState()
    val isVoiceSessionActive by viewModel.isLiveVoiceActive.collectAsState()
    
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val listState = rememberLazyListState()
    var autoScrollEnabled by remember { mutableStateOf(true) }

    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (layoutInfo.totalItemsCount == 0) return@derivedStateOf true
            val lastVisibleItem = visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisibleItem.index >= layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            val startOffset = listState.firstVisibleItemScrollOffset
            val startIndex = listState.firstVisibleItemIndex
            
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .collect { (index, offset) ->
                    if (index < startIndex || (index == startIndex && offset < startOffset)) {
                        autoScrollEnabled = false
                    }
                }
        }
    }
    
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            autoScrollEnabled = true
        }
    }

    val isModelThinking = remember(messages, isGenerating) {
        val lastMsg = messages.lastOrNull()
        isGenerating && lastMsg != null && lastMsg.role == "model" && lastMsg.content.isEmpty() && !lastMsg.thoughtContent.isNullOrEmpty()
    }

    val sensorManager = remember(context) { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val accelerometer = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    
    DisposableEffect(isAuraEnabled, accelerometer) {
        if (!isAuraEnabled || accelerometer == null) return@DisposableEffect onDispose {}
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    val rawX = it.values[0]
                    val rawY = it.values[1]
                    tiltX = tiltX + 0.1f * (rawX - tiltX)
                    tiltY = tiltY + 0.1f * (rawY - tiltY)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

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
            isModelThinking -> Color(0xFF6366F1)
            isGenerating -> Color(0xFFA855F7)
            modelStatus == ModelStatus.LOADING || modelStatus == ModelStatus.DOWNLOADING || isReflecting || isVectorizing -> DaexTheme.colors.warning
            else -> DaexTheme.colors.primary
        },
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "AuraColor"
    )

    val centerAlpha by animateFloatAsState(
        targetValue = when {
            isModelThinking -> 0.28f
            isGenerating -> 0.22f
            else -> 0.16f
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
            viewModel.startLiveVoiceSession { recognizedText ->
                inputText = recognizedText
            }
        }
    }

    val voiceModeProgress by animateFloatAsState(
        targetValue = if (voiceState == VoiceState.LISTENING) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
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

    val liveOverlayAlpha by animateFloatAsState(
        targetValue = if (isVoiceSessionActive) 1f else 0f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "LiveOverlayAlpha"
    )

    val liveAuraColor by animateColorAsState(
        targetValue = when (voiceState) {
            VoiceState.SPEAKING   -> Color(0xFF06B6D4)
            VoiceState.PROCESSING -> Color(0xFF6366F1)
            VoiceState.LISTENING  -> DaexTheme.colors.primary
            else                  -> DaexTheme.colors.primary
        },
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "LiveAuraColor"
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

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                var fileName: String? = null
                if (uri.scheme == "content") {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use { c ->
                        if (c.moveToFirst()) {
                            val displayNameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (displayNameIndex != -1) {
                                fileName = c.getString(displayNameIndex)
                            }
                        }
                    }
                }
                if (fileName == null) fileName = uri.path?.substringAfterLast('/')
                val finalFileName = fileName ?: "uploaded_file"
                
                viewModel.uploadFile(uri, finalFileName)
            } catch (e: Exception) {
                android.util.Log.e("ExecutionScreen", "Failed to initiate file upload", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshUploadedFiles()
        viewModel.refreshDownloadedModels()
    }
    var selectedModel by remember { mutableStateOf(ModelBank.generativeModels.first()) }
    
    val lastMessageContent = messages.lastOrNull()?.content ?: ""

    LaunchedEffect(messages.size, isGenerating, lastMessageContent) {
        if (messages.isNotEmpty() && autoScrollEnabled) {
            if (isGenerating || isAtBottom) {
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
                        // DIIZZY: Read tilt state strictly in draw phase to avoid composition thrashing
                        val maxShiftX = 35.dp.toPx()
                        val maxShiftY = 35.dp.toPx()
                        val shiftX = (-tiltX * 4.5f.dp.toPx()).coerceIn(-maxShiftX, maxShiftX)
                        val shiftY = (tiltY * 4.5f.dp.toPx()).coerceIn(-maxShiftY, maxShiftY)

                        val scrollVal = listState.firstVisibleItemIndex * 200f + listState.firstVisibleItemScrollOffset
                        val scrollSine = kotlin.math.sin(scrollVal * 0.002f)
                        val scrollShiftY = scrollSine * 25.dp.toPx()

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

                    if (liveOverlayAlpha > 0f) {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.38f
                        val radius = size.width * 1.1f * auraScale
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    liveAuraColor.copy(alpha = 0.30f * liveOverlayAlpha),
                                    liveAuraColor.copy(alpha = 0.08f * liveOverlayAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = radius
                            ),
                            center = Offset(cx, cy),
                            radius = radius
                        )
                        val cx2 = size.width * 0.85f
                        val cy2 = size.height * 0.7f
                        val r2 = size.width * 0.6f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    liveAuraColor.copy(alpha = 0.10f * liveOverlayAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(cx2, cy2),
                                radius = r2
                            ),
                            center = Offset(cx2, cy2),
                            radius = r2
                        )
                    }
                }
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 1f - liveOverlayAlpha }
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
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

                                Column(modifier = Modifier.fillMaxWidth().padding(6.dp)) {
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
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (selectedBackend == BackendType.CPU) DaexTheme.colors.primary else Color.Transparent))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicText(text = "CPU Backend", style = DaexTheme.typography.body2.copy(color = if (selectedBackend == BackendType.CPU) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = if (selectedBackend == BackendType.CPU) FontWeight.Bold else FontWeight.Normal))
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
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (selectedBackend == BackendType.GPU) DaexTheme.colors.primary else Color.Transparent))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicText(text = "GPU Offload", style = DaexTheme.typography.body2.copy(color = if (selectedBackend == BackendType.GPU) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = if (selectedBackend == BackendType.GPU) FontWeight.Bold else FontWeight.Normal))
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
                                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (selectedBackend == BackendType.NPU) DaexTheme.colors.primary else Color.Transparent))
                                            Spacer(modifier = Modifier.width(10.dp))
                                            BasicText(text = "NPU Acceleration", style = DaexTheme.typography.body2.copy(color = if (selectedBackend == BackendType.NPU) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = if (selectedBackend == BackendType.NPU) FontWeight.Bold else FontWeight.Normal))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
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
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = DaexTheme.colors.warning)
                                BasicText(text = "Downloading model... $downloadProgress%", style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.warning, fontSize = 12.sp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(DaexTheme.colors.warning.copy(alpha = 0.15f))) {
                                Box(modifier = Modifier.fillMaxWidth(downloadProgress / 100f).fillMaxHeight().background(DaexTheme.colors.warning))
                            }
                        }
                        ModelStatus.LOADING -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DaexLoader(size = 16.dp)
                                BasicText(text = "Loading engine into memory...", style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.warning, fontSize = 12.sp))
                            }
                        }
                        ModelStatus.ERROR -> {
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                BasicText(text = errorMessage ?: "Model error", modifier = Modifier.weight(1f), style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.error, fontSize = 12.sp))
                                BasicText(text = "RETRY", modifier = Modifier.clickable { (currentModel ?: selectedModel).let { viewModel.loadModel(it) } }, style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 12.sp))
                            }
                        }
                        else -> {
                            Box(modifier = Modifier.fillMaxWidth().clickable { (currentModel ?: selectedModel).let { viewModel.loadModel(it) } }, contentAlignment = Alignment.Center) {
                                BasicText(text = "Model not loaded — tap to initialize", style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.warning, fontSize = 12.sp))
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - liveOverlayAlpha }) {
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
                                MessageLine(message = msg, isLastModel = isLastModel, isGenerating = isGenerating, tokenSpeed = tokenSpeed, activePermission = if (isLastModel) activePermission else null)
                            }
                            item { Spacer(modifier = Modifier.height(1.dp).fillMaxWidth()) }
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.Center).offset(y = (-80).dp)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVoiceSessionActive,
                        enter = fadeIn(animationSpec = tween(500)),
                        exit  = fadeOut(animationSpec = tween(350))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // DIIZZY: Replaced Crossfade with the Ephemeral Telemetry Ticker
                            val telemetryState = when (voiceState) {
                                VoiceState.SPEAKING   -> "Streaming audio..."
                                VoiceState.PROCESSING -> "Inferring tokens..."
                                VoiceState.LISTENING  -> "Awaiting audio input..."
                                else                  -> "Initializing pipeline..."
                            }
                            
                            TelemetryTicker(systemState = telemetryState, textColor = liveAuraColor)
                        }
                    }
                }

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
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isReasoningEnabled) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha=0.4f)))
                                Spacer(modifier = Modifier.width(6.dp))
                                BasicText(text = if (isReasoningEnabled) "REASONING" else "FAST", style = DaexTheme.typography.mono.copy(color = if (isReasoningEnabled) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha=0.6f), fontSize = 10.sp, letterSpacing = 1.sp))
                            }
                        }
                    }

                    if (attachedFiles.isNotEmpty()) {
                        LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(attachedFiles.size) { index ->
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(DaexTheme.colors.primary.copy(alpha = 0.12f)).border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    BasicText(text = attachedFiles[index], style = DaexTheme.typography.caption.copy(color = DaexTheme.colors.primary))
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
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = DaexTheme.colors.warning, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            BasicText(text = "Vectorizing file...", style = DaexTheme.typography.caption.copy(color = DaexTheme.colors.warning))
                        }
                    }
 
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
                        val maxBarWidth = maxWidth
                        
                        // DIIZZY: Stateful visual tracking to avoid coroutine/allocation overhead during active drag
                        var isDragging by remember { mutableStateOf(false) }
                        var rawDragOffset by remember { mutableFloatStateOf(0f) }
                        val dragOffset = remember { Animatable(0f) }
                        val maxDragDistancePx = with(LocalDensity.current) { 150.dp.toPx() }

                        val currentOffset = if (isDragging) rawDragOffset else dragOffset.value

                        val currentIsVoiceSessionActive by rememberUpdatedState(isVoiceSessionActive)
                        val currentIsGenerating by rememberUpdatedState(isGenerating)
                        val currentIsModelReady by rememberUpdatedState(isModelReady)

                        LaunchedEffect(isVoiceSessionActive) {
                            if (!isVoiceSessionActive) {
                                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                rawDragOffset = 0f
                            }
                        }

                        val dragProgress = if (maxDragDistancePx > 0f) {
                            (kotlin.math.abs(currentOffset) / maxDragDistancePx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        val transitionProgress by animateFloatAsState(
                            targetValue = if (isVoiceSessionActive) 1f else 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "transition_progress"
                        )

                        val dragShrunkWidth = maxBarWidth + with(LocalDensity.current) { currentOffset.toDp() }
                        val barWidth = dragShrunkWidth + (120.dp - dragShrunkWidth) * transitionProgress

                        Box(
                            modifier = Modifier
                                .width(barWidth)
                                .height(52.dp)
                                .align(Alignment.Center)
                                .pointerInput(Unit) {
                                    detectHorizontalDragGestures(
                                        onDragStart = {
                                            if (!currentIsVoiceSessionActive && currentIsModelReady && !currentIsGenerating) {
                                                isDragging = true
                                                rawDragOffset = dragOffset.value
                                            }
                                        },
                                        onDragCancel = {
                                            if (isDragging) {
                                                isDragging = false
                                                coroutineScope.launch {
                                                    dragOffset.snapTo(rawDragOffset)
                                                    dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (isDragging) {
                                                isDragging = false
                                                coroutineScope.launch {
                                                    dragOffset.snapTo(rawDragOffset)
                                                    if (rawDragOffset < -maxDragDistancePx * 0.6f && currentIsModelReady && !currentIsGenerating) {
                                                        dragOffset.animateTo(-maxDragDistancePx, tween(200))
                                                        val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                                        if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                            viewModel.triggerHapticFeedback(context)
                                                            viewModel.startLiveVoiceSession { recognizedText -> inputText = recognizedText }
                                                        } else {
                                                            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                            dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                        }
                                                    } else {
                                                        dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                    }
                                                }
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            if (currentIsVoiceSessionActive) {
                                                if (dragAmount > 10f) {
                                                    change.consume()
                                                    viewModel.triggerHapticFeedback(context)
                                                    viewModel.stopLiveVoiceSession()
                                                }
                                            } else {
                                                if (isDragging && currentIsModelReady && !currentIsGenerating) {
                                                    change.consume()
                                                    rawDragOffset = (rawDragOffset + dragAmount).coerceIn(-maxDragDistancePx, 0f)
                                                    // DIIZZY: Trigger immediately on pull when crossing 95% threshold
                                                    if (rawDragOffset <= -maxDragDistancePx * 0.95f) {
                                                        isDragging = false
                                                        coroutineScope.launch {
                                                            dragOffset.snapTo(rawDragOffset)
                                                            dragOffset.animateTo(-maxDragDistancePx, tween(150))
                                                            val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                                            if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                                viewModel.triggerHapticFeedback(context)
                                                                viewModel.startLiveVoiceSession { recognizedText -> inputText = recognizedText }
                                                            } else {
                                                                recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                                dragOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(28.dp))
                                    .graphicsLayer {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                            renderEffect = android.graphics.RenderEffect.createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.DECAL).asComposeRenderEffect()
                                        }
                                    }
                                    .background(DaexTheme.colors.background.copy(alpha = 0.85f))
                                    .drawBehind {
                                        // Draw drag progress glow
                                        if (dragProgress > 0f) {
                                            val glowColor = auraColor.copy(alpha = dragProgress * 0.25f)
                                            drawRoundRect(
                                                brush = Brush.radialGradient(
                                                    colors = listOf(glowColor, Color.Transparent),
                                                    center = Offset(size.width * (1f - dragProgress), size.height / 2f),
                                                    radius = size.width * 0.5f
                                                ),
                                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx(), 28.dp.toPx())
                                            )
                                        }

                                        val waveAlpha = if (isVoiceSessionActive) 1f else voiceModeProgress
                                        if (waveAlpha > 0f) {
                                            val baseLineY = size.height * 0.5f
                                            val widthF = size.width
                                            val piF = kotlin.math.PI.toFloat()

                                            val activeAmplitude = if (isVoiceSessionActive && (voiceState == VoiceState.PROCESSING || isGenerating)) {
                                                0.15f + 0.1f * kotlin.math.sin(wavePhase * 2.0f)
                                            } else {
                                                smoothedAmplitude
                                            }

                                            val path1 = Path()
                                            val amplitude1 = 4.dp.toPx() + (activeAmplitude * 10.dp.toPx())
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
                                            drawPath(path = path1, brush = Brush.verticalGradient(colors = listOf(auraColor.copy(alpha = waveAlpha * 0.25f), Color.Transparent), startY = baseLineY - amplitude1, endY = size.height))

                                            val path2 = Path()
                                            val amplitude2 = 6.dp.toPx() + (activeAmplitude * 14.dp.toPx())
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
                                            drawPath(path = path2, brush = Brush.verticalGradient(colors = listOf(auraColor.copy(alpha = waveAlpha * 0.40f), Color.Transparent), startY = baseLineY - amplitude2, endY = size.height))

                                            val path3 = Path()
                                            val amplitude3 = 8.dp.toPx() + (activeAmplitude * 18.dp.toPx())
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
                                            drawPath(path = path3, brush = Brush.verticalGradient(colors = listOf(auraColor.copy(alpha = waveAlpha * 0.60f), Color.Transparent), startY = baseLineY - amplitude3, endY = size.height))
                                        }
                                    }
                                    .border(
                                        width = if (isVoiceSessionActive) 1.dp else (0.5.dp + (1.dp * dragProgress).coerceAtMost(1.dp)),
                                        color = if (isVoiceSessionActive) {
                                            auraColor.copy(alpha = 0.6f)
                                        } else {
                                            lerp(
                                                DaexTheme.colors.onSurface.copy(alpha = 0.15f),
                                                auraColor.copy(alpha = 0.6f),
                                                dragProgress
                                            )
                                        },
                                        shape = RoundedCornerShape(28.dp)
                                    )
                            )

                            Row(
                                modifier = Modifier.matchParentSize().padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val textFieldWeight = (1f - transitionProgress).coerceAtLeast(0.001f)

                                Row(
                                    modifier = Modifier
                                        .weight(textFieldWeight)
                                        .fillMaxHeight()
                                        // DIIZZY: Layout freeze to prevent text squishing on compress
                                        .requiredWidth(maxBarWidth - 64.dp)
                                        .graphicsLayer {
                                            alpha = (1f - transitionProgress).coerceIn(0f, 1f)
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DaexButton(
                                        onClick = { viewModel.triggerHapticFeedback(context); documentLibraryVisible = true },
                                        enabled = !isGenerating && !isVectorizing && isModelReady && transitionProgress < 0.5f,
                                        modifier = Modifier.size(36.dp),
                                        backgroundColor = Color.Transparent,
                                        useDefaultPadding = false,
                                        shape = CircleShape
                                    ) {
                                        BasicText(text = "+", style = DaexTheme.typography.body1.copy(color = DaexTheme.colors.primary, fontSize = 18.sp))
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
                                        enabled = !isGenerating && isModelReady && voiceState != VoiceState.LISTENING && transitionProgress < 0.5f,
                                        backgroundColor = Color.Transparent
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp * (1f - transitionProgress)))

                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                            alpha = (1f - transitionProgress).coerceIn(0f, 1f)
                                            // DIIZZY: Subtle visual shift for gesture pull feeling
                                            translationX = -dragProgress * 12.dp.toPx()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    DaexButton(
                                        onClick = {
                                            if (isGenerating) {
                                                viewModel.triggerHapticFeedback(context)
                                                viewModel.cancelGeneration()
                                                val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                                if (lastUserMsg != null) inputText = lastUserMsg.content
                                            } else if (inputText.isNotEmpty()) {
                                                viewModel.triggerHapticFeedback(context)
                                                viewModel.submitPrompt(inputText)
                                                inputText = ""
                                            } else {
                                                viewModel.triggerHapticFeedback(context)
                                                val permissionCheck = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                                                if (permissionCheck == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    viewModel.startLiveVoiceSession { recognizedText -> inputText = recognizedText }
                                                } else {
                                                    recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        },
                                        enabled = (isGenerating || isModelReady) && (isGenerating || inputText.isNotEmpty() || voiceState != VoiceState.PROCESSING) && transitionProgress < 0.5f,
                                        modifier = Modifier.size(44.dp),
                                        backgroundColor = if (isVoiceSessionActive) Color.Transparent else if (isGenerating) DaexTheme.colors.primary else if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.1f) else DaexTheme.colors.primary,
                                        useDefaultPadding = false,
                                        shape = CircleShape
                                    ) {
                                        if (isGenerating) {
                                            DaexStopIcon(color = DaexTheme.colors.onPrimary, modifier = Modifier.size(16.dp))
                                        } else if (voiceState == VoiceState.PROCESSING) {
                                            DaexLoader(size = 28.dp)
                                        } else {
                                            Crossfade(targetState = inputText.isEmpty(), label = "morph_button") { isEmpty ->
                                                if (isEmpty) {
                                                    DaexMicIcon(color = if (isVoiceSessionActive) DaexTheme.colors.primary else if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.3f) else DaexTheme.colors.onPrimary, modifier = Modifier.size(18.dp))
                                                } else {
                                                    DaexSendIcon(color = if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.3f) else DaexTheme.colors.onPrimary, modifier = Modifier.size(16.dp))
                                                }
                                            }
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
            onOpenMarketplace = { selectorVisible = false; onOpenGallery() },
            downloadedModelIds = downloadedModelIds,
            onDelete = { viewModel.deleteModel(it) }
        )

        Sidebar(
            visible = sidebarVisible,
            onClose = { sidebarVisible = false },
            onNewConversation = { sidebarVisible = false; viewModel.clearMessages() },
            onOpenSettings = { sidebarVisible = false; onOpenSettings() },
            onOpenGallery = onOpenGallery,
            viewModel = viewModel
        )

        MemoryEditorModal(
            visible = memoryEditorVisible,
            onClose = { memoryEditorVisible = false },
            initialContent = viewModel.coreMemoryText.collectAsState().value,
            onSave = { viewModel.saveCoreMemory(it); memoryEditorVisible = false }
        )

        DocumentLibraryModal(
            visible = documentLibraryVisible,
            onClose = { documentLibraryVisible = false },
            uploadedFiles = uploadedFiles,
            attachedFiles = attachedFiles,
            onToggleAttachment = { viewModel.toggleAttachedFile(it) },
            onDeleteFromLibrary = { viewModel.deleteUploadedFile(it) },
            onUploadNew = { filePickerLauncher.launch("*/*") }
        )
    }
}