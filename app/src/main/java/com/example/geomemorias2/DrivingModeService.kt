package com.example.geomemorias2

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * DrivingModeService — Foreground Service that keeps location tracking + TTS alerts
 * running when the app goes to background during driving mode.
 *
 * Architecture:
 * - Owns its own FusedLocationProviderClient + LocationCallback
 * - Owns its own TextToSpeech instance
 * - Publishes location updates via [locationUpdates] SharedFlow → MainActivity observes
 * - Publishes TTS announcements directly (TTS works from services)
 * - Runs a persistent notification (required for foreground services on Android 8+)
 */
class DrivingModeService : Service() {

    companion object {
        private const val TAG = "DrivingModeService"
        private const val NOTIFICATION_ID = 7777

        const val ACTION_START = "com.example.geomemorias2.ACTION_DRIVING_START"
        const val ACTION_STOP = "com.example.geomemorias2.ACTION_DRIVING_STOP"

        // SharedFlow: location updates emitted by the service → observed by MainActivity
        private val _locationUpdates = MutableSharedFlow<Pair<Double, Double>>(extraBufferCapacity = 16)
        val locationUpdates = _locationUpdates.asSharedFlow()

        // SharedFlow: TTS announcements emitted by the service → observed by MainActivity for UI
        private val _ttsEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
        val ttsEvents = _ttsEvents.asSharedFlow()
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private lateinit var ttsManager: DrivingTtsManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── Lifecycle ──────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize DrivingTtsManager
        ttsManager = DrivingTtsManager(this)
        ttsManager.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.d(TAG, "ACTION_START")
                startForegroundWithNotification()
                startLocationUpdates()
            }
            ACTION_STOP -> {
                Log.d(TAG, "ACTION_STOP")
                stopDriving()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                // Default: treat as start
                startForegroundWithNotification()
                startLocationUpdates()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        stopDriving()
        ttsManager.shutdown()
        serviceScope.cancel()
        super.onDestroy()
    }

    // ── Location ───────────────────────────────────────────────────

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No location permission")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val pair = Pair(loc.latitude, loc.longitude)
                    serviceScope.launch {
                        _locationUpdates.emit(pair)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, null)
        Log.d(TAG, "Location updates started")
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
            locationCallback = null
        }
    }

    // ── TTS (background-capable) ───────────────────────────────────

    fun speakReminder(text: String, distance: String) {
        ttsManager.speakReminderAlert(text, distance)

        // Emit event so MainActivity can update UI if visible
        serviceScope.launch {
            _ttsEvents.emit(text)
        }
    }

    // ── Notification ───────────────────────────────────────────────

    private fun startForegroundWithNotification() {
        NotificationHelper.ensureDrivingChannel(this)

        // Tap notification → open MainActivity
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = "RESUME_DRIVING_MODE"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action
        val stopIntent = Intent(this, DrivingModeService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NotificationHelper.DRIVING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText("Modo conducción activo — alertas por voz activadas")
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, "Detener", pendingStop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    // ── Cleanup ────────────────────────────────────────────────────

    private fun stopDriving() {
        stopLocationUpdates()
        ttsManager.reset()
    }
}
