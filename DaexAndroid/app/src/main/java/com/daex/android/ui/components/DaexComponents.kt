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
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "button_scale")
    val alpha by animateFloatAsState(if (!enabled) 0.5f else 1f, label = "button_alpha")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
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
    textStyle: TextStyle = DaexTheme.typography.body1.copy(color = DaexTheme.colors.onBackground)
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(DaexTheme.colors.surface)
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
    
    Box(
        modifier = modifier
            .width(52.dp)
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (checked) checkedColor.copy(alpha = 0.4f) else Color(0x26FFFFFF))
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
                .background(if (checked) checkedColor else uncheckedColor)
        )
    }
}
