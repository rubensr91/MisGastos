package com.mfreya.misgastos

import android.app.Application
import com.mfreya.misgastos.di.AppContainer

class MisGastosApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}