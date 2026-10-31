package com.mfreya.misgastos.ingest

import android.util.Log
import com.mfreya.misgastos.data.BatchIngest
import com.mfreya.misgastos.data.ExpenseRepository
import com.mfreya.misgastos.data.InsertOutcome
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.domain.parse.ExpensePreFilter
import com.mfreya.misgastos.domain.parse.NotificationTextParser
import com.mfreya.misgastos.domain.parse.ParsedExpense
import com.mfreya.misgastos.domain.parse.TransactionKind

sealed interface IngestResult {
    data class Inserted(val expense: Expense) : IngestResult

    /** El aviso se combino con un gasto del mismo pago que ya estaba guardado. */
    data class Merged(val expense: Expense) : IngestResult

    data object Duplicate : IngestResult
    data object NotAnExpense : IngestResult

    /** El aviso describe dinero que entra: nomina, bizum recibido, devolucion... */
    data object Income : IngestResult
    data object PackageNotAllowed : IngestResult
}

class NotificationIngestor(
    private val repository: ExpenseRepository,
    private val isPackageAllowed: (String) -> Boolean,
    private val parser: (String?, String) -> ParsedExpense? = NotificationTextParser::parse,
) {

    suspend fun ingest(
        sourcePackage: String,
        title: String?,
        body: String,
        postedAt: Long,
        rawText: String,
        ingestedAt: Long = System.currentTimeMillis(),
    ): IngestResult = ingestAll(
        items = listOf(
            PendingIngest(
                sourcePackage = sourcePackage,
                title = title,
                body = body,
                postedAt = postedAt,
                rawText = rawText,
            ),
        ),
        ingestedAt = ingestedAt,
    ).first()

    /**
     * Ingiere una rafaga de avisos en una sola llamada. Cada aviso se filtra y se parsea por
     * separado (son operaciones puras y no tocan la base de datos) y despues los que han
     * pasado se guardan juntos, en una unica transaccion.
     *
     * El resultado mantiene el mismo orden que [items].
     */
    suspend fun ingestAll(
        items: List<PendingIngest>,
        ingestedAt: Long = System.currentTimeMillis(),
    ): List<IngestResult> {
        if (items.isEmpty()) return emptyList()
        val results = MutableList<IngestResult>(items.size) { IngestResult.NotAnExpense }
        val batch = ArrayList<BatchIngest>(items.size)
        val positions = ArrayList<Int>(items.size)
        items.forEachIndexed { position, item ->
            when (val prepared = prepare(item, ingestedAt)) {
                is Prepared.Rejected -> results[position] = prepared.result
                is Prepared.Accepted -> {
                    batch.add(prepared.ingest)
                    positions.add(position)
                }
            }
        }
        if (batch.isNotEmpty()) {
            repository.ingestBatch(batch, MERGE_WINDOW_MILLIS).forEachIndexed { index, outcome ->
                results[positions[index]] = when (outcome) {
                    is InsertOutcome.Inserted -> {
                        Log.i(
                            TAG,
                            "guardada con id=${outcome.expense.id}, " +
                                "categoría=${outcome.expense.category}",
                        )
                        IngestResult.Inserted(outcome.expense)
                    }

                    is InsertOutcome.Merged -> {
                        Log.i(
                            TAG,
                            "combinada con la id=${outcome.expense.id}; " +
                                "app=${outcome.expense.sourcePackage}; " +
                                "conf=${outcome.expense.parseConfidence}",
                        )
                        IngestResult.Merged(outcome.expense)
                    }

                    InsertOutcome.Duplicate -> {
                        Log.i(TAG, "descartada: mismo aviso repetido con la misma fecha -> ${batch[index].sourcePackage}")
                        IngestResult.Duplicate
                    }
                }
            }
        }
        return results
    }

    /**
     * Filtra y parsea un aviso sin tocar la base de datos. Todo lo que se descarta por aqui
     * (ruido del movil, apps no permitidas) no llega a la cola de escritura.
     */
    private fun prepare(item: PendingIngest, ingestedAt: Long): Prepared {
        if (!isPackageAllowed(item.sourcePackage)) {
            Log.i(TAG, "descartada: app no permitida -> ${item.sourcePackage}")
            return Prepared.Rejected(IngestResult.PackageNotAllowed)
        }

        // Filtro previo: dos busquedas de regex y nada de base de datos. Descarta el
        // ruido del movil (correos, chats, avisos) sin llegar a parsear ni consultar nada.
        if (!ExpensePreFilter.looksLikeExpense(item.title, item.body)) {
            Log.i(
                TAG,
                "descartada: sin pistas de gasto -> ${item.sourcePackage}",
            )
            return Prepared.Rejected(IngestResult.NotAnExpense)
        }

        val parsed = parser(item.title, item.body)
            ?: run {
                Log.i(
                    TAG,
                    "descartada: no parece un gasto -> ${item.sourcePackage}",
                )
                return Prepared.Rejected(IngestResult.NotAnExpense)
            }

        if (parsed.kind == TransactionKind.INCOME) {
            Log.i(
                TAG,
                "descartada: dinero recibido, no es un gasto -> ${item.sourcePackage}",
            )
            return Prepared.Rejected(IngestResult.Income)
        }

        Log.i(TAG, "parseada: conf=${parsed.confidence}")

        return Prepared.Accepted(
            BatchIngest(
                parsed = parsed,
                postedAt = item.postedAt,
                rawText = item.rawText,
                sourcePackage = item.sourcePackage,
                sourceTitle = item.title.orEmpty(),
                ingestedAt = ingestedAt,
            ),
        )
    }

    /** Resultado de preparar un aviso: o entra en el lote, o queda descartado con su motivo. */
    private sealed interface Prepared {
        data class Accepted(val ingest: BatchIngest) : Prepared
        data class Rejected(val result: IngestResult) : Prepared
    }

    companion object {
        const val TAG = "MisGastosIngest"

        /**
         * Margen para dar por hecho que dos avisos son el mismo pago. Al pagar con Google Pay
         * avisan el banco y Google Wallet, y solo se guarda una fila con lo mejor de los dos.
         * Treinta segundos bastan de sobra para los dos avisos del mismo pago y evitan unir dos
         * compras distintas del mismo importe.
         */
        const val MERGE_WINDOW_MILLIS = 30 * 1000L
    }
}