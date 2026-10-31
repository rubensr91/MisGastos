package com.mfreya.misgastos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mfreya.misgastos.domain.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = TealSecondary,
    onSecondary = TealOnSecondary,
    secondaryContainer = TealContainer,
    onSecondaryContainer = TealOnContainer,
    tertiary = AccentTertiary,
    onTertiary = AccentOnTertiary,
    tertiaryContainer = AccentTertiaryContainer,
    onTertiaryContainer = AccentOnTertiaryContainer,
    error = ErrorRed,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerHighLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceDim = SurfaceDimLight,
    surfaceBright = SurfaceBrightLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceTint = SurfaceTintLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = InversePrimaryLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenDarkPrimary,
    onPrimary = GreenDarkOnPrimary,
    primaryContainer = GreenDarkContainer,
    onPrimaryContainer = GreenDarkOnContainer,
    secondary = TealDarkSecondary,
    onSecondary = TealDarkOnSecondary,
    secondaryContainer = TealDarkContainer,
    onSecondaryContainer = TealDarkOnContainer,
    tertiary = AccentDarkTertiary,
    onTertiary = AccentDarkOnTertiary,
    tertiaryContainer = AccentDarkTertiaryContainer,
    onTertiaryContainer = AccentDarkOnTertiaryContainer,
    error = ErrorDark,
    errorContainer = ErrorDarkContainer,
    onErrorContainer = OnErrorDarkContainer,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerHighDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceDim = SurfaceDimDark,
    surfaceBright = SurfaceBrightDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceTint = SurfaceTintDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = InversePrimaryDark,
)

/**
 * Colores de marca fuera del [androidx.compose.material3.ColorScheme], porque los degradados
 * no existen en Material 3 pero son la mitad del aspecto "app de finanzas".
 */
@Immutable
data class BrandColors(
    val start: Color,
    val end: Color,
    val glow: Color,
    val onBrand: Color,
    val trendUp: Color,
    val trendDown: Color,
) {
    val gradient: Brush
        get() = Brush.linearGradient(listOf(start, end))

    val glowGradient: Brush
        get() = Brush.linearGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent))
}

private val BrandLight = BrandColors(
    start = BrandLightStart,
    end = BrandLightEnd,
    glow = BrandLightGlow,
    onBrand = Color.White,
    trendUp = TrendUp,
    trendDown = TrendDown,
)

private val BrandDark = BrandColors(
    start = BrandDarkStart,
    end = BrandDarkEnd,
    glow = BrandDarkGlow,
    onBrand = Color.White,
    trendUp = Color(0xFF5FD9A4),
    trendDown = Color(0xFFFFB4AB),
)

val LocalBrandColors = staticCompositionLocalOf { BrandLight }

@Composable
fun MisGastosTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val brand = if (darkTheme) BrandDark else BrandLight

    androidx.compose.runtime.CompositionLocalProvider(LocalBrandColors provides brand) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MisGastosTypography,
            shapes = MisGastosShapes,
            content = content,
        )
    }
}