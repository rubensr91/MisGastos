package com.mfreya.misgastos.data.category

import com.mfreya.misgastos.data.db.MerchantCategoryDao
import com.mfreya.misgastos.data.db.MerchantCategoryEntity
import com.mfreya.misgastos.domain.category.CategoryRule
import com.mfreya.misgastos.domain.category.CategoryRules
import com.mfreya.misgastos.domain.category.MerchantKeys
import com.mfreya.misgastos.domain.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Regla memorizada a partir de una correccion manual del usuario. */
data class LearnedCategory(
    val merchantKey: String,
    val category: String,
    val hits: Int,
    val learnedAt: Long,
)

/**
 * Decide la categoria de un gasto. Primero consulta lo que el usuario ya corrigio a mano (mas una
 * coincidencia exacta de la clave memorizada o, en su defecto, la coincidencia de tokens mas
 * larga) y solo si no hay nada aprendido aplica el catalogo de reglas.
 *
 * Todo ocurre en local: no hay red, ni modelo descargado, ni permisos adicionales.
 */
class ExpenseClassifier(
    private val dao: MerchantCategoryDao,
    private val rules: List<CategoryRule> = CategoryRules.DEFAULT,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Cache de lo aprendido, para no ir a la base de datos en cada notificacion. */
    private val cache = HashMap<String, String>()
    private var cacheLoaded = false

    /** Devuelve la categoria deducida, o `null` si no hay suficiente evidencia. */
    suspend fun classify(merchant: String, rawText: String): String? {
        loadCache()
        learnedFor(merchant)?.let { return it }
        return CategoryRules.match(merchant, rawText, rules)
    }

    /**
     * Memoriza que [merchant] pertenece a [category]. Reemplaza la categoria si ya habia otra.
     *
     * La categoria sin clasificar significa "sin opinion", asi que nunca se memoriza: si el usuario
     * deja un gasto sin categoria, los siguientes de ese mismo comercio se vuelven a clasificar por
     * reglas en lugar de quedar marcados como sin clasificar.
     */
    suspend fun learn(merchant: String, category: String) {
        val normalizedCategory = Category.normalize(category)
        if (normalizedCategory == Category.UNCLASSIFIED) return
        val key = MerchantKeys.normalize(merchant)
        if (key.isEmpty()) return

        val existing = dao.find(key)
        dao.upsert(
            MerchantCategoryEntity(
                merchantKey = key,
                category = normalizedCategory,
                hits = (existing?.hits ?: 0) + 1,
                learnedAt = existing?.learnedAt ?: now(),
            ),
        )
        cache[key] = normalizedCategory
        cacheLoaded = true
    }

    suspend fun forget(merchantKey: String) {
        dao.forget(merchantKey)
        cache.remove(merchantKey)
        cacheLoaded = true
    }

    suspend fun forgetAll() {
        dao.forgetAll()
        cache.clear()
        cacheLoaded = true
    }

    fun observeLearned(): Flow<List<LearnedCategory>> =
        dao.observeAll().map { rows -> rows.map { it.toLearned() } }

    fun observeLearnedCount(): Flow<Int> = dao.observeCount()

    /** Descarta la cache para releer de la base de datos (tras migraciones o restauraciones). */
    fun invalidate() {
        cache.clear()
        cacheLoaded = false
    }

    private suspend fun loadCache() {
        if (cacheLoaded) return
        val rows = dao.observeAll().first()
        cache.clear()
        rows.forEach { cache[it.merchantKey] = it.category }
        cacheLoaded = true
    }

    /**
     * Busca en la cache lo aprendido. Primero la clave exacta y, si no hay, la entrada con mas
     * tokens cuya secuencia aparezca dentro del comercio: asi una regla aprendida como
     * `repsol` tambien sirve para `repsol estacion servicio`.
     */
    private fun learnedFor(merchant: String): String? {
        val exact = cache[MerchantKeys.normalize(merchant)]
        if (exact != null) return exact

        val merchantTokens = MerchantKeys.tokenize(merchant)
        if (merchantTokens.isEmpty()) return null
        var bestKey: String? = null
        var bestSize = 0
        for (key in cache.keys) {
            val needle = MerchantKeys.tokenize(key)
            if (needle.size <= bestSize) continue
            if (MerchantKeys.containsTokens(merchantTokens, needle)) {
                bestKey = key
                bestSize = needle.size
            }
        }
        return bestKey?.let(cache::get)
    }

    private fun MerchantCategoryEntity.toLearned() =
        LearnedCategory(
            merchantKey = merchantKey,
            category = category,
            hits = hits,
            learnedAt = learnedAt,
        )
}