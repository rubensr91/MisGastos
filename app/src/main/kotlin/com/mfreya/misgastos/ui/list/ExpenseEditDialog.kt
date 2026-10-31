package com.mfreya.misgastos.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.ui.format.Money
import java.util.Locale

/**
 * Dialogo de correccion manual de un gasto: importe, comercio y categoria.
 *
 * El usuario puede corregir lo que el banco escribio o lo que el parseo dedujo. El importe
 * se valida con el mismo [amountFromText] que usa el resto de la app.
 */
@Composable
fun ExpenseEditDialog(
    expense: Expense,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, merchant: String, category: String) -> Unit,
    locale: Locale = Locale.getDefault(),
    availableCategories: List<String> = emptyList(),
) {
    var amountText by remember(expense.id) {
        mutableStateOf(Money.formatPlain(expense.amountCents, locale))
    }
    var merchantText by remember(expense.id) { mutableStateOf(expense.merchant) }
    var category by remember(expense.id) { mutableStateOf(expense.category) }
    var customCategory by remember(expense.id) { mutableStateOf("") }
    val options = remember(availableCategories) { categoryOptions(availableCategories) }

    val cents = remember(amountText) { amountFromText(amountText) }
    val amountValid = cents != null && cents > 0L
    val showAmountError = amountText.isNotBlank() && !amountValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expense_category_edit)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExpenseAmountField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    isError = showAmountError,
                )
                ExpenseMerchantField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                )
                ExpenseCategoryLabel()
                ExpenseCategoryChips(
                    selected = category,
                    options = options,
                    onSelect = { category = it },
                )
                ExpenseCustomCategoryField(
                    value = customCategory,
                    onValueChange = { customCategory = it },
                    onAdd = {
                        category = Category.normalize(customCategory)
                        customCategory = ""
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amountValid,
                onClick = { onSave(requireNotNull(cents), merchantText, category) },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
