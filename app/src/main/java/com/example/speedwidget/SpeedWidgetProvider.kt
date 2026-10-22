package com.example.speedwidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.SizeF
import android.widget.RemoteViews
import kotlin.math.roundToInt

/**
 * Widget compteur de vitesse, redimensionnable, avec 3 présentations :
 *  - PETIT  : texte de la vitesse + km/h
 *  - MOYEN  : jauge néon avec la vitesse au centre
 *  - GRAND  : jauge néon + distance du trajet + vitesse max
 */
class SpeedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, manager, it) }
    }

    // Appelé à chaque redimensionnement du widget par l'utilisateur
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        update(context, manager, appWidgetId)
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    companion object {
        private const val SMALL = 0
        private const val MEDIUM = 1
        private const val LARGE = 2

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SpeedWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val state = SpeedState.read(context)

            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ : le système choisit la présentation selon la taille réelle
                RemoteViews(
                    mapOf(
                        SizeF(40f, 40f) to build(context, SMALL, state),
                        SizeF(100f, 100f) to build(context, MEDIUM, state),
                        SizeF(110f, 170f) to build(context, LARGE, state)
                    )
                )
            } else {
                // Android 8 à 11 : on lit la taille du widget et on choisit nous-mêmes
                val o = manager.getAppWidgetOptions(id)
                val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val kind = when {
                    w >= 105 && h >= 165 -> LARGE
                    w >= 95 && h >= 95 -> MEDIUM
                    else -> SMALL
                }
                build(context, kind, state)
            }
            manager.updateAppWidget(id, views)
        }

        private fun build(context: Context, kind: Int, s: SpeedSnapshot): RemoteViews {
            val hint = if (s.running) null else context.getString(R.string.tap_to_start)
            val views: RemoteViews = when (kind) {
                SMALL -> RemoteViews(context.packageName, R.layout.widget_small).apply {
                    setTextViewText(
                        R.id.speed_text,
                        if (s.running) s.speedKmh.roundToInt().toString() else "--"
                    )
                }

                MEDIUM -> RemoteViews(context.packageName, R.layout.widget_medium).apply {
                    setImageViewBitmap(
                        R.id.gauge_image,
                        GaugeRenderer.render(320, s.speedKmh, s.running, hint)
                    )
                }

                else -> RemoteViews(context.packageName, R.layout.widget_large).apply {
                    setImageViewBitmap(
                        R.id.gauge_image,
                        GaugeRenderer.renderOnCar(context, 480, 720, s.speedKmh, s.running)
                    )
                    setTextViewText(R.id.trip_value, String.format("%.1f km", s.tripKm))
                    setTextViewText(R.id.max_value, "${s.maxKmh.roundToInt()} km/h")
                    setTextViewText(R.id.status_text, hint ?: context.getString(R.string.tap_to_stop))
                }
            }
            views.setOnClickPendingIntent(R.id.root, clickIntent(context, s.running))
            return views
        }

        /** Un appui sur le widget démarre / arrête le suivi GPS (ou ouvre l'app si la permission manque). */
        private fun clickIntent(context: Context, running: Boolean): PendingIntent {
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            val hasPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            return if (hasPermission) {
                val i = Intent(context, SpeedService::class.java).setAction(
                    if (running) SpeedService.ACTION_STOP else SpeedService.ACTION_START
                )
                PendingIntent.getForegroundService(context, 0, i, flags)
            } else {
                PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), flags)
            }
        }
    }
}
