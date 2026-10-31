package com.mfreya.misgastos.ingest

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Una notificacion pendiente de ingerir, con los datos ya extraidos del aviso.
 *
 * Se extraen aqui (y no mas tarde) porque despues el sistema puede reusar o soltar el
 * `Notification`: el contenido en memoria deja de ser valido en cuanto el aviso desaparece.
 */
data class PendingIngest(
    val sourcePackage: String,
    val title: String?,
    val body: String,
    val postedAt: Long,
    val rawText: String,
)

/**
 * Procesa las notificaciones en rafaga en vez de una por una.
 *
 * El sistema entrega los avisos uno a uno y casi siempre de uno en uno, asi que ingerir al
 * instante obliga a despertar el proceso y a escribir en disco por cada notificacion.
 * Aqui se encolan y se procesan juntas: se agrupa lo pendiente, se espera un momento por si
 * llega mas y se atiende el lote entero. Menoswakelocks y menos escrituras para el mismo
 * resultado.
 *
 * La cola no tiene limite (`UNLIMITED`) a proposito: `submit` nunca bloquea ni falla, que es
 * lo que hace falta en el hilo del sistema, y la loss de memoria es inviable (un aviso ocupa
 * unos pocos cientos de bytes y solo se encola durante milisegundos).
 */
class IngestQueue(
    private val scope: CoroutineScope,
    private val windowMillis: Long = DEFAULT_WINDOW_MILLIS,
    private val handler: suspend (List<PendingIngest>) -> Unit,
) {

    private val channel = Channel<PendingIngest>(Channel.UNLIMITED)

    private val _processedBatches = MutableStateFlow(0)

    /** Lotes ya atendidos. Solo para diagnostico y tests. */
    val processedBatches: StateFlow<Int> = _processedBatches.asStateFlow()

    init {
        scope.launch { consume() }
    }

    /** Encola un aviso. No suspende, no lanza y no bloquea. */
    fun submit(item: PendingIngest) {
        channel.trySend(item)
    }

    private suspend fun consume() {
        for (first in channel) {
            val batch = ArrayList<PendingIngest>(DEFAULT_BATCH_CAPACITY).apply { add(first) }
            drainInto(batch)
            // Ventana de agrupacion: si el sistema tiene mas avisos del mismo evento, llegan
            // seguidos y entran en el mismo lote.
            delay(windowMillis)
            drainInto(batch)
            _processedBatches.value += 1
            Log.i(TAG, "lote de ${batch.size} notificaciones")
            process(batch)
        }
    }

    private fun drainInto(batch: MutableList<PendingIngest>) {
        while (true) {
            batch.add(channel.tryReceive().getOrNull() ?: return)
        }
    }

    private suspend fun process(batch: List<PendingIngest>) {
        runCatching { handler(batch) }.onFailure { error ->
            // Un lote problematico no puede dejar el consumidor: al siguiente lote se sigue.
            Log.e(TAG, "fallo al ingerir un lote de ${batch.size} notificaciones", error)
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 1_500L

        private const val DEFAULT_BATCH_CAPACITY = 8
        private const val TAG = "MisGastosIngest"
    }
}