package com.mfreya.misgastos.data

import android.content.Context
import com.mfreya.misgastos.domain.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    // Por defecto solo se leen las apps marcadas una por una. Leer los avisos de todo el
    // telefono es la opcion mas invasiva, asi que el usuario tiene que elegirla a proposito.
    // Quien ya la tenga activa en sus preferencias (instalaciones anteriores) la conserva.
    private val _allowAllPackages = MutableStateFlow(prefs.getBoolean(KEY_ALLOW_ALL, false))
    val allowAllPackages: StateFlow<Boolean> = _allowAllPackages.asStateFlow()

    private val _allowedPackages = MutableStateFlow(readPackages())
    val allowedPackages: StateFlow<Set<String>> = _allowedPackages.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.fromStorage(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()


    fun isPackageAllowed(packageName: String): Boolean =
        _allowAllPackages.value || packageName in _allowedPackages.value

    fun setAllowAllPackages(allowAll: Boolean) {
        prefs.edit().putBoolean(KEY_ALLOW_ALL, allowAll).apply()
        _allowAllPackages.value = allowAll
    }

    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        val current = _allowedPackages.value.toMutableSet()
        val changed = if (allowed) {
            current.add(packageName)
        } else {
            current.remove(packageName)
        }
        if (!changed) return
        prefs.edit().putStringSet(KEY_PACKAGES, current).apply()
        _allowedPackages.value = current
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING, completed).apply()
        _onboardingCompleted.value = completed
    }

    fun setThemeMode(mode: ThemeMode) {
        if (_themeMode.value == mode) return
        prefs.edit().putString(KEY_THEME, mode.storageValue).apply()
        _themeMode.value = mode
    }

    fun knownPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty()

    private fun readPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet()).orEmpty().toSet()

    private companion object {
        const val FILE_NAME = "mis_gastos_settings"
        const val KEY_ALLOW_ALL = "allow_all_packages"
        const val KEY_PACKAGES = "allowed_packages"
        const val KEY_ONBOARDING = "onboarding_completed"
        const val KEY_THEME = "theme_mode"
    }
}