package com.example.epubnoveltranslator.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(12.dp),
  small = RoundedCornerShape(16.dp),
  medium = RoundedCornerShape(22.dp),
  large = RoundedCornerShape(28.dp),
  extraLarge = RoundedCornerShape(36.dp)
)

private val DarkColorScheme = darkColorScheme(
  primary = Indigo, onPrimary = Ink,
  primaryContainer = IndigoDeep, onPrimaryContainer = Mist,
  secondary = Lavender, onSecondary = Ink,
  secondaryContainer = InkCard, onSecondaryContainer = Lavender,
  tertiary = MutedLavender,
  background = Ink, onBackground = Lavender,
  surface = Ink, onSurface = Lavender,
  surfaceVariant = InkCard, onSurfaceVariant = MutedLavender,
  surfaceContainerLowest = Ink,
  surfaceContainerLow = InkElevated,
  surfaceContainer = Color(0xFF151A28),
  surfaceContainerHigh = InkCard,
  surfaceContainerHighest = Color(0xFF222A3C),
  outline = Color(0xFF3A435B),
  outlineVariant = Color(0xFF272F44)
)

private val LightColorScheme =
  lightColorScheme(
    primary = MidnightNavy, onPrimary = White,
    primaryContainer = RoyalBlue, onPrimaryContainer = White,
    secondary = RoyalBlue, onSecondary = White,
    secondaryContainer = ReadingBlue, onSecondaryContainer = NavyOnLight,
    tertiary = ReadingBlue, onTertiary = White,
    background = IceBlue, onBackground = NavyOnLight,
    surface = IceBlue, onSurface = NavyOnLight,
    surfaceVariant = Color(0xFFE2F5FA), onSurfaceVariant = MidnightNavy,
    surfaceContainerLow = Color(0xFFD9F2F9),
    surfaceContainer = Color(0xFFCDECF6),
    surfaceContainerHigh = Color(0xFFBDE8F5)
  )

@Composable
fun EPUBNovelTranslatorTheme(
  // Navy is the app's primary reading surface, independent of device wallpaper/theme.
  darkTheme: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, shapes = AppShapes, content = content)
}
