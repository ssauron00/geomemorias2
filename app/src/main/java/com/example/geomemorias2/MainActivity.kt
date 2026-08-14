package com.example.geomemorias2

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.geomemorias2.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var geofence: GeofenceHelper
    private var pendingPoint: GeoPoint? = null

    // Permisos ubicación + notificaciones (Android 13+)
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            geofence.add(testReminder()) { err -> err?.let { Toast.makeText(this, "Geo: $it", Toast.LENGTH_SHORT).show() } }
        } else {
            Toast.makeText(this, "Se requiere ubicación", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // User-Agent único para tu app (requerido por política OSM) — ANTES de load()
        Configuration.getInstance().userAgentValue = "com.example.geomemorias2"
        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabaseProvider.get(this)
        geofence = GeofenceHelper(this)
        NotificationHelper.ensureChannel(this)

        setupMap()
        setupUi()

        // Pedir permisos al iniciar (igual filosofía: pedir al arrancar)
        permLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        ))
    }

    private fun setupMap() {
        // CartoDB Positron — CDN global, gratis, sin API key, política permisiva
        // XYTileSource: 5º param = path template, 6º param = array de base URLs completas
        val cartoPositron = XYTileSource(
            "CartoDB Positron",
            0, 19, 256,
            "/light_all/{z}/{x}/{y}.png",                    // path template
            arrayOf(                                         // base URLs (subdominios a/b/c/d)
                "https://a.basemaps.cartocdn.com",
                "https://b.basemaps.cartocdn.com",
                "https://c.basemaps.cartocdn.com",
                "https://d.basemaps.cartocdn.com"
            ),
            "© OpenStreetMap contributors, © CARTO"
        )
        binding.map.setTileSource(cartoPositron)

        // Forzar recarga de tiles
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(15.0)
        binding.map.controller.setCenter(GeoPoint(19.4326, -99.1332)) // CDMX default
        binding.map.invalidate()

        // Tocar el mapa -> fija pendingPoint y abre formulario
        binding.map.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
                val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                pendingPoint = GeoPoint(p.latitude.toDouble(), p.longitude.toDouble())
                binding.etText.setText("")
                binding.etRadius.setText(Reminder.DEFAULT_RADIUS_M.toString())
                binding.etText.hint = "Recordatorio en ${"%.4f".format(p.latitude)}, ${"%.4f".format(p.longitude)}"
                binding.etText.requestFocus()
                return true
            }
        })
    }

    private fun setupUi() {
        binding.btnSave.setOnClickListener {
            val p = pendingPoint
            if (p == null) {
                Toast.makeText(this, "Toca el mapa para ubicar", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val fixed = p // ya no nullable dentro del bloque
            val text = binding.etText.text.toString().ifBlank { "Recordatorio" }
            val radius = binding.etRadius.text.toString().toIntOrNull() ?: Reminder.DEFAULT_RADIUS_M
            val r = Reminder(
                id = "r" + UUID.randomUUID().toString().take(12),
                text = text, lat = fixed.latitude, lng = fixed.longitude,
                radiusM = radius, notified = false
            )
            lifecycleScope.launch {
                db.reminderDao().insert(r)
                if (geofence.hasPermission()) {
                    geofence.add(r) { err -> err?.let { Toast.makeText(this@MainActivity, "Geo: $it", Toast.LENGTH_SHORT).show() } }
                }
                addMarker(r)
                pendingPoint = null
                Toast.makeText(this@MainActivity, "Guardado y geocerca activa", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun addMarker(r: Reminder) {
        val m = Marker(binding.map)
        m.position = GeoPoint(r.lat, r.lng)
        m.title = r.text
        m.subDescription = "radio ${r.radiusM} m"
        binding.map.overlays.add(m)
        binding.map.invalidate()
    }

    // Reminder de prueba para validar geofence al arrancar (quítalo en prod)
    private fun testReminder() = Reminder(
        id = "r-test", text = "PRUEBA: pasaste por aquí",
        lat = 19.4326, lng = -99.1332, radiusM = 100, notified = false
    )
}
