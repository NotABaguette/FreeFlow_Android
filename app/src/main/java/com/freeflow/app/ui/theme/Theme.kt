package com.freeflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// FreeFlow brand colors
val FreeFlowBlue = Color(0xFF2196F3)
val FreeFlowBlueDark = Color(0xFF1565C0)
val FreeFlowBlueLight = Color(0xFF64B5F6)
val FreeFlowTeal = Color(0xFF00BCD4)
val FreeFlowGreen = Color(0xFF4CAF50)
val FreeFlowRed = Color(0xFFF44336)
val FreeFlowOrange = Color(0xFFFF9800)

// Dark theme colors
val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkSurfaceVariant = Color(0xFF21262D)
val DarkOnBackground = Color(0xFFC9D1D9)
val DarkOnSurface = Color(0xFFC9D1D9)
val DarkOnSurfaceVariant = Color(0xFF8B949E)

// Chat bubble colors
val SentBubbleColor = Color(0xFF1565C0)
val ReceivedBubbleColor = Color(0xFF2D333B)
val SentTextColor = Color(0xFFFFFFFF)
val ReceivedTextColor = Color(0xFFC9D1D9)

val MonoFontFamily = FontFamily.Monospace

private val DarkColorScheme = darkColorScheme(
    primary = FreeFlowBlue,
    onPrimary = Color.White,
    primaryContainer = FreeFlowBlueDark,
    onPrimaryContainer = FreeFlowBlueLight,
    secondary = FreeFlowTeal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = FreeFlowTeal,
    tertiary = FreeFlowGreen,
    error = FreeFlowRed,
    onError = Color.White,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = Color(0xFF30363D)
)

val FreeFlowTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp
    )
)

@Composable
fun FreeFlowTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = DarkBackground.toArgb()
            window.navigationBarColor = DarkSurface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FreeFlowTypography,
        content = content
    )
}
