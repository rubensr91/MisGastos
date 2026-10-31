package com.mfreya.misgastos.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Dates {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun formatDate(millis: Long, locale: Locale = Locale.getDefault()): String =
        toLocalDate(millis).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone)
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale))

    fun formatDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

    fun formatDayMonth(millis: Long, locale: Locale = Locale.getDefault()): String =
        toLocalDate(millis).format(DateTimeFormatter.ofPattern("d MMM", locale))

    /**
     * Combina el dia elegido con la hora actual.
     *
     * Asi un gasto anotado ahora no sale con la hora a la que se abrio el dialogo, sino con la
     * de ahora mismo: si el usuario apunta un pago de hace un rato, la hora tiene que ser la
     * real y no las 00:00.
     */
    fun withCurrentTime(date: LocalDate, now: Long = System.currentTimeMillis()): Long {
        val time = Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        return date.atStartOfDay(zone)
            .plusHours(time.hour.toLong())
            .plusMinutes(time.minute.toLong())
            .plusSeconds(time.second.toLong())
            .plusNanos(time.nano.toLong())
            .toInstant()
            .toEpochMilli()
    }
}
