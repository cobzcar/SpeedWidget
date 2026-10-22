package com.example.speedwidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Utilise VOS images (face_medium.png / face_large.png) comme cadran et ajoute dessus :
 *  - le chiffre de la vitesse,
 *  - la jauge : graduations qui s'allument, aiguille lumineuse (+ anneau pour le moyen / arc pour le grand).
 *
 * Les angles sont en degrés, 0 = en haut, sens horaire. Toutes les coordonnées sont celles
 * des images d'origine (512x512 et 323x485).
 */
object GaugeRenderer {

    private const val SCALE = 1.5f        // rendu plus net que l'image d'origine
    private const val MAX_KMH = 240f      // vitesse en bout de jauge

    private val RED = Color.rgb(255, 90, 77)

    private class Ring(
        val cx: Float, val cy: Float, val minR: Float, val maxR: Float,
        val a0: Float, val a1: Float
    )

    /** Anneau (cadre) rendu semi-transparent par géométrie : utile quand l'anneau n'est pas rouge. */
    private class GeomRing(
        val cx: Float, val cy: Float, val minR: Float, val maxR: Float,
        val fullAngle: Float, val zeroAngle: Float   // transparence complète jusqu'à fullAngle, nulle à partir de zeroAngle
    )

    private class Spec(
        val resId: Int,
        // graduations
        val cx: Float, val cy: Float,
        val a0: Float, val a1: Float,
        val tickMinR: Float, val tickMaxR: Float,
        val tickLit: Int,
        val tickGlowBlur: Float,
        val redTicks: Boolean,            // true : graduations rouges dans l'image, false : blanches
        // aiguille
        val needleInner: Float, val needleOuter: Float, val needleWidth: Float,
        // anneau extérieur de l'image (moyen) ou arc dessiné (grand)
        val ring: Ring?,
        val ringOpacity: Float,           // opacité de l'anneau extérieur de l'image : 1 = opaque, 0 = invisible
        val geomRing: GeomRing?,
        val arcR: Float, val arcWidth: Float,
        // textes
        val numX: Float, val numY: Float, val numSize: Float, val numSize3: Float,
        val hintY: Float, val hintSize: Float
    )

    private val MEDIUM = Spec(
        resId = R.drawable.face_medium,
        cx = 256f, cy = 262f, a0 = -112f, a1 = 112f,
        tickMinR = 120f, tickMaxR = 196f,
        tickLit = Color.rgb(255, 236, 226), tickGlowBlur = 5f, redTicks = true,
        needleInner = 128f, needleOuter = 182f, needleWidth = 6f,
        ring = Ring(255.5f, 270.5f, 204f, 232f, -118.4f, 118.4f),
        ringOpacity = 0.45f,   // <-- RÉGLER ICI la transparence de l'anneau (0.0 à 1.0)
        geomRing = null,
        arcR = 0f, arcWidth = 0f,
        numX = 256f, numY = 250f, numSize = 124f, numSize3 = 104f,
        hintY = 300f, hintSize = 20f
    )

    private val LARGE = Spec(
        resId = R.drawable.face_large,
        cx = 162.25f, cy = 168.5f, a0 = -150f, a1 = 150f,
        tickMinR = 78f, tickMaxR = 106f,
        tickLit = Color.rgb(255, 84, 64), tickGlowBlur = 2.5f, redTicks = false,
        needleInner = 80f, needleOuter = 104f, needleWidth = 3f,
        ring = null,
        ringOpacity = 0.45f,   // <-- RÉGLER ICI la transparence de l'anneau du grand widget (0.0 à 1.0)
        geomRing = GeomRing(162.25f, 168.5f, 105f, 116.5f, 115f, 135f),
        arcR = 110.5f, arcWidth = 5f,
        numX = 162.25f, numY = 170f, numSize = 48f, numSize3 = 40f,
        hintY = 198f, hintSize = 10f
    )

    /** Calques précalculés une seule fois (graduations « allumées » + halo). */
    private class Face(
        val spec: Spec,
        val base: Bitmap,
        val tickLit: Bitmap,
        val tickGlow: Bitmap,
        val ringLit: Bitmap?,
        val ringGlow: Bitmap?
    )

    private val faces = HashMap<Int, Face>()

