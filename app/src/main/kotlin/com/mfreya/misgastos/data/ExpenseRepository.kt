package com.mfreya.misgastos.data

import androidx.room.withTransaction
import com.mfreya.misgastos.data.category.ExpenseClassifier
import com.mfreya.misgastos.data.db.AppDatabase
import com.mfreya.misgastos.data.db.ExpenseDao
import com.mfreya.misgastos.data.db.ExpenseEntity
import com.mfreya.misgastos.data.db.ExpenseFilter
import com.mfreya.misgastos.data.db.ExpenseSort
import com.mfreya.misgastos.data.db.SourcePackageCount
import com.mfreya.misgastos.data.db.toDomain
import com.mfreya.misgastos.domain.model.Category
import com.mfreya.misgastos.domain.model.Expense
import com.mfreya.misgastos.domain.parse.MerchantParser
import com.mfreya.misgastos.domain.parse.ParsedExpense
import com.mfreya.misgastos.domain.payment.MergeCandidate
import com.mfreya.misgastos.domain.payment.PaymentMerger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface InsertOutcome {
    data class Inserted(val expense: Expense) : InsertOutcome

    /** El aviso se combino con un gasto del mismo pago que ya estaba guardado. */
    data class Merged(val expense: Expense) : InsertOutcome

    data object Duplicate : InsertOutcome
}

/** Un gasto ya parseado a la espera de guardarse. Sirve para ingerir lotes de una vez. */
data class BatchIngest(
    val parsed: ParsedExpense,
    val postedAt: Long,
    val rawText: String,
    val sourcePackage: String,
    val sourceTitle: String,
    val ingestedAt: Long,
)

