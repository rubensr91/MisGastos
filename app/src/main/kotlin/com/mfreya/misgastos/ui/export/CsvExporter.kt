package com.mfreya.misgastos.ui.export

import android.content.Context
import android.net.Uri
import com.mfreya.misgastos.domain.model.Expense
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CsvExporter {

    const val MIME_TYPE = "text/csv"

    private const val SEPARATOR = ','
    private const val BOM = '\uFEFF'
    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@', '\t')

    private val header: List<String> = listOf(
        "fecha",
        "hora",
        "importe",
        "moneda",
        "comercio",
        "categoria",
        "app_origen",
        "confianza",
        "texto_original",
    )

    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "mis-gastos-$today.csv"

    fun build(
        expenses: List<Expense>,
        locale: Locale = Locale.ROOT,
    ): String {
        val zone = ZoneId.systemDefault()
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        val builder = StringBuilder()
        builder.append(header.joinToString(SEPARATOR.toString()) { escape(it) })
        builder.append('\n')
        for (expense in expenses) {
            val moment = Instant.ofEpochMilli(expense.postedAt).atZone(zone)
            val cells = listOf(
                dateFormat.format(moment),
                timeFormat.format(moment),
                String.format(
                    Locale.ROOT,
                    "%d.%02d",
                    expense.amountCents / 100,
                    expense.amountCents % 100,
                ),
                expense.currency,
                expense.merchantRaw.ifBlank { expense.merchant },
                expense.category,
                expense.sourcePackage,
                expense.parseConfidence.toString(),
                expense.rawText,
            )
            builder.append(cells.joinToString(SEPARATOR.toString()) { escape(it) })
            builder.append('\n')
        }
        return builder.toString()
    }

    suspend fun write(context: Context, uri: Uri, csv: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream, Charsets.UTF_8).apply {
                        write(BOM.toString())
                        write(csv)
                        flush()
                    }
                } ?: error("No se pudo abrir el fichero de destino")
            }.isSuccess
        }

    private fun escape(value: String): String {
        val flattened = value
            .replace("\r\n", " ")
            .replace("\n", " ")
            .replace("\r", " ")
        val trimmed = flattened.trimStart()
        val neutralized = if (trimmed.isNotEmpty() && trimmed.first() in FORMULA_PREFIXES) {
            "'$flattened"
        } else {
            flattened
        }
        val needsQuotes = neutralized.any { it == SEPARATOR || it == '"' }
        if (!needsQuotes) return neutralized
        return "\"${neutralized.replace("\"", "\"\"")}\""
    }
}
