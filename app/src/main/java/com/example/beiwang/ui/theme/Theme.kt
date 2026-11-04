package com.example.beiwang.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue100,
    onPrimaryContainer = Blue900,
    inversePrimary = Blue300,

    secondary = Synced,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F2E4),
    onSecondaryContainer = Color(0xFF0E5136),

    tertiary = Unsynced,
    onTertiary = Color(0xFF3E2A00),
    tertiaryContainer = Color(0xFFFFE8C2),
    onTertiaryContainer = Color(0xFF5C4300),

    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    inverseSurface = Color(0xFF2E3037),
    inverseOnSurface = Color(0xFFF0F0F4),

    outline = Divider,
    outlineVariant = Color(0xFFE8EAEF),
    scrim = Color(0xFF000000),

    surfaceBright = Surface,
    surfaceDim = Color(0xFFDBDCE0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1F3F7),
    surfaceContainerHigh = Color(0xFFECEEF3),
    surfaceContainerHighest = Color(0xFFE7E9EF)
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue300,
    onPrimary = Blue900,
    primaryContainer = Color(0xFF33499F),
    onPrimaryContainer = Blue100,
    inversePrimary = Blue600,

    secondary = Color(0xFF7BD8AC),
    onSecondary = Color(0xFF0E5136),
    secondaryContainer = Color(0xFF1F5D41),
    onSecondaryContainer = Color(0xFFD7F2E4),

    tertiary = Color(0xFFFFC46B),
    onTertiary = Color(0xFF5C4300),
    tertiaryContainer = Color(0xFF6E5210),
    onTertiaryContainer = Color(0xFFFFE8C2),

    background = Color(0xFF121419),
    onBackground = Color(0xFFE2E3E8),
    surface = Color(0xFF17191F),
    onSurface = Color(0xFFE2E3E8),
    surfaceVariant = Color(0xFF262932),
    onSurfaceVariant = Color(0xFF9CA1AC),
    inverseSurface = Color(0xFFE2E3E8),
    inverseOnSurface = Color(0xFF2E3037),

    outline = Color(0xFF464A54),
    outlineVariant = Color(0xFF2E323B),
    scrim = Color(0xFF000000),

    surfaceBright = Color(0xFF3A3D45),
    surfaceDim = Color(0xFF121419),
    surfaceContainerLowest = Color(0xFF0E1014),
    surfaceContainerLow = Color(0xFF191C22),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353D)
)

// 统一形状语言：输入/小件 12dp，内容卡片 16dp，大卡/对话框 20dp
private val BeiwangShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun BeiwangTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = BeiwangShapes,
        content = content
    )
}
