package com.mfreya.misgastos.data

import com.mfreya.misgastos.data.category.ExpenseClassifier
import com.mfreya.misgastos.data.db.MerchantCategoryDao
import com.mfreya.misgastos.data.db.MerchantCategoryEntity
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.parse.ParsedExpense
import com.mfreya.misgastos.ingest.IngestResult
import com.mfreya.misgastos.ingest.NotificationIngestor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gastos escritos a mano desde la app (tarjeta fisica, efectivo, pagos que no avisan):
 * se marcan con un origen reservado para que no se confundan con un aviso ni se fundan con
 * otro del mismo importe.
 */
class ExpenseRepositoryManualTest {

    private val fixedTime = 1_700_000_000_000L

    private val learned = MutableStateFlow<List<MerchantCategoryEntity>>(emptyList())

    private val merchantCategoryDao = object : MerchantCategoryDao {
        override fun observeAll(): Flow<List<MerchantCategoryEntity>> = learned
        override fun observeCount(): Flow<Int> = learned.map { it.size }
        override suspend fun find(merchantKey: String): MerchantCategoryEntity? =
            learned.value.firstOrNull { it.merchantKey == merchantKey }

        override suspend fun upsert(entity: MerchantCategoryEntity) {
            learned.value = learned.value.filterNot { it.merchantKey == entity.merchantKey } + entity
        }

        override suspend fun forget(merchantKey: String) {
            learned.value = learned.value.filterNot { it.merchantKey == merchantKey }
        }

        override suspend fun forgetAll() {
            learned.value = emptyList()
        }
    }

    private fun repository(
        dao: FakeExpenseDao,
        withClassifier: Boolean = true,
    ) = ExpenseRepository(
        dao = dao,
        classifier = if (withClassifier) ExpenseClassifier(merchantCategoryDao) else null,
        now = { fixedTime },
    )

    @Test
    fun elGastoManualSeGuardaConOrigenReservadoYConfianzaPlena() = runBlocking {
        val dao = FakeExpenseDao()

        val gasto = repository(dao).createExpense(2_540L, "Farmacia Central", Category.UNCLASSIFIED, 60_000L)

        assertEquals(ExpenseRepository.MANUAL_SOURCE_PACKAGE, gasto.sourcePackage)
        assertEquals(ExpenseRepository.MANUAL_CONFIDENCE, gasto.parseConfidence)
        assertFalse(gasto.needsReview)
        assertEquals("EUR", gasto.currency)
        assertEquals("", gasto.rawText)
        assertEquals(60_000L, gasto.postedAt)
        assertEquals(fixedTime, gasto.ingestedAt)
        assertEquals(1, dao.count())
    }

    @Test
    fun siNoSeEligeCategoriaSeDeduceDelComercio() = runBlocking {
        val dao = FakeExpenseDao()

        val gasto = repository(dao).createExpense(6_000L, "REPSOL", Category.UNCLASSIFIED, 60_000L)

        assertEquals("Combustible", gasto.category)
        // Lo deducido por las reglas no se fija como preferencia del usuario.
        assertTrue(learned.value.isEmpty())
    }

    @Test
    fun siNoSeEligeCategoriaYNoHayReglaQuedaSinClasificar() = runBlocking {
        val dao = FakeExpenseDao()

        val gasto = repository(dao).createExpense(1_000L, "Tienda del pueblo", Category.UNCLASSIFIED, 60_000L)

        assertEquals(Category.UNCLASSIFIED, gasto.category)
    }

    @Test
    fun laCategoriaElegidaSeRespetaYQuedaAprendida() = runBlocking {
        val dao = FakeExpenseDao()

        val gasto = repository(dao).createExpense(7_500L, "Zara", "Freya", 60_000L)

        assertEquals("Freya", gasto.category)
        assertEquals(listOf("zara"), learned.value.map { it.merchantKey })
        assertEquals("Freya", learned.value.single().category)
        // Y el clasificador ya la aplica a los siguientes gastos de ese comercio.
        assertEquals(
            "Freya",
            repository(dao).createExpense(8_000L, "ZARA", Category.UNCLASSIFIED, 120_000L).category,
        )
    }

