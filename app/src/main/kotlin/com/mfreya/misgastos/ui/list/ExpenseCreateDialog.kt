package com.mfreya.misgastos.ui.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.ui.components.SingleDatePickerDialog
import com.mfreya.misgastos.ui.format.Dates
import java.time.LocalDate
import java.util.Locale

/**
 * Dialogo para apuntar un gasto que no ha llegado por notificacion: un pago con tarjeta
 * fisica, en efectivo, o cualquier otro que el usuario quiera apuntar a mano.
 *
 * Pide lo mismo que la correccion de un gasto (importe, comercio y categoria) y ademas la
 * fecha, que por defecto es hoy. Si el usuario no elige categoria se deducira con las reglas
 * a partir del comercio, y la que elija quedara aprendida para los proximos gastos de ese
 * comercio.
 */
@Composable
fun ExpenseCreateDialog(
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, merchant: String, category: String, date: LocalDate) -> Unit,
    availableCategories: List<String> = emptyList(),
    locale: Locale = Locale.getDefault(),
) {
    var amountText by remember { mutableStateOf("") }
    var merchantText by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(Category.UNCLASSIFIED) }
    var customCategory by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val options = remember(availableCategories) { categoryOptions(availableCategories) }

    val cents = remember(amountText) { amountFromText(amountText) }
    val amountValid = cents != null && cents > 0L
    val showAmountError = amountText.isNotBlank() && !amountValid
    // El comercio se pide a proposito: si no hay aviso del que copiarlo, es el usuario quien
    // sabe a quien pago, y un gasto sin nombre no sirve de nada en el resumen ni al agrupar.
    val merchantValid = merchantText.isNotBlank()
    val canSave = amountValid && merchantValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expense_create_title)) },
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
                DateRow(date = date, onPick = { datePickerOpen = true })
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
                Text(
                    text = stringResource(R.string.expense_category_auto_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(requireNotNull(cents), merchantText.trim(), category, date) },
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

    if (datePickerOpen) {
        SingleDatePickerDialog(
            initialDate = date,
            onDismiss = { datePickerOpen = false },
            onConfirm = {
                date = it
                datePickerOpen = false
            },
        )
    }
}

/**
 * Fecha del gasto, con un boton de calendario al lado.
 *
 * Se muestra como fila y no como campo editable porque el calendario emergente ya es el editor
 * de la fecha: asi no hay dos formas de escribirla ni que pulsarla no haga nada.
 */
@Composable
private fun DateRow(date: LocalDate, onPick: () -> Unit, locale: Locale = Locale.getDefault()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.expense_date_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Dates.formatDate(date, locale),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onPick) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = stringResource(R.string.expense_date_change),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
