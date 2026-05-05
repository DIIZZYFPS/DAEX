package com.daex.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
    primaryColor: Color = Color(0xFF00FFFF),
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = if (isDark) {
        DaexColors(
            primary = primaryColor,
            background = Color(0xFF000000),
            surface = Color(0xFF0D0D0D),
            surfaceVariant = primaryColor.copy(alpha = 0.15f),
            onBackground = Color(0xFFFFFFFF),
            onSurface = Color(0xFFE2E8F0),
            onPrimary = Color(0xFF000000),
            error = Color(0xFFEF4444),
            warning = Color(0xFFF59E0B),
            success = Color(0xFF4ADE80)
        )
    } else {
        DaexColors(
            primary = primaryColor,
            background = Color(0xFFF8FAFC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = primaryColor.copy(alpha = 0.1f),
            onBackground = Color(0xFF0F172A),
            onSurface = Color(0xFF334155),
            onPrimary = Color(0xFFFFFFFF),
            error = Color(0xFFDC2626),
            warning = Color(0xFFD97706),
            success = Color(0xFF16A34A)
        )
    }

    val typography = defaultDaexTypography

    val materialColors = if (isDark) {
        darkColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = colors.onPrimary,
            onBackground = Color.White,
            onSurface = Color.White,
            error = colors.error
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            background = colors.background,
            surface = colors.surface,
            onPrimary = colors.onPrimary,
            onBackground = Color.Black,
            onSurface = Color.Black,
            error = colors.error
        )
    }

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
