package com.daex.android.ui

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daex.android.ui.theme.DaexTheme

// Compares dot-separated numeric version strings segment by segment (e.g. "0.4.0" > "0.3.5").
// Non-numeric or missing segments are treated as 0, so uneven lengths compare sensibly.
fun compareSemVer(a: String, b: String): Int {
    val partsA = a.split(".").map { it.toIntOrNull() ?: 0 }
    val partsB = b.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(partsA.size, partsB.size)) {
        val x = partsA.getOrElse(i) { 0 }
        val y = partsB.getOrElse(i) { 0 }
        if (x != y) return x.compareTo(y)
    }
    return 0
}

// Keeps only "## [version](...)" sections newer than sinceVersion, plus the top "# " title line.
fun filterChangelogSince(fullText: String, sinceVersion: String): String {
    val result = StringBuilder()
    var includeCurrentSection = true
    for (line in fullText.split("\n")) {
        val trimmed = line.trim()
        if (trimmed.startsWith("## ")) {
            val version = if (trimmed.contains("[") && trimmed.contains("]")) {
                trimmed.substringAfter("[").substringBefore("]")
            } else {
                trimmed.substring(3).trim()
            }
            includeCurrentSection = compareSemVer(version, sinceVersion) > 0
        }
        if (trimmed.startsWith("# ") || includeCurrentSection) {
            result.append(line).append("\n")
        }
    }
    return result.toString()
}

@Composable
fun ChangelogModal(
    visible: Boolean,
    onClose: () -> Unit,
    sinceVersion: String? = null
) {
    if (!visible) return

    val context = LocalContext.current
    var changelogText by remember { mutableStateOf("Loading changelog...") }

    LaunchedEffect(visible) {
        if (visible) {
            changelogText = try {
                val fullText = context.assets.open("CHANGELOG.md").bufferedReader().use { it.readText() }
                if (sinceVersion != null) filterChangelogSince(fullText, sinceVersion) else fullText
            } catch (e: Exception) {
                "Failed to read changelog: ${e.message}"
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onClose
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DaexTheme.colors.surface)
                    .border(0.5.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            text = if (sinceVersion != null) "WHAT'S NEW" else "CHANGELOG",
                            style = DaexTheme.typography.h2.copy(
                                color = DaexTheme.colors.primary,
                                letterSpacing = 1.5.sp
                            )
                        )
                        BasicText(
                            text = "✕",
                            style = DaexTheme.typography.h2.copy(color = DaexTheme.colors.onBackground.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .clickable { onClose() }
                                .padding(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Content
                    val lines = changelogText.split("\n")
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(lines) { line ->
                            val trimmed = line.trim()
                            when {
                                trimmed.startsWith("# ") -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicText(
                                        text = trimmed.substring(2).uppercase(),
                                        style = DaexTheme.typography.h1.copy(
                                            color = DaexTheme.colors.onBackground,
                                            fontSize = 18.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                trimmed.startsWith("## ") -> {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    // Parse Markdown link text: ## [0.2.0](link) -> 0.2.0
                                    val headerText = if (trimmed.contains("[") && trimmed.contains("]")) {
                                        trimmed.substringAfter("[").substringBefore("]")
                                    } else {
                                        trimmed.substring(3)
                                    }
                                    BasicText(
                                        text = headerText,
                                        style = DaexTheme.typography.h2.copy(
                                            color = DaexTheme.colors.primary,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                trimmed.startsWith("### ") -> {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicText(
                                        text = trimmed.substring(4).uppercase(),
                                        style = DaexTheme.typography.mono.copy(
                                            color = DaexTheme.colors.primary.copy(alpha = 0.8f),
                                            fontSize = 12.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                        )
                                    )
                                }
                                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                                    val text = trimmed.substring(2)
                                    val isSubItem = line.startsWith("  ") || line.startsWith("\t")
                                    Row(
                                        modifier = Modifier.padding(start = if (isSubItem) 24.dp else 12.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        BasicText(
                                            text = if (isSubItem) "◦ " else "▪ ",
                                            style = DaexTheme.typography.mono.copy(
                                                color = DaexTheme.colors.primary.copy(alpha = 0.6f),
                                                fontSize = 10.sp
                                            ),
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        BasicText(
                                            text = text,
                                            style = DaexTheme.typography.body2.copy(
                                                color = DaexTheme.colors.onSurface
                                            )
                                        )
                                    }
                                }
                                trimmed.isBlank() -> {
                                    // Spacer
                                }
                                else -> {
                                    BasicText(
                                        text = trimmed,
                                        style = DaexTheme.typography.body2.copy(
                                            color = DaexTheme.colors.onSurface.copy(alpha = 0.8f)
                                        ),
                                        modifier = Modifier.padding(vertical = 2.dp)
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
