package com.example.speedwidget

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Widget FLOTTANT : une fenêtre affichée par-dessus toutes les applications.
 * Pour les écrans qui n'acceptent pas les widgets Android (AI box de voiture, BigdroidOS…).
 *
 *  - glisser            : déplacer
 *  - pincer             : agrandir / réduire
 *  - appui simple       : changer de format (petit -> moyen -> grand)
 */
class OverlayController(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density

    private var view: ImageView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var last = SpeedSnapshot(0f, 0f, 0f, false)

    private var kind = prefs.getInt(K_KIND, MEDIUM).coerceIn(SMALL, LARGE)
    private var scale = prefs.getFloat(K_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)

    fun canDraw(): Boolean = Settings.canDrawOverlays(context)

    /** Affiche la fenêtre si l'option est activée et la permission accordée. */
    fun syncWithPrefs() {
        if (isEnabled(context)) show() else hide()
    }

    fun show() {
        if (view != null || !canDraw()) return

        val iv = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val params = WindowManager.LayoutParams(
            widthPx(), heightPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt(K_X, (40 * density).toInt())
            y = prefs.getInt(K_Y, (80 * density).toInt())
        }

        try {
            wm.addView(iv, params)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.overlay_failed, Toast.LENGTH_LONG).show()
            return
        }
        view = iv
        lp = params
        attachTouch(iv, params)
        render()
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        lp = null
    }

    fun update(s: SpeedSnapshot) {
        last = s
        if (view != null) render()
    }

    private fun render() {
        val iv = view ?: return
        val bmp = when (kind) {
            SMALL -> GaugeRenderer.renderSmall(last.speedKmh, last.running)
            MEDIUM -> GaugeRenderer.renderMedium(context, last.speedKmh, last.running, null)
            else -> GaugeRenderer.renderLarge(context, last.speedKmh, last.running, null)
        }
        iv.setImageBitmap(bmp)
    }

    private fun widthPx() = (BASE_W_DP[kind] * scale * density).roundToInt()
    private fun heightPx() = (BASE_H_DP[kind] * scale * density).roundToInt()

    private fun applySize() {
        val p = lp ?: return
        p.width = widthPx()
        p.height = heightPx()
        view?.let { runCatching { wm.updateViewLayout(it, p) } }
        prefs.edit().putInt(K_KIND, kind).putFloat(K_SCALE, scale).apply()
    }

    private fun attachTouch(v: ImageView, p: WindowManager.LayoutParams) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var downTime = 0L
        var moved = false

        val pinch = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                scale = (scale * d.scaleFactor).coerceIn(MIN_SCALE, MAX_SCALE)
                applySize()
                return true
            }
        })

        v.setOnTouchListener { _, e ->
            pinch.onTouchEvent(e)
            if (pinch.isInProgress) {
                moved = true
                return@setOnTouchListener true
            }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                    downTime = e.eventTime
                    moved = false
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    // un doigt est levé après un pincement : on repart de la position actuelle
                    downX = e.rawX; downY = e.rawY
                    startX = p.x; startY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) moved = true
                    if (moved && e.pointerCount == 1) {
                        p.x = startX + dx.toInt()
                        p.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, p) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && e.eventTime - downTime < 400) {
                        kind = (kind + 1) % 3
                        applySize()
                        render()
                    } else {
                        prefs.edit().putInt(K_X, p.x).putInt(K_Y, p.y).apply()
                    }
                }
            }
            true
        }
    }

    companion object {
        private const val PREFS = "overlay"
        private const val K_ENABLED = "enabled"
        private const val K_KIND = "kind"
        private const val K_SCALE = "scale"
        private const val K_X = "x"
        private const val K_Y = "y"

        private const val SMALL = 0
        private const val MEDIUM = 1
        private const val LARGE = 2

        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 3f

        // Taille de base de chaque format en dp (respecte les proportions des images)
        private val BASE_W_DP = floatArrayOf(140f, 200f, 160f)
        private val BASE_H_DP = floatArrayOf(70f, 200f, 240f)

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_ENABLED, enabled).apply()
        }
    }
}
