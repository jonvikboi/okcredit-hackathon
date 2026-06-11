package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val JewellsColorScheme = lightColorScheme(
    primary = PrimaryColor,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryLightColor,
    onPrimaryContainer = Color(0xFF1A1612),
    secondary = AccentColor,
    onSecondary = Color(0xFFFFFFFF),
    background = BgColor,
    onBackground = TextColor,
    surface = SurfaceColor,
    onSurface = TextColor,
    surfaceVariant = SurfaceMutedColor,
    onSurfaceVariant = TextMutedColor,
    outline = BorderColor,
    error = ErrorColor,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = JewellsColorScheme

    MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = Shapes, content = content)
}
