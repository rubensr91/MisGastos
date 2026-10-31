package com.mfreya.misgastos.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import java.time.LocalDate
import java.time.ZoneId

class MonthCloseTest {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val today: LocalDate = LocalDate.of(2026, 3, 12)

    /** `month` en formato "AAAA-MM"; el gasto queda el dia 1 del mes. */
    private fun expense(month: String, cents: Long, id: Long = 0L): Expense {
        val parts = month.split("-")
        val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1).atStartOfDay(zone)
        return Expense(
            id = id,
            amountCents = cents,
            currency = "EUR",
            merchant = "M$id",
            merchantRaw = "M$id",
            category = Category.UNCLASSIFIED,
            postedAt = date.toInstant().toEpochMilli(),
            ingestedAt = 0L,
            rawText = "",
            sourcePackage = "p",
            sourceTitle = "",
            parseConfidence = 90,
        )
    }

    @Test
    fun agrupaPorMesNaturalConTotalesYMovimientos() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(
                expense("2026-03", 10_000, 1),
                expense("2026-03", 5_000, 2),
            ),
            months = 6,
            today = today,
        )
        val march = report.months.single()
        assertEquals(LocalDate.of(2026, 3, 1), march.periodStart)
        assertEquals(15_000L, march.totalCents)
        assertEquals(2, march.count)
        assertEquals(7_500L, march.averageCents)
    }

    @Test
    fun ordenaDelMesMasRecienteAlMasAntiguo() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(
                expense("2026-01", 1_000, 1),
                expense("2026-03", 3_000, 2),
                expense("2026-02", 2_000, 3),
            ),
            months = 6,
            today = today,
        )
        assertEquals(
            listOf("2026-03", "2026-02", "2026-01"),
            report.months.map { it.periodStart.toString().substring(0, 7) },
        )
    }

    @Test
    fun laVentanaLimitaCuantosMesesSeMuestran() {
        val expenses = (1..12).map { expense("2026-%02d".format(it), 100L * it, it.toLong()) }
        val three = MonthCloseCalculator.build(expenses, months = 3, today = today)
        assertEquals(3, three.months.size)
        assertEquals("2026-03", three.months.first().periodStart.toString().substring(0, 7))
        assertEquals("2026-01", three.months.last().periodStart.toString().substring(0, 7))
    }

    @Test
    fun rellenaLosMesesVaciosEntreElActualYElUltimoConDatos() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(expense("2026-03", 1_000), expense("2025-12", 2_000)),
            months = 12,
            today = today,
        )
        assertEquals(
            listOf("2026-03", "2026-02", "2026-01", "2025-12"),
            report.months.map { it.periodStart.toString().substring(0, 7) },
        )
        val empty = report.months.filter { it.count == 0 }
        assertEquals(2, empty.size)
        assertTrue(empty.all { it.totalCents == 0L && it.averageCents == 0L })
    }

    @Test
    fun laVariacionSubeCuandoSeGastaMas() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(expense("2026-02", 10_000), expense("2026-03", 15_000)),
            months = 6,
            today = today,
        )
        val march = report.months.first()
        assertEquals(50, march.variationPercent)
        assertEquals(10_000L, march.previousTotalCents)
        assertTrue(march.hasPrevious)
        assertEquals(true, march.isRising)
    }

    @Test
    fun laVariacionBajaCuandoSeGastaMenos() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(expense("2026-02", 20_000), expense("2026-03", 5_000)),
            months = 6,
            today = today,
        )
        val march = report.months.first()
        assertEquals(-75, march.variationPercent)
        assertEquals(false, march.isRising)
    }

    @Test
    fun sinMesAnteriorNoHayVariacion() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(expense("2026-03", 5_000)),
            months = 6,
            today = today,
        )
        val march = report.months.single()
        assertNull(march.variationPercent)
        assertFalse(march.hasPrevious)
        assertNull(march.isRising)
    }

    @Test
    fun elMesAnteriorSeCalculaAunqueQuedeFueraDeLaVentana() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(
                expense("2026-01", 10_000),
                expense("2026-02", 10_000),
                expense("2026-03", 20_000),
            ),
            months = 1,
            today = today,
        )
        // Solo se muestra marzo, pero su comparacion sigue siendo con febrero.
        val march = report.months.single()
        assertEquals("2026-03", march.periodStart.toString().substring(0, 7))
        assertEquals(100, march.variationPercent)
    }

    @Test
    fun sinGastosElInformeVieneVacio() {
        val report = MonthCloseCalculator.build(emptyList(), months = 6, today = today)
        assertTrue(report.months.isEmpty())
        assertEquals(0L, report.yearTotalCents)
        assertEquals(0, report.yearCount)
        assertEquals(2026, report.year)
    }

    @Test
    fun elTotalDelAnoSoloCuentaElAnoEnCurso() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(
                expense("2026-01", 1_000, 1),
                expense("2026-03", 2_000, 2),
                expense("2025-12", 90_000, 3),
                expense("2027-01", 70_000, 4),
            ),
            months = 6,
            today = today,
        )
        assertEquals(3_000L, report.yearTotalCents)
        assertEquals(2, report.yearCount)
        assertEquals(2026, report.year)
    }

    @Test
    fun unaVentanaDeCeroOMenosSeTrataComoUnSoloMes() {
        val expenses = listOf(expense("2026-03", 1_000), expense("2026-02", 2_000))
        assertEquals(1, MonthCloseCalculator.build(expenses, months = 0, today = today).months.size)
        assertEquals(1, MonthCloseCalculator.build(expenses, months = -5, today = today).months.size)
    }

    @Test
    fun elTicketMedioEsElTotalEntreLosMovimientos() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(
                expense("2026-03", 10_000, 1),
                expense("2026-03", 10_000, 2),
                expense("2026-03", 5_000, 3),
            ),
            months = 6,
            today = today,
        )
        assertEquals(8_333L, report.months.single().averageCents)
    }

    @Test
    fun losGastosDelMesActualSeCierranIgualQueLosViejos() {
        val report = MonthCloseCalculator.build(
            expenses = listOf(expense("2026-03", 4_200), expense("2026-02", 4_200)),
            months = 2,
            today = today,
        )
        assertEquals(0, report.months.first().variationPercent)
        assertEquals(false, report.months.first().isRising)
    }

    @Test
    fun lasOpcionesDeVentanaSonTresSeisYDoce() {
        assertEquals(listOf(3, 6, 12), MonthCloseCalculator.RANGE_OPTIONS)
    }
}