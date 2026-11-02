package com.mfreya.misgastos.data

import com.mfreya.misgastos.data.db.ExpenseDao
import com.mfreya.misgastos.data.db.ExpenseEntity
import com.mfreya.misgastos.data.db.toDomain
import com.mfreya.misgastos.domain.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseRepositorySelectionTest {

    private val dao = FakeExpenseDao()
    private val repository = ExpenseRepository(dao = dao)

    private suspend fun seed(amountCents: Long, merchant: String, postedAt: Long): Long =
        dao.insertIgnore(
            ExpenseEntity(
                amountCents = amountCents,
                currency = "EUR",
                merchant = merchant,
                merchantRaw = merchant,
                category = "Otros",
                postedAt = postedAt,
                ingestedAt = postedAt,
                rawText = "",
                sourcePackage = "manual",
                sourceTitle = "Manual",
                parseConfidence = 100,
            ),
        )

    private suspend fun toExpense(id: Long): Expense = dao.findById(id)!!.toDomain()

    @Test
    fun deleteAllBorraVariosIdsYRetornaCount() = runBlocking {
        val e1 = seed(1000, "Tienda A", 1_000L)
        val e2 = seed(2000, "Tienda B", 2_000L)
        val e3 = seed(3000, "Tienda C", 3_000L)

        val count = repository.deleteAll(listOf(e1, e2))

        assertEquals(2, count)
        assertNull(dao.findById(e1))
        assertNull(dao.findById(e2))
        assertNotNull(dao.findById(e3))
    }

    @Test
    fun deleteAllConListaVaciaRetornaCero() = runBlocking {
        seed(1000, "Tienda A", 1_000L)

        val count = repository.deleteAll(emptyList())

        assertEquals(0, count)
        assertEquals(1, dao.count())
    }

    @Test
    fun deleteAllConIdInexistenteSoloBorraLosQueExisten() = runBlocking {
        val e1 = seed(1000, "Tienda A", 1_000L)

        val count = repository.deleteAll(listOf(e1, 9_999L))

        assertEquals(1, count)
        assertNull(dao.findById(e1))
    }

    @Test
    fun mergeExpensesCreaGastoConSumaYBorraOriginales() = runBlocking {
        val e1 = seed(1000, "Tienda A", 1_000L) // primary (mas reciente)
        val e2 = seed(2000, "Tienda B", 2_000L) // secondary

        val primary = toExpense(e1)
        val secondary = toExpense(e2)

        val merged = repository.mergeExpenses(
            primary = primary,
            secondary = secondary,
            amountCents = primary.amountCents + secondary.amountCents,
            merchant = primary.merchant,
            category = primary.category,
            postedAt = primary.postedAt,
        )

        assertNotNull(merged)
        assertEquals(3000L, merged.amountCents)
        assertEquals("Tienda A", merged.merchant)
        assertEquals("Otros", merged.category)
        assertEquals(1_000L, merged.postedAt)

        // Los originales se borraron
        assertNull(dao.findById(e1))
        assertNull(dao.findById(e2))
        // El nuevo existe
        assertNotNull(dao.findById(merged.id))
    }

    @Test
    fun mergeExpensesUsaFechaDelMasReciente() = runBlocking {
        val e1 = seed(1000, "Tienda A", 5_000L) // mas reciente
        val e2 = seed(2000, "Tienda B", 1_000L)

        val primary = toExpense(e1)
        val secondary = toExpense(e2)

        val merged = repository.mergeExpenses(
            primary = primary,
            secondary = secondary,
            amountCents = 3000L,
            merchant = "Comercio X",
            category = "Supermercado",
            postedAt = primary.postedAt,
        )

        assertEquals(5_000L, merged.postedAt)
    }

    @Test
    fun deshacerDeleteAllRestauraTodosLosBorrados() = runBlocking {
        val e1 = seed(1000, "Tienda A", 1_000L)
        val e2 = seed(2000, "Tienda B", 2_000L)
        // Obtener los gastos ANTES de borrar (restore necesita el objeto Expense completo)
        val gasto1 = toExpense(e1)
        val gasto2 = toExpense(e2)

        repository.deleteAll(listOf(e1, e2))
        assertNull(dao.findById(e1))
        assertNull(dao.findById(e2))

        // Deshacer: restaurar cada uno
        val r1 = repository.restore(gasto1)
        val r2 = repository.restore(gasto2)

        assertTrue(r1)
        assertTrue(r2)
        // Los gastos restaurados existen (pueden tener id distinto al original)
        assertEquals(2, dao.count())
        val restaurados = dao.observeAllRaw().first()
        assertTrue(restaurados.any { it.merchant == "Tienda A" && it.amountCents == 1000L })
        assertTrue(restaurados.any { it.merchant == "Tienda B" && it.amountCents == 2000L })
    }

    @Test
    fun deshacerMergeBorraCreadoYRestauraOriginales() = runBlocking {
        val e1 = seed(1000, "Tienda A", 1_000L)
        val e2 = seed(2000, "Tienda B", 2_000L)

        val primary = toExpense(e1)
        val secondary = toExpense(e2)

        val merged = repository.mergeExpenses(
            primary = primary,
            secondary = secondary,
            amountCents = 3000L,
            merchant = primary.merchant,
            category = primary.category,
            postedAt = primary.postedAt,
        )!!

        assertNotNull(dao.findById(merged.id))
        assertNull(dao.findById(e1))
        assertNull(dao.findById(e2))

        // Deshacer: borrar el creado y restaurar los originales
        repository.delete(merged.id)
        val r1 = repository.restore(primary)
        val r2 = repository.restore(secondary)

        assertTrue(r1)
        assertTrue(r2)
        assertNull(dao.findById(merged.id))
        // Los originales existen (pueden tener id distinto al original)
        assertEquals(2, dao.count())
        val restaurados = dao.observeAllRaw().first()
        assertTrue(restaurados.any { it.merchant == "Tienda A" && it.amountCents == 1000L })
        assertTrue(restaurados.any { it.merchant == "Tienda B" && it.amountCents == 2000L })
    }
}
