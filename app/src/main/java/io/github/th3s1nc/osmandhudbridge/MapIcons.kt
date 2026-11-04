package io.github.th3s1nc.osmandhudbridge

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable

/** Gezeichnete Symbole (gleich auf jedem Handy, keine Emoji): Zielflagge, Diskette und Zielkreuz (Standort). */
class FlagDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
        val x0 = b.left + w * 0.22f; val y0 = b.top + h * 0.10f; val fw = w * 0.74f; val fh = h * 0.52f
        val cols = 4; val rows = 3; val cw = fw / cols; val ch = fh / rows
        for (r in 0 until rows) for (k in 0 until cols) {
            p.style = Paint.Style.FILL; p.color = if ((r + k) % 2 == 0) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
            c.drawRect(x0 + k * cw, y0 + r * ch, x0 + (k + 1) * cw, y0 + (r + 1) * ch, p)
        }
        p.style = Paint.Style.STROKE; p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = (w * 0.04f).coerceAtLeast(1f)
        c.drawRect(x0, y0, x0 + fw, y0 + fh, p)
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt()
        c.drawRect(b.left + w * 0.12f, b.top + h * 0.04f, b.left + w * 0.20f, b.top + h * 0.96f, p)
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

class FloppyDrawable : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds; val w = b.width().toFloat(); val h = b.height().toFloat()
        val l = b.left + w * 0.06f; val t = b.top + h * 0.06f; val r = b.left + w * 0.94f; val bt = b.top + h * 0.94f; val cut = w * 0.22f
        val body = Path().apply {
            moveTo(l, t); lineTo(r - cut, t); lineTo(r, t + cut); lineTo(r, bt); lineTo(l, bt); close()
        }
        p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); c.drawPath(body, p)
        p.color = 0xFF3A3A3C.toInt()
        c.drawRect(RectF(l + w * 0.20f, t, l + w * 0.62f, t + h * 0.30f), p)                 // Schieber oben
        c.drawRect(RectF(l + w * 0.14f, t + h * 0.52f, r - w * 0.14f, bt), p)                // Etikett unten
        p.color = 0xFFFFFFFF.toInt()
        c.drawRect(RectF(l + w * 0.46f, t + h * 0.05f, l + w * 0.56f, t + h * 0.24f), p)     // Schlitz im Schieber
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Zielkreuz für den Standort-Knopf: Ring, vier Striche, Punkt in der Mitte. */
class CrosshairDrawable(private val color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = bounds; val w = b.width().toFloat(); val cx = b.exactCenterX(); val cy = b.exactCenterY()
        val r = w * 0.29f; val sw = (w * 0.085f).coerceAtLeast(1.5f)
        p.color = color; p.strokeWidth = sw; p.strokeCap = Paint.Cap.ROUND
        p.style = Paint.Style.STROKE
        c.drawCircle(cx, cy, r, p)
        val a = w * 0.5f; val i = r * 0.45f
        c.drawLine(cx, cy - a, cx, cy - i, p); c.drawLine(cx, cy + i, cx, cy + a, p)
        c.drawLine(cx - a, cy, cx - i, cy, p); c.drawLine(cx + i, cy, cx + a, cy, p)
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, sw * 0.9f, p)
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(f: ColorFilter?) { p.colorFilter = f }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Text-Knopf mit grünem Leuchtpunkt in der oberen rechten Ecke (an, solange [lit] gilt). */
class LedTextView(ctx: android.content.Context) : android.widget.TextView(ctx) {
    var lit = false
        set(v) { if (field != v) { field = v; invalidate() } }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (!lit) return
        val d = resources.displayMetrics.density
        val cx = width - 9f * d; val cy = 9f * d
        p.style = Paint.Style.FILL; p.color = 0x4D2ECC40
        c.drawCircle(cx, cy, 8f * d, p)
        p.color = 0xFF2ECC40.toInt()
        c.drawCircle(cx, cy, 5f * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * d; p.color = 0xFFBEFFC8.toInt()
        c.drawCircle(cx, cy, 5f * d, p)
    }
}
