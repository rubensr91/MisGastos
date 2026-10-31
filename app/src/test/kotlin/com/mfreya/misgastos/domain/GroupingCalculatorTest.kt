package com.mfreya.misgastos.domain

import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class GroupingCalculatorTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun expense(
        merchant: String,
        amountCents: Long,
        postedAt: LocalDate,
        category: String = Category.UNCLASSIFIED,
        id: Long = 0L,
    ) = Expense(
        id = id,
        amountCents = amountCents,
        currency = "EUR",
        merchant = merchant,
        merchantRaw = merchant,
        category = category,
        postedAt = postedAt.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli(),
        ingestedAt = postedAt.atStartOfDay(zone).toInstant().toEpochMilli(),
        rawText = "Gastaste ${amountCents / 100.0} EUR en $merchant",
        sourcePackage = "com.banco",
        sourceTitle = "Tu Banco",
        parseConfidence = 95,
    )

    private val day: LocalDate = LocalDate.of(2026, 3, 12)

    @Test
    fun agrupaPorEstablecimientoSumandoImportes() {
        val groups = GroupingCalculator.byMerchant(
            listOf(
                expense("MERCADONA", 1250, day),
                expense("MERCADONA", 750, day.minusDays(1)),
                expense("REPSOL", 6000, day),
            ),
        )

        assertEquals(2, groups.size)
        assertEquals("REPSOL", groups[0].key)
        assertEquals(6000L, groups[0].totalCents)
        assertEquals("MERCADONA", groups[1].key)
        assertEquals(2000L, groups[1].totalCents)
        assertEquals(2, groups[1].count)
    }

    @Test
    fun losGruposOrdenanPorTotalDescendenteYDesempatanPorNombre() {
        val groups = GroupingCalculator.byMerchant(
            listOf(
                expense("ALFA", 1000, day),
                expense("BETA", 1000, day),
                expense("GAMMA", 3000, day),
            ),
        )

        assertEquals(listOf("GAMMA", "ALFA", "BETA"), groups.map { it.key })
    }

    @Test
    fun agrupaPorCategoriaYRespetaElTextoDeLaCategoria() {
        val groups = GroupingCalculator.byCategory(
            listOf(
                expense("MERCADONA", 1250, day, Category.normalize("Supermercado")),
                expense("CARREFOUR", 800, day, Category.normalize("Supermercado")),
                expense("NETFLIX", 1299, day, Category.normalize("Suscripciones")),
                expense("SIN CLASIFICAR", 500, day),
            ),
        )

        assertEquals(3, groups.size)
        assertEquals("Supermercado", groups[0].key)
        assertEquals(2050L, groups[0].totalCents)
        assertEquals("Suscripciones", groups[1].key)
        assertEquals(Category.UNCLASSIFIED, groups[2].key)
    }

    @Test
    fun losTramosDiariosSeparanCadaDia() {
        val groups = GroupingCalculator.byPeriod(
            listOf(
                expense("A", 100, day),
                expense("B", 200, day.minusDays(1)),
                expense("C", 300, day),
            ),
            PeriodBucket.DAY,
        )

        assertEquals(2, groups.size)
        assertEquals(day, groups[0].periodStart)
        assertEquals(day, groups[0].periodEnd)
        assertEquals(400L, groups[0].totalCents)
        assertEquals(2, groups[0].count)
        assertEquals(day.minusDays(1), groups[1].periodStart)
    }

    @Test
    fun losTramosSemanalesEmpiezanElLunes() {
        // 2026-03-12 es jueves: la semana empieza el lunes 9.
        val groups = GroupingCalculator.byPeriod(
            listOf(
                expense("A", 100, day),
                expense("B", 250, LocalDate.of(2026, 3, 9)),
                expense("C", 400, LocalDate.of(2026, 3, 8)),
            ),
            PeriodBucket.WEEK,
        )

        assertEquals(2, groups.size)
        assertEquals(LocalDate.of(2026, 3, 9), groups[0].periodStart)
        assertEquals(LocalDate.of(2026, 3, 15), groups[0].periodEnd)
        assertEquals(350L, groups[0].totalCents)
        assertEquals(LocalDate.of(2026, 3, 2), groups[1].periodStart)
        assertEquals(400L, groups[1].totalCents)
    }

    @Test
    fun losTramosMensualesCubrenTodoElMes() {
        val groups = GroupingCalculator.byPeriod(
            listOf(
                expense("A", 100, LocalDate.of(2026, 1, 5)),
                expense("B", 200, LocalDate.of(2026, 1, 31)),
                expense("C", 400, LocalDate.of(2026, 2, 1)),
            ),
            PeriodBucket.MONTH,
        )

        assertEquals(2, groups.size)
        assertEquals(LocalDate.of(2026, 2, 1), groups[0].periodStart)
        assertEquals(LocalDate.of(2026, 2, 28), groups[0].periodEnd)
        assertEquals(LocalDate.of(2026, 1, 1), groups[1].periodStart)
        assertEquals(LocalDate.of(2026, 1, 31), groups[1].periodEnd)
        assertEquals(300L, groups[1].totalCents)
    }

    @Test
    fun losTramosNoInventanPeriodosVacios() {
        val groups = GroupingCalculator.byPeriod(
            listOf(expense("A", 100, LocalDate.of(2026, 1, 5))),
            PeriodBucket.MONTH,
        )

        assertEquals(1, groups.size)
    }

    @Test
    fun losGastosDeCadaGrupoVanDelMasRecienteAlMasAntiguo() {
        val groups = GroupingCalculator.byMerchant(
            listOf(
                expense("MERCADONA", 100, day.minusDays(3)),
                expense("MERCADONA", 300, day),
                expense("MERCADONA", 200, day.minusDays(1)),
            ),
        )

        assertEquals(listOf(300L, 200L, 100L), groups.single().expenses.map { it.amountCents })
    }

    @Test
    fun elPorcentajeDeCadaGrupoSumaHastaCien() {
        val expenses = listOf(
            expense("A", 1000, day),
            expense("B", 2000, day),
            expense("C", 7000, day),
        )

        val groups = GroupingCalculator.byMerchant(expenses)
        val total = StatsCalculator.total(expenses)
        val share = groups.map { it.shareOf(total) }

        assertEquals(1f, share.sum(), 0.001f)
        assertEquals(0.7f, groups.first().shareOf(total), 0.001f)
    }

    @Test
    fun elTicketMedioSeCalculaSobreLosCentiamos() {
        val group = GroupingCalculator.byMerchant(
            listOf(expense("A", 1000, day), expense("A", 500, day)),
        ).single()

        assertEquals(750L, group.averageCents())
    }

    @Test
    fun elTicketMedioEsCeroSiElGrupoEstaVacio() {
        val group = ExpenseGroup(key = "X", totalCents = 0L, count = 0, expenses = emptyList())

        assertEquals(0L, group.averageCents())
        assertEquals(0f, group.shareOf(1000L), 0.001f)
        assertNull(group.periodStart)
    }

    @Test
    fun elPorcentajeEsCeroSiElTotalEsCero() {
        val group = ExpenseGroup(key = "X", totalCents = 500L, count = 1, expenses = emptyList())

        assertEquals(0f, group.shareOf(0L), 0.001f)
    }

    @Test
    fun laCategoriaDominanteEsLaMasRepetida() {
        val group = GroupingCalculator.byMerchant(
            listOf(
                expense("MIXTO", 100, day, "Ocio"),
                expense("MIXTO", 200, day, "Restaurante"),
                expense("MIXTO", 300, day, "Restaurante"),
            ),
        ).single()

        assertEquals("Restaurante", group.dominantCategory())
    }

    @Test
    fun laCategoriaDominanteCaeEnSinCategoriaSiNoHayGastos() {
        val group = ExpenseGroup(key = "X", totalCents = 0L, count = 0, expenses = emptyList())

        assertEquals(Category.UNCLASSIFIED, group.dominantCategory())
    }

    @Test
    fun elGrupoSeEligeSegunElModo() {
        val expenses = listOf(
            expense("MERCADONA", 1000, day, "Supermercado"),
            expense("REPSOL", 6000, day, "Combustible"),
        )

        assertEquals(listOf("REPSOL", "MERCADONA"), GroupingCalculator.group(expenses, GroupMode.MERCHANT).map { it.key })
        assertEquals(listOf("Combustible", "Supermercado"), GroupingCalculator.group(expenses, GroupMode.CATEGORY).map { it.key })
        assertEquals(1, GroupingCalculator.group(expenses, GroupMode.PERIOD).size)
    }

    @Test
    fun laClaveDeUnTramoEsLaFechaIsoDelInicio() {
        val groups = GroupingCalculator.byPeriod(listOf(expense("A", 100, day)), PeriodBucket.MONTH)

        assertEquals("2026-03-01", groups.single().key)
        assertNotNull(groups.single().periodStart)
    }

    @Test
    fun sinGastosNoHayGrupos() {
        assertTrue(GroupingCalculator.byMerchant(emptyList()).isEmpty())
        assertTrue(GroupingCalculator.byCategory(emptyList()).isEmpty())
        assertTrue(GroupingCalculator.byPeriod(emptyList(), PeriodBucket.DAY).isEmpty())
    }

    @Test
    fun elInicioDelTramaSeCalculaParaCadaGranularidad() {
        assertEquals(day, GroupingCalculator.periodStart(day, PeriodBucket.DAY))
        assertEquals(LocalDate.of(2026, 3, 9), GroupingCalculator.periodStart(day, PeriodBucket.WEEK))
        assertEquals(LocalDate.of(2026, 3, 1), GroupingCalculator.periodStart(day, PeriodBucket.MONTH))
    }

    @Test
    fun elFinDelTramaSeCalculaParaCadaGranularidad() {
        // periodEnd trabaja sobre el inicio del tramo, no sobre un dia suelto.
        assertEquals(day, GroupingCalculator.periodEnd(day, PeriodBucket.DAY))
        assertEquals(LocalDate.of(2026, 3, 15), GroupingCalculator.periodEnd(LocalDate.of(2026, 3, 9), PeriodBucket.WEEK))
        assertEquals(LocalDate.of(2026, 3, 24), GroupingCalculator.periodEnd(LocalDate.of(2026, 3, 18), PeriodBucket.WEEK))
        assertEquals(LocalDate.of(2026, 3, 31), GroupingCalculator.periodEnd(LocalDate.of(2026, 3, 1), PeriodBucket.MONTH))
        assertEquals(LocalDate.of(2026, 2, 28), GroupingCalculator.periodEnd(LocalDate.of(2026, 2, 1), PeriodBucket.MONTH))
    }

    @Test
    fun laLongitudDelTramaCuentaLosDias() {
        assertEquals(1L, GroupingCalculator.periodLengthDays(day, day))
        assertEquals(7L, GroupingCalculator.periodLengthDays(day, day.plusDays(6)))
        assertEquals(31L, GroupingCalculator.periodLengthDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)))
        assertEquals(28L, GroupingCalculator.periodLengthDays(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
    }

    /** Coloca el gasto en un instante exacto, para probar los bordes del tramo. */
    private fun at(base: java.time.LocalDate, millis: Long) =
        expense("X", 1000, base).copy(postedAt = millis)

    @Test
    fun sinTramoNoFiltraNada() {
        val expenses = listOf(expense("A", 1000, day), expense("B", 2000, day.minusMonths(1)))
        assertEquals(expenses, GroupingCalculator.inRange(expenses, null))
    }

    @Test
    fun elTramoIncluyeLosDosExtremos() {
        val expenses = listOf(
            expense("ANTES", 1000, day.minusDays(1)),
            expense("INICIO", 1000, day),
            expense("MEDIO", 1000, day.plusDays(2)),
            expense("FIN", 1000, day.plusDays(3)),
            expense("DESPUES", 1000, day.plusDays(4)),
        )
        val kept = GroupingCalculator.inRange(expenses, DateRange(day, day.plusDays(3)))
        assertEquals(
            listOf("INICIO", "MEDIO", "FIN"),
            kept.map { it.merchant },
        )
    }

    @Test
    fun unTramoDeUnSoloDiaSeQuedaConEseDia() {
        val expenses = listOf(
            expense("AYER", 1000, day.minusDays(1)),
            expense("ESE", 1000, day),
            expense("MAÑANA", 1000, day.plusDays(1)),
        )
        val kept = GroupingCalculator.inRange(expenses, DateRange(day, day))
        assertEquals(1, kept.size)
        assertEquals("ESE", kept.single().merchant)
    }

    @Test
    fun elTramoAbarcaTodoElDiaFinal() {
        // El ultimo dia entra completo: se compara contra el inicio del dia siguiente.
        val expenses = listOf(
            at(day.plusDays(1), GroupingCalculator.startOfDayMillis(day.plusDays(2)) - 1L),
            at(day.plusDays(1), GroupingCalculator.startOfDayMillis(day.plusDays(2))),
        )
        val kept = GroupingCalculator.inRange(expenses, DateRange(day, day.plusDays(1)))
        assertEquals(1, kept.size)
    }

    @Test
    fun unTramoInvertidoSeNormaliza() {
        val expenses = listOf(expense("A", 1000, day), expense("B", 1000, day.plusDays(2)))
        val kept = GroupingCalculator.inRange(expenses, DateRange(day.plusDays(2), day))
        assertEquals(2, kept.size)
        // day..day+2 son tres dias, contando los dos extremos.
        assertEquals(3L, DateRange(day.plusDays(2), day).normalized().lengthDays())
    }

    @Test
    fun elTramoSeCombinaConLaAgrupacionPorEstablecimiento() {
        val expenses = listOf(
            expense("MERCADONA", 1250, day),
            expense("MERCADONA", 750, day.plusDays(5)),
            expense("REPSOL", 6000, day.plusDays(9)),
        )
        val grouped = GroupingCalculator.byMerchant(
            GroupingCalculator.inRange(expenses, DateRange(day, day.plusDays(5))),
        )
        assertEquals(1, grouped.size)
        assertEquals("MERCADONA", grouped.single().key)
        assertEquals(2000L, grouped.single().totalCents)
    }

    @Test
    fun unTramoDeUnDiaSeAgrupaPorDia() {
        val expenses = listOf(expense("A", 1000, day), expense("B", 2000, day))
        val groups = GroupingCalculator.byPeriod(
            GroupingCalculator.inRange(expenses, DateRange(day, day)),
            PeriodBucket.DAY,
        )
        assertEquals(1, groups.size)
        assertEquals(day, groups.single().periodStart)
        assertEquals(day, groups.single().periodEnd)
        assertEquals(3000L, groups.single().totalCents)
    }

    @Test
    fun unTramoSinGastosDevuelveUnaListaVacia() {
        val expenses = listOf(expense("A", 1000, day))
        assertTrue(GroupingCalculator.inRange(expenses, DateRange(day.plusMonths(2), day.plusMonths(2))).isEmpty())
    }
}
