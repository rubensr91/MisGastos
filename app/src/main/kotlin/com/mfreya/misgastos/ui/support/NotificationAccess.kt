package com.mfreya.misgastos.ui.support

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import com.mfreya.misgastos.listener.GastoNotificationListener
import com.mfreya.misgastos.listener.ListenerState

/**
 * Utilidades alrededor del permiso de acceso a notificaciones.
 *
 * El permiso no es un `runtime permission`: el usuario lo concede en los ajustes del sistema
 * marcando la app como servicio de accesibilidad de notificaciones. Por eso hay que consultar
 * el estado real en cada `onResume` en lugar de cachearlo.
 */
object NotificationAccess {

    fun isGranted(context: Context): Boolean {
        val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabled.contains(context.packageName)
    }

    /**
     * Si el servicio de escucha está realmente conectado ahora mismo.
     *
     * No se puede deducir de `areNotificationsEnabled()`, que solo dice si la app puede
     * *publicar* notificaciones (permiso `POST_NOTIFICATIONS`), y `getConnectedInstances()`
     * está en la API oculta. El estado lo publica el propio servicio en `ListenerState`.
     */
    fun isListenerConnected(context: Context): Boolean {
        if (!isGranted(context)) return false
        return ListenerState.isConnected
    }

    /** Ajustes del sistema donde el usuario concede el acceso. Devuelve `null` si no hay actividad capaz de abrirlo. */
    fun settingsIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Deep link directo a la pantalla de la lista de servicios, cuando el ROM la soporta. */
    fun directSettingsIntent(context: Context): Intent? {
        val component = ComponentName(context, GastoNotificationListener::class.java)
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                component.flattenToString()
            )
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val power = context.getSystemService(PowerManager::class.java) ?: return true
        return runCatching { power.isIgnoringBatteryOptimizations(context.packageName) }.getOrDefault(true)
    }

    /** Abre la lista de apps exentas de optimización de batería. Nunca pide permisos ni muestra diálogos del sistema. */
    fun batteryOptimizationIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /** Intenta abrir la pantalla de uso de la app; útil cuando el sistema mata el servicio en segundo plano. */
    fun appUsageIntent(context: Context): Intent? {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return intent.takeIf { it.resolveActivity(context.packageManager) != null }
    }

    /**
     * Resultado de pedir al sistema que vuelva a enlazar el servicio de escucha.
     */
    enum class RebindResult {
        /** No hacia falta nada: el permiso no esta concedido o el servicio ya estaba conectado. */
        NOTHING_TO_DO,

        /** Se le ha pedido al sistema que lo vuelva a enlazar. */
        REQUESTED,

        /** El sistema no permite hacerlo (Android 12 o anterior) o ha rechazado la peticion. */
        UNSUPPORTED,
    }

    /** Si el sistema permite pedir que se vuelva a enlazar el servicio (Android 13 o posterior). */
    fun canRebind(): Boolean = RebindCompat.isSupported()

    fun rebindIfDisconnected(context: Context): RebindResult {
        if (!isGranted(context) || ListenerState.isConnected) return RebindResult.NOTHING_TO_DO
        if (!RebindCompat.isSupported()) return RebindResult.UNSUPPORTED
        val component = ComponentName(context, GastoNotificationListener::class.java)
        val requested = runCatching { RebindCompat.requestRebind(component) }.getOrDefault(false)
        return if (requested) RebindResult.REQUESTED else RebindResult.UNSUPPORTED
    }
}

/** Envoltura de `NotificationListenerService.requestRebind`, que es estatico y solo existe desde API 33. */
private object RebindCompat {

    fun isSupported(): Boolean = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU

    fun requestRebind(component: ComponentName): Boolean {
        NotificationListenerService.requestRebind(component)
        return true
    }
}

/** Descripción legible de un paquete emisor, con reserva si el paquete ya no está instalado. */
fun Context.appLabelFor(packageName: String): String? {
    val pm = packageManager
    // Se prueban varias vias porque getApplicationInfo falla con algunas apps y el sistema
    // devuelve el propio paquete cuando no hay etiqueta pública; en ese caso es mejor
    // quedarse sin nombre que repetir el identificador dos veces.
    val desdeInfo = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
    val desdeActivity = runCatching {
        val component = pm.getLaunchIntentForPackage(packageName)?.component
        if (component == null) return@runCatching null
        pm.getActivityInfo(component, 0).loadLabel(pm).toString()
    }.getOrNull()
    val desdeUid = runCatching {
        val uid = pm.getPackageUid(packageName, 0)
        if (uid < 0) return@runCatching null
        pm.getPackagesForUid(uid)
            ?.firstOrNull { it == packageName }
            ?.let { pm.getApplicationInfo(it, 0) }
            ?.let { pm.getApplicationLabel(it).toString() }
    }.getOrNull()
    return listOf(desdeInfo, desdeActivity, desdeUid)
        .filterNotNull()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() && it != packageName }
}

/** Como [appLabelFor], pero nunca devuelve null: cae en el propio identificador. */
fun Context.appLabelOrPackage(packageName: String): String = appLabelFor(packageName) ?: packageName
