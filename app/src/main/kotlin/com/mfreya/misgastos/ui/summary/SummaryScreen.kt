package com.mfreya.misgastos.ui.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.BudgetStatus
import com.mfreya.misgastos.domain.MonthClose
import com.mfreya.misgastos.domain.MonthCloseCalculator
import com.mfreya.misgastos.domain.MonthCloseReport
import com.mfreya.misgastos.domain.StatsCalculator
import com.mfreya.misgastos.domain.Summary
import com.mfreya.misgastos.ui.components.AnimatedAmount
import com.mfreya.misgastos.ui.components.BarItem
import com.mfreya.misgastos.ui.components.BrandCard
import com.mfreya.misgastos.ui.components.CategoryAvatar
import com.mfreya.misgastos.ui.components.ChartBars
import com.mfreya.misgastos.ui.components.ChartDonut
import com.mfreya.misgastos.ui.components.ChartLine
import com.mfreya.misgastos.ui.components.DonutLegend
import com.mfreya.misgastos.ui.components.DonutSlice
import com.mfreya.misgastos.ui.components.EmptyState
import com.mfreya.misgastos.ui.components.ProgressTrack
import com.mfreya.misgastos.ui.components.ScreenHeader
import com.mfreya.misgastos.ui.components.SectionHeader
import com.mfreya.misgastos.ui.components.StatCell
import com.mfreya.misgastos.ui.components.SurfaceCard
import com.mfreya.misgastos.ui.components.ThinDivider
import com.mfreya.misgastos.ui.components.TrendPill
import com.mfreya.misgastos.ui.components.categoryColor
import com.mfreya.misgastos.ui.components.rememberScreenHeaderScrollBehavior
import com.mfreya.misgastos.ui.containerViewModel
import com.mfreya.misgastos.ui.format.Money
import com.mfreya.misgastos.ui.format.categoryLabel
import com.mfreya.misgastos.ui.theme.LocalBrandColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(modifier: Modifier = Modifier) {
    val headerScroll = rememberScreenHeaderScrollBehavior()
    val viewModel: SummaryViewModel = containerViewModel("summary") { container ->
        SummaryViewModel(container.expenseRepository, container.budgetRepository)
    }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val monthClose by viewModel.monthClose.collectAsStateWithLifecycle()
    val monthRange by viewModel.monthRange.collectAsStateWithLifecycle()
    val budgets by viewModel.budgetStatus.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.summary_title),
                scrollBehavior = headerScroll,
            )
        },
    ) { padding ->
        val current = summary
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.summary_empty_title),
                    body = stringResource(R.string.summary_empty_body),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .nestedScroll(headerScroll.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                MonthHero(
                    current = current,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            item {
                val previous = current.previousMonthCents
                val delta = current.monthCents - previous
                val trendText = when {
                    previous == 0L -> stringResource(R.string.summary_trend_first_month)
                    delta > 0 -> stringResource(
                        R.string.summary_up,
                        "+" + Money.format(delta),
                    )

                    delta < 0 -> stringResource(R.string.summary_down, Money.format(abs(delta)))
                    else -> stringResource(R.string.summary_vs_previous, Money.format(0))
                }
                TrendPill(
                    // Gastar menos es la señal buena: por eso "rising" se invierte aquí.
                    // Sin mes anterior no hay comparación, así que queda neutra.
                    text = trendText,
                    rising = if (previous == 0L) null else delta <= 0,
                )
            }
            item {
                SurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val dailyAverage =
                            ((current.movingAverage.lastOrNull() ?: 0.0) * 100).roundToLong()
                        StatCell(
                            label = stringResource(R.string.summary_average_daily),
                            value = Money.format(dailyAverage),
                            modifier = Modifier.weight(1f),
                        )
                        ThinDivider()
                        StatCell(
                            label = stringResource(R.string.summary_ticket_average),
                            value = Money.format(
                                StatsCalculator.averageTicket(
                                    current.monthCents,
                                    current.monthCount,
                                ),
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.summary_average_daily_hint) + " · " +
                            stringResource(R.string.summary_ticket_average_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(
                        title = stringResource(R.string.summary_evolution),
                        action = {
                            HeaderHint(stringResource(R.string.summary_average))
                        },
                    )
                    SurfaceCard {
                        ChartLine(
                            points = current.dailySeries,
                            average = current.movingAverage,
                            contentDescription = stringResource(R.string.chart_cd_evolution),
                        )
                    }
                }
            }
            if (current.topMerchants.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.summary_top_merchants),
                            action = { HeaderHint(stringResource(R.string.summary_top_merchants_hint)) },
                        )
                        val maxValue = current.topMerchants.maxOf { it.totalCents }.coerceAtLeast(1L)
                        SurfaceCard {
                            ChartBars(
                                items = current.topMerchants.map { merchant ->
                                    BarItem(
                                        label = merchant.merchant,
                                        valueLabel = Money.format(merchant.totalCents),
                                        fraction = (merchant.totalCents.toDouble() / maxValue.toDouble())
                                            .toFloat(),
                                    )
                                },
                                contentDescription = stringResource(R.string.chart_cd_merchants),
                            )
                        }
                    }
                }
            }
            if (current.byCategory.isNotEmpty()) {
                item {
                    val cents = current.byCategory.map { it.totalCents }
                    val percentages = Money.percentages(cents)
                    val slices = current.byCategory.mapIndexed { index, category ->
                        DonutSlice(
                            label = categoryLabel(category.category),
                            percent = percentages[index],
                            valueLabel = Money.format(category.totalCents),
                            color = categoryColor(category.category, index),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeader(
                            title = stringResource(R.string.summary_by_category),
                            action = { HeaderHint(stringResource(R.string.summary_by_category_hint)) },
                        )
                        SurfaceCard {
                            ChartDonut(
                                slices = slices,
                                contentDescription = stringResource(R.string.chart_cd_categories),
                                centerContent = {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = stringResource(R.string.summary_donut_center),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        AnimatedAmount(
                                            cents = current.monthCents,
                                            style = MaterialTheme.typography.titleLarge,
                                            durationMillis = 800,
                                        )
                                    }
                                },
                            )
                            Spacer(Modifier.height(14.dp))
                            DonutLegend(slices = slices)
                        }
                    }
                }
            }
            if (budgets.isNotEmpty()) {
                item {
                    BudgetSection(statuses = budgets)
                }
            }
            monthClose?.let { report ->
                item {
                    MonthCloseSection(
                        report = report,
                        range = monthRange,
                        onRangeChange = viewModel::setMonthRange,
                    )
                }
            }
        }
    }
}

