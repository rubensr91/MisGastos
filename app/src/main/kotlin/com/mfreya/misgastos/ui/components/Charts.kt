package com.mfreya.misgastos.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mfreya.misgastos.domain.DailyPoint

private const val CHART_ANIMATION_MILLIS = 700

/**
 * Línea de evolución con curva suave (Bézier cúbica), relleno con degradado y
 * animación de entrada. La media móvil se dibuja como línea discontinua.
 */
@Composable
fun ChartLine(
    points: List<DailyPoint>,
    average: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    contentDescription: String? = null,
) {
    val lineColor = chartLineColor()
    val trackColor = chartTrackColor()
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(CHART_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "chartLineProgress",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        if (points.isEmpty()) return@Canvas
        val values = points.map { it.totalCents.toDouble() }
        val maxValue = maxOf(values.maxOrNull() ?: 0.0, average.maxOrNull() ?: 0.0, 1.0)
        val stepX = if (points.size > 1) size.width / (points.size - 1).toFloat() else 0f
        val baseY = size.height

        fun offsetFor(index: Int, value: Double): Offset {
            val ratio = (value / maxValue).toFloat().coerceIn(0f, 1f)
            return Offset(index * stepX, baseY - baseY * ratio)
        }

        if (values.size > 1) {
            val offsets = values.mapIndexed { index, value -> offsetFor(index, value) }
            val line = Path()
            val fill = Path()
            smoothedPath(offsets, line)
            fill.addPath(line)
            fill.lineTo(offsets.last().x, baseY)
            fill.lineTo(offsets.first().x, baseY)
            fill.close()
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.30f), Color.Transparent),
                    startY = 0f,
                    endY = baseY,
                ),
                alpha = progress,
            )
            drawPath(
                path = line,
                brush = Brush.horizontalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.45f), lineColor),
                ),
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                alpha = progress,
            )
            val last = offsets.last()
            drawCircle(color = lineColor.copy(alpha = 0.22f), radius = 10.dp.toPx(), center = last)
            drawCircle(
                color = lineColor,
                radius = 5.dp.toPx(),
                center = last,
                alpha = progress,
            )
        } else {
            drawCircle(
                color = lineColor,
                radius = 6.dp.toPx(),
                center = offsetFor(0, values[0]),
                alpha = progress,
            )
        }

        if (average.size > 1) {
            val offsets = average.mapIndexed { index, value -> offsetFor(index, value) }
            val path = Path()
            smoothedPath(offsets, path)
            drawPath(
                path = path,
                color = trackColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                alpha = progress,
            )
        }
    }
}

/** Traza una curva suave que pasa por todos los puntos dados. */
private fun DrawScope.smoothedPath(offsets: List<Offset>, target: Path) {
    target.moveTo(offsets.first().x, offsets.first().y)
    for (index in 1 until offsets.size) {
        val previous = offsets[index - 1]
        val current = offsets[index]
        val controlX = (previous.x + current.x) / 2f
        target.cubicTo(controlX, previous.y, controlX, current.y, current.x, current.y)
    }
}

data class BarItem(val label: String, val valueLabel: String, val fraction: Float)

/** Barras horizontales con degradado y ancho animado. */
@Composable
fun ChartBars(
    items: List<BarItem>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val trackColor = chartTrackColor()
    val lineColor = chartLineColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items.forEach { item ->
            val fraction by animateFloatAsState(
                targetValue = item.fraction.coerceIn(0f, 1f),
                animationSpec = tween(CHART_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
                label = "barFraction",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.valueLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                ) {
                    val radius = CornerRadius(size.height / 2f)
                    drawRoundRect(color = trackColor, size = size, cornerRadius = radius)
                    if (fraction > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(lineColor.copy(alpha = 0.55f), lineColor),
                            ),
                            size = Size(size.width * fraction, size.height),
                            cornerRadius = radius,
                        )
                    }
                }
            }
        }
    }
}

data class DonutSlice(val label: String, val percent: Int, val valueLabel: String, val color: Color)

/** Donut animado con extremos redondeados y contenido opcional en el centro. */
@Composable
fun ChartDonut(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    centerContent: @Composable BoxScope.() -> Unit = {},
) {
    val trackColor = chartTrackColor()
    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(CHART_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
        label = "donutProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(196.dp)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = 30.dp.toPx()
            val ring = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - ring) / 2f, (size.height - ring) / 2f)
            val arcSize = Size(ring, ring)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            var start = -90f
            slices.forEach { slice ->
                val sweep = slice.percent.toFloat() * 3.6f * progress
                if (sweep > 0.5f) {
                    drawArc(
                        color = slice.color,
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    start += sweep
                }
            }
        }
        centerContent()
    }
}

@Composable
fun DonutLegend(slices: List<DonutSlice>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        slices.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(slice.color, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = slice.valueLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .padding(end = 2.dp),
                ) {
                    Text(
                        text = "${slice.percent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** remember auxiliar: evita recrear la lista de barras en cada recomposición. */
@Composable
fun rememberBarItems(
    labels: List<String>,
    values: List<Long>,
    formatValue: (Long) -> String,
): List<BarItem> {
    val max = values.maxOrNull()?.toFloat()?.takeIf { it > 0f } ?: 1f
    return remember(labels, values) {
        labels.mapIndexed { index, label ->
            val value = values.getOrElse(index) { 0L }
            BarItem(label = label, valueLabel = formatValue(value), fraction = value / max)
        }
    }
}