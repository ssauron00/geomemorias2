package mx.ssauroncorp.ecos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognizerIntent
import android.provider.Settings

import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.widget.TooltipCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import mx.ssauroncorp.ecos.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import java.util.UUID

class MainActivity : AppCompatActivity() {



    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private lateinit var geofence: GeofenceHelper
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: ReminderAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    
    private var pendingPoint: GeoPoint? = null
    private var currentLocation: Pair<Double, Double>? = null
    private var editingReminderId: String? = null  // ID of reminder being location-edited
    private var pendingEditPoint: GeoPoint? = null  // New location chosen during edit
    private data class PendingEditData(val text: String, val radius: Int)
    private var pendingEditData: PendingEditData? = null  // Preserved when dialog dismissed for location change
    private var editLocationPopup: android.widget.PopupWindow? = null
    private var editLocationSnackbar: Snackbar? = null
    private var drivingReminderPopup: android.widget.PopupWindow? = null
    private val markerMap = mutableMapOf<String, Marker>()
    private val circleMap = mutableMapOf<String, Polygon>()
    private var currentLocationMarker: Marker? = null
    
    // Modo Susurro
    private lateinit var ttsManager: DrivingTtsManager
    private var isDrivingMode = false
    private var isServiceDrivingMode = false  // true when the Foreground Service is running (Modo Susurro)
    private var previousNearestId: String? = null  // Track nearest reminder for fade animation

    // Voice input (Intent-based, works on all devices)
    private var isListening = false
    private var pendingVoiceCmdResult = false // true when expecting result for voice command flow
    private var isDrivingVoiceMode = false // true when recording voice for driving-mode reminder
    private var menuPopup: android.widget.PopupWindow? = null
    private lateinit var speechLauncher: androidx.activity.result.ActivityResultLauncher<Intent>


    // Voice command state machine (driving mode)
    private enum class VoiceCommandState { IDLE, ASK_TEXT, ASK_RADIUS }
    private var voiceCmdState = VoiceCommandState.IDLE
    private var pendingVoiceText = ""

    // Permisos ubicación + notificaciones + micrófono (Android 13+)
    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { res ->
            if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                startLocationUpdates()
                centerMapOnCurrentLocation()
                reregisterGeofences()
            } else {
                Toast.makeText(this, R.string.toast_location_required, Toast.LENGTH_LONG).show()
            }

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Suppress the activity window title — the MaterialToolbar shows it
        title = ""
        // 1️⃣ Cargar configuración ANTES de inflar el MapView (requisito de osmdroid:
        //    el MapView lee Configuration al construirse)
        Configuration.getInstance().load(this, getSharedPreferences("osm", MODE_PRIVATE))
        // 2️⃣ User-Agent único para tu app (load() lo sobreescribe, por eso va después)
        Configuration.getInstance().userAgentValue = "mx.ssauroncorp.ecos"

