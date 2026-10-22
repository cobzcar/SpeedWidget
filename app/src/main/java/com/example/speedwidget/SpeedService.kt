package com.example.speedwidget

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat

/** Service au premier plan qui lit le GPS et met à jour le widget chaque seconde. */
class SpeedService : Service(), LocationListener {

    private lateinit var locationManager: LocationManager
    private lateinit var overlay: OverlayController
    private var lastLocation: Location? = null
    private var tripKm = 0f
    private var maxKmh = 0f

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LocationManager::class.java)
        overlay = OverlayController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Activer / désactiver le widget flottant pendant que le suivi tourne
        if (intent?.action == ACTION_OVERLAY_SYNC) {
            if (SpeedState.read(this).running) {
                overlay.syncWithPrefs()
                overlay.update(SpeedState.read(this))
                return START_STICKY
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val hasPermission = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        val previous = SpeedState.read(this)
        tripKm = previous.tripKm
        maxKmh = previous.maxKmh

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        }

        val started = previous.copy(running = true, speedKmh = 0f)
        SpeedState.write(this, started)
        SpeedWidgetProvider.updateAll(this)
        overlay.syncWithPrefs()
        overlay.update(started)
        return START_STICKY
    }

    override fun onLocationChanged(location: Location) {
        val kmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
        val speed = if (kmh < 1f) 0f else kmh

        lastLocation?.let { prev ->
            if (speed > 2f) tripKm += prev.distanceTo(location) / 1000f
        }
        lastLocation = location
        if (speed > maxKmh) maxKmh = speed

        val snapshot = SpeedSnapshot(speed, maxKmh, tripKm, true)
        SpeedState.write(this, snapshot)
        SpeedWidgetProvider.updateAll(this)
        overlay.update(snapshot)
    }

    // Requis pour Android 8 à 10 (méthodes abstraites de l'interface)
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        overlay.hide()
        locationManager.removeUpdates(this)
        val s = SpeedState.read(this)
        SpeedState.write(this, s.copy(running = false, speedKmh = 0f))
        SpeedWidgetProvider.updateAll(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 2, Intent(this, SpeedService::class.java).setAction(ACTION_STOP), flags
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(openApp)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    companion object {
        const val ACTION_START = "com.example.speedwidget.START"
        const val ACTION_STOP = "com.example.speedwidget.STOP"
        const val ACTION_OVERLAY_SYNC = "com.example.speedwidget.OVERLAY_SYNC"
        private const val CHANNEL_ID = "speed_tracking"
        private const val NOTIF_ID = 1
    }
}
