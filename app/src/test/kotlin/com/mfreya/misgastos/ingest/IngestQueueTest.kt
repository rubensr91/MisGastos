package com.mfreya.misgastos.ingest

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La cola agrupa notificaciones: se prueba con reloj virtual (`StandardTestDispatcher`), asi
 * que la ventana de 1,5 s no cuesta nada en tiempo real.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IngestQueueTest {

    private val base = 1_700_000_000_000L

    private fun item(n: Int) = PendingIngest(
        sourcePackage = "com.banco.demo",
        title = "Tu Banco",
        body = "Gastaste $n,00 EUR",
        postedAt = base + n,
        rawText = "Tu Banco. Gastaste $n,00 EUR",
    )

    @Test
    fun lasNotificacionesQueLlegenJuntasVanEnUnSoloLote() = runTest {
        val batches = mutableListOf<List<PendingIngest>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { batches += it }
        try {
            queue.submit(item(1))
            queue.submit(item(2))
            queue.submit(item(3))

            advanceUntilIdle()

            assertEquals(1, batches.size)
            assertEquals(
                listOf("Gastaste 1,00 EUR", "Gastaste 2,00 EUR", "Gastaste 3,00 EUR"),
                batches.single().map { it.body },
            )
            assertEquals(1, queue.processedBatches.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unSoloAvisoSeProcesaEnSuPropioLote() = runTest {
        val batches = mutableListOf<List<PendingIngest>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { batches += it }
        try {
            queue.submit(item(1))

            advanceUntilIdle()

            assertEquals(1, batches.size)
            assertEquals(1, batches.single().size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun loQueLlegaDuranteLaVentanaEntraEnElMismoLote() = runTest {
        val batches = mutableListOf<List<PendingIngest>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { batches += it }
        try {
            queue.submit(item(1))
            // El consumidor ya esta esperando en su ventana de agrupacion.
            advanceTimeBy(50)

            queue.submit(item(2))
            advanceUntilIdle()

            assertEquals(1, batches.size)
            assertEquals(2, batches.single().size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun loQueLlegaDespuesDeLaVentanaVaEnOtroLote() = runTest {
        val batches = mutableListOf<List<PendingIngest>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { batches += it }
        try {
            queue.submit(item(1))
            advanceUntilIdle()
            queue.submit(item(2))
            advanceUntilIdle()

            assertEquals(2, batches.size)
            assertEquals(1, batches[0].size)
            assertEquals(1, batches[1].size)
            assertEquals(2, queue.processedBatches.value)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unFalloEnUnLoteNoDetieneElConsumidor() = runTest {
        var llamadas = 0
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) {
            llamadas += 1
            if (llamadas == 1) throw IllegalStateException("lote problematico")
        }
        try {
            queue.submit(item(1))
            advanceUntilIdle()
            queue.submit(item(2))
            advanceUntilIdle()

            assertEquals(2, llamadas)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun losDatosSeEntreganTalCualVinieronDelAviso() = runTest {
        var recibido: PendingIngest? = null
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { recibido = it.single() }
        try {
            val original = item(7)
            queue.submit(original)

            advanceUntilIdle()

            assertEquals(original, recibido)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun laColaAguantaUnaAvalanchaDeAvisos() = runTest {
        val batches = mutableListOf<List<PendingIngest>>()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { batches += it }
        try {
            repeat(100) { queue.submit(item(it)) }

            advanceUntilIdle()

            // Sin limite de cola: los 100 avisos entran y se atendien en un solo lote.
            assertEquals(1, batches.size)
            assertEquals(100, batches.single().size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun elContadorDeLotesEmpiezaEnCero() = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val queue = IngestQueue(scope, windowMillis = 100) { }
        try {
            assertEquals(0, queue.processedBatches.value)
            assertTrue(queue.processedBatches.value >= 0)
        } finally {
            scope.cancel()
        }
    }
}