        // OpenStreetMap Mapnik — tiles gratuitos, sin API key
        val osmMapnik = XYTileSource(
            "Mapnik", 0, 19, 256,
            ".png",
            arrayOf(
                "https://a.tile.openstreetmap.org/",
                "https://b.tile.openstreetmap.org/",
                "https://c.tile.openstreetmap.org/"
            ),
            "© OpenStreetMap contributors"
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        drawerLayout = binding.drawerLayout
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // 3️⃣ Fija tu tile source (pisará el Mapnik por defecto que restaura load())
        binding.map.setTileSource(osmMapnik)

        // 4️⃣ Descarga de tiles en background
        binding.map.setUseDataConnection(true)           // descarga en background

        db = AppDatabaseProvider.get(this)
        geofence = GeofenceHelper(this)
        NotificationHelper.ensureChannel(this)

        // Initialize speech launcher for voice recognition (Google online)
        speechLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
                if (text.isNotEmpty()) {
                    isListening = false
                    if (isDrivingVoiceMode) {
                        isDrivingVoiceMode = false
                        binding.drivingModeOverlay.voiceRecordingIndicator.visibility = View.GONE
                        saveDrivingModeReminder(text)
                    } else if (pendingVoiceCmdResult) {
                        pendingVoiceCmdResult = false
                        handleVoiceCommandResult(text)
                    } else {
                        handleVoiceResult(text)
                    }
                }
            } else {
                isListening = false
                if (isDrivingVoiceMode) {
                    isDrivingVoiceMode = false
                    binding.drivingModeOverlay.voiceRecordingIndicator.visibility = View.GONE
                }
            }
        }

        setupMap()
        setupDrawer()
        setupUi()
        setupDrivingMode()
        loadReminders()
        reregisterGeofences()

        // Handle resume from driving mode notification
        if (intent?.action == "RESUME_DRIVING_MODE" && !isDrivingMode) {
            Handler(Looper.getMainLooper()).postDelayed({
                enterDrivingMode()
            }, 300)
        }

        // Show initial hint to tap the map (skip if already in edit mode)
        Handler(Looper.getMainLooper()).postDelayed({
            if (editingReminderId == null) {
                Snackbar.make(binding.root, R.string.popup_tap_map_hint, Snackbar.LENGTH_LONG).show()
            }
        }, 500)

        // Pedir permisos al iniciar (igual filosofía: pedir al arrancar)
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        perms.add(Manifest.permission.RECORD_AUDIO)
        permLauncher.launch(perms.toTypedArray())
    }

    private fun setupMap() {
        // Multi-touch + zoom/centro + invalidar para forzar recarga
        binding.map.setMultiTouchControls(true)
        binding.map.controller.setZoom(15.0)
        binding.map.controller.setCenter(GeoPoint(19.4326, -99.1332)) // CDMX default
        binding.map.invalidate()

        // Tocar el mapa -> fija pendingPoint y abre formulario (o mock location)
        binding.map.overlays.add(object : Overlay() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent, mapView: org.osmdroid.views.MapView): Boolean {
                val p = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                val tapPoint = GeoPoint(p.latitude.toDouble(), p.longitude.toDouble())

                // Edit-location mode: set new position for the reminder being edited
                editingReminderId?.let { id ->
                    val reminder = findReminderById(id)
                    if (reminder == null) {
                        // Stale state — reminder was deleted; clean up and fall through
                        cancelEditLocationMode()
                    } else {
                        pendingEditPoint = tapPoint
                        // Show the confirm popup now that a location has been chosen
                        showConfirmLocationPopup(tapPoint)
                        // Update marker position in real-time
                        markerMap[id]?.let { marker ->
                            marker.position = tapPoint
                            marker.subDescription = getString(R.string.dialog_edit_location_mode)
                            mapView.invalidate()
                        }
                        // Update circle in real-time
                        circleMap[id]?.let { circle ->
                            circle.points = createCirclePoints(tapPoint, reminder.radiusM.toDouble())
                            mapView.invalidate()
                        }
                        return true
                    }
                }

                // In driving mode, ignore map taps (only voice/fix-point buttons allowed)
                if (isDrivingMode) return false

                // Normal mode: set pendingPoint and show add-reminder bottom sheet
                pendingPoint = tapPoint
                showAddReminderBottomSheet(tapPoint)
                return true
            }
        })
    }

    private fun setupDrawer() {
        adapter = ReminderAdapter(
            onEdit = { reminder -> showEditDialog(reminder) },
            onDelete = { reminder -> deleteReminder(reminder) }
        )
        binding.drawerReminders.rvReminders.layoutManager = LinearLayoutManager(this)
        binding.drawerReminders.rvReminders.adapter = adapter

        // Search filter — debounced via TextWatcher
        binding.drawerReminders.etSearch.addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    adapter.filter(s?.toString() ?: "")
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )
        // Show "no results" text when filter yields nothing
        adapter.onEmptyResults = { empty ->
            binding.drawerReminders.tvNoResults.visibility = if (empty) View.VISIBLE else View.GONE
        }

        // Clear search when drawer closes
        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                binding.drawerReminders.etSearch.setText("")
            }
        })

        binding.drawerReminders.btnCloseDrawer.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }
    }

    private var addReminderBottomSheet: android.widget.PopupWindow? = null
    private var popupEtText: com.google.android.material.textfield.TextInputEditText? = null
    private var popupEtRadius: com.google.android.material.textfield.TextInputEditText? = null
    private var popupTvCoords: android.widget.TextView? = null

    private fun setupUi() {
        // Dynamic top padding for edge-to-edge status bar — apply to root CoordinatorLayout
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply top margin to the top bar card and bottom margin to the bottom bar card
            (binding.btnMenu.parent?.parent as? View)?.let { topCard ->
                val lp = topCard.layoutParams as? android.view.ViewGroup.MarginLayoutParams
                lp?.topMargin = insets.top + (12 * resources.displayMetrics.density).toInt()
                topCard.layoutParams = lp
            }
            (binding.bottomActionBar.layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = insets.bottom + (12 * resources.displayMetrics.density).toInt()
                binding.bottomActionBar.layoutParams = lp
            }
            windowInsets
        }

        // Hamburger menu → opens menu bottom sheet
        binding.btnMenu.setOnClickListener {
            showMenuBottomSheet()
        }

        // Top list button → opens drawer
        binding.btnTopList.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Bottom bar: Lista
        binding.btnBottomList.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Bottom bar: Mi ubicación
        binding.btnMyLocation.setOnClickListener {
            centerMapOnCurrentLocation()
        }

        // Bottom bar: Modo Susurro
        binding.btnDrivingMode.setOnClickListener {
            toggleDrivingMode()
        }

        // FAB: agregar recordatorio
        binding.fabAddReminder.setOnClickListener {
            if (editingReminderId != null) return@setOnClickListener
            pendingPoint = null
            Toast.makeText(this, R.string.popup_tap_map_hint, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAddReminderBottomSheet(tapPoint: GeoPoint) {
        addReminderBottomSheet?.dismiss()

        val popupView = layoutInflater.inflate(R.layout.popup_bottom_add_reminder, null)
        popupEtText = popupView.findViewById(R.id.etPopupText)
        popupEtRadius = popupView.findViewById(R.id.etPopupRadius)
        popupTvCoords = popupView.findViewById(R.id.tvPopupCoords)

        popupTvCoords?.text = getString(R.string.marker_hint, tapPoint.latitude, tapPoint.longitude)
        popupEtRadius?.setText(Reminder.DEFAULT_RADIUS_M.toString())
        popupEtText?.setText("")
        popupEtText?.requestFocus()

        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 16f * resources.displayMetrics.density
            showAtLocation(binding.root, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, 16)
        }
        addReminderBottomSheet = popupWindow

        // Close button
        popupView.findViewById<android.widget.ImageButton>(R.id.btnClosePopup).setOnClickListener {
            popupWindow.dismiss()
        }

        // Voice input
        popupView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPopupVoiceInput).setOnClickListener {
            pendingPoint = tapPoint  // ensure pendingPoint is set for voice save
            toggleVoiceInput()
        }

        // Save button
        popupView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPopupSave).setOnClickListener {
            val text = popupEtText?.text?.toString() ?: ""
            if (text.isBlank()) {
                Snackbar.make(popupView, R.string.error_empty_text, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val radius = popupEtRadius?.text?.toString()?.toIntOrNull() ?: Reminder.DEFAULT_RADIUS_M
            val r = Reminder(
                id = "r-${UUID.randomUUID().toString().substring(0, 12)}",
                text = text, lat = tapPoint.latitude, lng = tapPoint.longitude,
                radiusM = radius, notified = false
            )
            lifecycleScope.launch {
                db.reminderDao().insert(r)
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        geofence.add(r) { err -> err?.let { Toast.makeText(this@MainActivity, getString(R.string.toast_geofence_error, it), Toast.LENGTH_SHORT).show() } }
                    } catch (_: SecurityException) { /* permission checked above */ }
                }
                addMarker(r)
                addCircle(r)
                pendingPoint = null
                popupWindow.dismiss()
                Toast.makeText(this@MainActivity, R.string.toast_reminder_saved, Toast.LENGTH_SHORT).show()
                loadReminders()
            }
        }
    }

    private fun loadReminders() {
        lifecycleScope.launch {
            val reminders = db.reminderDao().getAll()
            adapter.submitFullList(reminders)
            updateMarkerMap(reminders)
        }
    }

    /**
     * Re-register all geofences from Room on app startup.
     * Play Services clears geofences when the app is killed,
     * so we need to re-register them from our persistent Room database.
     */
    private fun reregisterGeofences() {
        if (!geofence.hasPermission()) return
        lifecycleScope.launch {
            val reminders = db.reminderDao().getAll()
            reminders.forEach { reminder ->
                try {
                    geofence.add(reminder) { err ->
                        err?.let { Log.w("GeofenceReReg", "Error re-registering ${reminder.id}: $it") }
                    }
                } catch (e: SecurityException) {
                    Log.w("GeofenceReReg", "Permission denied for ${reminder.id}")
                }
            }
            Log.d("GeofenceReReg", "Re-registered ${reminders.size} geofences from Room")
        }
    }

    private fun updateMarkerMap(reminders: List<Reminder>) {
        // Clear existing reminder markers and circles (keep current location marker)
        binding.map.overlays.removeAll { (it is Marker && it != currentLocationMarker) || it is Polygon }
        markerMap.clear()
        circleMap.clear()

        // Add markers and circles for all reminders
        reminders.forEach { reminder ->
            addMarker(reminder)
            addCircle(reminder)
        }

        // Ensure current location marker is always on top
        currentLocationMarker?.let { locMarker ->
            binding.map.overlays.remove(locMarker)
            binding.map.overlays.add(locMarker)
        }
    }

    private fun addMarker(r: Reminder) {
        val m = Marker(binding.map)
        m.position = GeoPoint(r.lat, r.lng)
        m.title = r.text
        m.subDescription = getString(R.string.radius_prefix, r.radiusM)
        m.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_echos_pin))
        m.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        
        // Tap to show edit/delete options
        m.setOnMarkerClickListener { marker, _ ->
            // Show options dialog
            val options = arrayOf(
                getString(R.string.dialog_marker_options_edit),
                getString(R.string.dialog_marker_options_delete)
            )
            AlertDialog.Builder(this@MainActivity)
                .setTitle(marker.title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showEditDialog(r)
                        1 -> deleteReminder(r)
                    }
                }
                .show()
            true
        }
        
        binding.map.overlays.add(m)
        markerMap[r.id] = m
        binding.map.invalidate()
    }

    private fun addCircle(r: Reminder) {
        val circle = Polygon()
        val center = GeoPoint(r.lat, r.lng)
        val points = createCirclePoints(center, r.radiusM.toDouble())
        circle.points = points
        circle.fillPaint.color = 0x22FF6D00.toInt() // Semi-transparent orange fill (sunset)
        circle.outlinePaint.color = 0xFFFF6D00.toInt() // Orange outline (sunset)
        circle.outlinePaint.strokeWidth = 4f
        circle.isVisible = true
        
        binding.map.overlays.add(circle)
        circleMap[r.id] = circle
    }

    private fun createCirclePoints(center: GeoPoint, radiusMeters: Double, numPoints: Int = 64): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        val latRad = Math.toRadians(center.latitude)
        val lngRad = Math.toRadians(center.longitude)
        val earthRadius = 6371000.0 // Earth radius in meters
        val dR = radiusMeters / earthRadius // Angular distance in radians
        
        for (i in 0 until numPoints) {
            val angle = 2 * Math.PI * i / numPoints
            
            // Correct spherical coordinate transformation
            val lat = Math.asin(
                Math.sin(latRad) * Math.cos(dR) + 
                Math.cos(latRad) * Math.sin(dR) * Math.cos(angle)
            )
            val lng = lngRad + Math.atan2(
                Math.sin(angle) * Math.sin(dR) * Math.cos(latRad),
                Math.cos(dR) - Math.sin(latRad) * Math.sin(lat)
            )
            
            points.add(GeoPoint(Math.toDegrees(lat), Math.toDegrees(lng)))
        }
        return points
    }

    private fun findReminderById(id: String): Reminder? {
        // Search synchronously from what's currently loaded in the adapter
        return adapter.currentList.find { it.id == id }
    }

    private fun showEditDialog(
        reminder: Reminder,
        initialText: String = reminder.text,
        initialRadius: Int = reminder.radiusM,
        showChangeLocation: Boolean = true
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_reminder, null)
        val textLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.textInputLayout)
        val radiusLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.radiusInputLayout)
        val etText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditReminderText)
        val etRadius = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etEditReminderRadius)
        val btnChangeLocation = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnChangeLocation)

        etText.setText(initialText)
        etRadius.setText(initialRadius.toString())

        // Show or hide the change-location button
        btnChangeLocation.visibility = if (showChangeLocation) View.VISIBLE else View.GONE

        // lateinit so the click listener below can reference it before it's assigned
        lateinit var dialog: AlertDialog

        // Change-location button: dismiss dialog, enter edit-location mode on the map
        btnChangeLocation.setOnClickListener {
            editingReminderId = reminder.id
            pendingEditPoint = null
            // Preserve the text and radius the user may have edited
            pendingEditData = PendingEditData(
                text = etText.text?.toString() ?: reminder.text,
                radius = etRadius.text?.toString()?.toIntOrNull() ?: reminder.radiusM
            )

            // Center map on current reminder location
            val point = GeoPoint(reminder.lat, reminder.lng)
            binding.map.controller.animateTo(point, 17.0, 500L)

            // Dismiss the dialog
            dialog.dismiss()

            // Show edit-location mode with cancel + confirm FABs on the map
            enterEditLocationMode()
        }

        dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dialog_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        // Restore UI if the dialog is dismissed without saving (cancel, back, tap outside)
        dialog.setOnCancelListener { restoreNormalModeUI() }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newText = etText.text?.toString() ?: ""
                val newRadius = etRadius.text?.toString()?.toIntOrNull()

                // Validate text is not empty
                val isTextEmpty = newText.isBlank()
                textLayout.error = if (isTextEmpty) {
                    getString(R.string.error_empty_text)
                } else {
                    null
                }
                if (isTextEmpty) {
                    Snackbar.make(dialogView, R.string.error_empty_text, Snackbar.LENGTH_SHORT).show()
                }

                // Validate radius
                radiusLayout.error = if (newRadius == null || newRadius <= 0) {
                    getString(R.string.toast_invalid_radius)
                } else {
                    null
                }

                // Only save if both fields are valid
                if (newText.isNotBlank() && newRadius != null && newRadius > 0) {
                    // When coming from location change, re-fetch the original reminder
                    // since pendingEditPoint may have been set
                    val targetReminder = if (!showChangeLocation) {
                        findReminderById(reminder.id) ?: reminder
                    } else {
                        reminder
                    }
                    updateReminder(targetReminder, newText, newRadius)
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun enterEditLocationMode() {
        if (editLocationPopup != null) return  // already in edit-location mode

        // Hide normal-mode UI (top bar, bottom bar, FAB)
        binding.btnMenu.visibility = View.GONE
        binding.btnTopList.visibility = View.GONE
        binding.bottomActionBar.visibility = View.GONE
        binding.fabAddReminder.visibility = View.GONE

        // Show persistent banner
        editLocationSnackbar = Snackbar.make(binding.root, R.string.dialog_edit_location_mode, Snackbar.LENGTH_INDEFINITE).also { it.show() }
    }

    /** Show a bottom-sheet popup with Cancel + Save buttons after the user taps the map. */
    private fun showConfirmLocationPopup(point: GeoPoint) {
        editLocationPopup?.dismiss()

        val popupView = layoutInflater.inflate(R.layout.popup_bottom_confirm_location, null)
        val tvCoords = popupView.findViewById<android.widget.TextView>(R.id.tvConfirmCoords)
        tvCoords.text = getString(R.string.marker_hint, point.latitude, point.longitude)

        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 16f * resources.displayMetrics.density
            showAtLocation(binding.root, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, 16)
            setOnDismissListener { editLocationPopup = null }
        }
        editLocationPopup = popupWindow

        // Cancel → exit edit-location mode
        popupView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmLocationCancel)
            .setOnClickListener {
                popupWindow.dismiss()
                cancelEditLocationMode()
            }

        // Save → re-open edit dialog with the new location
        popupView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmLocationSave)
            .setOnClickListener {
                popupWindow.dismiss()
                confirmEditLocationMode()
            }
    }

    private fun confirmEditLocationMode() {
        val id = editingReminderId ?: return
        val reminder = findReminderById(id) ?: return
        if (pendingEditPoint == null) return

        // Save directly with the pending text/radius and new location.
        // Keep pendingEditPoint alive so updateReminder() can read it.
        val text = pendingEditData?.text ?: reminder.text
        val radius = pendingEditData?.radius ?: reminder.radiusM
        pendingEditData = null
        updateReminder(reminder, text, radius)
    }



    private fun cancelEditLocationMode() {
        val id = editingReminderId ?: return
        val reminder = findReminderById(id)
        if (reminder != null) {
            markerMap[id]?.let { marker ->
                marker.position = GeoPoint(reminder.lat, reminder.lng)
                marker.subDescription = getString(R.string.radius_prefix, reminder.radiusM)
            }
            circleMap[id]?.let { circle ->
                circle.points = createCirclePoints(GeoPoint(reminder.lat, reminder.lng), reminder.radiusM.toDouble())
            }
            binding.map.invalidate()
        }
        editingReminderId = null
        pendingEditPoint = null
        pendingEditData = null
        exitEditLocationMode()
        restoreNormalModeUI()
    }

    private fun exitEditLocationMode() {
        editLocationSnackbar?.dismiss()
        editLocationSnackbar = null
        editLocationPopup?.dismiss()
        editLocationPopup = null
    }

    /** Show a popup when a geofence reminder triggers in driving mode. */
    private fun showDrivingReminderAlertPopup(reminder: Reminder, distance: String) {
        if (!isDrivingMode) return

        drivingReminderPopup?.dismiss()

        val popupView = layoutInflater.inflate(R.layout.popup_driving_reminder_alert, null)
        popupView.findViewById<android.widget.TextView>(R.id.tvDrivingPopupReminderText).text = reminder.text
        popupView.findViewById<android.widget.TextView>(R.id.tvDrivingPopupDistance).text = getString(R.string.driving_mode_distance, "${distance}m")

        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 16f * resources.displayMetrics.density
            showAtLocation(binding.root, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL, 0, 16)
            setOnDismissListener { drivingReminderPopup = null }
        }
        drivingReminderPopup = popupWindow

        // Silence → stop TTS
        popupView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDrivingPopupSilence)
            .setOnClickListener {
                ttsManager.stop()
                popupWindow.dismiss()
            }

        // Delete → remove reminder from DB and UI
        popupView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDrivingPopupDelete)
            .setOnClickListener {
                ttsManager.stop()
                popupWindow.dismiss()
                // Delete directly without confirmation dialog while driving
                performDelete(reminder)
            }
    }

    /** Restore the normal-mode top/bottom bars (only if not in driving mode). */
    private fun restoreNormalModeUI() {
        if (!isDrivingMode) {
            binding.btnMenu.visibility = View.VISIBLE
            binding.btnTopList.visibility = View.VISIBLE
            binding.bottomActionBar.visibility = View.VISIBLE
            binding.fabAddReminder.visibility = View.VISIBLE
        }
    }

    private fun updateReminder(reminder: Reminder, newText: String, newRadius: Int) {
        // Check if user chose a new location during edit
        val newLat = pendingEditPoint?.latitude ?: reminder.lat
        val newLng = pendingEditPoint?.longitude ?: reminder.lng
        val updated = reminder.copy(text = newText, radiusM = newRadius, lat = newLat, lng = newLng)

        // Clear edit-location state
        editingReminderId = null
        pendingEditPoint = null
        exitEditLocationMode()
        restoreNormalModeUI()

        lifecycleScope.launch {
            db.reminderDao().insert(updated) // REPLACE strategy
            
            // Remove old geofence and add new one (at new location if changed)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    geofence.remove(reminder.id) {
                        geofence.add(updated) { err ->
                            err?.let { Toast.makeText(this@MainActivity, getString(R.string.toast_geofence_error, it), Toast.LENGTH_SHORT).show() }
                        }
                    }
                } catch (_: SecurityException) { /* permission checked above */ }
            }
            
            // Update marker position, title and radius
            markerMap[reminder.id]?.let { marker ->
                marker.position = GeoPoint(newLat, newLng)
                marker.title = newText
                marker.subDescription = getString(R.string.radius_prefix, newRadius)
                binding.map.invalidate()
            }
            
            // Update circle at new location with new radius
            circleMap[reminder.id]?.let { circle ->
                circle.points = createCirclePoints(GeoPoint(newLat, newLng), newRadius.toDouble())
                binding.map.invalidate()
            }
            
            Toast.makeText(this@MainActivity, R.string.toast_reminder_updated, Toast.LENGTH_SHORT).show()
            loadReminders()
        }
    }

    private fun deleteReminder(reminder: Reminder) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_title)
            .setMessage(getString(R.string.dialog_delete_message, reminder.text))
            .setPositiveButton(R.string.dialog_delete_confirm) { _, _ ->
                performDelete(reminder)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun performDelete(reminder: Reminder) {
        lifecycleScope.launch {
            db.reminderDao().delete(reminder)
            
            // Remove geofence
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    geofence.remove(reminder.id) { err ->
                        err?.let {
                            Toast.makeText(this@MainActivity, getString(R.string.toast_geofence_removal_error, it), Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (_: SecurityException) { /* permission checked above */ }
            }
            
            // Remove marker
            markerMap.remove(reminder.id)?.let { marker ->
                binding.map.overlays.remove(marker)
                binding.map.invalidate()
            }
            
            // Remove circle
            circleMap.remove(reminder.id)?.let { circle ->
                binding.map.overlays.remove(circle)
                binding.map.invalidate()
            }
            
            Toast.makeText(this@MainActivity, R.string.toast_reminder_deleted, Toast.LENGTH_SHORT).show()
            loadReminders()
        }
    }

    private fun startLocationUpdates() {
        // When the Foreground Service is running, it owns location updates
        // to avoid duplicate callbacks and wasted battery
        if (isServiceDrivingMode) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(2000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    // Skip GPS updates when mock mode is active
                    currentLocation = Pair(location.latitude, location.longitude)
                    updateDistances()
                    updateCurrentLocationMarker(location.latitude, location.longitude)
                    if (isDrivingMode) {
                        updateDrivingModeDisplay()
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, null)
    }

    private fun updateDistances() {
        currentLocation?.let { loc ->
            adapter.updateLocations(mapOf("current" to loc))
        }
    }

    private fun centerMapOnCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.toast_location_required, Toast.LENGTH_SHORT).show()
            return
        }

        // Use last known location first (fast)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val point = GeoPoint(location.latitude, location.longitude)
                binding.map.controller.animateTo(point, binding.map.zoomLevelDouble, 1000L)
                currentLocation = Pair(location.latitude, location.longitude)
                updateDistances()
                updateCurrentLocationMarker(location.latitude, location.longitude)
            } else {
                Toast.makeText(this, R.string.toast_location_not_available, Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, R.string.toast_location_not_available, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCurrentLocationMarker(lat: Double, lng: Double) {
        val point = GeoPoint(lat, lng)
        
        if (currentLocationMarker == null) {
            // Create marker first time
            currentLocationMarker = Marker(binding.map).apply {
                position = point
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setIcon(ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_current_location))
                isDraggable = false
            }
            binding.map.overlays.add(currentLocationMarker)
        } else {
            // Update existing marker position
            currentLocationMarker?.position = point
        }
        binding.map.invalidate()
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
        // Only start our own location updates if the service is NOT running
        // (the service publishes via SharedFlow which we observe)
        if (geofence.hasPermission() && !isServiceDrivingMode) {
            startLocationUpdates()
        }
        // Re-show add-reminder popup if it was open before rotation
        pendingPoint?.let { point ->
            if (addReminderBottomSheet == null && !isDrivingMode) {
                showAddReminderBottomSheet(point)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        binding.map.onPause()
        // Only stop location updates if the service is NOT running
        // (the service handles its own location updates in the background)
        if (!isServiceDrivingMode) {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
    }

    private fun setupDrivingMode() {
        // Initialize DrivingTtsManager
        ttsManager = DrivingTtsManager(this)
        ttsManager.initialize()

        // Show popup when a geofence reminder triggers
        ttsManager.onReminderTriggered = { reminder, distance ->
            showDrivingReminderAlertPopup(reminder, distance)
        }

        // FAB: exit driving mode
        TooltipCompat.setTooltipText(binding.fabDrivingExit, getString(R.string.btn_exit_driving_mode))
        binding.fabDrivingExit.setOnClickListener {
            exitDrivingMode()
        }

        // FAB: voice recording
        TooltipCompat.setTooltipText(binding.fabDrivingVoice, getString(R.string.driving_btn_voice))
        binding.fabDrivingVoice.setOnClickListener {
            startDrivingVoiceRecording()
        }

        // FAB: add reminder at current location
        TooltipCompat.setTooltipText(binding.fabDrivingAddReminder, getString(R.string.driving_btn_add_reminder))
        binding.fabDrivingAddReminder.setOnClickListener {
            saveDrivingModeReminder(null)
        }

        // FAB: re-center map on current location
        TooltipCompat.setTooltipText(binding.fabDrivingMyLocation, getString(R.string.btn_my_location))
        binding.fabDrivingMyLocation.setOnClickListener {
            centerMapOnCurrentLocation()
        }
    }

    private fun toggleDrivingMode() {
        if (isDrivingMode) {
            exitDrivingMode()
        } else {
            checkBatteryOptimizationAndEnter()
        }
    }

    /**
     * Check if the app is whitelisted from battery optimization.
     * If not, warn the user before entering driving mode — the service
     * may be killed by the system in the background.
     */
    private fun checkBatteryOptimizationAndEnter() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.battery_opt_title)
                .setMessage(R.string.battery_opt_message)
                .setPositiveButton(R.string.battery_opt_open) { _, _ ->
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                    } catch (_: Exception) {
                        // Fallback: open general battery optimization settings
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    }
                    // Enter driving mode after the user returns from settings
                }
                .setNegativeButton(R.string.battery_opt_dismiss) { _, _ ->
                    enterDrivingMode()
                }
                .setOnCancelListener {
                    enterDrivingMode()
                }
                .show()
        } else {
            enterDrivingMode()
        }
    }

    private fun enterDrivingMode() {
        isDrivingMode = true
        
        // Dismiss add-reminder popup if open
        addReminderBottomSheet?.dismiss()
        addReminderBottomSheet = null
        
        // Reset TTS state for fresh start
        ttsManager.reset()
        previousNearestId = null
        
        // Hide normal-mode UI
        binding.topBar.visibility = View.GONE
        binding.btnMenu.visibility = View.GONE
        binding.btnTopList.visibility = View.GONE
        binding.btnMyLocation.visibility = View.GONE
        binding.btnDrivingMode.visibility = View.GONE
        binding.bottomActionBar.visibility = View.GONE
        binding.fabAddReminder.visibility = View.GONE
        
        // Show driving mode overlay (map visible through it)
        binding.drivingOverlayWrapper.visibility = View.VISIBLE
        binding.drivingModeOverlay.root.visibility = View.VISIBLE
        binding.drivingModeOverlay.voiceRecordingIndicator.visibility = View.GONE
        
        // Show FABs with enter animation (staggered, fresh Animation per view)
        binding.fabDrivingVoice.visibility = View.VISIBLE
        binding.fabDrivingVoice.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_enter))
        binding.fabDrivingAddReminder.postDelayed({
            if (isDrivingMode) {
                binding.fabDrivingAddReminder.visibility = View.VISIBLE
                binding.fabDrivingAddReminder.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_enter))
            }
        }, 80)
        binding.fabDrivingMyLocation.postDelayed({
            if (isDrivingMode) {
                binding.fabDrivingMyLocation.visibility = View.VISIBLE
                binding.fabDrivingMyLocation.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_enter))
            }
        }, 160)
        binding.fabDrivingExit.postDelayed({
            if (isDrivingMode) {
                binding.fabDrivingExit.visibility = View.VISIBLE
                binding.fabDrivingExit.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_enter))
            }
        }, 240)
        
        // Center on current location
        centerMapOnCurrentLocation()
        
        // Update driving mode display with nearest reminder
        updateDrivingModeDisplay()
        
        // Stop our own location callback — the service will take over
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null

        // Start Foreground Service for background tracking + TTS
        startDrivingService()
        observeServiceLocation()
        
        Toast.makeText(this, R.string.driving_mode_active, Toast.LENGTH_SHORT).show()
    }

    private fun exitDrivingMode() {
        isDrivingMode = false
        isDrivingVoiceMode = false
        isServiceDrivingMode = false
        
        // Hide FABs with exit animation (fresh Animation per view, guard against rapid re-entry)
        binding.fabDrivingVoice.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_exit))
        binding.fabDrivingVoice.postDelayed({ if (!isDrivingMode) binding.fabDrivingVoice.visibility = View.GONE }, 200)
        binding.fabDrivingAddReminder.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_exit))
        binding.fabDrivingAddReminder.postDelayed({ if (!isDrivingMode) binding.fabDrivingAddReminder.visibility = View.GONE }, 200)
        binding.fabDrivingMyLocation.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_exit))
        binding.fabDrivingMyLocation.postDelayed({ if (!isDrivingMode) binding.fabDrivingMyLocation.visibility = View.GONE }, 200)
        binding.fabDrivingExit.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fab_exit))
        binding.fabDrivingExit.postDelayed({ if (!isDrivingMode) binding.fabDrivingExit.visibility = View.GONE }, 200)
        
        // Show normal-mode UI
        binding.topBar.visibility = View.VISIBLE
        binding.btnMenu.visibility = View.VISIBLE
        binding.btnTopList.visibility = View.VISIBLE
        binding.btnMyLocation.visibility = View.VISIBLE
        binding.btnDrivingMode.visibility = View.VISIBLE
        binding.bottomActionBar.visibility = View.VISIBLE
        binding.fabAddReminder.visibility = View.VISIBLE
        
        // Hide driving mode overlay
        binding.drivingModeOverlay.root.visibility = View.GONE
        binding.drivingModeOverlay.voiceRecordingIndicator.visibility = View.GONE
        binding.drivingOverlayWrapper.visibility = View.GONE
        
        // Stop TTS and driving service
        ttsManager.stop()
        drivingReminderPopup?.dismiss()
        drivingReminderPopup = null
        stopDrivingService()
        
        Toast.makeText(this, R.string.driving_mode_inactive, Toast.LENGTH_SHORT).show()
    }

    private fun startDrivingService() {
        NotificationHelper.ensureDrivingChannel(this)
        val intent = Intent(this, DrivingModeService::class.java).apply {
            action = DrivingModeService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceDrivingMode = true
        Log.d("DrivingMode", "Foreground service started")
    }

    private fun stopDrivingService() {
        if (isServiceDrivingMode) {
            val intent = Intent(this, DrivingModeService::class.java).apply {
                action = DrivingModeService.ACTION_STOP
            }
            startService(intent)
            isServiceDrivingMode = false
            Log.d("DrivingMode", "Foreground service stopped")
        }
    }

    private fun observeServiceLocation() {
        lifecycleScope.launch {
            DrivingModeService.locationUpdates.collect { loc ->
                // Service is publishing location updates in the background
                currentLocation = loc
                updateDistances()
                updateCurrentLocationMarker(loc.first, loc.second)
                if (isDrivingMode) {
                    updateDrivingModeDisplay()
                }
            }
        }
    }

    private fun startDrivingVoiceRecording() {
        if (!hasMicPermission()) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        if (isListening) return
        isDrivingVoiceMode = true
        pendingVoiceCmdResult = false
        isListening = true
        binding.drivingModeOverlay.voiceRecordingIndicator.visibility = View.VISIBLE
        binding.drivingModeOverlay.tvVoiceHint.text = getString(R.string.driving_voice_listening)
        startGoogleSpeechRecognition()
    }

    private fun saveDrivingModeReminder(spokenText: String?) {
        val loc = currentLocation
        if (loc == null) {
            ttsManager.speakFlush(getString(R.string.driving_no_location), "driving_noloc")
            Toast.makeText(this, R.string.driving_no_location, Toast.LENGTH_SHORT).show()
            return
        }

        val text = spokenText?.takeIf { it.isNotBlank() }
            ?: getString(R.string.default_reminder_text)
        val radius = Reminder.DEFAULT_RADIUS_M

        saveVoiceReminder(text, radius)
    }

    private fun updateDrivingModeDisplay() {
        if (!isDrivingMode) return

        currentLocation?.let { loc ->
            lifecycleScope.launch {
                val reminders = db.reminderDao().getAll()
                if (reminders.isEmpty()) {
                    binding.drivingModeOverlay.tvDrivingReminderText.text = getString(R.string.driving_mode_no_reminders)
                    binding.drivingModeOverlay.tvDrivingRadius.text = ""
                    binding.drivingModeOverlay.tvDrivingDistance.text = ""
                    return@launch
                }

                val df = java.text.DecimalFormat("#.#")

                // Delegate TTS state machine to DrivingTtsManager
                val nearest = ttsManager.processReminders(reminders, loc)

                // Update overlay with nearest reminder
                nearest?.let { r ->
                    val d = GeoUtils.haversineM(loc.first, loc.second, r.lat, r.lng)
                    val infoBar = binding.drivingModeOverlay.drivingInfoBar
                    val changed = previousNearestId != r.id
                    previousNearestId = r.id

                    if (changed && previousNearestId != null) {
                        // Cancel any in-progress fade to prevent race conditions
                        infoBar.animate().cancel()
                        // Fade out → update text → fade in
                        infoBar.animate().alpha(0f).setDuration(150).withEndAction {
                            binding.drivingModeOverlay.tvDrivingReminderText.text = r.text
                            binding.drivingModeOverlay.tvDrivingRadius.text = getString(R.string.driving_mode_radius, r.radiusM)
                            binding.drivingModeOverlay.tvDrivingDistance.text = getString(R.string.driving_mode_distance, "${df.format(d)}m")
                            infoBar.animate().alpha(1f).setDuration(200).start()
                        }.start()
                    } else {
                        binding.drivingModeOverlay.tvDrivingReminderText.text = r.text
                        binding.drivingModeOverlay.tvDrivingRadius.text = getString(R.string.driving_mode_radius, r.radiusM)
                        binding.drivingModeOverlay.tvDrivingDistance.text = getString(R.string.driving_mode_distance, "${df.format(d)}m")
                    }
                }
            }
        }
    }

    // ── Voice Command Flow (Driving Mode) ─────────────────────────

    private fun handleVoiceResult(text: String) {
        // Normal mode: fill text field with recognized speech
        if (!isDrivingMode) {
            if (popupEtText != null && addReminderBottomSheet?.isShowing == true) {
                popupEtText?.setText(text)
            } else if (pendingPoint != null) {
                // Popup was dismissed during voice recognition — re-show with recognized text
                pendingPoint?.let { point ->
                    showAddReminderBottomSheet(point)
                    popupEtText?.setText(text)
                }
            }
            Toast.makeText(this, "\"$text\"", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseRadius(text: String): Int {
        // Try to extract a number from the text
        val numbers = Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()
        return if (numbers.isNotEmpty()) {
            numbers.first().coerceIn(10, 1000) // Clamp between 10-1000m
        } else {
            // Check for "default" or "cien" or similar
            val lower = text.lowercase(java.util.Locale.getDefault())
            if (lower.contains("default") || lower.contains("cien") || lower.contains("normal")) {
                Reminder.DEFAULT_RADIUS_M
            } else {
                ttsManager.speakAdd(getString(R.string.vc_invalid_radius), "vc_invalid")
                Reminder.DEFAULT_RADIUS_M
            }
        }
    }

    private fun saveVoiceReminder(text: String, radiusM: Int) {
        val loc = currentLocation
        if (loc == null) {
            ttsManager.speakFlush(getString(R.string.vc_no_location), "vc_noloc")
            return
        }

        val reminder = Reminder(
            id = "r-${UUID.randomUUID().toString().substring(0, 12)}",
            text = text,
            lat = loc.first,
            lng = loc.second,
            radiusM = radiusM,
            notified = false
        )

        lifecycleScope.launch {
            db.reminderDao().insert(reminder)
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try {
                    geofence.add(reminder) { err ->
                        err?.let { Log.e("VoiceCmd", "Geofence error: $it") }
                    }
                } catch (_: SecurityException) { /* permission checked above */ }
            }
            addMarker(reminder)
            addCircle(reminder)
            loadReminders()

            // Mark as recently created to skip immediate geofence TTS
            ttsManager.markRecentlyCreated(reminder.id)

            val msg = getString(R.string.vc_saved, text, radiusM)
            ttsManager.speakFlush(msg, "vc_saved")
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListeningForVoiceCommand() {
        if (isListening) return
        pendingVoiceCmdResult = true
        isListening = true
        startGoogleSpeechRecognition()
    }

    private fun handleVoiceCommandResult(text: String) {
        val lower = text.lowercase(java.util.Locale.getDefault())

        // Check for cancel keywords
        if (voiceCmdState != VoiceCommandState.IDLE &&
            (lower.contains("cancelar") || lower.contains("cancela") || lower.trim() == "no")) {
            cancelVoiceCommand()
            ttsManager.speakFlush(getString(R.string.vc_cancelled), "vc_cancel")
            return
        }

        when (voiceCmdState) {
            VoiceCommandState.IDLE -> {
                if (lower.contains("recordatorio") || lower.contains("guardar recordatorio") ||
                    lower.contains("nuevo recordatorio")) {
                    val keywordPatterns = listOf("recordatorio", "guardar recordatorio", "nuevo recordatorio")
                    var remainder = text
                    for (keyword in keywordPatterns) {
                        val idx = lower.indexOf(keyword)
                        if (idx >= 0) {
                            remainder = text.substring(idx + keyword.length).trim()
                            break
                        }
                    }
                    if (remainder.isNotBlank()) {
                        pendingVoiceText = remainder
                        voiceCmdState = VoiceCommandState.ASK_RADIUS
                        ttsManager.speakFlush(getString(R.string.vc_ask_radius), "vc_radius")
                        startListeningForVoiceCommand()
                    } else {
                        voiceCmdState = VoiceCommandState.ASK_TEXT
                        ttsManager.speakFlush(getString(R.string.vc_ask_text), "vc_text")
                        startListeningForVoiceCommand()
                    }
                }
            }

            VoiceCommandState.ASK_TEXT -> {
                pendingVoiceText = text
                voiceCmdState = VoiceCommandState.ASK_RADIUS
                ttsManager.speakFlush(getString(R.string.vc_ask_radius), "vc_radius")
                startListeningForVoiceCommand()
            }

            VoiceCommandState.ASK_RADIUS -> {
                val radius = parseRadius(text)
                voiceCmdState = VoiceCommandState.IDLE
                saveVoiceReminder(pendingVoiceText, radius)
                pendingVoiceText = ""
            }
        }
    }

    private fun cancelVoiceCommand() {
        voiceCmdState = VoiceCommandState.IDLE
        pendingVoiceText = ""
        ttsManager.stop()
    }

    private fun toggleVoiceInput() {
        if (!hasMicPermission()) {
            permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            return
        }
        if (isListening) {
            resetVoiceButton()
            return
        }
        pendingVoiceCmdResult = false
        isListening = true
        addReminderBottomSheet?.let {
            it.contentView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPopupVoiceInput)?.text = getString(R.string.btn_voice_stop)
        }
        startGoogleSpeechRecognition()
    }

    private fun resetVoiceButton() {
        isListening = false
        if (addReminderBottomSheet?.isShowing == true) {
            addReminderBottomSheet?.let {
                it.contentView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnPopupVoiceInput)?.text = getString(R.string.btn_voice_input)
            }
        }
    }

    private fun startGoogleSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        speechLauncher.launch(intent)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
    }

    /** Check if device has internet connectivity (WiFi or mobile data). */
    private fun hasInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showMenuBottomSheet() {
        menuPopup?.dismiss()

        val popupView = layoutInflater.inflate(R.layout.popup_bottom_menu, null)

        val popupWindow = android.widget.PopupWindow(
            popupView,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 16f * resources.displayMetrics.density
            showAsDropDown(binding.btnMenu, 0, 0, android.view.Gravity.BOTTOM)
            setOnDismissListener { menuPopup = null }
        }
        menuPopup = popupWindow

        // Close button
        popupView.findViewById<android.widget.ImageButton>(R.id.btnCloseMenu).setOnClickListener {
            popupWindow.dismiss()
        }
    }

    override fun onDestroy() {
        // Stop driving service if still running
        stopDrivingService()
        // Shutdown TTS and speech recognition
        ttsManager.shutdown()

        super.onDestroy()
        binding.map.onDetach()
    }
}