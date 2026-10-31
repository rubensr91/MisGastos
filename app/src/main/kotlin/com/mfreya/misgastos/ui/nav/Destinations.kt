package com.mfreya.misgastos.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mfreya.misgastos.R

object Routes {
    const val ONBOARDING = "onboarding"
}

/**
 * Destinos de la barra inferior. Los iconos son drawables propios y se pintan con
 * painterResource porque ImageVector.vectorResource no existe en compose ui 1.10.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Summary("summary", R.string.nav_summary, R.drawable.ic_nav_summary),
    Expenses("expenses", R.string.nav_expenses, R.drawable.ic_nav_expenses),
    Settings("settings", R.string.nav_settings, R.drawable.ic_nav_settings),
}