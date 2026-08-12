package com.example.geomemorias2

import android.location.Location
import kotlin.math.*

/**
 * GeoUtils — mismo núcleo de la v1 web (geo.js), portado a Kotlin.
 * - haversineM: distancia en metros entre dos puntos.
 * - shouldNotify: aplica histeresis (no duplica mientras estés adentro).
 * - intervalForDistance: polling adaptativo por buckets (ahorro de batería).
 */
object GeoUtils {

    private const val R = 6371000.0 // radio de la Tierra en metros

    fun haversineM(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val lat1 = Math.toRadians(aLat)
        val lat2 = Math.toRadians(bLat)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
        return 2 * R * asin(sqrt(h))
    }

    fun haversineM(a: Location, bLat: Double, bLng: Double): Double =
        haversineM(a.latitude, a.longitude, bLat, bLng)

    /**
     * Lógica de proximidad + histeresis (igual que evaluateProximity en v1).
     * Devuelve el nuevo estado y si debe disparar notificación.
     */
    data class EvalResult(val notified: Boolean, val fire: Boolean)

    fun evaluate(distanceM: Double, radiusM: Int, wasNotified: Boolean): EvalResult {
        val inside = distanceM <= radiusM
        return if (inside && !wasNotified) {
            EvalResult(notified = true, fire = true)
        } else if (!inside && wasNotified) {
            EvalResult(notified = false, fire = false) // al salir, resetea
        } else {
            EvalResult(notified = wasNotified, fire = false)
        }
    }

    /**
     * Polling adaptativo: intervalo hasta el próximo chequeo según distancia
     * al recordatorio más cercano (igual tabla que v1).
     */
    fun intervalForDistance(distanceM: Double): Long = when {
        distanceM > 10_000 -> 300_000L  // 5 min
        distanceM > 1_000  -> 120_000L  // 2 min
        distanceM > 100    -> 30_000L   // 30 s
        else               -> 10_000L   // 10 s
    }
}
