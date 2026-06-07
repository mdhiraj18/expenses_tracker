package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme =
  lightColorScheme(
    primary = PolishPrimary,
    secondary = PolishSecondary,
    tertiary = PolishTertiary,
    background = PolishBg,
    surface = PolishSurface,
    onPrimary = Color.White,
    onSecondary = PolishTextDark,
    onTertiary = PolishTextDark,
    onBackground = PolishTextDark,
    onSurface = PolishTextDark
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = PolishPrimary,
    secondary = PolishSecondary,
    tertiary = PolishTertiary,
    background = PolishBg,
    surface = PolishSurface,
    onPrimary = Color.Black,
    onSecondary = PolishTextDark,
    onTertiary = PolishTextDark,
    onBackground = PolishTextDark,
    onSurface = PolishTextDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isDarkModeGlobal,
  dynamicColor: Boolean = false, // Keep disabled to match the brand exact colors
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
