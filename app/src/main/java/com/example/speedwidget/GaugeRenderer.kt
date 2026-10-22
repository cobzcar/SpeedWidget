package com.example.speedwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Dessine la jauge néon rouge (style de vos visuels) dans un Bitmap carré. */
object GaugeRenderer {

    private val RED = Color.parseColor("#FF5A4D")
    private val RED_DIM = Color.parseColor("#55FF5A4D")
    private val DARK_1 = Color.parseColor("#151B30")
    private val DARK_2 = Color.parseColor("#070A14")

    private const val START_ANGLE = 135f
    private const val SWEEP = 270f
    private const val TICKS = 48 // une graduation tous les 5 km/h sur 240 km/h

    fun render(
        size: Int,
        speedKmh: Float,
        running: Boolean,
        hint: String?,
        maxKmh: Float = 240f
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), size / 2f, size / 2f, size, speedKmh, running, hint, maxKmh, 255)
        return bmp
    }

    /**
     * GRAND widget : visuel de la voiture en fond, la jauge est dessinée par-dessus
     * à l'emplacement du cadran du visuel (qu'elle recouvre).
     */
    fun renderOnCar(context: Context, w: Int, h: Int, speedKmh: Float, running: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawBitmap(carBackground(context), null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        val size = (w * 0.92f).toInt()
        draw(c, w / 2f, h * 0.31f, size, speedKmh, running, null, 240f, 235)
        return bmp
    }
	fun renderOnSpeed(
		context: Context,
		size: Int,
		speedKmh: Float,
		running: Boolean,
		hint: String?
	): Bitmap {
		val bmp = Bitmap.createBitmap(
			size, size, Bitmap.Config.ARGB_8888
		)
		val canvas = Canvas(bmp)

		val background = BitmapFactory.decodeResource(
			context.resources,
			R.drawable.bg_speed,
			BitmapFactory.Options().apply { inScaled = false }
		)

		canvas.drawBitmap(
			background,
			null,
			Rect(0, 0, size, size),
			Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
		)

		draw(
			canvas,
			size / 2f,
			size / 2f,
			size,
			speedKmh,
			running,
			hint,
			240f,
			255
		)

		background.recycle()
		return bmp
	}
    private var cachedCar: Bitmap? = null

    private fun carBackground(context: Context): Bitmap =
        cachedCar ?: BitmapFactory.decodeResource(
            context.resources,
            R.drawable.bg_car,
            BitmapFactory.Options().apply { inScaled = false }
        ).also { cachedCar = it }

    private fun draw(
        c: Canvas,
        cx: Float,
        cy: Float,
        size: Int,
        speedKmh: Float,
        running: Boolean,
        hint: String?,
        maxKmh: Float,
        discAlpha: Int
    ) {
        val outer = size / 2f - size * 0.05f
        val frac = if (running) (speedKmh / maxKmh).coerceIn(0f, 1f) else 0f

        // Disque sombre
        val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, cy, outer, intArrayOf(DARK_1, DARK_2), null, Shader.TileMode.CLAMP)
            alpha = discAlpha
        }
        c.drawCircle(cx, cy, outer * 0.9f, disc)

        // Arc extérieur (piste + progression lumineuse)
        val rect = RectF(cx - outer, cy - outer, cx + outer, cy + outer)
        val stroke = size * 0.035f

        val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            color = RED_DIM
        }
        c.drawArc(rect, START_ANGLE, SWEEP, false, track)

        if (frac > 0f) {
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke * 1.6f
                strokeCap = Paint.Cap.ROUND
                color = RED
                maskFilter = BlurMaskFilter(stroke * 1.2f, BlurMaskFilter.Blur.NORMAL)
            }
            val crisp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = RED
            }
            c.drawArc(rect, START_ANGLE, SWEEP * frac, false, glow)
            c.drawArc(rect, START_ANGLE, SWEEP * frac, false, crisp)
        }

        // Graduations
        val tickOuter = outer * 0.80f
        val litGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = RED
            maskFilter = BlurMaskFilter(size * 0.012f, BlurMaskFilter.Blur.NORMAL)
        }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        for (i in 0..TICKS) {
            val ratio = i / TICKS.toFloat()
            val a = Math.toRadians((START_ANGLE + SWEEP * ratio).toDouble())
            val major = i % 4 == 0
            val len = if (major) outer * 0.15f else outer * 0.08f
            val lit = ratio <= frac && running
            val x1 = cx + cos(a).toFloat() * tickOuter
            val y1 = cy + sin(a).toFloat() * tickOuter
            val x2 = cx + cos(a).toFloat() * (tickOuter - len)
            val y2 = cy + sin(a).toFloat() * (tickOuter - len)
            val w = if (major) size * 0.013f else size * 0.007f
            if (lit) {
                litGlow.strokeWidth = w * 2f
                c.drawLine(x1, y1, x2, y2, litGlow)
            }
            tickPaint.strokeWidth = w
            tickPaint.color = if (lit) RED else RED_DIM
            c.drawLine(x1, y1, x2, y2, tickPaint)
        }

        // Vitesse (grand nombre central)
        val speedText = if (running) speedKmh.roundToInt().toString() else "--"
        val speedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * if (speedText.length >= 3) 0.22f else 0.27f
            setShadowLayer(size * 0.035f, 0f, 0f, RED)
        }
        val fm = speedPaint.fontMetrics
        val speedY = cy - (fm.ascent + fm.descent) / 2f - size * 0.03f
        c.drawText(speedText, cx, speedY, speedPaint)

        // Unité
        val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = size * 0.08f
        }
        c.drawText("km/h", cx, cy + size * 0.17f, unitPaint)

        // Petit message d'aide (quand le suivi est arrêté)
        if (hint != null) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(190, 255, 255, 255)
                textAlign = Paint.Align.CENTER
                textSize = size * 0.05f
            }
            c.drawText(hint, cx, cy + size * 0.28f, hintPaint)
        }
    }
}
