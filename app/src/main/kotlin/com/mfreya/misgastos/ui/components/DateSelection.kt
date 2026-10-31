package com.mfreya.misgastos.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mfreya.misgastos.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Los gastos nunca son futuros, asi que ningun calendario deja elegir fechas por delante. */
internal object UpToTodayDates : SelectableDates {
    private fun endOfTodayUtc(): Long = LocalDate.now(ZoneOffset.UTC)
        .atTime(23, 59, 59)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= endOfTodayUtc()

    override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now(ZoneOffset.UTC).year
}

/**
 * Calendario emergente para elegir un solo dia, por ejemplo la fecha de un gasto escrito a mano.
 *
 * Comparte [UpToTodayDates] con el selector de tramos de la pantalla de agrupar: los dos
 * calendarios tienen que comportarse igual, y duplicar las reglas de "no futuro" es la forma
 * facil de que se separen sin que nadie se dé cuenta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialMillis = remember(initialDate) { initialDate.toUtcMillis() }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        initialDisplayedMonthMillis = initialMillis,
        selectableDates = remember { UpToTodayDates },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(),
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val millis = state.selectedDateMillis ?: return@TextButton
                    onConfirm(millis.toUtcLocalDate())
                },
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.common_cancel))
            }
        },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

/** El calendario de Material trabaja en UTC, asi que el dia se recupera en UTC y no en la zona local. */
internal fun Long.toUtcLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal fun todayUtcMillis(): Long = LocalDate.now(ZoneOffset.UTC).toUtcMillis()