class ExpenseRepository(
    private val dao: ExpenseDao,
    private val classifier: ExpenseClassifier? = null,
    /**
     * Solo hace falta para ingerir en lote (una sola transaccion por rafaga). En los tests
     * se puede dejar a `null`: entonces cada gasto va en su propia transaccion.
     */
    private val database: AppDatabase? = null,
    /** Reloj inyectable, para que los tests sean deterministas. */
    private val now: () -> Long = System::currentTimeMillis,
) {

    fun observe(filter: ExpenseFilter): Flow<List<Expense>> =
        dao.observe(
            from = filter.from,
            to = filter.to,
            category = filter.category,
            query = filter.query?.trim()?.takeIf { it.isNotEmpty() },
            orderBy = filter.sort.orderIndex,
        ).map { rows -> rows.map(ExpenseEntity::toDomain) }

    fun observeAll(): Flow<List<Expense>> = dao.observeAllRaw().map { rows ->
        rows.map(ExpenseEntity::toDomain)
    }

    fun observeCount(): Flow<Int> = dao.observeCount()

    /**
     * Paquetes que han emitido alguna vez un gasto guardado. El origen reservado a los gastos
     * escritos a mano no se cuela en la lista: no es una app que el usuario pueda desactivar.
     */
    fun observeSourcePackages(): Flow<List<String>> =
        dao.observeSourcePackages().map { packages -> packages.filter { it != MANUAL_SOURCE_PACKAGE } }

    /** Cuantos gastos aporta cada app, para poder mostrarselo al usuario en Ajustes. */
    fun observeSourcePackageCounts(): Flow<List<SourcePackageCount>> =
        dao.observeSourcePackageCounts()

    /** Olvida todo lo que haya guardado de una app. Devuelve cuantos gastos ha borrado. */
    suspend fun deleteFromPackage(sourcePackage: String): Int =
        dao.deleteBySourcePackage(sourcePackage)

    /** Categorias presentes en los gastos, incluidas las creadas a mano por el usuario. */
    fun observeCategories(): Flow<List<String>> = dao.observeCategories()

    fun observeNeedsReview(limit: Int = 10): Flow<List<Expense>> =
        dao.observeLowConfidence(Expense.LOW_CONFIDENCE_THRESHOLD, limit)
            .map { rows -> rows.map(ExpenseEntity::toDomain) }

    suspend fun ingest(
        parsed: ParsedExpense,
        postedAt: Long,
        rawText: String,
        sourcePackage: String,
        sourceTitle: String,
        ingestedAt: Long,
    ): InsertOutcome = insert(
        item = BatchIngest(parsed, postedAt, rawText, sourcePackage, sourceTitle, ingestedAt),
        mergeWindowMillis = DEFAULT_MERGE_WINDOW_MILLIS,
    )

    /**
     * Guarda un lote de avisos con una sola transaccion: una escritura en disco por rafaga en
     * vez de una por aviso. Ademas la combinacion de avisos del mismo pago ve lo ya insertado
     * en el mismo lote, asi que si banco y Google Wallet avisan casi a la vez solo queda una fila.
     */
    suspend fun ingestBatch(
        items: List<BatchIngest>,
        mergeWindowMillis: Long,
    ): List<InsertOutcome> {
        if (items.isEmpty()) return emptyList()
        val block: suspend () -> List<InsertOutcome> = { items.map { insert(it, mergeWindowMillis) } }
        return database?.let { db -> db.withTransaction { block() } } ?: block()
    }

    private suspend fun insert(item: BatchIngest, mergeWindowMillis: Long): InsertOutcome {
        val merged = mergeWithRecent(item, mergeWindowMillis)
        if (merged != null) return InsertOutcome.Merged(merged)
        val parsed = item.parsed
        val entity = ExpenseEntity(
            amountCents = parsed.amountCents,
            currency = parsed.currency,
            merchant = parsed.merchant,
            merchantRaw = parsed.merchantRaw,
            category = classifier?.classify(parsed.merchant, item.rawText) ?: Category.UNCLASSIFIED,
            postedAt = item.postedAt,
            ingestedAt = item.ingestedAt,
            rawText = item.rawText,
            sourcePackage = item.sourcePackage,
            sourceTitle = item.sourceTitle,
            parseConfidence = parsed.confidence,
        )
        val rowId = dao.insertIgnore(entity)
        if (rowId == INSERT_DUPLICATE) return InsertOutcome.Duplicate
        return InsertOutcome.Inserted(entity.copy(id = rowId).toDomain())
    }

    /**
     * Si ya hay un gasto del mismo importe en los ultimos [mergeWindowMillis], este aviso
     * describe el mismo pago que otro (lo normal: avisan el banco y Google Wallet) y se
     *_combina_ con el en vez de crear una fila nueva, quedandose con el dato bueno de cada uno.
     * Devuelve `null` cuando de verdad es un gasto nuevo.
     */
    private suspend fun mergeWithRecent(item: BatchIngest, mergeWindowMillis: Long): Expense? {
        // Un gasto escrito a mano no se combina con ningun aviso: si el usuario anota un pago
        // con tarjeta fisica y el banco avisa de ese mismo pago, son dos gastos distintos.
        val candidates = dao.findRecentSameAmount(
            amountCents = item.parsed.amountCents,
            fromMillis = item.postedAt - mergeWindowMillis,
            toMillis = item.postedAt + mergeWindowMillis,
            limit = MERGE_CANDIDATE_LIMIT,
        ).filter { it.sourcePackage != MANUAL_SOURCE_PACKAGE }
        val target = candidates.firstOrNull() ?: return null
        val fields = PaymentMerger.merge(target.toCandidate(), item.toCandidate())
        // La categoria se recalcula con el comercio que ha ganado: asi una regla nueva o una
        // correccion del usuario tambien se aplican a un gasto que ya estaba guardado. Si el
        // clasificador no sabe, se deja la que ya tenia para no degradarla.
        val category = classifier?.classify(fields.merchant, fields.rawText) ?: target.category
        val updated = runCatching {
            dao.updateFromBestNotice(
                id = target.id,
                merchant = fields.merchant,
                merchantRaw = fields.merchantRaw,
                rawText = fields.rawText,
                sourcePackage = fields.sourcePackage,
                sourceTitle = fields.sourceTitle,
                parseConfidence = fields.parseConfidence,
                category = category,
            )
        }.getOrElse {
            // Adoptar el emisor puede chocar con el indice unico; en ese caso se combina todo
            // menos el origen, que es el unico dato que se puede perder.
            dao.updateFromBestNoticeKeepingPackage(
                id = target.id,
                merchant = fields.merchant,
                merchantRaw = fields.merchantRaw,
                rawText = fields.rawText,
                parseConfidence = fields.parseConfidence,
                category = category,
            )
        }
        if (updated <= 0) return null
        return target.copy(
            merchant = fields.merchant,
            merchantRaw = fields.merchantRaw,
            rawText = fields.rawText,
            sourcePackage = fields.sourcePackage,
            sourceTitle = fields.sourceTitle,
            parseConfidence = fields.parseConfidence,
            category = category,
        ).toDomain()
    }

    /**
     * Guarda un gasto escrito a mano por el usuario: un pago con tarjeta fisica, en efectivo o
     * cualquier otra cosa de la que no llega aviso.
     *
     * Como no viene de ninguna notificacion se marca con el origen [MANUAL_SOURCE_PACKAGE] y con
     * la confianza maxima, asi que nunca aparecera en la lista de "revisar" ni se combinara con
     * el aviso de un banco que llegue por el mismo importe. Si el usuario deja la categoria en
     * "Sin categoria" se deduce con las reglas, y la que elija se memoriza para ese comercio,
     * igual que cuando corrige un gasto ya guardado.
     */
    suspend fun createExpense(
        amountCents: Long,
        merchant: String,
        category: String,
        postedAt: Long,
    ): Expense {
        require(amountCents > 0L) { "El importe de un gasto manual tiene que ser mayor que cero" }
        val cleanMerchant = merchant.trim().take(48).ifEmpty { MerchantParser.UNKNOWN }
        val chosen = Category.normalize(category)
        // Solo se aprende lo que elige el usuario: si la categoria se deduce con las reglas
        // no hay que fijarla como si fuera una correccion suya, o un acierto erroneo de
        // las reglas se quedaria pegado para siempre.
        val chosenByUser = chosen != Category.UNCLASSIFIED
        val category = if (chosenByUser) {
            chosen
        } else {
            classifier?.classify(cleanMerchant, "") ?: chosen
        }
        val stored = insertManual(
            ExpenseEntity(
                amountCents = amountCents,
                currency = "EUR",
                merchant = cleanMerchant,
                merchantRaw = cleanMerchant,
                category = category,
                postedAt = postedAt,
                ingestedAt = now(),
                rawText = "",
                sourcePackage = MANUAL_SOURCE_PACKAGE,
                sourceTitle = "",
                parseConfidence = MANUAL_CONFIDENCE,
            ),
        )
        if (chosenByUser) {
            classifier?.learn(cleanMerchant, category)
        }
        return stored
    }

    /**
     * Inserta el gasto manual reintentando con el milisegundo siguiente si choca con el indice
     * unico `(sourcePackage, postedAt, amountCents)`. Solo puede pasar si se anotan dos gastos
     * del mismo importe en el mismo milisegundo, y en ese caso interesa guardar los dos.
     */
    private suspend fun insertManual(entity: ExpenseEntity): Expense {
        var candidate = entity
        repeat(MANUAL_INSERT_ATTEMPTS) {
            val rowId = dao.insertIgnore(candidate)
            if (rowId != INSERT_DUPLICATE) return candidate.copy(id = rowId).toDomain()
            candidate = candidate.copy(postedAt = candidate.postedAt + 1)
        }
        throw IllegalStateException("No se pudo guardar el gasto manual")
    }

    suspend fun updateCategory(id: Long, category: String) {
        dao.updateCategory(id, Category.normalize(category))
    }

    /**
     * Cambia la categoria desde la UI. Si [learn] es `true` (por defecto) y la categoria no es
     * "Sin categoria", la correccion queda memorizada para los proximos gastos de ese mismo
     * comercio.
     */
    suspend fun setCategory(
        expense: Expense,
        category: String,
        learn: Boolean = true,
    ) {
        val normalized = Category.normalize(category)
        updateCategory(expense.id, normalized)
        if (learn && normalized != Category.UNCLASSIFIED) {
            classifier?.learn(expense.merchant, normalized)
        }
    }

    suspend fun updateMerchant(id: Long, merchant: String) {
        val clean = merchant.trim()
        if (clean.isNotEmpty()) dao.updateMerchant(id, clean.take(48))
    }

    /**
     * Corrige el importe guardado. Devuelve `false` si la fila ya no existe o si el cambio
     * choca con el indice unico `(sourcePackage, postedAt, amountCents)`: por eso el
     * llamante debe envolver la llamada en `runCatching`.
     */
    suspend fun updateAmount(id: Long, amountCents: Long): Boolean {
        if (amountCents <= 0L) return false
        return dao.updateAmount(id, amountCents) > 0
    }

    /**
     * Guarda importe, comercio y categoria en una sola transaccion. Devuelve `false` si el
     * importe no se pudo aplicar (fila borrada o choque con el indice unico), en cuyo caso no
     * se toca nada mas. La categoria se aprende con el comercio NUEVO.
     */
    suspend fun updateExpense(
        id: Long,
        amountCents: Long,
        merchant: String,
        category: String,
    ): Boolean {
        if (amountCents <= 0L) return false
        val clean = merchant.trim().take(48)
        val normalized = Category.normalize(category)
        suspend fun apply(): Boolean {
            if (dao.updateAmount(id, amountCents) <= 0) return false
            if (clean.isNotEmpty()) dao.updateMerchant(id, clean)
            dao.updateCategory(id, normalized)
            return true
        }
        val ok = database?.withTransaction { apply() } ?: apply()
        if (ok && clean.isNotEmpty() && normalized != Category.UNCLASSIFIED) {
            classifier?.learn(clean, normalized)
        }
        return ok
    }

    suspend fun delete(id: Long): Boolean = dao.deleteById(id) > 0

    suspend fun restore(expense: Expense): Boolean =
        dao.insertIgnore(expense.toEntity()) != INSERT_DUPLICATE

    suspend fun deleteAll() = dao.deleteAll()

    companion object {
        /** Origen reservado a los gastos que escribe el usuario: no los manda ninguna app. */
        const val MANUAL_SOURCE_PACKAGE = "manual"

        /** Confianza de un gasto escrito a mano: el dato viene del usuario, no de un parseo. */
        const val MANUAL_CONFIDENCE = 100

        private const val INSERT_DUPLICATE = -1L

        /**
         * Margen para dar por hecho que dos avisos del mismo pago. Los dos del banco y Google
         * Wallet llegan con uno o dos segundos de diferencia, asi que 30 s sobra y minimiza el
         * riesgo de unir dos compras distintas del mismo importe.
         */
        const val DEFAULT_MERGE_WINDOW_MILLIS = 30_000L

        /** Solo se mira el primero: si hay varios, son pagos distintos que coinciden en importe. */
        const val MERGE_CANDIDATE_LIMIT = 5

        /** Intentos al guardar un gasto manual antes de rendirse (choque con el indice unico). */
        const val MANUAL_INSERT_ATTEMPTS = 3
    }
}

