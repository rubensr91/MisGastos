package com.mfreya.misgastos.data

import com.mfreya.misgastos.data.db.ExpenseEntity
import com.mfreya.misgastos.data.db.ExpenseFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseRepositoryUpdateTest {

    private val dao = FakeExpenseDao()
    private val repository = ExpenseRepository(dao = dao)

    private suspend fun seed(): Long = dao.insertIgnore(
        ExpenseEntity(
            amountCents = 500L,
            currency = "EUR",
            merchant = "Tienda",
            merchantRaw = "Tienda",
            category = "Otros",
            postedAt = 1_000L,
            ingestedAt = 2_000L,
            rawText = "",
            sourcePackage = "manual",
            sourceTitle = "Manual",
            parseConfidence = 100,
        ),
    )

    @Test
    fun guardaImporteComercioYCategoriaDeUnaVez() = runBlocking {
        val id = seed()

        val ok = repository.updateExpense(id, 1_250L, "  Mercadona  ", "Supermercado")

        assertTrue(ok)
        val row = dao.findById(id)!!
        assertEquals(1_250L, row.amountCents)
        assertEquals("Mercadona", row.merchant)
        assertEquals("Supermercado", row.category)
    }

    @Test
    fun noTocaNadaSiElImporteNoSePuedeAplicar() = runBlocking {
        val id = seed()

        val ok = repository.updateExpense(9_999L, 1_250L, "Mercadona", "Supermercado")

        assertFalse(ok)
        val row = dao.findById(id)!!
        assertEquals(500L, row.amountCents)
        assertEquals("Tienda", row.merchant)
        assertEquals("Otros", row.category)
    }

    @Test
    fun rechazaImportesNoPositivos() = runBlocking {
        val id = seed()

        assertFalse(repository.updateExpense(id, 0L, "Mercadona", "Supermercado"))
        assertFalse(repository.updateExpense(id, -10L, "Mercadona", "Supermercado"))
        assertEquals(500L, dao.findById(id)!!.amountCents)
    }

    @Test
    fun elFiltroPorFechaExcluyeElLimiteSuperior() = runBlocking {
        seed()

        val hastaElMismoInstante = repository.observe(ExpenseFilter(to = 1_000L)).first()
        val hastaDespues = repository.observe(ExpenseFilter(to = 1_001L)).first()

        assertEquals(0, hastaElMismoInstante.size)
        assertEquals(1, hastaDespues.size)
    }
}