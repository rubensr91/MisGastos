package com.mfreya.misgastos.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CategoryPalette: List<Color> = listOf(
    Color(0xFF1B6B4A),
    Color(0xFF2E7D6B),
    Color(0xFF4E9E8F),
    Color(0xFF7FB8A6),
    Color(0xFFB4D6C6),
    Color(0xFF3C6E9B),
    Color(0xFF6C93BC),
    Color(0xFFA8C4DE),
    Color(0xFFB4632E),
    Color(0xFFD89A6B),
    Color(0xFF8C5B9E),
    Color(0xFFC0A3CE),
)

@Composable
fun categoryColor(category: String, index: Int): Color {
    if (index >= 0) return CategoryPalette[index % CategoryPalette.size]
    val key = category.lowercase()
    val hash = key.fold(0) { acc, ch -> acc * 31 + ch.code }
    return CategoryPalette[((hash % CategoryPalette.size) + CategoryPalette.size) % CategoryPalette.size]
}

@Composable
fun chartTrackColor(): Color = MaterialTheme.colorScheme.surfaceVariant

@Composable
fun chartLineColor(): Color = MaterialTheme.colorScheme.primary
