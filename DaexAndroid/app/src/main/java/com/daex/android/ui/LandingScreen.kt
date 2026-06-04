package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daex.android.services.DaexInferenceViewModel
import com.daex.android.services.Model
import com.daex.android.services.ModelBank
import com.daex.android.services.ModelManager
import com.daex.android.services.ModelStatus
import com.daex.android.services.BackendType
import com.daex.android.services.DeviceSpecs
import com.daex.android.ui.components.DaexButton
import com.daex.android.ui.components.DaexLogo
import com.daex.android.ui.theme.DaexTheme
import com.daex.android.services.DaexPreferences
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LandingScreen(
    onContinue: () -> Unit,
    daexPreferences: DaexPreferences,
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager
) {
    val pagerState = rememberPagerState(pageCount = { 6 })
    val coroutineScope = rememberCoroutineScope()
    val deviceSpecs = viewModel.deviceSpecs

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DaexTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Main Horizontal Pager (Locked swiping to control user onboarding steps)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> WelcomeSlide {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
                1 -> PhilosophySlide {
                    coroutineScope.launch { pagerState.animateScrollToPage(2) }
                }
                2 -> DiagnosticsSlide(deviceSpecs, isVisible = pagerState.currentPage == 2) {
                    coroutineScope.launch { pagerState.animateScrollToPage(3) }
                }
                3 -> IcarusShowcaseSlide {
                    coroutineScope.launch { pagerState.animateScrollToPage(4) }
                }
                4 -> EngineSelectorSlide(viewModel, modelManager, deviceSpecs) {
                    coroutineScope.launch { pagerState.animateScrollToPage(5) }
                }
                5 -> TutorialSlide(viewModel, daexPreferences, onContinue)
            }
        }

        // Custom Dot Indicator footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(6) { index ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 8.dp else 5.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) DaexTheme.colors.primary else Color.White.copy(alpha = 0.2f))
                )
            }
        }
    }
}

// Slide 1: Welcome and Glowing Logo Animation
@Composable
private fun WelcomeSlide(onNext: () -> Unit) {
    var animateSpacing by remember { mutableStateOf(false) }
    val spacing by animateFloatAsState(
        targetValue = if (animateSpacing) 14f else 0f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "logo_spacing"
    )

    // Pulsing chevron arrow animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val arrowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_alpha"
    )

    LaunchedEffect(Unit) {
        animateSpacing = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            DaexLogo(size = 120.dp, ambient = true)
            Spacer(modifier = Modifier.height(32.dp))
            BasicText(
                text = "DAEX",
                style = DaexTheme.typography.h1.copy(
                    color = DaexTheme.colors.primary,
                    fontSize = 44.sp,
                    letterSpacing = spacing.sp
                )
            )
            BasicText(
                text = "DAEDALUS EXECUTION ENGINE",
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onSurface,
                    fontSize = 11.sp,
                    letterSpacing = 3.sp
                ),
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            // Pulsing visual indicator arrow/button to begin
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(DaexTheme.colors.primary.copy(alpha = 0.1f * arrowAlpha))
                    .border(1.dp, DaexTheme.colors.primary.copy(alpha = 0.35f * arrowAlpha), CircleShape)
                    .clickable { onNext() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = "→",
                    style = DaexTheme.typography.h2.copy(
                        color = DaexTheme.colors.primary,
                        fontSize = 24.sp
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            BasicText(
                text = "START CONFIGURATION",
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.primary.copy(alpha = 0.6f),
                    fontSize = 9.sp,
                    letterSpacing = 1.sp
                )
            )
        }
    }
}

