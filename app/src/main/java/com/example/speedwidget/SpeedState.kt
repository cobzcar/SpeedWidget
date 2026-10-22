package com.example.speedwidget

import android.content.Context

/** Données partagées entre le service GPS, l'activité et le widget. */
data class SpeedSnapshot(
    val speedKmh: Float,
    val maxKmh: Float,
    val tripKm: Float,
    val running: Boolean
)

object SpeedState {
    private const val PREFS = "speed_state"
    private const val K_SPEED = "speed"
    private const val K_MAX = "max"
    private const val K_TRIP = "trip"
    private const val K_RUNNING = "running"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(c: Context): SpeedSnapshot {
        val p = prefs(c)
        return SpeedSnapshot(
            speedKmh = p.getFloat(K_SPEED, 0f),
            maxKmh = p.getFloat(K_MAX, 0f),
            tripKm = p.getFloat(K_TRIP, 0f),
            running = p.getBoolean(K_RUNNING, false)
        )
    }

    fun write(c: Context, s: SpeedSnapshot) {
        prefs(c).edit()
            .putFloat(K_SPEED, s.speedKmh)
            .putFloat(K_MAX, s.maxKmh)
            .putFloat(K_TRIP, s.tripKm)
            .putBoolean(K_RUNNING, s.running)
            .apply()
    }

    fun resetTrip(c: Context) {
        val s = read(c)
        write(c, s.copy(maxKmh = 0f, tripKm = 0f))
        SpeedWidgetProvider.updateAll(c)
    }
}
