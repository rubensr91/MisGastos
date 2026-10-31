package com.mfreya.misgastos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * "tnum" fuerza cifras de ancho fijo: es lo que hace que las columnas de importes
 * queden alineadas como en las apps de banca.
 */
private const val TabularFigures = "tnum"

private fun numbers(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
): TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontFeatureSettings = TabularFigures,
)

val MisGastosTypography = Typography(
    displayLarge = numbers(57, 64, FontWeight.Bold),
    displayMedium = numbers(45, 52, FontWeight.Bold),
    displaySmall = numbers(36, 44, FontWeight.Bold),
    headlineLarge = numbers(32, 40, FontWeight.Bold),
    headlineMedium = numbers(28, 36, FontWeight.Bold),
    headlineSmall = numbers(24, 32, FontWeight.Bold),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = numbers(16, 24, FontWeight.SemiBold),
    titleSmall = numbers(14, 20, FontWeight.SemiBold),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.15.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    ),
)

/** Estilo de titular: versalitas cortas para rotular bloques de la interfaz. */
val SectionOverline = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 1.2.sp,
    textAlign = TextAlign.Start,
)