// Slide 2: Philosophy and Core Principles with cascading entries
@Composable
private fun PhilosophySlide(onNext: () -> Unit) {
    var card1Visible by remember { mutableStateOf(false) }
    var card2Visible by remember { mutableStateOf(false) }
    var card3Visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(100)
        card1Visible = true
        delay(200)
        card2Visible = true
        delay(200)
        card3Visible = true
    }

    val card1Alpha by animateFloatAsState(
        targetValue = if (card1Visible) 1f else 0f,
        animationSpec = tween(500),
        label = "card1_alpha"
    )
    val card2Alpha by animateFloatAsState(
        targetValue = if (card2Visible) 1f else 0f,
        animationSpec = tween(500),
        label = "card2_alpha"
    )
    val card3Alpha by animateFloatAsState(
        targetValue = if (card3Visible) 1f else 0f,
        animationSpec = tween(500),
        label = "card3_alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = "CORE MISSION",
                style = DaexTheme.typography.h2.copy(
                    color = DaexTheme.colors.primary,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(32.dp))

            PhilosophyItem(
                title = "100% PRIVATE",
                description = "Neural model execution runs entirely inside your device sandbox. No chat logs or inputs are ever leaked to external cloud networks.",
                modifier = Modifier.graphicsLayer { alpha = card1Alpha }
            )
            
            Spacer(modifier = Modifier.height(20.dp))

            PhilosophyItem(
                title = "OFFLINE CAPABILITY",
                description = "DAEX functions with zero network connectivity. Perfect for high-privacy environments and guaranteed system access anywhere.",
                modifier = Modifier.graphicsLayer { alpha = card2Alpha }
            )
            
            Spacer(modifier = Modifier.height(20.dp))

            PhilosophyItem(
                title = "HYPER-ACCELERATED",
                description = "Engineered to map local AI model operations directly to your device CPU, GPU, and hardware Neural Processing Units (NPUs).",
                modifier = Modifier.graphicsLayer { alpha = card3Alpha }
            )
        }

        DaexButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = "INITIALIZE DIAGNOSTICS →",
                style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onPrimary)
            )
        }
    }
}

@Composable
private fun PhilosophyItem(title: String, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            BasicText(
                text = title,
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            )
            Spacer(modifier = Modifier.height(6.dp))
            BasicText(
                text = description,
                style = DaexTheme.typography.body2.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            )
        }
    }
}

