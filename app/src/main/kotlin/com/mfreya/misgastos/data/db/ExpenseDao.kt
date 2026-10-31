package com.mfreya.misgastos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT * FROM expenses
        WHERE (:from IS NULL OR postedAt >= :from)
          AND (:to IS NULL OR postedAt < :to)
          AND (:category IS NULL OR category = :category)
          AND (
                :query IS NULL OR :query = ''
                OR merchant LIKE '%' || :query || '%'
                OR merchantRaw LIKE '%' || :query || '%'
                OR category LIKE '%' || :query || '%'
                OR rawText LIKE '%' || :query || '%'
          )
        ORDER BY
            CASE WHEN :orderBy = 1 THEN postedAt END ASC,
            CASE WHEN :orderBy = 2 THEN amountCents END DESC,
            CASE WHEN :orderBy = 3 THEN amountCents END ASC,
            postedAt DESC
        """,
    )
    fun observe(
        from: Long?,
        to: Long?,
        category: String?,
        query: String?,
        orderBy: Int,
    ): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: ExpenseEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoreAll(entities: List<ExpenseEntity>): List<Long>

    @Query("UPDATE expenses SET category = :category WHERE id = :id")
    suspend fun updateCategory(id: Long, category: String)

    @Query("UPDATE expenses SET merchant = :merchant WHERE id = :id")
    suspend fun updateMerchant(id: Long, merchant: String)

    @Query("UPDATE expenses SET amountCents = :amountCents WHERE id = :id")
    suspend fun updateAmount(id: Long, amountCents: Long): Int

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeCount(): Flow<Int>

    @Query(
        "SELECT * FROM expenses WHERE parseConfidence < :threshold ORDER BY postedAt DESC LIMIT :limit",
    )
    fun observeLowConfidence(threshold: Int, limit: Int): Flow<List<ExpenseEntity>>

    /**
     * Solo los paquetes emisores, sin traer el resto de columnas (que incluyen el texto
     * original). Ajustes necesita esta lista y no los gastos, asi que asking por
     * `DISTINCT` evita cargar toda la tabla en memoria en cada cambio.
     */
    @Query("SELECT DISTINCT sourcePackage FROM expenses WHERE sourcePackage != '' ORDER BY sourcePackage")
    fun observeSourcePackages(): Flow<List<String>>

    /** Cuantos gastos ha aportado cada app, para que el usuario vea quien le lee y decida. */
    @Query(
        """
        SELECT sourcePackage, COUNT(*) AS total FROM expenses
        WHERE sourcePackage != '' AND sourcePackage != 'manual'
        GROUP BY sourcePackage ORDER BY total DESC, sourcePackage
        """,
    )
    fun observeSourcePackageCounts(): Flow<List<SourcePackageCount>>

    /** Borra todos los gastos que vinieron de una app: el derecho a olvidar de verdad. */
    @Query("DELETE FROM expenses WHERE sourcePackage = :sourcePackage")
    suspend fun deleteBySourcePackage(sourcePackage: String): Int

    /**
     * Categorias que aparecen de verdad en los gastos. Sirve para ofrecer en los presupuestos
     * tambien las categorias propias que el usuario se haya creado, no solo el catalogo.
     */
    @Query("SELECT DISTINCT category FROM expenses WHERE category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query("SELECT * FROM expenses ORDER BY postedAt DESC")
    fun observeAllRaw(): Flow<List<ExpenseEntity>>

/**
     * Gastos con el mismo importe publicados dentro de la ventana de fusion.
     *
     * Solo se miran el importe y la hora a proposito: cuando pagas, el aviso del banco y el
     * de Google Wallet describen el mismo pago pero llegan de apps distintas y con textos que
     * no se parecen en nada, asi que comparar el emisor o el texto dejaria pasar los dobles.
     * El rango con `BETWEEN` aprovecha el indice de `postedAt`, y como la ventana son unos
     * segundos, los candidatos son pocos.
     */
    @Query(
        """
        SELECT * FROM expenses
        WHERE amountCents = :amountCents
          AND postedAt BETWEEN :fromMillis AND :toMillis
        ORDER BY postedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun findRecentSameAmount(
        amountCents: Long,
        fromMillis: Long,
        toMillis: Long,
        limit: Int,
    ): List<ExpenseEntity>

    /**
     * Deja la fila con lo mejor que traian los dos avisos del mismo pago. No toca el importe
     * ni la fecha (son los del primer aviso, que es el que conserva) ni la categoria, que se
     * recalcula en Kotlin con el clasificador antes de llamar aqui.
     */
    @Query(
        """
        UPDATE expenses SET
            merchant = :merchant,
            merchantRaw = :merchantRaw,
            rawText = :rawText,
            sourcePackage = :sourcePackage,
            sourceTitle = :sourceTitle,
            parseConfidence = :parseConfidence,
            category = :category
        WHERE id = :id
        """,
    )
    suspend fun updateFromBestNotice(
        id: Long,
        merchant: String,
        merchantRaw: String,
        rawText: String,
        sourcePackage: String,
        sourceTitle: String,
        parseConfidence: Int,
        category: String,
    ): Int

    /**
     * Igual que [updateFromBestNotice] pero sin cambiar el emisor, para el caso de que adoptarlo
     * choque con el indice unico `(sourcePackage, postedAt, amountCents)`.
     */
    @Query(
        """
        UPDATE expenses SET
            merchant = :merchant,
            merchantRaw = :merchantRaw,
            rawText = :rawText,
            parseConfidence = :parseConfidence,
            category = :category
        WHERE id = :id
        """,
    )
    suspend fun updateFromBestNoticeKeepingPackage(
        id: Long,
        merchant: String,
        merchantRaw: String,
        rawText: String,
        parseConfidence: Int,
        category: String,
    ): Int
}