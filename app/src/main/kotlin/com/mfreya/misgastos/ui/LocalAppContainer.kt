package com.mfreya.misgastos.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.mfreya.misgastos.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer no proporcionado en el árbol de composición")
}
