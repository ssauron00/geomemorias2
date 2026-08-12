package com.example.geomemorias2

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Reminder — recordatorio anclado a una coordenada + radio.
 * Igual modelo que la v1 web (Documento Tecnico), adaptado a Room.
 *
 * Regla de oro (de la v1): el recordatorio YA EXISTE. La app valida
 * proximidad (distancia <= radioM) y notifica; NO crea nada al chequear.
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: String,
    val text: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Int,        // metros (default 50)
    val notified: Boolean    // estado de histeresis (evita spam dentro del radio)
) {
    companion object {
        const val DEFAULT_RADIUS_M = 50
    }
}
