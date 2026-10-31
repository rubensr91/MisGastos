package com.mfreya.misgastos.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.parse.AmountParser
import com.mfreya.misgastos.ui.format.Money
import com.mfreya.misgastos.ui.format.categoryLabel

/**
 * Importe escrito por el usuario en centimos, o `null` si el texto todavia no es un importe.
 *
 * Se reutiliza el mismo [AmountParser.toCents] que el resto de la app, de forma que "45,90" y
 * "45.90" significan lo mismo y nunca se tocan coma flotante.
 */
fun amountFromText(text: String): Long? = AmountParser.toCents(text)

/** Catalogo de categorias mas las que el usuario se haya creado, sin repetir. */
fun categoryOptions(extra: List<String>): List<String> =
    (Category.DEFAULT_CATEGORIES + extra).distinct()

/** Campo del importe, con el simbolo de moneda y el mensaje de error si no se entiende. */
@Composable
fun ExpenseAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.expense_amount_label)) },
        prefix = { Text(Money.SYMBOL) },
        singleLine = true,
        isError = isError,
        supportingText = if (isError) {
            { Text(stringResource(R.string.expense_amount_invalid)) }
        } else {
            null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Campo del comercio o concepto. */
@Composable
fun ExpenseMerchantField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.expense_merchant_hint)) },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Las categorias en chips, con un tick en la activa. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseCategoryChips(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(text = categoryLabel(option), maxLines = 1) },
                leadingIcon = if (option == selected) {
                    { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                } else {
                    null
                },
            )
        }
    }
}

/** Campo para inventar una categoria que no este en la lista. */
@Composable
fun ExpenseCustomCategoryField(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.expense_category_new)) },
        singleLine = true,
        trailingIcon = {
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.expense_category_new),
                )
            }
        },
        modifier = modifier.fillMaxWidth(),
    )
}

/** Rotulo de "Categoria" que precede a los chips. */
@Composable
fun ExpenseCategoryLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.expense_category_label),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
