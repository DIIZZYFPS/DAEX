package com.daex.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.daex.android.ui.theme.DaexTheme
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset

@Composable
fun Modifier.liquidGlass(
    radius: Float = 20f,
    alpha: Float = 0.8f,
    color: Color = Color.Black
): Modifier {
    return this.then(
        Modifier
            .graphicsLayer {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(radius, radius, android.graphics.Shader.TileMode.DECAL)
                        .asComposeRenderEffect()
                }
            }
            .background(color.copy(alpha = alpha))
    )
}

@Composable
fun DaexButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = DaexTheme.colors.primary,
    useDefaultPadding: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "button_scale")
    val alpha by animateFloatAsState(if (!enabled) 0.5f else 1f, label = "button_alpha")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor.copy(alpha = alpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .let { 
                if (useDefaultPadding) it.padding(horizontal = 16.dp, vertical = 12.dp) else it 
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun DaexTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    textStyle: TextStyle = DaexTheme.typography.body1.copy(color = DaexTheme.colors.onBackground),
    backgroundColor: Color = DaexTheme.colors.surface
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .padding(12.dp),
        textStyle = textStyle,
        enabled = enabled,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(DaexTheme.colors.primary),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    androidx.compose.foundation.text.BasicText(
                        text = placeholder,
                        style = textStyle.copy(color = textStyle.color.copy(alpha = 0.4f))
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun DaexSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    checkedColor: Color = DaexTheme.colors.primary,
    uncheckedColor: Color = Color(0xFFFFFFFF)
) {
    val thumbOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        label = "switch_thumb"
    )
    
    val isDark = DaexTheme.colors.onBackground == Color(0xFFFFFFFF)
    val actualUncheckedColor = if (uncheckedColor == Color(0xFFFFFFFF)) {
        if (isDark) Color(0xFFFFFFFF) else DaexTheme.colors.onSurface.copy(alpha = 0.4f)
    } else {
        uncheckedColor
    }
    
    val trackColor = if (checked) checkedColor.copy(alpha = 0.4f) else DaexTheme.colors.onSurface.copy(alpha = 0.15f)

    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange?.invoke(!checked) }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) checkedColor else actualUncheckedColor)
        )
    }
}

@Composable
fun DaexSendIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.45f)
            lineTo(w * 0.5f, h * 0.2f)
            lineTo(w * 0.75f, h * 0.45f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.2f),
            end = Offset(w * 0.5f, h * 0.8f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun DaexMicIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Mic capsule
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.35f, h * 0.15f),
            size = Size(w * 0.3f, h * 0.45f),
            cornerRadius = CornerRadius(w * 0.15f, w * 0.15f),
            style = Stroke(width = 2.dp.toPx())
        )
        // Mic cradle
        val cradlePath = Path().apply {
            moveTo(w * 0.2f, h * 0.4f)
            quadraticTo(w * 0.5f, h * 0.75f, w * 0.8f, h * 0.4f)
        }
        drawPath(
            path = cradlePath,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        // Stem
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.65f),
            end = Offset(w * 0.5f, h * 0.85f),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun DaexStopIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.28f),
            size = Size(w * 0.44f, h * 0.44f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
    }
}