/**
 * Presupuestos del mes: una fila por categoria con lo gastado, el limite y una barra. El color
 * avisa sin need de leer la cifra: verde tranquilo, teal al nearing del limite y rojo al pasarse.
 * No hay ningun aviso ni notificacion; solo informa.
 */
@Composable
private fun BudgetSection(statuses: List<BudgetStatus>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.summary_budgets),
            action = { HeaderHint(stringResource(R.string.summary_budgets_hint)) },
        )
        SurfaceCard {
            statuses.forEachIndexed { index, status ->
                if (index > 0) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(14.dp))
                }
                BudgetRow(status)
            }
        }
    }
}

@Composable
private fun BudgetRow(status: BudgetStatus, modifier: Modifier = Modifier) {
    val color = when {
        status.isOver -> MaterialTheme.colorScheme.error
        status.progress >= NEAR_LIMIT -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryAvatar(
                merchant = categoryLabel(status.category),
                category = status.category,
                size = 40.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryLabel(status.category),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (status.spentCents == 0L) {
                        stringResource(R.string.summary_budget_no_spend)
                    } else {
                        stringResource(
                            R.string.summary_budget_spent_of,
                            Money.format(status.spentCents),
                            Money.format(status.limitCents),
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = when {
                    status.isOver -> stringResource(
                        R.string.summary_budget_over,
                        Money.format(status.overCents),
                    )

                    status.spentCents == 0L -> Money.format(status.limitCents)
                    else -> stringResource(
                        R.string.summary_budget_remaining,
                        Money.format(status.remainingCents),
                    )
                },
                style = MaterialTheme.typography.labelLarge,
                color = color,
            )
        }
        Spacer(Modifier.height(10.dp))
        ProgressTrack(fraction = status.progress, color = color)
    }
}

/** Por encima de este avance la barra pasa a avisar en el color de acento. */
private const val NEAR_LIMIT = 0.85f

/**
 * Cierre de mes: selector de 3, 6 o 12 meses y una fila por mes con el total, los movimientos,
 * el ticket medio y la variacion frente al mes anterior. Al pie, el total del ano en curso.
 */
@Composable
private fun MonthCloseSection(
    report: MonthCloseReport,
    range: Int,
    onRangeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeader(
            title = stringResource(R.string.summary_month_close),
            action = {
                HeaderHint(pluralStringResource(R.plurals.summary_month_close_hint, range, range))
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MonthCloseCalculator.RANGE_OPTIONS.forEach { option ->
                FilterChip(
                    selected = range == option,
                    onClick = { onRangeChange(option) },
                    label = {
                        Text(
                            text = pluralStringResource(
                                R.plurals.summary_month_close_option,
                                option,
                                option,
                            ),
                        )
                    },
                    leadingIcon = if (range == option) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = range == option,
                        borderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )
            }
        }
        SurfaceCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                report.months.forEachIndexed { index, month ->
                    if (index > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    MonthCloseRow(month = month, locale = locale)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.summary_month_close_year, report.year),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = Money.format(report.yearTotalCents),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCloseRow(month: MonthClose, locale: Locale, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = month.periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.summary_month_movements, month.count, month.count) +
                    " · " + stringResource(R.string.summary_month_average_ticket, Money.format(month.averageCents)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.format(month.totalCents),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
            )
            VariationLabel(month = month)
        }
    }
}

@Composable
private fun VariationLabel(month: MonthClose, modifier: Modifier = Modifier) {
    val brand = LocalBrandColors.current
    val percent = month.variationPercent
    val (text, color) = when {
        percent == null -> stringResource(R.string.summary_trend_first_month) to
            MaterialTheme.colorScheme.onSurfaceVariant

        percent > 0 -> stringResource(R.string.summary_month_variation_up, percent) to brand.trendDown
        percent < 0 -> stringResource(R.string.summary_month_variation_down, -percent) to brand.trendUp
        else -> stringResource(R.string.summary_month_variation_flat) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier,
    )
}


/** Tarjeta protagonista: la cifra del mes con degradado de marca y dos datos de apoyo. */
@Composable
private fun MonthHero(current: Summary, modifier: Modifier = Modifier) {
    val brand = LocalBrandColors.current
    BrandCard(modifier = modifier) {
        Text(
            text = stringResource(R.string.summary_spent_month),
            style = MaterialTheme.typography.labelLarge,
            color = brand.onBrand.copy(alpha = 0.86f),
        )
        Spacer(Modifier.height(8.dp))
        AnimatedAmount(
            cents = current.monthCents,
            style = MaterialTheme.typography.displayMedium,
            color = brand.onBrand,
            durationMillis = 900,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell(
                label = stringResource(R.string.summary_spent_today),
                value = Money.format(current.todayCents),
                valueColor = brand.onBrand,
                labelColor = brand.onBrand.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f),
            )
            ThinDivider(color = brand.onBrand.copy(alpha = 0.32f))
            StatCell(
                label = stringResource(R.string.summary_movements),
                value = current.monthCount.toString(),
                valueColor = brand.onBrand,
                labelColor = brand.onBrand.copy(alpha = 0.78f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun HeaderHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
