package com.example.geomemorias2

import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ReminderListScreen — pantalla principal de Android Auto.
 *
 * Muestra la lista de recordatorios como filas en un ListTemplate.
 * Cada fila muestra: título del recordatorio, distancia, radio.
 * Click en una fila abre el detalle (PaneTemplate).
 *
 * Se actualiza periódicamente para mostrar distancias en tiempo real.
 */
class ReminderListScreen(carContext: CarContext) : Screen(carContext) {

    private var reminders: List<Reminder> = emptyList()
    private var currentLat: Double = 0.0
    private var currentLng: Double = 0.0

    init {
        loadData()
        // Auto-refresh cada 10s solo mientras el screen está en primer plano
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(10_000)
                    loadData()
                }
            }
        }
    }

    private fun loadData() {
        lifecycleScope.launch {
            try {
                val db = AppDatabaseProvider.get(carContext)
                reminders = db.reminderDao().getAll()

                // Intentar obtener última ubicación conocida
                try {
                    val fusedClient = com.google.android.gms.location.LocationServices
                        .getFusedLocationProviderClient(carContext)
                    fusedClient.lastLocation.addOnSuccessListener { loc ->
                        loc?.let {
                            currentLat = it.latitude
                            currentLng = it.longitude
                            invalidate()
                        }
                    }
                } catch (e: SecurityException) {
                    Log.w("CarReminderList", "Sin permiso de ubicación: ${e.message}")
                }

                invalidate()
            } catch (e: Exception) {
                Log.e("CarReminderList", "Error cargando recordatorios: ${e.message}")
            }
        }
    }

    override fun onGetTemplate(): Template {
        val df = java.text.DecimalFormat("#.#")

        val rows = reminders.map { reminder ->
            val distance = if (currentLat != 0.0 && currentLng != 0.0) {
                val dist = GeoUtils.haversineM(currentLat, currentLng, reminder.lat, reminder.lng)
                df.format(dist) + "m"
            } else {
                carContext.getString(R.string.car_no_distance)
            }

            Row.Builder()
                .setTitle(reminder.text)
                .addText(carContext.getString(R.string.car_radius, reminder.radiusM))
                .addText(carContext.getString(R.string.car_distance, distance))
                .setOnClickListener {
                    screenManager.push(ReminderDetailScreen(carContext, reminder))
                }
                .build()
        }

        val itemList = if (rows.isNotEmpty()) {
            ItemList.Builder()
                .apply { rows.forEach { addItem(it) } }
                .build()
        } else {
            ItemList.Builder()
                .addItem(
                    Row.Builder()
                        .setTitle(carContext.getString(R.string.car_empty_list))
                        .build()
                )
                .build()
        }

        val sectionedList = SectionedItemList.create(
            itemList,
            carContext.getString(R.string.car_list_title)
        )

        val actionStrip = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("🔄")
                    .setOnClickListener { loadData() }
                    .build()
            )
            .build()

        return ListTemplate.Builder()
            .setTitle(carContext.getString(R.string.car_list_title))
            .setHeaderAction(Action.BACK)
            .addSectionedList(sectionedList)
            .setActionStrip(actionStrip)
            .build()
    }
}
