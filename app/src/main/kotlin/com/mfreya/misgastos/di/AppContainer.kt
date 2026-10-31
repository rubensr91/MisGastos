package com.mfreya.misgastos.di

import android.content.Context
import com.mfreya.misgastos.data.AppSettings
import com.mfreya.misgastos.data.BudgetRepository
import com.mfreya.misgastos.data.ExpenseRepository
import com.mfreya.misgastos.data.category.ExpenseClassifier
import com.mfreya.misgastos.data.db.AppDatabase
import com.mfreya.misgastos.ingest.NotificationIngestor

class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val appSettingsContext: Context get() = appContext

    val appSettings: AppSettings by lazy { AppSettings(appContext) }

    private val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    val classifier: ExpenseClassifier by lazy { ExpenseClassifier(database.merchantCategoryDao()) }

    val expenseRepository: ExpenseRepository by lazy {
        // La base de datos se pasa para que los lotes de notificaciones se escriban en una
        // sola transaccion.
        ExpenseRepository(database.expenseDao(), classifier, database)
    }

    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database.budgetDao()) }

    val notificationIngestor: NotificationIngestor by lazy {
        NotificationIngestor(expenseRepository, appSettings::isPackageAllowed)
    }
}