package mx.ssauroncorp.ecos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * GeofenceBroadcastReceiver — Play Services lo invoca al ENTRAR/SALIR de un radio.
 * Al entrar, busca el Reminder por id y dispara la notificación (regla de oro:
 * el recordatorio ya existe; solo notifica lo guardado).
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        val db = AppDatabaseProvider.get(context)
        val geofences = event.triggeringGeofences ?: emptyList()
        for (geofence in geofences) {
            val id = geofence.requestId
            val transition = event.geofenceTransition
            if (transition == Geofence.GEOFENCE_TRANSITION_ENTER) {
                CoroutineScope(Dispatchers.IO).launch {
                    val r = db.reminderDao().getById(id) ?: return@launch
                    // Histeresis: solo notifica si no estaba ya notificado
                    if (!r.notified) {
                        NotificationHelper.fire(context, r)
                        db.reminderDao().insert(r.copy(notified = true))
                    }
                }
            } else if (transition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                // Al salir, resetea el flag para permitir re-aviso al regresar
                CoroutineScope(Dispatchers.IO).launch {
                    val r = db.reminderDao().getById(id) ?: return@launch
                    if (r.notified) db.reminderDao().insert(r.copy(notified = false))
                }
            }
        }
    }
}
