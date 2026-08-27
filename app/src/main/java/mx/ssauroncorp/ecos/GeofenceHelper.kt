package mx.ssauroncorp.ecos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

/**
 * GeofenceHelper — registra/quita geocercas en Play Services.
 * Cada Reminder -> un Geofence de radio radiusM. Al cruzarlo, Play Services
 * dispara GeofenceBroadcastReceiver (aunque la app esté cerrada). Esto resuelve
 * la limitación de la v1 web (que no despertaba en segundo plano real).
 */
class GeofenceHelper(private val context: Context) {

    private val client = LocationServices.getGeofencingClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    fun buildGeofence(r: Reminder): Geofence =
        Geofence.Builder()
            .setRequestId(r.id)
            .setCircularRegion(r.lat, r.lng, r.radiusM.toFloat())
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

    fun add(r: Reminder, onDone: (error: Exception?) -> Unit) {
        if (!hasPermission()) { onDone(SecurityException("Falta permiso ACCESS_FINE_LOCATION")); return }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(buildGeofence(r))
            .build()
        val intent = android.content.Intent(context, GeofenceBroadcastReceiver::class.java)
            .setAction("mx.ssauroncorp.ecos.ACTION_GEOFENCE")
        val pi = android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        )
        client.addGeofences(request, pi).addOnCompleteListener { onDone(it.exception) }
    }

    fun remove(id: String, onDone: (error: Exception?) -> Unit) {
        client.removeGeofences(listOf(id)).addOnCompleteListener { onDone(it.exception) }
    }
}
