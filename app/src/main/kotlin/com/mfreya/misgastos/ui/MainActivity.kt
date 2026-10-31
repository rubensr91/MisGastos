package com.mfreya.misgastos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mfreya.misgastos.MisGastosApp
import com.mfreya.misgastos.ui.nav.AppNavHost
import com.mfreya.misgastos.ui.support.NotificationAccess
import com.mfreya.misgastos.ui.theme.MisGastosTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as MisGastosApp).container
        val startWithOnboarding = !container.appSettings.onboardingCompleted.value
        setContent {
            val themeMode by container.appSettings.themeMode.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppContainer provides container) {
                MisGastosTheme(
                    themeMode = themeMode,
                ) {
                    AppNavHost(startWithOnboarding = startWithOnboarding)
                }
            }
        }
    }

    /**
     * El servicio de escucha de notificaciones no se relanza solo: si el sistema lo desenlaza,
     * la app sigue teniendo el permiso concedido pero deja de capturar gastos en silencio. Al abrir la
     * app (o al volver de los ajustes del sistema) se le pide al sistema que lo vuelva a
     * enlazar. No cuesta bateria: solo es una peticion cuando ya hay permiso y el servicio no
     * esta conectado.
     */
    override fun onResume() {
        super.onResume()
        NotificationAccess.rebindIfDisconnected(this)
    }
}