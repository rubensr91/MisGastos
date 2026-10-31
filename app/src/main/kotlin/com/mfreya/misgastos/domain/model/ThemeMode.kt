package com.mfreya.misgastos.domain.model

/**
 * Modo de appearance elegido por el usuario. [SYSTEM] sigue al ajuste del sistema,
 * [LIGHT] y [DARK] lo fuerzan.
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM
    }
}