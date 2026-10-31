package com.mfreya.misgastos.domain.parse

/**
 * Punto de entrada único para interpretar una notificación de gasto.
 *
 * Cada banco escribe el texto a su manera, así que el parser puntúa los
 * importes candidatos y solo devuelve algo cuando hay suficiente evidencia.
 * El resultado incluye una confianza para que la app pueda pedir revisión
 * manual de los casos dudosos (pantalla de Ajustes).
 *
 * Kotlin puro, sin dependencias de Android: testeable en la JVM.
 */
object NotificationTextParser {

    const val CURRENCY = "EUR"

    private const val SCORE_CURRENCY = 40
    private const val SCORE_VERB = 30
    private const val SCORE_DECIMALS = 15
    private const val MIN_SCORE = 30

    private val SPENDING_VERB = Regex(
        "(?i)\\b(gastaste|gastado|pagaste|pagado|compra|pago|importe|cargo|transacci[oó]n)\\b"
    )

    private val EUR_WORD = Regex("(?i)\\beur\\b")
    private val HARD_SPACES = Regex("[\u00A0\u202F\u2007\u2009]")
    private val HORIZONTAL_SPACES = Regex("[ \\t]+")
    private val AROUND_NEWLINE = Regex(" *\n *")

    /**
     * @param title título de la notificación, suele contener "Gastaste ...".
     * @param body texto de la notificación, con el importe y el comercio.
     * @return null si no hay evidencia suficiente de un gasto.
     */
    fun parse(title: String?, body: String): ParsedExpense? {
        val normalizedBody = normalize(body)
        val normalizedTitle = title?.let { normalize(it) } ?: ""
        val combined = if (normalizedTitle.isEmpty()) {
            normalizedBody
        } else {
            "$normalizedTitle. $normalizedBody"
        }
        if (combined.isBlank()) return null

        val hasVerb = SPENDING_VERB.containsMatchIn(normalizedTitle) ||
            SPENDING_VERB.containsMatchIn(normalizedBody)

        val best = AmountParser.findAll(combined)
            .map { it to score(it, hasVerb) }
            .filter { (_, points) -> points >= MIN_SCORE }
            .maxWithOrNull(compareBy<Pair<AmountParser.Candidate, Int>> { it.second }.thenByDescending { it.first.start })
            ?: return null

        val (candidate, points) = best
        val merchant = MerchantParser.parse(combined, candidate.start, candidate.end)

        return ParsedExpense(
            amountCents = candidate.cents,
            currency = CURRENCY,
            merchant = merchant.name,
            merchantRaw = merchant.raw,
            confidence = confidenceFor(points, merchant.known),
            normalizedText = combined,
        )
    }

    private fun score(candidate: AmountParser.Candidate, hasVerb: Boolean): Int {
        var points = 0
        if (candidate.hasCurrency) points += SCORE_CURRENCY
        if (hasVerb) points += SCORE_VERB
        if (candidate.hasTwoDigitDecimal) points += SCORE_DECIMALS
        return points
    }

    private fun confidenceFor(points: Int, merchantKnown: Boolean): Int {
        val base = when {
            points >= 80 -> 90
            points >= 70 -> 80
            points >= 55 -> 60
            points >= MIN_SCORE -> 45
            else -> points
        }
        return if (merchantKnown) (base + 5).coerceAtMost(100) else base
    }

    /** Unifica espacios duros, colapsa blancos y traduce `EUR` a `€`. */
    fun normalize(text: String): String =
        text.replace(HARD_SPACES, " ")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(HORIZONTAL_SPACES, " ")
            .replace(AROUND_NEWLINE, "\n")
            .replace(EUR_WORD, "€")
            .trim()
}