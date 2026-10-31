package com.mfreya.misgastos.data

import com.mfreya.misgastos.data.db.ExpenseEntity
import com.mfreya.misgastos.data.db.SourcePackageCount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lo que el usuario ve en "Apps de las que se leen avisos" y el derecho a olvidar. */
class ExpenseSourcePackageTest {

    private fun entity(pkg: String, id: Long, cents: Long = 1000L) = ExpenseEntity(
        id = id,
        amountCents = cents,
        currency = "EUR",
        merchant = "Comercio $id",
        merchantRaw = "Comercio $id",
        category = "Sin categoría",
        postedAt = 1_000L * id,
        ingestedAt = 1_000L * id,
        rawText = "",
        sourcePackage = pkg,
        sourceTitle = "",
        parseConfidence = 95,
    )

    @Test
    fun cuentaCuantosGastosAportaCadaApp() = runBlocking {
        val dao = FakeExpenseDao()
        dao.rows.value = listOf(
            entity("de.banco", 1), entity("de.banco", 2), entity("de.banco", 3),
            entity("com.wallet", 4),
        )

        val counts = dao.observeSourcePackageCounts().first()

        assertEquals(2, counts.size)
        assertEquals("de.banco", counts[0].sourcePackage)
        assertEquals(3, counts[0].total)
        assertEquals("com.wallet", counts[1].sourcePackage)
        assertEquals(1, counts[1].total)
    }

    @Test
    fun elRecuentoIgnoraLosGastosAnadidosAMano() = runBlocking {
        val dao = FakeExpenseDao()
        dao.rows.value = listOf(entity("de.banco", 1), entity("manual", 2))

        val counts = dao.observeSourcePackageCounts().first()

        assertEquals(1, counts.size)
        assertEquals("de.banco", counts.single().sourcePackage)
    }

    @Test
    fun olvidarUnaAppBorraSoloLoSuyo() = runBlocking {
        val dao = FakeExpenseDao()
        dao.rows.value = listOf(
            entity("de.banco", 1), entity("de.banco", 2), entity("com.wallet", 3),
        )
        val repository = ExpenseRepository(dao)

        val deleted = repository.deleteFromPackage("de.banco")

        assertEquals(2, deleted)
        assertEquals(1, dao.rows.value.size)
        assertEquals("com.wallet", dao.rows.value.single().sourcePackage)
    }

    @Test
    fun olvidarUnaAppQueNoHaMandadoNadaNoBorraNiFalla() = runBlocking {
        val dao = FakeExpenseDao()
        dao.rows.value = listOf(entity("de.banco", 1))

        assertEquals(0, ExpenseRepository(dao).deleteFromPackage("com.otro"))
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun laListaDeEmisorasSigueOcultandoLosGastosManuales() = runBlocking {
        val dao = FakeExpenseDao()
        dao.rows.value = listOf(entity("de.banco", 1), entity("manual", 2))

        assertEquals(listOf("de.banco"), ExpenseRepository(dao).observeSourcePackages().first())
        assertTrue(ExpenseRepository(dao).observeSourcePackages().first().none { it == "manual" })
    }
}