private fun BatchIngest.toCandidate(): MergeCandidate = MergeCandidate(
    parsed = parsed,
    sourcePackage = sourcePackage,
    sourceTitle = sourceTitle,
    rawText = rawText,
    postedAt = postedAt,
    ingestedAt = ingestedAt,
)

/** Reconstruye el aviso guardado en la base de datos como si acabara de llegar. */
private fun ExpenseEntity.toCandidate(): MergeCandidate = MergeCandidate(
    parsed = ParsedExpense(
        amountCents = amountCents,
        currency = currency,
        merchant = merchant,
        merchantRaw = merchantRaw,
        confidence = parseConfidence,
        normalizedText = rawText,
    ),
    sourcePackage = sourcePackage,
    sourceTitle = sourceTitle,
    rawText = rawText,
    postedAt = postedAt,
    ingestedAt = ingestedAt,
)

fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    amountCents = amountCents,
    currency = currency,
    merchant = merchant,
    merchantRaw = merchantRaw,
    category = category,
    postedAt = postedAt,
    ingestedAt = ingestedAt,
    rawText = rawText,
    sourcePackage = sourcePackage,
    sourceTitle = sourceTitle,
    parseConfidence = parseConfidence,
)

val ExpenseSort.orderIndex: Int
    get() = when (this) {
        ExpenseSort.DATE_DESC -> 0
        ExpenseSort.DATE_ASC -> 1
        ExpenseSort.AMOUNT_DESC -> 2
        ExpenseSort.AMOUNT_ASC -> 3
    }