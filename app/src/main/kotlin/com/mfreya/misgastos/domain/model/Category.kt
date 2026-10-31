package com.mfreya.misgastos.domain.model

import androidx.annotation.StringRes
import com.mfreya.misgastos.R

/**
 * Definicion de una categoria del catalogo.
 *
 * [id] es el valor estable que se guarda en la base de datos y el que usan las reglas, asi que no
 * debe cambiar aunque se reescriba la traduccion. [nameRes] es el texto que se ve en pantalla, y
 * por eso la interfaz se traduce aunque la base de datos no lo haga.
 */
data class CategoryDef(val id: String, @StringRes val nameRes: Int)

/**
 * Categorias de gasto. El usuario puede crear las que quiera; [DEFS] solo son las sugerencias
 * del selector y las que el clasificador automatico sabe identificar.
 */
object Category {

    const val UNCLASSIFIED = "Sin categoría"

    val DEFS: List<CategoryDef> = listOf(
        CategoryDef(UNCLASSIFIED, R.string.category_uncategorized),
        CategoryDef("Supermercado", R.string.category_groceries),
        CategoryDef("Restaurante", R.string.category_restaurant),
        CategoryDef("Comida rápida", R.string.category_fast_food),
        CategoryDef("Transporte", R.string.category_transport),
        CategoryDef("Combustible", R.string.category_fuel),
        CategoryDef("Estacionamiento", R.string.category_parking),
        CategoryDef("Viajes", R.string.category_travel),
        CategoryDef("Compras online", R.string.category_online_shopping),
        CategoryDef("Suscripciones", R.string.category_subscriptions),
        CategoryDef("Salud", R.string.category_health),
        CategoryDef("Ocio", R.string.category_leisure),
        CategoryDef("Servicios", R.string.category_services),
        CategoryDef("Educación", R.string.category_education),
        CategoryDef("Deportes", R.string.category_sports),
        CategoryDef("Mascotas", R.string.category_pets),
        CategoryDef("Regalos", R.string.category_gifts),
        CategoryDef("Banca", R.string.category_banking),
        CategoryDef("Impuestos", R.string.category_taxes),
        CategoryDef("Efectivo", R.string.category_cash),
        CategoryDef("Otros", R.string.category_other),
    )

    /** Identificadores del catalogo, en el mismo orden que [DEFS]. */
    val DEFAULT_CATEGORIES: List<String> = DEFS.map(CategoryDef::id)

    private val NAME_RES: Map<String, Int> = DEFS.associate { it.id to it.nameRes }

    /** Recurso de tradccion de [category], o `null` si es una categoria creada por el usuario. */
    fun nameRes(category: String): Int? = NAME_RES[category.trim()]

    fun isKnown(category: String): Boolean = category.trim() in NAME_RES

    fun normalize(value: String): String = value.trim().ifEmpty { UNCLASSIFIED }
}
