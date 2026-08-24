package com.example.geomemorias2

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template

/**
 * ReminderDetailScreen — pantalla de detalle para un recordatorio en Android Auto.
 *
 * Muestra información detallada del recordatorio seleccionado:
 * - Texto del recordatorio
 * - Coordenadas exactas
 * - Radio configurado
 * - Estado de notificación
 *
 * Usa PaneTemplate con filas de información.
 */
class ReminderDetailScreen(
    carContext: CarContext,
    private val reminder: Reminder
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        // Fila 1: Texto del recordatorio
        val textRow = Row.Builder()
            .setTitle(reminder.text)
            .addText(carContext.getString(R.string.car_detail_radius, reminder.radiusM))
            .build()

        // Fila 2: Coordenadas
        val coordsRow = Row.Builder()
            .setTitle("📍 Coordenadas")
            .addText(carContext.getString(R.string.car_detail_coords, reminder.lat, reminder.lng))
            .build()

        // Fila 3: Estado de notificación
        val notifiedText = if (reminder.notified) {
            carContext.getString(R.string.car_detail_yes)
        } else {
            carContext.getString(R.string.car_detail_no)
        }
        val statusRow = Row.Builder()
            .setTitle("🔔 Notificación")
            .addText(carContext.getString(R.string.car_detail_notification, notifiedText))
            .build()

        // Construir el pane con las filas
        val pane = Pane.Builder()
            .addRow(textRow)
            .addRow(coordsRow)
            .addRow(statusRow)
            .build()

        return PaneTemplate.Builder(pane)
            .setTitle(reminder.text)
            .setHeaderAction(Action.BACK)
            .build()
    }
}
