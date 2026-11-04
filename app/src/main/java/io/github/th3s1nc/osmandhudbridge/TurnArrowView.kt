package io.github.th3s1nc.osmandhudbridge

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import io.github.th3s1nc.osmandhudbridge.pilot.BrouterCmd
import io.github.th3s1nc.osmandhudbridge.pilot.Instruction
import io.github.th3s1nc.osmandhudbridge.protocol.NavCommand
import kotlin.math.hypot

/** Gezeichneter Abbiegepfeil (dicker, geknickter Pfeil wie in Navigations-Apps) statt eines Schriftzeichens. */
class TurnArrowView(context: Context) : View(context) {
    private var cmd: NavCommand? = null
    private var brCmd = 0
    private var exit = 0
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = 0xFFFFFFFF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }

    fun set(i: Instruction?) {
        cmd = i?.command; brCmd = i?.brCmd ?: 0; exit = i?.exit ?: 0
        invalidate()
    }

    fun setColor(c: Int) { line.color = c; fill.color = c; txt.color = c; invalidate() }

    private fun left(c: NavCommand?): Boolean = when (c) {
        NavCommand.TURN_LEFT, NavCommand.KEEP_LEFT, NavCommand.SHARP_LEFT, NavCommand.RAMP_LEFT, NavCommand.UTURN_LEFT,
        NavCommand.ROUNDABOUT_LEFT, NavCommand.GOAL_LEFT -> true
        else -> false
    }

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        val ox = (width - s) / 2f
        val oy = (height - s) / 2f
        canvas.save(); canvas.translate(ox, oy)
        line.strokeWidth = s * 0.14f
        // Die Grundform ist ein Linkspfeil; für rechts wird gespiegelt (sx = -1)
        val sx = if (left(cmd)) 1f else -1f
        fun x(v: Float) = s * (if (sx < 0) 1f - v else v)
        fun y(v: Float) = s * v
        fun head(px: Float, py: Float, dx: Float, dy: Float) {
            // Pfeilspitze bei (px,py), zeigt in Richtung (dx,dy) (Bildschirm-Koordinaten, schon gespiegelt)
            val len = hypot(dx, dy).takeIf { it > 0f } ?: return
            val ux = dx / len; val uy = dy / len
            val tip = s * 0.17f; val wid = s * 0.17f
            val tx = px + ux * tip; val ty = py + uy * tip
            val bx = px - ux * s * 0.03f; val by = py - uy * s * 0.03f
            val p = Path().apply {
                moveTo(tx, ty)
                lineTo(bx - uy * wid, by + ux * wid)
                lineTo(bx + uy * wid, by - ux * wid)
                close()
            }
            canvas.drawPath(p, fill)
        }
        val path = Path()
        when (cmd) {
            NavCommand.TURN_LEFT, NavCommand.TURN_RIGHT -> {
                path.moveTo(x(0.62f), y(0.90f)); path.lineTo(x(0.62f), y(0.60f))
                path.quadTo(x(0.62f), y(0.42f), x(0.44f), y(0.42f)); path.lineTo(x(0.34f), y(0.42f))
                canvas.drawPath(path, line); head(x(0.30f), y(0.42f), -sx, 0f)
            }
            NavCommand.SHARP_LEFT, NavCommand.SHARP_RIGHT -> {
                path.moveTo(x(0.62f), y(0.12f)); path.lineTo(x(0.62f), y(0.40f))
                path.lineTo(x(0.40f), y(0.62f)); path.lineTo(x(0.34f), y(0.68f))
                canvas.drawPath(path, line); head(x(0.30f), y(0.72f), -sx * 0.7f, 0.7f)
            }
            NavCommand.KEEP_LEFT, NavCommand.KEEP_RIGHT, NavCommand.RAMP_LEFT, NavCommand.RAMP_RIGHT -> {
                // leichte Kurve: Schaft, dann schräg nach oben
                path.moveTo(x(0.55f), y(0.90f)); path.lineTo(x(0.55f), y(0.62f)); path.lineTo(x(0.36f), y(0.34f))
                canvas.drawPath(path, line); head(x(0.34f), y(0.31f), -sx * 0.55f, -0.8f)
            }
            NavCommand.UTURN, NavCommand.UTURN_LEFT, NavCommand.UTURN_RIGHT -> {
                val dirL = cmd == NavCommand.UTURN_LEFT || cmd == NavCommand.UTURN
                val u = if (dirL) 1f else -1f   // Grundform = Linkswende, rechts gespiegelt
                fun ux(v: Float) = s * (if (u < 0) 1f - v else v)
                path.moveTo(ux(0.68f), y(0.90f)); path.lineTo(ux(0.68f), y(0.38f))
                path.quadTo(ux(0.68f), y(0.14f), ux(0.50f), y(0.14f)); path.quadTo(ux(0.32f), y(0.14f), ux(0.32f), y(0.38f))
                path.lineTo(ux(0.32f), y(0.60f))
                canvas.drawPath(path, line); head(ux(0.32f), y(0.66f), 0f, 1f)
            }
            NavCommand.ROUNDABOUT, NavCommand.ROUNDABOUT_LEFT, NavCommand.ROUNDABOUT_RIGHT, NavCommand.ROUNDABOUT_FINISH -> {
                line.strokeWidth = s * 0.10f
                canvas.drawCircle(s * 0.5f, s * 0.38f, s * 0.24f, line)
                canvas.drawLine(s * 0.5f, s * 0.90f, s * 0.5f, s * 0.62f, line)
                if (exit > 0) { txt.textSize = s * 0.30f; canvas.drawText("$exit", s * 0.5f, s * 0.38f + s * 0.105f, txt) }
            }
            NavCommand.GOAL, NavCommand.GOAL_LEFT, NavCommand.GOAL_RIGHT -> {
                line.strokeWidth = s * 0.08f
                canvas.drawLine(s * 0.30f, s * 0.90f, s * 0.30f, s * 0.12f, line)
                val f = Path().apply { moveTo(s * 0.30f, s * 0.14f); lineTo(s * 0.80f, s * 0.30f); lineTo(s * 0.30f, s * 0.48f); close() }
                canvas.drawPath(f, fill)
            }
            NavCommand.VIA -> {
                val d = Path().apply { moveTo(s * 0.5f, s * 0.18f); lineTo(s * 0.80f, s * 0.5f); lineTo(s * 0.5f, s * 0.82f); lineTo(s * 0.20f, s * 0.5f); close() }
                canvas.drawPath(d, fill)
            }
            else -> {
                // geradeaus (auch Einfädeln und ohne Angabe)
                path.moveTo(s * 0.5f, s * 0.90f); path.lineTo(s * 0.5f, s * 0.30f)
                canvas.drawPath(path, line); head(s * 0.5f, s * 0.14f, 0f, -1f)
            }
        }
        canvas.restore()
    }
}
