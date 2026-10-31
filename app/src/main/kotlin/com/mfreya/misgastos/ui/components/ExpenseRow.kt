package com.mfreya.misgastos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.ui.format.Dates
import com.mfreya.misgastos.ui.format.Money
import com.mfreya.misgastos.ui.format.categoryLabel
import java.util.Locale

/**
 * Fila de gasto: avatar con la inicial del comercio sobre el color de su categoría,
 * nombre, categoría traducida y hora a la izquierda, importe a la derecha.
 */
@Composable
fun ExpenseRow(
    expense: Expense,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
    avatarSize: Dp = 44.dp,
    categoryIndex: Int = -1,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(
            merchant = expense.merchant,
            category = expense.category,
            size = avatarSize,
            index = categoryIndex,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = expense.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (expense.needsReview) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.width(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = categoryLabel(expense.category),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = Dates.formatDateTime(expense.postedAt, locale),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = Money.format(expense.amountCents, locale),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
