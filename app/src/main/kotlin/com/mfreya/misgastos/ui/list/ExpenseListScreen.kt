package com.mfreya.misgastos.ui.list

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfreya.misgastos.R
import com.mfreya.misgastos.domain.GroupingCalculator
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.ui.components.EmptyState
import com.mfreya.misgastos.ui.components.ExpenseRow
import com.mfreya.misgastos.ui.components.ScreenHeader
import com.mfreya.misgastos.ui.components.rememberScreenHeaderScrollBehavior
import com.mfreya.misgastos.ui.containerViewModel
import com.mfreya.misgastos.ui.format.Dates
import com.mfreya.misgastos.ui.format.Money
import com.mfreya.misgastos.ui.theme.SectionOverline
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch


/** Ancho reservado a las dos acciones que aparecen al deslizar la fila. */
private val ActionsWidth: Dp = 152.dp

/** Fraccion del ancho de acciones a partir de la cual la fila se ancla abierta. */
private const val OpenThreshold = 0.35f

private const val SettleMillis = 200

/** Fila de la lista: o una cabecera de periodo o un gasto. */
private sealed interface ListEntry {
    /** Cabecera de un tramo (dia, semana o mes segun el criterio elegido). */
    data class PeriodHeader(
        val start: LocalDate,
        val end: LocalDate,
        val totalCents: Long,
        val count: Int,
    ) : ListEntry

