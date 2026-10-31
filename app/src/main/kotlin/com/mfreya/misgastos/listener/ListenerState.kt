package com.mfreya.misgastos.listener

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Estado real del servicio de escucha dentro del proceso.
 *
 * El sistema no ofrece una API pública para consultar si un `NotificationListenerService`
 * está conectado (`getConnectedInstances()` está oculta), así que el propio servicio publica
 * su estado al conectarse y al desconectarse. Es la única fuente de verdad fiable y evita
 * confundir "la app puede publicar notificaciones" con "el servicio está escuchando".
 */
object ListenerState {

    private val connected = AtomicBoolean(false)

    val isConnected: Boolean get() = connected.get()

    internal fun setConnected(value: Boolean) {
        connected.set(value)
    }
}
