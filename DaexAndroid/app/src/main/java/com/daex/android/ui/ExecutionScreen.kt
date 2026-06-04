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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.services.ModelStatus
import com.daex.android.ui.components.*
import com.daex.android.services.BackendType
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import com.daex.android.ui.theme.DaexTheme

@Composable
fun ExecutionScreen(
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val messages by viewModel.messages.collectAsState()
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
    
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
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
    }
    var selectedModel by remember { mutableStateOf(ModelBank.generativeModels.first()) }
    
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
        isReflecting -> "Updating memory..."
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DaexTheme.colors.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime) // Root Column handles the IME
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { selectorVisible = true }
                ) {
                    BasicText(
                        text = "☰",
                        style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.primary),
                        modifier = Modifier
                            .clickable { sidebarVisible = true }
                            .padding(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DaexLogo(size = 22.dp, ambient = true)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        BasicText(
                            text = "DAEX", 
                            style = DaexTheme.typography.h1.copy(
                                color = DaexTheme.colors.onBackground,
                                fontSize = 16.sp,
                                letterSpacing = 2.sp
                            )
                        )
                        if (isModelReady && currentModel != null) {
                            BasicText(
                                text = currentModel!!.name.uppercase(),
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.primary.copy(alpha = 0.6f),
                                    fontSize = 8.sp,
                                    letterSpacing = 1.sp
                                )
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Status Badge
                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DaexTheme.colors.primary.copy(alpha = 0.08f))
                                .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .clickable { 
                                    if (modelStatus == ModelStatus.READY) {
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
                            BasicText(
                                text = statusBadgeText + if (modelStatus == ModelStatus.READY) " ▾" else "",
                                style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onSurface, fontSize = 10.sp)
                            )
                        }

                        DropdownMenu(
                            expanded = backendMenuExpanded,
                            onDismissRequest = { backendMenuExpanded = false }
                        ) {
                            if (currentModel == null || currentModel!!.supportedBackends.contains(BackendType.CPU)) {
                                DropdownMenuItem(
                                    text = { Text("CPU Backend", color = DaexTheme.colors.onBackground) },
                                    onClick = {
                                        backendMenuExpanded = false
                                        viewModel.setBackend(BackendType.CPU)
                                    }
                                )
                            }
                            if (currentModel == null || currentModel!!.supportedBackends.contains(BackendType.GPU)) {
                                DropdownMenuItem(
                                    text = { Text("GPU Offload", color = DaexTheme.colors.onBackground) },
                                    onClick = {
                                        backendMenuExpanded = false
                                        viewModel.setBackend(BackendType.GPU)
                                    }
                                )
                            }
                            if (currentModel == null || currentModel!!.supportedBackends.contains(BackendType.NPU)) {
                                DropdownMenuItem(
                                    text = { Text("NPU Acceleration", color = DaexTheme.colors.onBackground) },
                                    onClick = {
                                        backendMenuExpanded = false
                                        viewModel.setBackend(BackendType.NPU)
                                    }
                                )
                            }
                        }
                    }

                    BasicText(
                        text = "+",
                        style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.primary),
                        modifier = Modifier
                            .clickable { viewModel.clearMessages() }
                            .padding(8.dp)
                    )
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
                    SuggestedPrompts(onSelectPrompt = {
                        if (isModelReady && !isGenerating) {
                            viewModel.submitPrompt(it)
                        } else if (!isModelReady) {
                            (currentModel ?: selectedModel).let { model -> viewModel.loadModel(model) }
                        }
                    })
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 120.dp, top = 16.dp) 
                    ) {
                        itemsIndexed(messages) { index, msg ->
                            val isLastModel = msg.role == "model" && messages.subList(index + 1, messages.size).none { it.role == "model" }
                            MessageLine(
                                message = msg,
                                isLastModel = isLastModel,
                                isGenerating = isGenerating,
                                tokenSpeed = tokenSpeed
                            )
                        }
                        
                        // Anchor item for robust bottom-scrolling
                        item {
                            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth())
                        }
                    }
                }

                // --- LAYERED INPUT BAR (Overlay Style) ---
                
                // 1. Liquid Glass Background Layer
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(110.dp) 
                        .graphicsLayer {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                renderEffect = android.graphics.RenderEffect
                                    .createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.DECAL)
                                    .asComposeRenderEffect()
                            }
                        }
                        .background(DaexTheme.colors.background.copy(alpha = 0.5f))
                        .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f))
                )

                // 2. Foreground Content Layer (SHARP)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 8.dp, start = 16.dp, end = 16.dp)
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
                                .clickable { viewModel.toggleReasoning() }
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
                                        text = "📄 ${uploadedFiles[index]}",
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                            .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Attachment button
                        DaexButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            enabled = !isGenerating && !isVectorizing && isModelReady,
                            modifier = Modifier.size(36.dp),
                            backgroundColor = Color.Transparent,
                            useDefaultPadding = false
                        ) {
                            BasicText(
                                text = "📎",
                                style = DaexTheme.typography.body1.copy(
                                    fontSize = 18.sp
                                )
                            )
                        }
                        DaexTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = if (isModelReady) "Initialize execution with Icarus..." else "Engine not loaded...",
                            enabled = !isGenerating && isModelReady
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        DaexButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.submitPrompt(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = !isGenerating && isModelReady && inputText.isNotBlank(),
                            modifier = Modifier.size(44.dp),
                            backgroundColor = if (isGenerating || !isModelReady) DaexTheme.colors.primary.copy(alpha = 0.1f) else DaexTheme.colors.primary,
                            useDefaultPadding = false
                        ) {
                            if (isGenerating) {
                                DaexLoader(size = 28.dp)
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(if (!isModelReady) DaexTheme.colors.primary.copy(alpha = 0.3f) else DaexTheme.colors.onPrimary)
                                )
                            }
                        }
                    }
                }
            }
        }
        
        var settingsVisible by remember { mutableStateOf(false) }
        var memoryEditorVisible by remember { mutableStateOf(false) }

        ModelSelectorModal(
            visible = selectorVisible,
            onClose = { selectorVisible = false },
            onSelect = { 
                selectedModel = it
                selectorVisible = false
                viewModel.loadModel(it)
            },
            onOpenMarketplace = {
                selectorVisible = false
                onOpenGallery()
            },
            modelManager = modelManager
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
                settingsVisible = true
            },
            onOpenGallery = onOpenGallery,
            viewModel = viewModel
        )

        SettingsModal(
            visible = settingsVisible,
            onClose = { settingsVisible = false },
            modelStatus = modelStatus,
            selectedModel = currentModel ?: selectedModel,
            useGPU = viewModel.useGPU.collectAsState().value,
            isDark = viewModel.isDarkMode.collectAsState().value,
            primaryColor = viewModel.primaryColor.collectAsState().value,
            onToggleGPU = { viewModel.toggleGPU() },
            onToggleDark = { viewModel.setDarkMode(it) },
            onSelectColor = { viewModel.setThemeColor(it) },
            onDownloadModel = { 
                currentModel?.let { viewModel.downloadModel(it) } ?: run { selectorVisible = true }
            },
            onChangeModel = {
                settingsVisible = false
                selectorVisible = true
            },
            onDeleteModel = { /* TODO */ },
            onClearConversations = { 
                viewModel.clearMessages()
                settingsVisible = false
            },
            onEditMemory = {
                settingsVisible = false
                memoryEditorVisible = true
                viewModel.loadCoreMemory()
            },
            onShareLogs = {
                LogShareHelper.shareAppLogs(context)
            }
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
