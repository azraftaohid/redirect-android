package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class SemanticColors(
  val successBg: Color,
  val successText: Color,
  val successBorder: Color,
  val warningBg: Color,
  val warningText: Color,
  val warningBorder: Color,
  val dangerBg: Color,
  val dangerText: Color,
  val dangerBorder: Color,
  val neutralBg: Color,
  val neutralText: Color,
  val neutralBorder: Color,
)

private val LightSemanticColors = SemanticColors(
  successBg = EmeraldBgLight,
  successText = EmeraldTextLight,
  successBorder = EmeraldBorderLight,
  warningBg = AmberBgLight,
  warningText = AmberTextLight,
  warningBorder = AmberBorderLight,
  dangerBg = RedBgLight,
  dangerText = RedTextLight,
  dangerBorder = RedBorderLight,
  neutralBg = Zinc100,
  neutralText = Zinc600,
  neutralBorder = Zinc200,
)

private val DarkSemanticColors = SemanticColors(
  successBg = EmeraldBgDark,
  successText = EmeraldTextDark,
  successBorder = EmeraldBorderDark,
  warningBg = AmberBgDark,
  warningText = AmberTextDark,
  warningBorder = AmberBorderDark,
  dangerBg = RedBgDark,
  dangerText = RedTextDark,
  dangerBorder = RedBorderDark,
  neutralBg = Zinc800,
  neutralText = Zinc400,
  neutralBorder = Zinc700,
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
  primary = Zinc900,
  onPrimary = Zinc50,
  primaryContainer = Zinc200,
  onPrimaryContainer = Zinc900,
  secondary = Zinc700,
  onSecondary = PureWhite,
  secondaryContainer = Zinc100,
  onSecondaryContainer = Zinc800,
  tertiary = Zinc600,
  onTertiary = PureWhite,
  background = Zinc50,
  onBackground = Zinc900,
  surface = PureWhite,
  onSurface = Zinc900,
  surfaceVariant = Zinc100,
  onSurfaceVariant = Zinc600,
  surfaceContainerLowest = PureWhite,
  surfaceContainerLow = Zinc50,
  surfaceContainer = Zinc100,
  surfaceContainerHigh = Zinc200,
  outline = Zinc300,
  outlineVariant = Zinc200,
  error = RedTextLight,
  onError = PureWhite,
  errorContainer = RedBgLight,
  onErrorContainer = RedTextLight,
)

private val DarkColorScheme = darkColorScheme(
  primary = Zinc50,
  onPrimary = Zinc950,
  primaryContainer = Zinc800,
  onPrimaryContainer = Zinc50,
  secondary = Zinc300,
  onSecondary = Zinc950,
  secondaryContainer = Zinc800,
  onSecondaryContainer = Zinc200,
  tertiary = Zinc400,
  onTertiary = Zinc950,
  background = Zinc950,
  onBackground = Zinc50,
  surface = Zinc900,
  onSurface = Zinc50,
  surfaceVariant = Zinc800,
  onSurfaceVariant = Zinc400,
  surfaceContainerLowest = Zinc950,
  surfaceContainerLow = Zinc900,
  surfaceContainer = Zinc800,
  surfaceContainerHigh = Zinc700,
  outline = Zinc700,
  outlineVariant = Zinc800,
  error = RedTextDark,
  onError = Zinc950,
  errorContainer = RedBgDark,
  onErrorContainer = RedTextDark,
)

private val RedirectShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(8.dp),
  medium = RoundedCornerShape(12.dp),
  large = RoundedCornerShape(16.dp),
  extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun RedirectTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

  val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

  CompositionLocalProvider(LocalSemanticColors provides semanticColors) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = RedirectTypography,
      shapes = RedirectShapes,
      content = content,
    )
  }
}
