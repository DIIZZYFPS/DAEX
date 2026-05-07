package com.daex.android.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daex.android.ui.components.DaexButton
import com.daex.android.ui.theme.DaexTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun MemoryEditorModal(
    visible: Boolean,
    onClose: () -> Unit,
    initialContent: String,
    onSave: (String) -> Unit
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

        var editableContent by remember(initialContent) { mutableStateOf(initialContent) }

        val springSpec = spring<Float>(dampingRatio = 0.75f, stiffness = 300f)
        val timingSpec = tween<Float>(durationMillis = 260, easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f))

        LaunchedEffect(visible) {
            if (visible) {
                editableContent = initialContent
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
                    .fillMaxHeight(0.9f)
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
                Column(modifier = Modifier.fillMaxSize()) {
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
                            text = "Global Core Memory",
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    BasicText(
                        text = "Edit the raw markdown. Changes are injected into Icarus's system prompt.",
                        style = DaexTheme.typography.body2.copy(color = DaexTheme.colors.onSurface.copy(alpha = 0.6f))
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Editor Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(DaexTheme.colors.background)
                            .border(1.dp, DaexTheme.colors.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        BasicTextField(
                            value = editableContent,
                            onValueChange = { editableContent = it },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = TextStyle(
                                color = DaexTheme.colors.onBackground,
                                fontSize = 14.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                lineHeight = 20.sp
                            ),
                            cursorBrush = SolidColor(DaexTheme.colors.primary)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DaexButton(
                        onClick = {
                            onSave(editableContent)
                            dismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        backgroundColor = DaexTheme.colors.primary
                    ) {
                        BasicText(
                            text = "SAVE MEMORY",
                            style = DaexTheme.typography.body1.copy(
                                color = DaexTheme.colors.onPrimary,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
