package com.example.speedwidget

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Écran simple : permissions, démarrage/arrêt du suivi, ajout du widget. */
class MainActivity : ComponentActivity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var overlayButton: Button

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasLocationPermission()) startTracking()
            else Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dp = resources.displayMetrics.density
        fun px(v: Int) = (v * dp).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B1020"))
            setPadding(px(24), px(48), px(24), px(24))
        }

        root.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.parseColor("#FF5A4D"))
            textSize = 28f
            gravity = Gravity.CENTER
        })

        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, px(24), 0, px(24))
        }
        root.addView(status)

        toggle = Button(this).apply {
            setOnClickListener {
                if (SpeedState.read(this@MainActivity).running) stopTracking() else requestAndStart()
            }
        }
        root.addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)))

        root.addView(Button(this).apply {
            text = getString(R.string.reset_trip)
            setOnClickListener { SpeedState.resetTrip(this@MainActivity); refresh() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)).apply { topMargin = px(12) })

        overlayButton = Button(this).apply {
            setOnClickListener { toggleOverlay() }
        }
        root.addView(overlayButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)).apply { topMargin = px(12) })

        root.addView(Button(this).apply {
            text = getString(R.string.add_widget)
            setOnClickListener { pinWidget() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, px(56)).apply { topMargin = px(12) })

        root.addView(TextView(this).apply {
            text = getString(R.string.help)
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 14f
            setPadding(0, px(32), 0, 0)
        })

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val s = SpeedState.read(this)
        status.text = if (s.running) getString(R.string.status_on) else getString(R.string.status_off)
        toggle.text = if (s.running) getString(R.string.stop) else getString(R.string.start)
        overlayButton.text = getString(
            if (OverlayController.isEnabled(this) && Settings.canDrawOverlays(this)) R.string.overlay_off
            else R.string.overlay_on
        )
    }

    /** Widget flottant : pour les écrans (AI box de voiture…) qui n'acceptent pas les widgets Android. */
    private fun toggleOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.overlay_permission, Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            } catch (e: Exception) {
                Toast.makeText(this, R.string.overlay_no_settings, Toast.LENGTH_LONG).show()
            }
            return
        }

        val enable = !OverlayController.isEnabled(this)
        OverlayController.setEnabled(this, enable)

        if (SpeedState.read(this).running) {
            startService(Intent(this, SpeedService::class.java).setAction(SpeedService.ACTION_OVERLAY_SYNC))
        } else if (enable) {
            requestAndStart() // le widget flottant apparaît dès que le suivi démarre
        }
        window.decorView.postDelayed({ refresh() }, 300)
    }

    private fun hasLocationPermission() =
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestAndStart() {
        if (hasLocationPermission()) {
            startTracking()
            refresh()
            return
        }
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun startTracking() {
        startForegroundService(Intent(this, SpeedService::class.java).setAction(SpeedService.ACTION_START))
    }

    private fun stopTracking() {
        startService(Intent(this, SpeedService::class.java).setAction(SpeedService.ACTION_STOP))
        window.decorView.postDelayed({ refresh() }, 300)
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, SpeedWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, R.string.pin_unsupported, Toast.LENGTH_LONG).show()
        }
    }
}
