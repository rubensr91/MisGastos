package com.mfreya.misgastos.data

import com.mfreya.misgastos.data.db.ExpenseDao
import com.mfreya.misgastos.data.db.ExpenseEntity
import com.mfreya.misgastos.data.db.SourcePackageCount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * DAO falsa en memoria, compartida por los tests de varias capas: sirve para el ingestor (que
 * necesita insertar, buscar gemelos por importe y actualizar) y para el repositorio (que
 * ademas crea gastos a mano). Reproduce el indice unico `(sourcePackage, postedAt,
 * amountCents)`: si choca, `insertIgnore` devuelve -1L en lugar de insertar.
 */
internal class FakeExpenseDao : ExpenseDao {
    val rows = MutableStateFlow<List<ExpenseEntity>>(emptyList())
    var nextId = 1L

    override fun observe(
        from: Long?,
        to: Long?,
        category: String?,
        query: String?,
        orderBy: Int,
    ): Flow<List<ExpenseEntity>> = rows.map { list ->
        list.filter { (from == null || it.postedAt >= from) && (to == null || it.postedAt < to) }
    }

    override suspend fun findById(id: Long): ExpenseEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun insertIgnore(entity: ExpenseEntity): Long {
        val exists = rows.value.any {
            it.sourcePackage == entity.sourcePackage &&
                it.postedAt == entity.postedAt &&
                it.amountCents == entity.amountCents
        }
        if (exists) return -1L
        val id = nextId++
        rows.value = rows.value + entity.copy(id = id)
        return id
    }

    override suspend fun insertIgnoreAll(entities: List<ExpenseEntity>): List<Long> =
        entities.map { insertIgnore(it) }

    override suspend fun updateCategory(id: Long, category: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(category = category) else it }
    }

    override suspend fun updateMerchant(id: Long, merchant: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(merchant = merchant) else it }
    }

    override suspend fun updateAmount(id: Long, amountCents: Long): Int {
        var changed = 0
        rows.value = rows.value.map {
            if (it.id == id) {
                changed = 1
                it.copy(amountCents = amountCents)
            } else {
                it
            }
        }
        return changed
    }

    override suspend fun deleteById(id: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.id == id }
        return before - rows.value.size
    }

    override suspend fun deleteAll() {
        rows.value = emptyList()
    }

    override fun observeCount(): Flow<Int> = rows.map { it.size }

    suspend fun count(): Int = rows.value.size

    override fun observeLowConfidence(threshold: Int, limit: Int): Flow<List<ExpenseEntity>> =
        rows.map { list -> list.filter { it.parseConfidence < threshold }.take(limit) }

    override fun observeAllRaw(): Flow<List<ExpenseEntity>> = rows

    override fun observeSourcePackageCounts(): Flow<List<SourcePackageCount>> =
        rows.map { all ->
            all.filter { it.sourcePackage.isNotBlank() && it.sourcePackage != "manual" }
                .groupingBy { it.sourcePackage }
                .eachCount()
                .map { (pkg, total) -> SourcePackageCount(pkg, total) }
                .sortedWith(compareByDescending<SourcePackageCount> { it.total }.thenBy { it.sourcePackage })
        }

    override suspend fun deleteBySourcePackage(sourcePackage: String): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { it.sourcePackage == sourcePackage }
        return before - rows.value.size
    }

    override fun observeSourcePackages(): Flow<List<String>> =
        rows.map { all -> all.map { it.sourcePackage }.filter { it.isNotBlank() }.distinct() }

    override fun observeCategories(): Flow<List<String>> =
        rows.map { all -> all.map { it.category }.filter { it.isNotBlank() }.distinct().sorted() }

    override suspend fun findRecentSameAmount(
        amountCents: Long,
        fromMillis: Long,
        toMillis: Long,
        limit: Int,
    ): List<ExpenseEntity> = rows.value
        .filter { it.amountCents == amountCents && it.postedAt in fromMillis..toMillis }
        .sortedByDescending { it.postedAt }
        .take(limit)

    private fun patch(id: Long, transform: (ExpenseEntity) -> ExpenseEntity): Int {
        var changed = 0
        rows.value = rows.value.map {
            if (it.id == id) {
                changed = 1
                transform(it)
            } else {
                it
            }
        }
        return changed
    }

    override suspend fun updateFromBestNotice(
        id: Long,
        merchant: String,
        merchantRaw: String,
        rawText: String,
        sourcePackage: String,
        sourceTitle: String,
        parseConfidence: Int,
        category: String,
    ): Int = patch(id) {
        it.copy(
            merchant = merchant,
            merchantRaw = merchantRaw,
            rawText = rawText,
            sourcePackage = sourcePackage,
            sourceTitle = sourceTitle,
            parseConfidence = parseConfidence,
            category = category,
        )
    }

    override suspend fun updateFromBestNoticeKeepingPackage(
        id: Long,
        merchant: String,
        merchantRaw: String,
        rawText: String,
        parseConfidence: Int,
        category: String,
    ): Int = patch(id) {
        it.copy(
            merchant = merchant,
            merchantRaw = merchantRaw,
            rawText = rawText,
            parseConfidence = parseConfidence,
            category = category,
        )
    }
}
