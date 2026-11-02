package com.mfreya.misgastos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mfreya.misgastos.ui.theme.LocalBrandColors

/**
 * Círculo con la inicial del comercio sobre el color de su categoría. Es el recurso
 * visual que más veces se repite en la app, por eso vive en su propio fichero.
 */
@Composable
fun CategoryAvatar(
    merchant: String,
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    index: Int = -1,
) {
    val base = categoryColor(category, index)
    val initial = merchant.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = modifier
            .size(size)
            .background(base.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, base.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleMedium,
            color = base,
            fontSize = (size.value * 0.42f).sp,
            maxLines = 1,
        )
    }
}

/**
 * Píldora con la variación respecto al mes anterior: flecha, color y cantidad.
 * [rising] a `null` la pinta neutra, para los mensajes que no comparan nada
 * (por ejemplo "primer mes registrado").
 */
@Composable
fun TrendPill(
    text: String,
    rising: Boolean?,
    modifier: Modifier = Modifier,
) {
    val brand = LocalBrandColors.current
    val tint = when (rising) {
        true -> brand.trendUp
        false -> brand.trendDown
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .background(tint.copy(alpha = 0.14f), MaterialTheme.shapes.small)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
        )
    }
}