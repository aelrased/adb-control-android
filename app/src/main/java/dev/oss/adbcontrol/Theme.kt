package dev.oss.adbcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF0F1216)
val CardBg = Color(0xFF171B22)
val CardBgAlt = Color(0xFF1E242D)
val TextMain = Color(0xFFE8EAF0)
val TextMuted = Color(0xFF8B93A3)
val AccentGreen = Color(0xFF27AE60)
val AccentBlue = Color(0xFF2F80ED)
val Danger = Color(0xFFFF6B6B)
val Divider = Color(0xFF262C36)

private val Colors = darkColorScheme(
    primary = AccentGreen,
    onPrimary = Color.White,
    secondary = AccentBlue,
    background = Bg,
    onBackground = TextMain,
    surface = CardBg,
    onSurface = TextMain,
    surfaceVariant = CardBgAlt,
    onSurfaceVariant = TextMuted,
    outline = Divider,
    error = Danger
)

@Composable
fun OpenAdbTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
