package com.mfreya.misgastos.data.db

/** Cuantos gastos guardados aporta cada app emisora. */
data class SourcePackageCount(
    val sourcePackage: String,
    val total: Int,
)