    @Synchronized
    private fun face(context: Context, spec: Spec): Face = faces.getOrPut(spec.resId) {
        val opts = BitmapFactory.Options().apply { inScaled = false }
        val base = BitmapFactory.decodeResource(context.resources, spec.resId, opts)
        val w = base.width
        val h = base.height
        val px = IntArray(w * h)
        base.getPixels(px, 0, w, 0, 0, w, h)

        val ticks = IntArray(w * h)
        val ring = if (spec.ring != null) IntArray(w * h) else null

        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = px[y * w + x]
                val r = Color.red(p)
                val g = Color.green(p)
                val b = Color.blue(p)
                val rt = hypot(x - spec.cx, y - spec.cy)

                if (rt in spec.tickMinR..spec.tickMaxR) {
                    var alpha = 0f
                    if (spec.redTicks) {
                        // graduations rouges : exclure la zone de l'anneau
                        val rg = spec.ring
                        val rr = if (rg != null) hypot(x - rg.cx, y - rg.cy) else 0f
                        if ((rg == null || rr < rg.minR) && r >= 120 && r - b >= 50) {
                            alpha = (r - 100) / 120f
                        }
                    } else {
                        // graduations blanches (voiture exclue : angle et hauteur limités)
                        val ang = Math.toDegrees(atan2((x - spec.cx).toDouble(), -(y - spec.cy).toDouble()))
                        val mn = min(r, min(g, b))
                        val mx = max(r, max(g, b))
                        if (abs(ang) <= 135.0 && y < 235 && mn >= 125 && mx - mn <= 70) {
                            alpha = (mn - 100) / 80f
                        }
                    }
                    if (alpha > 0f) {
                        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
                        ticks[y * w + x] = Color.argb(
                            a, Color.red(spec.tickLit), Color.green(spec.tickLit), Color.blue(spec.tickLit)
                        )
                    }
                }

                val rg = spec.ring
                if (rg != null && ring != null) {
                    val rr = hypot(x - rg.cx, y - rg.cy)
                    if (rr in rg.minR..rg.maxR && r >= 120 && r - b >= 50) {
                        val a = (((r - 100) / 120f).coerceIn(0f, 1f) * 255).toInt()
                        ring[y * w + x] = Color.argb(a, 255, 214, 204)
                    }
                }
            }
        }

        fun layer(data: IntArray): Bitmap {
            val bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bm.setPixels(data, 0, w, 0, 0, w, h)
            return bm
        }

        fun glow(bm: Bitmap, blur: Float): Bitmap =
            bm.extractAlpha(Paint().apply { maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }, null)

        // Anneau extérieur semi-transparent : on baisse l'alpha des pixels de l'anneau dans l'image de fond
        var shown = base
        var changed = false
        if (ring != null && spec.ringOpacity < 1f) {
            changed = true
            for (i in px.indices) {
                val weight = Color.alpha(ring[i]) / 255f
                if (weight > 0f) {
                    val newAlpha = (Color.alpha(px[i]) * (1f - weight * (1f - spec.ringOpacity))).toInt()
                    px[i] = (px[i] and 0x00FFFFFF) or (newAlpha shl 24)
                }
            }
        }
        val gr = spec.geomRing
        if (gr != null && spec.ringOpacity < 1f) {
            changed = true
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val rr = hypot(x - gr.cx, y - gr.cy)
                    if (rr < gr.minR - 1.5f || rr > gr.maxR + 1.5f) continue
                    val ang = abs(Math.toDegrees(atan2((x - gr.cx).toDouble(), -(y - gr.cy).toDouble())).toFloat())
                    val wr = ((rr - (gr.minR - 1.5f)) / 3f).coerceIn(0f, 1f) *
                        (((gr.maxR + 1.5f) - rr) / 3f).coerceIn(0f, 1f)
                    val wa = ((gr.zeroAngle - ang) / (gr.zeroAngle - gr.fullAngle)).coerceIn(0f, 1f)
                    val weight = wr * wa
                    if (weight > 0f) {
                        val i = y * w + x
                        val newAlpha = (Color.alpha(px[i]) * (1f - weight * (1f - spec.ringOpacity))).toInt()
                        px[i] = (px[i] and 0x00FFFFFF) or (newAlpha shl 24)
                    }
                }
            }
        }
        if (changed) {
            shown = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            shown.setPixels(px, 0, w, 0, 0, w, h)
        }

        val tickLit = layer(ticks)
        val ringLit = ring?.let { layer(it) }
        Face(
            spec, shown, tickLit, glow(tickLit, spec.tickGlowBlur),
            ringLit, ringLit?.let { glow(it, 4f) }
        )
    }

    /** Petit format : vitesse + « km/h » sur fond sombre (même rendu que le petit widget classique). */
    fun renderSmall(speedKmh: Float, running: Boolean): Bitmap {
        val w = 360
        val h = 180
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val box = RectF(2f, 2f, w - 2f, h - 2f)

        c.drawRoundRect(box, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), Color.rgb(27, 32, 51), Color.rgb(5, 7, 15), Shader.TileMode.CLAMP)
        })
        c.drawRoundRect(box, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.argb(85, 255, 90, 77)
        })

        val text = if (running) speedKmh.roundToInt().toString() else "--"
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (text.length >= 3) 92f else 108f
            setShadowLayer(14f, 0f, 0f, RED)
        }
        val tb = Rect()
        number.getTextBounds(text, 0, text.length, tb)
        c.drawText(text, w / 2f - (tb.left + tb.right) / 2f, h * 0.42f - (tb.top + tb.bottom) / 2f, number)

        val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = RED
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 34f
        }
        c.drawText("km/h", w / 2f, h * 0.88f, unit)
        return out
    }

    fun renderMedium(context: Context, speedKmh: Float, running: Boolean, hint: String?): Bitmap =
        render(face(context, MEDIUM), speedKmh, running, hint)

    fun renderLarge(context: Context, speedKmh: Float, running: Boolean, hint: String?): Bitmap =
        render(face(context, LARGE), speedKmh, running, hint)

    private fun render(f: Face, speedKmh: Float, running: Boolean, hint: String?): Bitmap {
        val s = f.spec
        val out = Bitmap.createBitmap(
            (f.base.width * SCALE).toInt(), (f.base.height * SCALE).toInt(), Bitmap.Config.ARGB_8888
        )
        val c = Canvas(out)
        c.scale(SCALE, SCALE)

        val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(f.base, 0f, 0f, bitmapPaint)

        val frac = if (running) (speedKmh / MAX_KMH).coerceIn(0f, 1f) else 0f
        val angle = s.a0 + (s.a1 - s.a0) * frac

        if (frac > 0f) {
            // Graduations allumées jusqu'à la vitesse actuelle
            c.save()
            c.clipPath(wedge(s.cx, s.cy, s.tickMaxR + 80f, s.a0, angle))
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; alpha = 255 }
            c.drawBitmap(f.tickGlow, 0f, 0f, glowPaint)
            c.drawBitmap(f.tickGlow, 0f, 0f, glowPaint)
            c.drawBitmap(f.tickLit, 0f, 0f, bitmapPaint)
            c.restore()

            // Anneau extérieur de l'image (moyen)
            val rg = s.ring
            if (rg != null && f.ringLit != null && f.ringGlow != null) {
                val ringAngle = rg.a0 + (rg.a1 - rg.a0) * frac
                c.save()
                c.clipPath(wedge(rg.cx, rg.cy, rg.maxR + 80f, rg.a0, ringAngle))
                c.drawBitmap(f.ringGlow, 0f, 0f, glowPaint)
                c.drawBitmap(f.ringLit, 0f, 0f, bitmapPaint)
                c.restore()
            }

            // Arc lumineux sur le cadre (grand)
            if (s.arcR > 0f) {
                val rect = RectF(s.cx - s.arcR, s.cy - s.arcR, s.cx + s.arcR, s.cy + s.arcR)
                val arcGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = s.arcWidth * 1.8f
                    strokeCap = Paint.Cap.ROUND
                    color = s.tickLit
                    maskFilter = BlurMaskFilter(s.arcWidth * 0.9f, BlurMaskFilter.Blur.NORMAL)
                }
                val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = s.arcWidth
                    strokeCap = Paint.Cap.ROUND
                    color = s.tickLit
                }
                c.drawArc(rect, s.a0 - 90f, angle - s.a0, false, arcGlow)
                c.drawArc(rect, s.a0 - 90f, angle - s.a0, false, arc)
            }
        }

        // Aiguille lumineuse
        val rad = Math.toRadians(angle.toDouble())
        val x0 = s.cx + sin(rad).toFloat() * s.needleInner
        val y0 = s.cy - cos(rad).toFloat() * s.needleInner
        val x1 = s.cx + sin(rad).toFloat() * s.needleOuter
        val y1 = s.cy - cos(rad).toFloat() * s.needleOuter
        val needleGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = s.needleWidth * 2.2f
            color = RED
            maskFilter = BlurMaskFilter(s.needleWidth * 1.3f, BlurMaskFilter.Blur.NORMAL)
        }
        val needle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = s.needleWidth
            color = Color.rgb(255, 240, 235)
        }
        if (running) {
            c.drawLine(x0, y0, x1, y1, needleGlow)
        }
        c.drawLine(x0, y0, x1, y1, needle)

        // Chiffre de la vitesse
        val text = if (running) speedKmh.roundToInt().toString() else "--"
        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = if (text.length >= 3) s.numSize3 else s.numSize
            setShadowLayer(s.numSize * 0.12f, 0f, 0f, RED)
        }
        // Centrage réel des chiffres : on centre le rectangle exact occupé par le texte (horizontal et vertical)
        val tb = Rect()
        number.getTextBounds(text, 0, text.length, tb)
        c.drawText(text, s.numX - (tb.left + tb.right) / 2f, s.numY - (tb.top + tb.bottom) / 2f, number)

        // Message d'aide quand le suivi est arrêté
        if (hint != null && !running) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(200, 255, 255, 255)
                textAlign = Paint.Align.CENTER
                textSize = s.hintSize
            }
            c.drawText(hint, s.numX, s.hintY, hintPaint)
        }

        return out
    }

    /** Secteur « camembert » entre deux angles (0 = haut, sens horaire). */
    private fun wedge(cx: Float, cy: Float, r: Float, fromDeg: Float, toDeg: Float): Path {
        val p = Path()
        p.moveTo(cx, cy)
        p.arcTo(RectF(cx - r, cy - r, cx + r, cy + r), fromDeg - 90f, toDeg - fromDeg)
        p.close()
        return p
    }
}
