package com.mfreya.misgastos.listener

import android.app.Notification
import android.util.Log
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mfreya.misgastos.MisGastosApp
import com.mfreya.misgastos.ingest.IngestQueue
import com.mfreya.misgastos.ingest.PendingIngest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class GastoNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Los avisos no se ingeren al vuelo: se encolan y se atienden en rafaga. Asi el sistema
     * puede entregar diez notificaciones seguidas sin obligar a diez escrituras en disco, y
     * una compra que llega partida en varios avisos entra en el mismo lote.
     */
    private val queue: IngestQueue by lazy {
        IngestQueue(scope) { batch ->
            val ingestor = (application as? MisGastosApp)?.container?.notificationIngestor ?: return@IngestQueue
            ingestor.ingestAll(batch)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerState.setConnected(true)
        Log.i(TAG, "servicio de escucha conectado")
    }

    override fun onListenerDisconnected() {
        ListenerState.setConnected(false)
        Log.w(TAG, "servicio de escucha desconectado")
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        ListenerState.setConnected(false)
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = extractBody(notification)
        if (title.isNullOrBlank() && body.isBlank()) return

        val container = (application as? MisGastosApp)?.container ?: return

        // El filtro de apps va aqui, en el hilo del sistema, y no dentro de `ingest`: si el
        // aviso viene de una app descartada no hay ni que encolarlo ni que lanzar nada por
        // cada notificacion que aparece en la pantalla de bloqueo.
        val sourcePackage = sbn.packageName
        if (!container.appSettings.isPackageAllowed(sourcePackage)) return

        queue.submit(
            PendingIngest(
                sourcePackage = sourcePackage,
                title = title,
                body = body,
                postedAt = sbn.postTime,
                rawText = listOfNotNull(title, body).joinToString(". "),
            ),
        )
    }

    private fun extractBody(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) return bigText

        val lines = runCatching {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString("\n")
                ?.trim()
        }.getOrNull()
        if (!lines.isNullOrBlank()) return lines

        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().trim()
    }

    private companion object {
        const val TAG = "MisGastosListener"
    }
}