    @Test
    fun sinClasificadorLaCategoriaElegidaTambienSeRespeta() = runBlocking {
        val dao = FakeExpenseDao()

        val gasto = repository(dao, withClassifier = false).createExpense(1_000L, "Zara", "Freya", 60_000L)

        assertEquals("Freya", gasto.category)
        assertTrue(learned.value.isEmpty())
    }

    @Test
    fun dosGastosManualesDelMismoImporteEnElMismoMilisegundoSeGuardanLosDos() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = repository(dao)

        val primero = sut.createExpense(500L, "Cafe Uno", Category.UNCLASSIFIED, 60_000L)
        val segundo = sut.createExpense(500L, "Cafe Dos", Category.UNCLASSIFIED, 60_000L)

        assertTrue(primero.id != segundo.id)
        assertEquals(2, dao.count())
        assertEquals(60_001L, dao.rows.value.last().postedAt)
    }

    @Test
    fun elGastoManualNuncaSeFundeConElAvisoDelBanco() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = repository(dao)
        val aviso = NotificationIngestor(sut, { true }) { _, _ ->
            ParsedExpense(
                amountCents = 1_500L,
                currency = "EUR",
                merchant = "REPSOL",
                merchantRaw = "REPSOL",
                confidence = 95,
                normalizedText = "",
            )
        }

        sut.createExpense(1_500L, "Peaje", Category.UNCLASSIFIED, 60_000L)
        val resultado = aviso.ingest("de.banco", "REPSOL", "Gastaste 15,00 EUR en REPSOL", 61_000L, "banco", 61_000L)

        assertTrue(resultado is IngestResult.Inserted)
        assertEquals(2, dao.count())
        assertEquals("Peaje", dao.rows.value.first { it.sourcePackage == ExpenseRepository.MANUAL_SOURCE_PACKAGE }.merchant)
    }

    @Test
    fun elAvisoDelBancoTampocoSeFundeConElGastoManualCuandoLlegaDespues() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = repository(dao)
        val aviso = NotificationIngestor(sut, { true }) { _, _ ->
            ParsedExpense(
                amountCents = 1_500L,
                currency = "EUR",
                merchant = "REPSOL",
                merchantRaw = "REPSOL",
                confidence = 95,
                normalizedText = "",
            )
        }

        aviso.ingest("de.banco", "REPSOL", "Gastaste 15,00 EUR en REPSOL", 60_000L, "banco", 60_000L)
        sut.createExpense(1_500L, "Peaje", Category.UNCLASSIFIED, 61_000L)

        assertEquals(2, dao.count())
    }

    @Test
    fun elOrigenManualNoApareceEnLaListaDeEmisoras() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = repository(dao)
        NotificationIngestor(sut, { true }) { _, _ ->
            ParsedExpense(1_000L, "EUR", "Mercadona", "Mercadona", 95, "")
        }.ingest("de.banco", "Mercadona", "Gastaste 10,00 EUR en Mercadona", 60_000L, "banco", 60_000L)
        sut.createExpense(1_000L, "Efectivo", Category.UNCLASSIFIED, 61_000L)

        assertEquals(listOf("de.banco"), sut.observeSourcePackages().first())
    }

    @Test
    fun elComercioSeRecortaANormalizaYSiEstaVacioQuedaDesconocido() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = repository(dao)

        val largo = sut.createExpense(
            1_000L,
            "  Comercio con un nombre bastante mas largo de la cuenta entera  ",
            Category.UNCLASSIFIED,
            60_000L,
        )
        val vacio = sut.createExpense(1_000L, "   ", Category.UNCLASSIFIED, 61_000L)

        // Se recortan los espacios de los extremos y despues se limita a 48 caracteres.
        assertEquals(48, largo.merchant.length)
        assertTrue(largo.merchant.startsWith("Comercio con un nombre bastante mas largo de la "))
        assertEquals(largo.merchant, largo.merchantRaw)
        assertEquals("Desconocido", vacio.merchant)
    }

    @Test
    fun unImporteNoPositivoSeRechaza() = runBlocking {
        val dao = FakeExpenseDao()
        val sut = repository(dao)

        listOf(0L, -1L).forEach { importe ->
            val error = runCatching {
                runBlocking { sut.createExpense(importe, "Peaje", Category.UNCLASSIFIED, 60_000L) }
            }.exceptionOrNull()
            assertTrue(error is IllegalArgumentException)
        }
        assertEquals(0, dao.count())
    }
}