// Slide 3: Live Hardware Capability Scan
@Composable
private fun DiagnosticsSlide(
    specs: DeviceSpecs?,
    isVisible: Boolean,
    onNext: () -> Unit
) {
    val consoleLines = remember { mutableStateListOf<String>() }
    var scanComplete by remember { mutableStateOf(false) }

    // Dynamic model chipset target scanning
    val recommendedEngine = remember(specs) {
        if (specs == null) return@remember "Gemma 4 (LiteRT)"
        val compatibleModels = ModelBank.generativeModels.filter { model ->
            model.targetHardware == null || isTargetHardwareCompatible(model.targetHardware, specs)
        }
        val hasNpu = compatibleModels.any { it.supportedBackends.contains(BackendType.NPU) }
        val activeNpu = compatibleModels.firstOrNull { it.supportedBackends.contains(BackendType.NPU) }
        val activeCpu = compatibleModels.firstOrNull { it.supportedBackends.contains(BackendType.CPU) }

        activeNpu?.let { "${it.familyName} (${it.sizeName}) Qualcomm NPU build" }
            ?: activeCpu?.let { "${it.familyName} (${it.sizeName}) CPU/GPU build" }
            ?: "Gemma 4 (LiteRT)"
    }

    LaunchedEffect(specs, isVisible) {
        if (!isVisible || specs == null) return@LaunchedEffect
        consoleLines.clear()
        scanComplete = false
        
        val lines = listOf(
            "Connecting hardware abstraction layer...",
            "Checking Host Manufacturer: ${specs.manufacturer}",
            "Detected Model: ${specs.model}",
            "Scanning CPU Architecture & Board Platform: ${specs.board}",
            "Reading RAM footprint... ${formatBytes(specs.totalRAM)} detected.",
            "Checking GPU rendering pipeline (Vulkan Level 12)... ${if (specs.hasVulkan) "SUPPORTED" else "UNSUPPORTED"}",
            "Scanning Qualcomm/Tensor NPU Hardware... ${if (specs.socModel.isNotEmpty()) specs.socModel else "Generic chip"}",
            "Calibrating system parameters for local execution...",
            "SUCCESS: Local engine execution capabilities optimized."
        )

        for (line in lines) {
            consoleLines.add(line)
            delay(350)
        }
        scanComplete = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            BasicText(
                text = "HARDWARE DIAGNOSTICS",
                style = DaexTheme.typography.h2.copy(
                    color = DaexTheme.colors.primary,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Console output board
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, DaexTheme.colors.primary.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(consoleLines.size) { index ->
                        BasicText(
                            text = "> " + consoleLines[index],
                            style = DaexTheme.typography.mono.copy(
                                color = if (consoleLines[index].startsWith("SUCCESS")) DaexTheme.colors.success else Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Spec recommendations
            AnimatedVisibility(visible = scanComplete) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DaexTheme.colors.primary.copy(alpha = 0.05f))
                        .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    BasicText(
                        text = "RECOMMENDED ENGINE: $recommendedEngine is optimal for this device configuration.",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.primary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        DaexButton(
            onClick = onNext,
            enabled = scanComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = if (scanComplete) "SELECT ENGINE →" else "DIAGNOSTICS IN PROGRESS...",
                style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onPrimary)
            )
        }
    }
}

// Slide 4: Engine Selection (Reusing the Collapsible Model Gallery Cards)
@Composable
private fun EngineSelectorSlide(
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    specs: DeviceSpecs?,
    onNext: () -> Unit
) {
    // Exclude background embedding models completely
    val compatibleModels = remember(specs) {
        ModelBank.generativeModels.filter { model ->
            model.targetHardware == null || isTargetHardwareCompatible(model.targetHardware, specs)
        }
    }

    // Identify recommended model dynamically
    val recommendedModel = remember(compatibleModels) {
        val activeNpu = compatibleModels.firstOrNull { it.supportedBackends.contains(BackendType.NPU) }
        val activeCpu = compatibleModels.firstOrNull { it.supportedBackends.contains(BackendType.CPU) }
        activeNpu ?: activeCpu ?: compatibleModels.firstOrNull()
    }

    // Group models by Provider, then by Family Key (familyId)
    val groupedModels = remember(compatibleModels) {
        compatibleModels.groupBy { it.provider }.mapValues { (_, providerModels) ->
            providerModels.groupBy { it.familyId }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            BasicText(
                text = "ENGINE SELECTION",
                style = DaexTheme.typography.h2.copy(
                    color = DaexTheme.colors.primary,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = "Pick and activate compatible model configurations.",
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Reused Collapsible Model Marketplace layout
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                groupedModels.forEach { (provider, families) ->
                    item {
                        OnboardingProviderHeader(provider = provider)
                    }
                    families.forEach { (_, variantsList) ->
                        item {
                            OnboardingFamilyCard(
                                variants = variantsList,
                                viewModel = viewModel,
                                modelManager = modelManager,
                                recommendedModel = recommendedModel,
                                onSelectAndNext = onNext
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingProviderHeader(provider: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        BasicText(
            text = provider.uppercase(),
            style = DaexTheme.typography.h2.copy(
                color = DaexTheme.colors.primary,
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(DaexTheme.colors.primary.copy(alpha = 0.15f))
        )
    }
}

@Composable
private fun OnboardingFamilyCard(
    variants: List<Model>,
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    recommendedModel: Model?,
    onSelectAndNext: () -> Unit
) {
    val sizes = remember(variants) { variants.map { it.sizeName }.distinct() }
    var selectedSize by remember(sizes) { mutableStateOf(sizes.firstOrNull() ?: "") }

    val sizeVariants = remember(variants, selectedSize) {
        variants.filter { it.sizeName == selectedSize }
    }
    
    var selectedVariantIndex by remember(sizeVariants) { mutableStateOf(0) }
    val activeIndex = if (selectedVariantIndex in sizeVariants.indices) selectedVariantIndex else 0
    val activeModel = sizeVariants.getOrNull(activeIndex) ?: variants.first()

    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    
    var isDownloaded by remember(activeModel) { mutableStateOf(false) }
    var isHardwareCapable by remember(activeModel) { mutableStateOf(true) }

    // Start with the card pre-expanded for onboarding ease
    var isExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(activeModel) {
        isDownloaded = modelManager.isModelDownloaded(activeModel)
        isHardwareCapable = modelManager.checkSpecSupport(activeModel).supported
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DaexTheme.colors.onSurface.copy(alpha = 0.02f))
            .border(
                width = 1.dp,
                color = DaexTheme.colors.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(16.dp)
            .animateContentSize()
    ) {
        Column {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val isRecommended = activeModel.id == recommendedModel?.id
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            text = activeModel.familyName,
                            style = DaexTheme.typography.body1.copy(
                                color = DaexTheme.colors.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        if (isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DaexTheme.colors.primary.copy(alpha = 0.15f))
                                    .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                BasicText(
                                    text = "RECOMMENDED",
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.primary,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = "${sizes.joinToString(" / ")} sizes available",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                            fontSize = 9.sp
                        )
                    )
                }

                BasicText(
                    text = if (isExpanded) "▲" else "▼",
                    style = DaexTheme.typography.mono.copy(
                        color = DaexTheme.colors.primary,
                        fontSize = 11.sp
                    )
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(DaexTheme.colors.onSurface.copy(alpha = 0.04f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Size Selector
                if (sizes.size > 1) {
                    BasicText(
                        text = "SIZE VARIANT:",
                        style = DaexTheme.typography.mono.copy(color = Color.White.copy(alpha = 0.3f), fontSize = 8.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sizes.forEach { size ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedSize == size) DaexTheme.colors.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        width = 1.dp,
                                        color = if (selectedSize == size) DaexTheme.colors.primary.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedSize = size }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                BasicText(
                                    text = size,
                                    style = DaexTheme.typography.mono.copy(
                                        color = if (selectedSize == size) DaexTheme.colors.primary else Color.White.copy(alpha = 0.4f),
                                        fontSize = 9.sp
                                    )
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Description
                BasicText(
                    text = activeModel.description,
                    style = DaexTheme.typography.body2.copy(
                        color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )

                // Build Targets Selector
                if (sizeVariants.size > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    BasicText(
                        text = "HARDWARE BUILD TARGET:",
                        style = DaexTheme.typography.mono.copy(color = Color.White.copy(alpha = 0.3f), fontSize = 8.sp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        sizeVariants.forEachIndexed { idx, variant ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selectedVariantIndex == idx) DaexTheme.colors.primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
                                    .border(
                                        width = 1.dp,
                                        color = if (selectedVariantIndex == idx) DaexTheme.colors.primary.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable { selectedVariantIndex = idx }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                BasicText(
                                    text = variant.variantName,
                                    style = DaexTheme.typography.mono.copy(
                                        color = if (selectedVariantIndex == idx) DaexTheme.colors.primary else Color.White.copy(alpha = 0.4f),
                                        fontSize = 8.sp
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Stats and Action Download Button
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OnboardingMarketTag(label = formatBytes(activeModel.size))
                        OnboardingMarketTag(label = "${formatBytes(activeModel.requiredRAM)} RAM")
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isHardwareCapable) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                            .clickable(enabled = isHardwareCapable) {
                                if (!isDownloaded) {
                                    viewModel.downloadModel(activeModel)
                                }
                                onSelectAndNext()
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        BasicText(
                            text = if (isDownloaded) "ACTIVATE" else "DOWNLOAD",
                            style = DaexTheme.typography.mono.copy(
                                color = if (isHardwareCapable) DaexTheme.colors.onPrimary else DaexTheme.colors.onSurface.copy(alpha = 0.2f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingMarketTag(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        BasicText(
            text = label,
            style = DaexTheme.typography.mono.copy(
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 9.sp
            )
        )
    }
}

// Slide 5: Initializing Core Memory & Tutorials Pager
@Composable
private fun TutorialSlide(
    viewModel: DaexInferenceViewModel,
    daexPreferences: DaexPreferences,
    onContinue: () -> Unit
) {
    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var tutorialPageIndex by remember { mutableStateOf(0) }
    
    val tutorialCards = listOf(
        Pair("CHAIN OF THOUGHT REASONING", "DAEX displays real-time execution thoughts. You can see the neural model processing concepts inside the <|think|> channel before outputting the response."),
        Pair("LOCAL RAG KNOWLEDGE BASES", "Vectorize local PDF or text files offline. The local Nomic embedder references context from your local document index during live conversation."),
        Pair("SECURE SYSTEM CALLS", "Local models can secure execution triggers on your device. Launch applications or query disk specifications via secure sandbox tools.")
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(4000)
            tutorialPageIndex = (tutorialPageIndex + 1) % tutorialCards.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = "ENGINE INITIALIZATION",
                style = DaexTheme.typography.h2.copy(
                    color = DaexTheme.colors.primary,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Live download progress card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        BasicText(
                            text = if (modelStatus == ModelStatus.DOWNLOADING) "DOWNLOADING LIBS..." else "INITIALIZING SYSTEMS...",
                            style = DaexTheme.typography.mono.copy(color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        )
                        BasicText(
                            text = "$downloadProgress%",
                            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
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

            Spacer(modifier = Modifier.height(32.dp))

            // Auto-rotating tutorial card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(DaexTheme.colors.primary.copy(alpha = 0.03f))
                    .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                    .padding(20.dp)
                    .animateContentSize()
            ) {
                val currentTutorial = tutorialCards[tutorialPageIndex]
                Column {
                    BasicText(
                        text = currentTutorial.first,
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = currentTutorial.second,
                        style = DaexTheme.typography.body2.copy(
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 17.sp
                        )
                    )

                    TutorialVisualPreview(pageIndex = tutorialPageIndex)
                }
            }
        }

        DaexButton(
            onClick = {
                coroutineScope.launch {
                    daexPreferences.completeLandingPage()
                    onContinue()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = "CONTINUE TO WORKSPACE",
                style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onPrimary)
            )
        }
    }
}

@Composable
private fun TutorialVisualPreview(pageIndex: Int) {
    Spacer(modifier = Modifier.height(14.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
            .border(0.5.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        when (pageIndex) {
            0 -> {
                // Mock Chat Thinking UI replicating MessageLine's exact block layout
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Thinking header & box (Expanded)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                            .border(1.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(DaexTheme.colors.primary)
                                    )
                                    BasicText(
                                        text = "THOUGHT PROCESS",
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.primary.copy(alpha = 0.8f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.sp
                                        )
                                    )
                                }
                                BasicText(
                                    text = "▲",
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            BasicText(
                                text = "Formulating local security policy response...\nChecking privacy sandbox compliance...",
                                style = DaexTheme.typography.body1.copy(
                                    color = DaexTheme.colors.onSurface.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                    
                    // Final Response
                    Column {
                        BasicText(
                            text = "ICARUS",
                            style = DaexTheme.typography.mono.copy(
                                color = DaexTheme.colors.primary.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        BasicText(
                            text = "Your private information never leaves this device sandbox.",
                            style = DaexTheme.typography.body2.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }
            }
            1 -> {
                // Mock Document Indexing UI
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(DaexTheme.colors.primary.copy(alpha = 0.05f))
                            .border(0.5.dp, DaexTheme.colors.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column {
                                BasicText(
                                    text = "privacy_policy.pdf",
                                    style = DaexTheme.typography.mono.copy(
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                BasicText(
                                    text = "42.5 KB • PDF Document",
                                    style = DaexTheme.typography.mono.copy(
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 8.sp
                                    )
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(DaexTheme.colors.success.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            BasicText(
                                text = "INDEXED",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.success,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }

                    // Vector DB Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = "Vector Model:",
                            style = DaexTheme.typography.mono.copy(color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                        )
                        BasicText(
                            text = "Nomic Embed Text 1.5",
                            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
            2 -> {
                // Mock System Call Tool Approval UI
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.02f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(DaexTheme.colors.warning)
                                )
                                BasicText(
                                    text = "TOOL CALL REQUEST",
                                    style = DaexTheme.typography.mono.copy(
                                        color = DaexTheme.colors.warning,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            BasicText(
                                text = "com.daex.system.disk_specs",
                                style = DaexTheme.typography.mono.copy(
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 8.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        BasicText(
                            text = "The local engine wants to read disk storage specs to compute memory buffers.",
                            style = DaexTheme.typography.body2.copy(
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 9.sp,
                                lineHeight = 13.sp
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "DENY",
                                style = DaexTheme.typography.mono.copy(color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(DaexTheme.colors.primary.copy(alpha = 0.2f))
                                .border(0.5.dp, DaexTheme.colors.primary, RoundedCornerShape(6.dp))
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = "APPROVE",
                                style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isTargetHardwareCompatible(targetHardware: String, specs: DeviceSpecs?): Boolean {
    if (specs == null) return false
    val target = targetHardware.lowercase().trim()

    val soc = specs.socModel.lowercase().trim()
    if (soc.isNotEmpty()) {
        if (soc.contains(target) || target.contains(soc)) return true
    }

    val board = specs.board.lowercase().trim()
    val hardware = specs.hardware.lowercase().trim()
    val model = specs.model.lowercase().trim()

    return when (target) {
        "sm8850" -> {
            board.contains("sm8850") || board.contains("cliffs") || hardware.contains("sm8850") || model.contains("s948")
        }
        "sm8750" -> {
            board.contains("sm8750") || board.contains("sun") || hardware.contains("sm8750") || model.contains("s938")
        }
        "sm8650" -> {
            board.contains("sm8650") || board.contains("pineapple") || hardware.contains("sm8650") || model.contains("s928")
        }
        "tensor g5" -> {
            board.contains("g5") || hardware.contains("g5") || (hardware.contains("tensor") && board.contains("laguna"))
        }
        else -> {
            board.contains(target) || hardware.contains(target) || model.contains(target)
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    val k = 1024.0
    val sizes = arrayOf("B", "KB", "MB", "GB")
    val i = kotlin.math.floor(kotlin.math.log(bytes.toDouble(), k)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(k, i.toDouble()), sizes[i])
}

@Composable
private fun IcarusShowcaseSlide(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = "MEET ICARUS",
                style = DaexTheme.typography.h2.copy(
                    color = DaexTheme.colors.primary,
                    letterSpacing = 2.sp,
                    fontSize = 16.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicText(
                text = "On-device cognitive execution agent.",
                style = DaexTheme.typography.mono.copy(
                    color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 1. Sandbox Tool Call Mock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DaexTheme.colors.warning)
                            )
                            BasicText(
                                text = "SANDBOX TOOLS",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.warning,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        BasicText(
                            text = "ACTIVE",
                            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 8.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicText(
                        text = "Icarus executes secure functions on your device environment. Battery metrics, local storage specs, or launching system applications are processed locally.",
                        style = DaexTheme.typography.body2.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Persistent Memory Mock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DaexTheme.colors.primary)
                            )
                            BasicText(
                                text = "CORE MEMORY BANK",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.primary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        BasicText(
                            text = "PERSISTENT",
                            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 8.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicText(
                        text = "Your facts, topics of interest, and system settings are compiled silently into a local file after conversations. Zero data transfers occur external to the device.",
                        style = DaexTheme.typography.body2.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. RAG/Vector Engine Mock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.02f))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(DaexTheme.colors.success)
                            )
                            BasicText(
                                text = "OFFLINE DOCUMENT RAG",
                                style = DaexTheme.typography.mono.copy(
                                    color = DaexTheme.colors.success,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        BasicText(
                            text = "SECURE",
                            style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.primary, fontSize = 8.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    BasicText(
                        text = "Chunk, embed, and search your local PDF and text documents offline. References are injected directly into LLM prompts without cloud server processing.",
                        style = DaexTheme.typography.body2.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    )
                }
            }
        }

        DaexButton(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth()
        ) {
            BasicText(
                text = "CONTINUE TO CONFIGURATION →",
                style = DaexTheme.typography.mono.copy(color = DaexTheme.colors.onPrimary)
            )
        }
    }
}
