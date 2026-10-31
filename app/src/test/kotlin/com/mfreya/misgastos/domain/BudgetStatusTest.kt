package com.mfreya.misgastos.domain

import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetStatusTest {

    private fun expense(
        category: String,
        cents: Long,
        month: String = "2026-10",
        id: Long = 0L,
    ): Expense {
        val parts = month.split("-")
        val postedAt = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return Expense(
            id = id,
            amountCents = cents,
            currency = "EUR",
            merchant = "COMERCIO",
            merchantRaw = "COMERCIO",
            category = category,
            postedAt = postedAt,
            ingestedAt = postedAt,
            rawText = "Gastaste $cents en COMERCIO",
            sourcePackage = "com.banco",
            sourceTitle = "Tu Banco",
            parseConfidence = 90,
        )
    }

    @Test
    fun elProgresoEsLaParteDelLimiteGastada() {
        val status = BudgetCalculator.status("Supermercado", 25_000L, 100_000L)
        assertEquals(0.25f, status.progress, 0.0001f)
        assertTrue(status.hasLimit)
        assertFalse(status.isOver)
    }

    @Test
    fun elProgresoSeAcotaParaQueLaBarraNoSeSalga() {
        val status = BudgetCalculator.status("Ocio", 300_000L, 100_000L)
        assertEquals(1.0f, status.progress, 0.0001f)
        assertTrue(status.isOver)
        assertEquals(200_000L, status.overCents)
        assertEquals(0L, status.remainingCents)
    }

    @Test
    fun unLimiteDeCeroDejaElEstadoNeutral() {
        val status = BudgetCalculator.status("Ocio", 5_000L, 0L)
        assertFalse(status.hasLimit)
        assertEquals(0f, status.progress, 0.0001f)
        assertFalse(status.isOver)
        assertEquals(0L, status.remainingCents)
    }

    @Test
    fun unGastoNegativoNoAvanzaLaBarra() {
        val status = BudgetCalculator.status("Ocio", -100L, 100_000L)
        assertEquals(0f, status.progress, 0.0001f)
        assertEquals(100_000L, status.remainingCents)
    }

    @Test
    fun igualAlLimiteNoCuentaComoSuperado() {
        val status = BudgetCalculator.status("Ocio", 100_000L, 100_000L)
        assertEquals(1.0f, status.progress, 0.0001f)
        assertFalse(status.isOver)
        assertEquals(0L, status.overCents)
        assertEquals(0L, status.remainingCents)
    }

    @Test
    fun sinLimitesNoHayEstadosQueMostrar() {
        assertTrue(BudgetCalculator.limitsOf(emptyList(), listOf(expense("Ocio", 500L))).isEmpty())
    }

    @Test
    fun uneLosLimitesConElGastoDeCadaCategoria() {
        val limits = listOf(
            BudgetLimit("Supermercado", 100_000L),
            BudgetLimit("Combustible", 50_000L),
        )
        val expenses = listOf(
            expense("Supermercado", 40_000L),
            expense("Supermercado", 15_000L),
            expense("Combustible", 50_000L),
        )
        val statuses = BudgetCalculator.limitsOf(limits, expenses)
        assertEquals(2, statuses.size)
        val supermarket = statuses.first { it.category == "Supermercado" }
        val fuel = statuses.first { it.category == "Combustible" }
        assertEquals(55_000L, supermarket.spentCents)
        assertEquals(45_000L, supermarket.remainingCents)
        assertEquals(50_000L, fuel.spentCents)
        assertEquals(1.0f, fuel.progress, 0.0001f)
        assertFalse(fuel.isOver)
    }

    @Test
    fun unaCategoriaSinGastoApareceEnCero() {
        val statuses = BudgetCalculator.limitsOf(
            listOf(BudgetLimit("Deportes", 20_000L)),
            listOf(expense("Ocio", 900L)),
        )
        assertEquals(1, statuses.size)
        assertEquals(0L, statuses[0].spentCents)
        assertEquals(20_000L, statuses[0].remainingCents)
    }

    @Test
    fun ordenaPrimeroLoSuperadoYDespuesLoMasCercaDelLimite() {
        val limits = listOf(
            BudgetLimit("A", 100_000L),
            BudgetLimit("B", 100_000L),
            BudgetLimit("C", 100_000L),
        )
        val expenses = listOf(
            expense("A", 50_000L),
            expense("B", 200_000L),
            expense("C", 10_000L),
        )
        val statuses = BudgetCalculator.limitsOf(limits, expenses)
        assertEquals(listOf("B", "A", "C"), statuses.map { it.category })
    }

    @Test
    fun `a igualdadDeProgresoOrdenaPorCategoria`() {
        val statuses = BudgetCalculator.limitsOf(
            listOf(BudgetLimit("Zeta", 100_000L), BudgetLimit("Alfa", 100_000L)),
            listOf(expense("Zeta", 10_000L), expense("Alfa", 10_000L)),
        )
        assertEquals(listOf("Alfa", "Zeta"), statuses.map { it.category })
    }

    @Test
    fun elNombreDeLaCategoriaSeNormalizaAntesDeGuardarlo() {
        assertEquals(Category.UNCLASSIFIED, BudgetCalculator.normalize("   "))
        assertEquals("Ocio", BudgetCalculator.normalize("  Ocio  "))
    }
}
