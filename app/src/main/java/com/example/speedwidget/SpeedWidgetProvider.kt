package com.example.speedwidget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.RemoteViews
import kotlin.math.roundToInt

/**
 * Widget compteur de vitesse, redimensionnable, avec 3 présentations :
 *  - PETIT  : texte de la vitesse + km/h
 *  - MOYEN  : cadran carré (widget_medium.png) + vitesse et jauge
 *  - GRAND  : cadran + voiture (widget_grand.png) + vitesse et jauge
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

            // Taille réelle du widget sur l'écran d'accueil (en dp), selon l'orientation du téléphone
            val o = manager.getAppWidgetOptions(id)
            val landscape =
                context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            val w = o.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
                else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0
            )
            val h = o.getInt(
                if (landscape) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
                else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0
            )
            manager.updateAppWidget(id, build(context, pickKind(w, h), state))
        }

        /**
         * Choix de la présentation d'après la FORME du widget (et non des seuils fixes) :
         *  - un côté inférieur à 110 dp (ex. 1x1, 2x1, 3x1)  -> PETIT (texte)
         *  - plutôt carré (ex. 2x2, 3x3, 3x2)                 -> MOYEN (cadran carré)
         *  - nettement plus haut que large (ex. 2x3, 2x4)     -> GRAND (cadran + voiture)
         */
        private fun pickKind(w: Int, h: Int): Int {
            if (w <= 0 || h <= 0) return MEDIUM
            if (minOf(w, h) < 110) return SMALL
            return if (h.toFloat() / w >= 1.35f) LARGE else MEDIUM
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
                        GaugeRenderer.renderMedium(context, s.speedKmh, s.running, hint)
                    )
                }

                else -> RemoteViews(context.packageName, R.layout.widget_large).apply {
                    setImageViewBitmap(
                        R.id.gauge_image,
                        GaugeRenderer.renderLarge(context, s.speedKmh, s.running, hint)
                    )
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
