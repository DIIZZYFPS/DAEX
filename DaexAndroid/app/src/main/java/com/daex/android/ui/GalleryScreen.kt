package com.daex.android.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import com.daex.android.services.BackendType
import com.daex.android.ui.components.DaexTextField
import com.daex.android.ui.theme.DaexTheme

enum class SortOrder {
    NAME, SIZE
}

@Composable
fun GalleryScreen(
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedBackendFilter by remember { mutableStateOf<BackendType?>(null) }
    var sortBy by remember { mutableStateOf(SortOrder.NAME) }

    val currentModel by viewModel.currentModel.collectAsState()
    val deviceSpecs = viewModel.deviceSpecs

    // Filter and Sort Models
    val filteredGenerativeModels = remember(searchQuery, selectedBackendFilter, sortBy, deviceSpecs) {
        ModelBank.generativeModels.filter { model ->
            val matchesSearch = model.name.contains(searchQuery, ignoreCase = true) ||
                    model.familyName.contains(searchQuery, ignoreCase = true) ||
                    model.provider.contains(searchQuery, ignoreCase = true) ||
                    model.description.contains(searchQuery, ignoreCase = true)
            
            val matchesBackend = selectedBackendFilter == null || model.supportedBackends.contains(selectedBackendFilter)
            val matchesHardware = model.targetHardware == null || isTargetHardwareCompatible(model.targetHardware, deviceSpecs)

            matchesSearch && matchesBackend && matchesHardware
        }.sortedWith { a, b ->
            when (sortBy) {
                SortOrder.NAME -> a.familyName.compareTo(b.familyName, ignoreCase = true)
                SortOrder.SIZE -> a.size.compareTo(b.size)
            }
        }
    }

    // Group models by Provider, then by Family Key (familyId)
    val groupedModels = remember(filteredGenerativeModels) {
        filteredGenerativeModels.groupBy { it.provider }.mapValues { (_, providerModels) ->
            providerModels.groupBy { it.familyId }
        }
    }

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
                .padding(horizontal = 24.dp, vertical = 16.dp),
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

        // Search and Filter Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DaexTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = "Search engines, providers...",
                modifier = Modifier.fillMaxWidth()
            )

            // Filter and Sort Chips Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Backend Filter Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        label = "ALL",
                        selected = selectedBackendFilter == null,
                        onClick = { selectedBackendFilter = null }
                    )
                    FilterChip(
                        label = "CPU",
                        selected = selectedBackendFilter == BackendType.CPU,
                        onClick = { selectedBackendFilter = BackendType.CPU }
                    )
                    FilterChip(
                        label = "GPU",
                        selected = selectedBackendFilter == BackendType.GPU,
                        onClick = { selectedBackendFilter = BackendType.GPU }
                    )
                    FilterChip(
                        label = "NPU",
                        selected = selectedBackendFilter == BackendType.NPU,
                        onClick = { selectedBackendFilter = BackendType.NPU }
                    )
                }

                // Sort Dropdown/Toggle
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText(
                        text = "SORT:",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.onBackground.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    )
                    SortChip(
                        label = "NAME",
                        selected = sortBy == SortOrder.NAME,
                        onClick = { sortBy = SortOrder.NAME }
                    )
                    SortChip(
                        label = "SIZE",
                        selected = sortBy == SortOrder.SIZE,
                        onClick = { sortBy = SortOrder.SIZE }
                    )
                }
            }
        }

        // Marketplace List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            if (groupedModels.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "No compatible engines found",
                            style = DaexTheme.typography.body2.copy(
                                color = DaexTheme.colors.onBackground.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            } else {
                groupedModels.forEach { (provider, families) ->
                    item {
                        ProviderHeader(provider = provider)
                    }
                    
                    families.forEach { (_, variantsList) ->
                        item {
                            FamilyModelCard(
                                variants = variantsList,
                                viewModel = viewModel,
                                modelManager = modelManager,
                                currentModel = currentModel
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderHeader(provider: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        BasicText(
            text = provider.uppercase(),
            style = DaexTheme.typography.h2.copy(
                color = DaexTheme.colors.primary,
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DaexTheme.colors.primary.copy(alpha = 0.2f))
        )
    }
}

@Composable
private fun FamilyModelCard(
    variants: List<Model>,
    viewModel: DaexInferenceViewModel,
    modelManager: ModelManager,
    currentModel: Model?
) {
    // Unique Size names in this family (e.g. "E2B", "E4B")
    val sizes = remember(variants) { variants.map { it.sizeName }.distinct() }
    var selectedSize by remember(sizes) { mutableStateOf(sizes.firstOrNull() ?: "") }

    // Filter variants belonging to the selected size
    val sizeVariants = remember(variants, selectedSize) {
        variants.filter { it.sizeName == selectedSize }
    }
    
    // Pick the selected hardware variant
    var selectedVariantIndex by remember(sizeVariants) { mutableStateOf(0) }
    
    val activeIndex = if (selectedVariantIndex in sizeVariants.indices) selectedVariantIndex else 0
    val activeModel = sizeVariants.getOrNull(activeIndex) ?: variants.first()

    val modelStatus by viewModel.modelStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    
    val isCurrent = currentModel?.id == activeModel.id
    val isThisDownloading = isCurrent && modelStatus == ModelStatus.DOWNLOADING

    var isDownloaded by remember(activeModel) { mutableStateOf(false) }
    var isHardwareCapable by remember(activeModel) { mutableStateOf(true) }
    var isAnyVariantDownloaded by remember(variants) { mutableStateOf(false) }

    // Collapsible State (Default expanded if this family is currently selected/active)
    var isExpanded by remember(variants, currentModel) {
        mutableStateOf(variants.any { currentModel?.id == it.id })
    }

    LaunchedEffect(activeModel) {
        isDownloaded = modelManager.isModelDownloaded(activeModel)
        isHardwareCapable = modelManager.checkSpecSupport(activeModel).supported
    }

    LaunchedEffect(variants, modelStatus) {
        isAnyVariantDownloaded = variants.any { modelManager.isModelDownloaded(it) }
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
            .clickable { isExpanded = !isExpanded }
            .padding(20.dp)
            .animateContentSize()
    ) {
        Column {
            // Header Row (Visible when collapsed & expanded)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    BasicText(
                        text = activeModel.familyName,
                        style = DaexTheme.typography.body1.copy(
                            color = DaexTheme.colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    BasicText(
                        text = "${sizes.joinToString(" / ")} sizes available",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isAnyVariantDownloaded) {
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
                    
                    BasicText(
                        text = if (isExpanded) "▲" else "▼",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Expanded content section
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DaexTheme.colors.onSurface.copy(alpha = 0.05f))
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Size Selector (Only show if multiple sizes exist)
                if (sizes.size > 1) {
                    BasicText(
                        text = "SELECT MODEL SIZE:",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.3f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizes.forEach { size ->
                            SizeSelectChip(
                                label = size,
                                selected = selectedSize == size,
                                onClick = { selectedSize = size }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Description
                BasicText(
                    text = activeModel.description,
                    style = DaexTheme.typography.body2.copy(
                        color = DaexTheme.colors.onSurface.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Compatibility Status Indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isHardwareCapable) DaexTheme.colors.success else Color.Red)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    BasicText(
                        text = if (isHardwareCapable) "COMPATIBLE WITH THIS DEVICE" else "INCOMPATIBLE WITH HARDWARE",
                        style = DaexTheme.typography.mono.copy(
                            color = if (isHardwareCapable) DaexTheme.colors.success.copy(alpha = 0.6f) else Color.Red.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                // Variants Target Switcher (Only show if multiple targets exist for the selected size)
                if (sizeVariants.size > 1) {
                    Spacer(modifier = Modifier.height(16.dp))
                    BasicText(
                        text = "TARGET HARDWARE BUILD:",
                        style = DaexTheme.typography.mono.copy(
                            color = DaexTheme.colors.onSurface.copy(alpha = 0.3f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizeVariants.forEachIndexed { idx, variant ->
                            VariantSelectChip(
                                label = variant.variantName,
                                selected = selectedVariantIndex == idx,
                                onClick = { selectedVariantIndex = idx }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action controls and Tag details
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = false) {},
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarketTag(label = modelManager.formatBytes(activeModel.size))
                        MarketTag(label = "${modelManager.formatBytes(activeModel.requiredRAM)} RAM Req.")
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
                                .clickable(enabled = isHardwareCapable) { viewModel.loadModel(activeModel) }
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
                                .clickable { viewModel.loadModel(activeModel) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            BasicText(
                                text = if (isCurrent) "RE-INITIALIZE" else "DEPLOY",
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
}

@Composable
private fun SizeSelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DaexTheme.colors.primary.copy(alpha = 0.15f) else DaexTheme.colors.onSurface.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = if (selected) DaexTheme.colors.primary.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = label,
            style = DaexTheme.typography.mono.copy(
                color = if (selected) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun VariantSelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) DaexTheme.colors.primary.copy(alpha = 0.15f) else DaexTheme.colors.onSurface.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = if (selected) DaexTheme.colors.primary.copy(alpha = 0.6f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        BasicText(
            text = label.uppercase(),
            style = DaexTheme.typography.mono.copy(
                color = if (selected) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.6f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DaexTheme.colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) DaexTheme.colors.primary.copy(alpha = 0.4f) else DaexTheme.colors.onSurface.copy(alpha = 0.1f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        BasicText(
            text = label,
            style = DaexTheme.typography.mono.copy(
                color = if (selected) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.4f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun SortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    BasicText(
        text = label,
        style = DaexTheme.typography.mono.copy(
            color = if (selected) DaexTheme.colors.primary else DaexTheme.colors.onSurface.copy(alpha = 0.3f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        ),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    )
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

private fun isTargetHardwareCompatible(targetHardware: String, specs: com.daex.android.services.DeviceSpecs?): Boolean {
    if (specs == null) return false
    val target = targetHardware.lowercase().trim()

    // 1. Primary Check: Direct SoC Model comparison (Android 12+)
    val soc = specs.socModel.lowercase().trim()
    if (soc.isNotEmpty()) {
        if (soc.contains(target) || target.contains(soc)) return true
    }

    // 2. Fallback Check: Model, Board, and Hardware heuristics
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
