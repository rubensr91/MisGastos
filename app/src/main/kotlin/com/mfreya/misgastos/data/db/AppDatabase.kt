package com.mfreya.misgastos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ExpenseEntity::class, MerchantCategoryEntity::class, BudgetEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    abstract fun merchantCategoryDao(): MerchantCategoryDao

    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "mis_gastos.db"

        /**
         * Anade la tabla de reglas aprendidas. `expenses` no se toca, asi que todos
         * los gastos guardados conservan su categoria.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `merchant_categories` (
                        `merchantKey` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `hits` INTEGER NOT NULL,
                        `learnedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`merchantKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Anade la tabla de presupuestos. No se tocan ni `expenses` ni
         * `merchant_categories`, asi que los gastos y las reglas aprendidas sobreviven.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `budgets` (
                        `category` TEXT NOT NULL,
                        `monthlyCents` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`category`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                NAME,
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}