    data class Item(val expense: Expense) : ListEntry
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseListScreen(modifier: Modifier = Modifier) {
    val headerScroll = rememberScreenHeaderScrollBehavior()
    val viewModel: ExpenseListViewModel = containerViewModel("expenses") { container ->
        ExpenseListViewModel(container.expenseRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.currentQuery.collectAsStateWithLifecycle()
    val existingCategories by viewModel.categories.collectAsStateWithLifecycle()
    val grouping by viewModel.currentGrouping.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val deletedMessage = stringResource(R.string.expenses_deleted)
    val undoLabel = stringResource(R.string.common_undo)
    val editedMessage = stringResource(R.string.expense_edited)
    val editFailedMessage = stringResource(R.string.expense_edit_failed)
    val createdMessage = stringResource(R.string.expense_create_done)
    val createFailedMessage = stringResource(R.string.expense_create_failed)
    val resultText = pluralStringResource(
        R.plurals.expenses_result_count,
        uiState.expenses.size,
        uiState.expenses.size,
        Money.format(uiState.totalCents),
    )
    val hintText = stringResource(R.string.expenses_hint_swipe)
    var pendingDelete by remember { mutableStateOf<Expense?>(null) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var creating by remember { mutableStateOf(false) }
    // Solo una fila puede estar abierta a la vez: al abrir otra, esta se cierra sola.
    var revealedRow by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            ScreenHeader(
                title = stringResource(R.string.expenses_title),
                scrollBehavior = headerScroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Gastos que no pasan por el banco (tarjeta fisica, efectivo, Bizum entre
            // particulares...) se anaden a mano desde aqui.
            FloatingActionButton(
                onClick = { creating = true },
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.expenses_action_add),
                )
            }
        },
    ) { padding ->
        if (uiState.expenses.isEmpty() && !uiState.loading) {
            Column(modifier = Modifier.fillMaxSize()) {
                ListHeader(
                    query = query,
                    resultText = resultText,
                    grouping = grouping,
                    onQueryChange = viewModel::setQuery,
                    onGroupingChange = viewModel::setGrouping,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = padding.calculateTopPadding() + 8.dp,
                    ),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        title = stringResource(R.string.expenses_empty_title),
                        body = stringResource(R.string.expenses_empty_body),
                    )
                }
            }
        } else {
            val entries = remember(uiState.expenses, grouping) {
                buildEntries(uiState.expenses, grouping)
            }
            LazyColumn(
                modifier = Modifier.nestedScroll(headerScroll.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    // Espacio extra para que el boton flotante de anadir no tape la
                    // ultima fila de la lista.
                    bottom = padding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // El buscador es el primer elemento de la lista y no una cabecera fija:
                // asi se va de la vista al bajar y reaparece al subir, dejando hueco para
                // ver mas filas de gasto.
                item(key = "search") {
                    ListHeader(
                        query = query,
                        resultText = resultText,
                        grouping = grouping,
                        onQueryChange = viewModel::setQuery,
                        onGroupingChange = viewModel::setGrouping,
                        hint = hintText,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                items(
                    items = entries,
                    key = { entry ->
                        when (entry) {
                            is ListEntry.PeriodHeader -> "header-${entry.start}"
                            is ListEntry.Item -> "expense-${entry.expense.id}"
                        }
                    },
                ) { entry ->
                    when (entry) {
                        is ListEntry.PeriodHeader -> PeriodHeaderRow(entry, grouping)
                        is ListEntry.Item -> SwipeableExpenseRow(
                            expense = entry.expense,
                            revealed = revealedRow == entry.expense.id,
                            onRevealChange = { open ->
                                revealedRow = if (open) entry.expense.id else null
                            },
                            onEdit = {
                                revealedRow = null
                                editing = entry.expense
                            },
                            onDelete = { pendingDelete = entry.expense },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = {
                revealedRow = null
                pendingDelete = null
            },
            title = { Text(stringResource(R.string.expense_delete_confirm_title)) },
            text = { Text(stringResource(R.string.expense_delete_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        revealedRow = null
                        pendingDelete = null
                        viewModel.delete(expense)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.restore(expense)
                        }
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    revealedRow = null
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    editing?.let { expense ->
        ExpenseEditDialog(
            expense = expense,
            availableCategories = existingCategories,
            onDismiss = { editing = null },
            onSave = { amountCents, merchant, category ->
                viewModel.updateExpense(
                    expense = expense,
                    amountCents = amountCents,
                    merchant = merchant,
                    category = category,
                    onResult = { ok ->
                        editing = null
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) editedMessage else editFailedMessage,
                            )
                        }
                    },
                )
            },
        )
    }

    if (creating) {
        ExpenseCreateDialog(
            availableCategories = existingCategories,
            onDismiss = { creating = false },
            onSave = { amountCents, merchant, category, date ->
                viewModel.addExpense(
                    amountCents = amountCents,
                    merchant = merchant,
                    category = category,
                    date = date,
                    onResult = { ok ->
                        creating = false
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (ok) createdMessage else createFailedMessage,
                            )
                        }
                    },
                )
            },
        )
    }
}

/**
 * Buscador, contador y pista de deslizamiento.
 *
 * No es una cabecera fija de la pantalla, sino el primer elemento de la lista: al bajar se
 * va de la vista (igual que las filas) y al subir vuelve, dejando el hueco para ver mas
 * gastos. El estado vive en el ViewModel, asi que da igual que se recomponga o no.
 */
@Composable
private fun ListHeader(
    query: String,
    resultText: String,
    grouping: GroupingMode,
    onQueryChange: (String) -> Unit,
    onGroupingChange: (GroupingMode) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.expenses_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = resultText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GroupingSelector(selected = grouping, onSelect = onGroupingChange)
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Solo tiene sentido agrupar por dia cuando el orden es por fecha; si el usuario
 * ha pedido ordenar por importe, las cabeceras serian ruido.
 */
private fun buildEntries(
    expenses: List<Expense>,
    mode: GroupingMode,
): List<ListEntry> {
    // Los gastos llegan ordenados del mas reciente al mas antiguo, asi que al agrupar en un
    // LinkedHashMap el primer dia de cada tramo es el mas reciente y las cabeceras salen en el
    // orden correcto sin tener que reordenar nada.
    val grouped = LinkedHashMap<LocalDate, MutableList<Expense>>()
    expenses.forEach { expense ->
        val date = Dates.toLocalDate(expense.postedAt)
        val start = GroupingCalculator.periodStart(date, mode.bucket)
        grouped.getOrPut(start) { mutableListOf() }.add(expense)
    }
    return buildList {
        grouped.forEach { (start, items) ->
            add(
                ListEntry.PeriodHeader(
                    start = start,
                    end = GroupingCalculator.periodEnd(start, mode.bucket),
                    totalCents = items.sumOf { it.amountCents },
                    count = items.size,
                ),
            )
            items.forEach { add(ListEntry.Item(it)) }
        }
    }
}

/**
 * Cabecera de cada jornada: el dia a la izquierda y el total gastado ese dia a la derecha.
 *
 * El total va con mas cuerpo y color de marca que los importes de las filas, para que se lea
 * de un vistazo cuanto se gasto cada dia, y alineado al borde derecho de forma determinista
 * (el nombre del dia se queda con el ancho sobrante). El separador de debajo cierra el bloque
 * de esa jornada.
 */
@Composable
private fun PeriodHeaderRow(
    entry: ListEntry.PeriodHeader,
    mode: GroupingMode,
    locale: Locale = Locale.getDefault(),
) {
    val title = periodTitle(entry, mode, locale)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // El nombre del periodo se queda con el ancho sobrante (y se recorta si hace falta)
        // para que la cifra quede siempre pegada al borde derecho, sin depender de un Spacer.
        Text(
            text = title,
            style = SectionOverline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(entry.totalCents, locale),
            // Un escalon por encima del importe de las filas (titleMedium) para que el total
            // del periodo sea lo que primero se lee, y con cifras tabulares para que las
            // columnas de euros queden alineadas.
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/**
 * Nombre del tramo. El dia y el mes se nombran de forma natural ("martes 7 octubre",
 * "octubre 2026") y la semana muestra el rango de dias que cubre ("5 – 11 oct"), que es lo
 * unico que de verdad aporta su agrupacion.
 */
@Composable
private fun periodTitle(
    entry: ListEntry.PeriodHeader,
    mode: GroupingMode,
    locale: Locale,
): String {
    val today = LocalDate.now()
    val thisPeriod = GroupingCalculator.periodStart(today, mode.bucket)
    val previousPeriod = GroupingCalculator.periodStart(
        GroupingCalculator.periodEnd(entry.start, mode.bucket).minusDays(1),
        mode.bucket,
    )
    return when (mode) {
        GroupingMode.DAY -> when (entry.start) {
            today -> stringResource(R.string.expenses_group_today)
            today.minusDays(1) -> stringResource(R.string.expenses_group_yesterday)
            else -> entry.start.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        }
        GroupingMode.WEEK -> when (entry.start) {
            thisPeriod -> stringResource(R.string.expenses_group_this_week)
            previousPeriod -> stringResource(R.string.expenses_group_last_week)
            else -> stringResource(
                R.string.expenses_group_week_range,
                entry.start.format(DateTimeFormatter.ofPattern("d MMM", locale)),
                entry.end.format(DateTimeFormatter.ofPattern("d MMM", locale)),
            )
        }
        GroupingMode.MONTH -> when (entry.start) {
            thisPeriod -> stringResource(R.string.expenses_group_this_month)
            previousPeriod -> stringResource(R.string.expenses_group_last_month)
            else -> entry.start.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        }
    }
}

/** Selector dia / semana / mes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupingSelector(
    selected: GroupingMode,
    onSelect: (GroupingMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GroupingMode.entries.forEach { mode ->
            FilterChip(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                label = { Text(stringResource(mode.labelRes)) },
                shape = MaterialTheme.shapes.small,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = mode == selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
            )
        }
    }
}

/**
 * Fila que se desliza un poco hacia la izquierda para descubrir dos acciones (editar y
 * borrar) en lugar de desaparecer sola.
 *
 * El arrastre esta limitado al ancho de las acciones, asi que nunca se llega a "deslizar
 * fuera" el gasto: si se pasa del umbral la fila se ancla abierta, y si no, vuelve cerrada.
 * Solo una fila puede estar abierta a la vez, y al cerrarla el gasto no se borra: hace falta
 * tocar la papelera y confirmar.
 *
 * El desplazamiento pertenece al gasto (`remember(expense.id)`) y no al slot de la lista:
 * si se recordara solo por posicion, al cerrar una fila su estado "abierta" lo heredaria la
 * siguiente y esta quedaria desplazada sin haberlo pedido el usuario.
 */
@Composable
private fun SwipeableExpenseRow(
    expense: Expense,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val cardShape = MaterialTheme.shapes.medium
    val maxOffset = with(LocalDensity.current) { ActionsWidth.toPx() }

    // Dos valores, y no uno solo con un Animatable, por una razon concreta: `onDelta` de
    // `rememberDraggableState` no es suspend en esta version de Compose, asi que escribir
    // en un Animatable exigiria lanzar una corrutina por evento. Como cada `snapTo` cancela
    // al anterior, la mayoria se cancelarian antes de aplicarse y al soltar el dedo el
    // valor real seria casi cero (la fila volvio a cerrarse sola en las pruebas).
    // Aqui `dragOffset` se escribe de forma sincrona y exacta mientras se arrastra, y
    // `settleOffset` solo sirve para animar el asentamiento al soltar.
    var dragOffset by remember(expense.id) { mutableFloatStateOf(0f) }
    val settleOffset = remember(expense.id) { Animatable(0f) }
    var dragging by remember(expense.id) { mutableStateOf(false) }

    suspend fun animateOffsetTo(target: Float) {
        val current = if (dragging) dragOffset else settleOffset.value
        if (current == target) {
            dragOffset = target
            settleOffset.snapTo(target)
            return
        }
        dragging = false
        settleOffset.snapTo(current)
        settleOffset.animateTo(target, tween(SettleMillis))
        dragOffset = target
    }

    LaunchedEffect(expense.id, revealed) {
        if (!revealed) animateOffsetTo(0f)
    }

    val dragState = rememberDraggableState(
        // El dedo se mueve a la izquierda, asi que `delta` llega en negativo: el
        // desplazamiento "revelado" se guarda como positivo y se aplica en `graphicsLayer`
        // con signo negativo. Hay que invertir el signo aqui, porque si se sumara el delta
        // sobre un valor coercionado a [0, maxOffset] nunca se acumularia nada.
        onDelta = { delta ->
            // Si habia una animacion de cierre en curso, el arrastre retoma desde donde
            // estaba la tarjeta y no desde el ultimo valor del dedo.
            if (!dragging) dragOffset = settleOffset.value
            dragging = true
            dragOffset = (dragOffset - delta).coerceIn(0f, maxOffset)
        },
    )

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(ActionsWidth)
                .clip(cardShape),
        ) {
            RowAction(
                icon = Icons.Filled.Edit,
                label = stringResource(R.string.expenses_action_edit),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    onRevealChange(false)
                    onEdit()
                },
                modifier = Modifier.weight(1f),
            )
            RowAction(
                icon = Icons.Filled.Delete,
                label = stringResource(R.string.common_delete),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onClick = {
                    onRevealChange(false)
                    onDelete()
                },
                modifier = Modifier.weight(1f),
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val x = if (dragging) dragOffset else settleOffset.value
                    translationX = -x
                    // Al desplazar la tarjeta se recortan sus esquinas, para que no tape
                    // los botones que tiene delante.
                    clip = x > 0f
                    shape = cardShape
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = {
                        val open = dragOffset > maxOffset * OpenThreshold
                        animateOffsetTo(if (open) maxOffset else 0f)
                        onRevealChange(open)
                    },
                )
                .clickable {
                    if (dragOffset > 0f) {
                        onRevealChange(false)
                    } else {
                        onEdit()
                    }
                },
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                ExpenseRow(expense = expense)
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}

/** Boton de accion que aparece al desplazar la fila: icono y etiqueta. */
@Composable
private fun RowAction(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = content)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
