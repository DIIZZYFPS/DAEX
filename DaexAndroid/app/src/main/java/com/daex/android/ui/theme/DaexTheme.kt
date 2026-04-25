package com.daex.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class DaexColors(
    val primary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onPrimary: Color,
    val error: Color,
    val warning: Color,
    val success: Color
)

@Immutable
data class DaexTypography(
    val h1: TextStyle,
    val h2: TextStyle,
    val body1: TextStyle,
    val body2: TextStyle,
    val caption: TextStyle,
    val mono: TextStyle
)

val LocalDaexColors = staticCompositionLocalOf {
    DaexColors(
        primary = Color.Unspecified,
        background = Color.Unspecified,
        surface = Color.Unspecified,
        surfaceVariant = Color.Unspecified,
        onBackground = Color.Unspecified,
        onSurface = Color.Unspecified,
        onPrimary = Color.Unspecified,
        error = Color.Unspecified,
        warning = Color.Unspecified,
        success = Color.Unspecified
    )
}

val LocalDaexTypography = staticCompositionLocalOf {
    DaexTypography(
        h1 = TextStyle.Default,
        h2 = TextStyle.Default,
        body1 = TextStyle.Default,
        body2 = TextStyle.Default,
        caption = TextStyle.Default,
        mono = TextStyle.Default
    )
}

// OLED Black Theme
private val defaultDaexColors = DaexColors(
    primary = Color(0xFF00FFFF), // Hacker Cyan
    background = Color(0xFF000000), // OLED Black
    surface = Color(0xFF0D0D0D), // Dark Gray
    surfaceVariant = Color(0x2600FFFF), // Cyan 15% opacity
    onBackground = Color(0xFFFFFFFF),
    onSurface = Color(0xFFE2E8F0),
    onPrimary = Color(0xFF000000),
    error = Color(0xFFEF4444),
    warning = Color(0xFFF59E0B),
    success = Color(0xFF4ADE80)
)

private val defaultDaexTypography = DaexTypography(
    h1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 2.sp
    ),
    h2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        letterSpacing = 1.sp
    ),
    body1 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    body2 = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    )
)

object DaexTheme {
    val colors: DaexColors
        @Composable
        get() = LocalDaexColors.current
    val typography: DaexTypography
        @Composable
        get() = LocalDaexTypography.current
}

@Composable
fun DaexAppTheme(
    content: @Composable () -> Unit
) {
    // We force the dark OLED theme regardless of system setting for Daex
    val colors = defaultDaexColors
    val typography = defaultDaexTypography

    val materialColors = darkColorScheme(
        primary = colors.primary,
        background = colors.background,
        surface = colors.surface,
        onPrimary = colors.onPrimary,
        onBackground = Color.White,
        onSurface = Color.White,
        error = colors.error
    )

    CompositionLocalProvider(
        LocalDaexColors provides colors,
        LocalDaexTypography provides typography
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
