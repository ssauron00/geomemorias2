package mx.ssauroncorp.ecos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * NotificationHelper — canal de notificación (requerido en Android 8+).
 * Alerta al usuario cuando entra en el radio de un recordatorio.
 */
object NotificationHelper {
    private const val CHANNEL_ID = "ecos_channel"
    private const val CHANNEL_NAME = "Ecos"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Avisos de recordatorios por ubicación"
                setShowBadge(true)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    internal const val DRIVING_CHANNEL_ID = "ecos_whisper_channel"
    private const val DRIVING_CHANNEL_NAME = "Modo Susurro"

    fun ensureDrivingChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (mgr.getNotificationChannel(DRIVING_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    DRIVING_CHANNEL_ID, DRIVING_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Notificación persistente del Modo Susurro"
                    setShowBadge(false)
                }
                mgr.createNotificationChannel(ch)
            }
        }
    }

    fun fire(context: Context, r: Reminder) {
        ensureChannel(context)
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(r.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(r.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .build()
        mgr.notify(r.id.hashCode(), notif)
    }
}
