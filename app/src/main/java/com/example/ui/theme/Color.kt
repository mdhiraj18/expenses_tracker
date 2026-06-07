package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

// Global dark mode mutable state that Compose observes automatically
var isDarkModeGlobal: Boolean by mutableStateOf(false)

// Elegant Sage-Green Professional Polish theme (dynamically toggled between beautiful light ambient and readable dark)
val PolishBg: Color get() = if (isDarkModeGlobal) Color(0xFF111410) else Color(0xFFFBFDF8)
val PolishSurface: Color get() = if (isDarkModeGlobal) Color(0xFF191D17) else Color(0xFFFFFFFF)
val PolishPrimary: Color get() = if (isDarkModeGlobal) Color(0xFF90D577) else Color(0xFF386A20)      // Forest Green / Soft Mint
val PolishSecondary: Color get() = if (isDarkModeGlobal) Color(0xFF3B4F33) else Color(0xFFD7E8CD)    // Soft Sage Green
val PolishTertiary: Color get() = if (isDarkModeGlobal) Color(0xFF232D1F) else Color(0xFFEFF2E9)     // Cream-Beige quick tools bg
val PolishTextDark: Color get() = if (isDarkModeGlobal) Color(0xFFE2E3DD) else Color(0xFF191C1B)     // Dark charcoal text / light silver green
val PolishTextMuted: Color get() = if (isDarkModeGlobal) Color(0xFFBAC5B7) else Color(0xFF424940)    // Soft grey-green supporting text
val PolishTextSlate: Color get() = if (isDarkModeGlobal) Color(0xFF8B9489) else Color(0xFF72796F)    // Darker slate grey
val PolishBorder: Color get() = if (isDarkModeGlobal) Color(0xFF3D4A39) else Color(0xFFDCE5D8)       // Thin divider color
val PolishAlertRed: Color get() = if (isDarkModeGlobal) Color(0xFFFFB4AB) else Color(0xFFBA1A1A)     // Crimson warning color
val AccentGold: Color get() = if (isDarkModeGlobal) Color(0xFFFFD54F) else Color(0xFFFFA000)         // Alert Yellow-Gold

// Kept for backward compatibility references or fallback
val MutedGrey: Color get() = if (isDarkModeGlobal) Color(0xFFBAC5B7) else Color(0xFF424940)
val DarkWhite: Color get() = if (isDarkModeGlobal) Color(0xFF111410) else Color(0xFF191C1B)
val CardBg: Color get() = if (isDarkModeGlobal) Color(0xFF232D1F) else Color(0xFFEFF2E9)
val CardBorderColor: Color get() = if (isDarkModeGlobal) Color(0xFF3D4A39) else Color(0xFFDCE5D8)
val EmeraldPrimary: Color get() = if (isDarkModeGlobal) Color(0xFF90D577) else Color(0xFF386A20)
val DarkBg: Color get() = if (isDarkModeGlobal) Color(0xFF111410) else Color(0xFFFBFDF8)
val DarkSurface: Color get() = if (isDarkModeGlobal) Color(0xFF191D17) else Color(0xFFFFFFFF)


