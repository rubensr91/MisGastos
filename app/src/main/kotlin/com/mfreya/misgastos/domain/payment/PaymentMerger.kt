package com.mfreya.misgastos.domain.payment

import com.mfreya.misgastos.domain.parse.ParsedExpense
import java.text.Normalizer
import java.util.Locale

/**
 * Un aviso de pago ya parseado junto a su procedencia. Es lo que hace falta para decidir de
 * donde sale el dato bueno cuando dos avisos distintos describen el mismo pago (lo tipico:
 * el banco avisa y tambien lo hace Google Wallet, o al reves).
 */
data class MergeCandidate(
    val parsed: ParsedExpense,
    val sourcePackage: String,
    val sourceTitle: String,
    val rawText: String,
    val postedAt: Long,
    val ingestedAt: Long,
)

/**
 * Como queda la fila del gasto despues de combinar dos avisos del mismo pago. Cada campo
 * sale del aviso que mejor lo traia, no necesariamente del mismo, porque asi no se pierde
 * nada de ninguno de los dos.
 */
data class MergedFields(
    val merchant: String,
    val merchantRaw: String,
    val rawText: String,
    val sourcePackage: String,
    val sourceTitle: String,
    val parseConfidence: Int,
)

/**
 * Combina dos avisos del mismo pago quedandose con lo mejor de cada uno.
 *
 * El orden de llegada no importa: el mejor dato gana tanto si el aviso nuevo es el bueno como
 * si es el que ya estaba en la base de datos. No se fusiona por paquete ni por texto porque
 * en la vida real los dos avisos del mismo pago vienen de apps distintas y con textos que no
 * tienen nada que ver entre si.
 *
 * El comercio editado a mano por el usuario puede verse afectado solo si corrige un gasto y
 * en los siguientes segundos llega otro aviso del mismo importe; la ventana es de 30 s, asi
 * que en la practica no ocurre.
 */
object PaymentMerger {

    /** Peso de que el comercio sea conocido: manda sobre cualquier otra diferencia. */
    private const val KNOWN_MERCHANT_WEIGHT = 10_000
    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
    private val WHITESPACE = Regex("\\s+")

    /**
     * Puntuacion de calidad de un aviso: primero si sabe el comercio, luego lo que confio el
     * parser y por ultimo cuanto texto trae. Es un entero para que el desempate sea siempre
     * el mismo y no dependa del orden de llegada.
     */
    fun quality(candidate: MergeCandidate): Int {
        val known = if (candidate.parsed.merchantKnown) KNOWN_MERCHANT_WEIGHT else 0
        return known + candidate.parsed.confidence * 10 + candidate.rawText.length
    }

    fun better(existing: MergeCandidate, incoming: MergeCandidate): MergeCandidate =
        if (quality(incoming) >= quality(existing)) incoming else existing

    fun sameMerchant(existing: MergeCandidate, incoming: MergeCandidate): Boolean {
        if (!existing.parsed.merchantKnown || !incoming.parsed.merchantKnown) return true
        return normalizeMerchant(existing.parsed.merchant) == normalizeMerchant(incoming.parsed.merchant)
    }

    private fun normalizeMerchant(value: String): String {
        val folded = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
        return WHITESPACE.replace(folded, " ")
    }

    fun merge(existing: MergeCandidate, incoming: MergeCandidate): MergedFields {
        val winner = better(existing, incoming)
        return MergedFields(
            // El comercio y su version sin limpiar se quedan con el aviso que lo leyo mejor.
            merchant = winner.parsed.merchant,
            merchantRaw = winner.parsed.merchantRaw,
            // El texto original se queda con el mas largo: es el que mas informacion guarda,
            // aunque el otro aviso sea el que gane en comercio.
            rawText = longestText(existing.rawText, incoming.rawText),
            // El origen pasa a ser el del aviso bueno, para que en Ajustes se vea de donde
            // salio el dato que estamos enseñando.
            sourcePackage = winner.sourcePackage,
            // El titulo suele traer el nombre real del comercio ("BASILICA SANTA MARIA" en
            // vez de un codigo), asi que gana el mas largo.
            sourceTitle = longestText(existing.sourceTitle, incoming.sourceTitle),
            parseConfidence = maxOf(existing.parsed.confidence, incoming.parsed.confidence),
        )
    }

    private fun longestText(a: String, b: String): String = if (b.length > a.length) b else a
}
