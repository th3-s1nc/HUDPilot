package io.github.th3s1nc.osmandhudbridge

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.AdminNotes
import io.github.th3s1nc.osmandhudbridge.pilot.AdminNotesLogic
import java.util.concurrent.Executors

/**
 * Admin-Meldungen als eigene Seite (ab App 0.36.0): oben der Serverstatus als Kacheln, darunter die Meldungen als Karten.
 * Nur für Profile, die der Admin am Server dafür bestimmt hat (der Server prüft das).
 */
class AdminMessagesActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var body: LinearLayout
    /** Nummer der zuletzt gelesenen Meldung beim Öffnen: Meldungen darüber gelten als neu (oranger Rand). */
    private var seenAtOpen = 0

    private val ACC = Color.parseColor("#FF7A00")
    private val GREY = Color.parseColor("#8E96A0")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(fill: Int, stroke: Int, radiusDp: Int = 16, strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radiusDp).toFloat(); setStroke(dp(strokeDp), stroke)
    }

    private fun tv(t: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun levelColor(level: String): Int = when (level) {
        "bad" -> Color.parseColor("#FF453A")
        "warn", "run" -> Color.parseColor("#FF9F0A")
        else -> Color.parseColor("#34C759")
    }

    private val tick = object : Runnable {
        override fun run() {
            exec.execute {
                try { AdminNotes.checkAndAnnounce(this@AdminMessagesActivity) } catch (_: Exception) { }
                runOnUiThread { if (!isFinishing && !isDestroyed) render() }
            }
            main.postDelayed(this, 10_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seenAtOpen = AdminNotes.seenId(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#14141A")); fitsSystemWindows = true }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(16), dp(4)) }
        top.addView(tv("←", 24f).apply { setPadding(dp(8), dp(8), dp(16), dp(8)); setOnClickListener { finish() } })
        top.addView(tv(Tx.t("Admin-Meldungen", "Admin messages"), 20f, bold = true))
        root.addView(top)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(28)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        render()
    }

    override fun onStart() {
        super.onStart()
        main.removeCallbacks(tick); main.post(tick)
    }

    override fun onStop() {
        main.removeCallbacks(tick)
        super.onStop()
    }

    private fun label(t: String) = tv(t.uppercase(), 12f, GREY).apply { letterSpacing = 0.08f; setPadding(dp(2), dp(16), 0, dp(8)) }

    private fun tile(s: AdminNotesLogic.Status): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = round(Color.parseColor("#1B1F23"), Color.parseColor("#2A2F35"))
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv(s.label, 12f, GREY), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(View(this).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(levelColor(s.level)) } }, LinearLayout.LayoutParams(dp(10), dp(10)))
        box.addView(head)
        box.addView(tv(s.value, 17f, bold = true).apply { setPadding(0, dp(2), 0, 0) })
        if (s.info.isNotEmpty()) box.addView(tv(s.info, 12f, GREY))
        if (s.pct >= 0 && s.level == "run") box.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progress = s.pct.coerceIn(0, 100)
            progressTintList = android.content.res.ColorStateList.valueOf(ACC)
            setPadding(0, dp(8), 0, 0)
        }, LinearLayout.LayoutParams(-1, dp(14)))
        return box
    }

    private fun messageCard(e: AdminNotesLogic.Ev, fresh: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(dp(14), dp(12), dp(14), dp(12))
            background = if (fresh) round(Color.parseColor("#241A10"), ACC) else round(Color.parseColor("#1B1F23"), Color.parseColor("#2A2F35"))
        }
        val tint = when (AdminNotesLogic.level(e.typ)) {
            "ok" -> "#14301E"; "warn" -> "#3A2A18"; "bad" -> "#3A1B1B"; else -> "#1C2A3D"
        }
        row.addView(tv(AdminNotesLogic.icon(e.typ), 17f).apply {
            gravity = Gravity.CENTER; background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(tint)) }
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(12) })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(tv(AdminNotesLogic.headline(e.typ, Tx.en), 15f, bold = true))
        col.addView(tv(e.text, 14f).apply { setPadding(0, dp(2), 0, dp(2)) })
        col.addView(tv(AdminNotesLogic.ageText(e.ts, System.currentTimeMillis() / 1000, java.util.TimeZone.getDefault(), Tx.en), 12f, GREY))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        return row
    }

    private fun gap(h: Int = 10) = View(this).apply { minimumHeight = dp(h) }

    private fun render() {
        if (!AdminNotes.allowed(this)) { finish(); return }
        body.removeAllViews()
        val status = AdminNotes.status(this)
        if (status.isNotEmpty()) {
            body.addView(label(Tx.t("Server", "Server")))
            var i = 0
            while (i < status.size) {
                val a = status[i]
                if (a.level == "run") {   // laufendes Update: eine breite Kachel
                    body.addView(tile(a), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }); i++; continue
                }
                val b = status.getOrNull(i + 1)?.takeIf { it.level != "run" }
                val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                line.addView(tile(a), LinearLayout.LayoutParams(0, -1, 1f).apply { rightMargin = dp(5) })
                line.addView(if (b != null) tile(b) else View(this), LinearLayout.LayoutParams(0, -1, 1f).apply { leftMargin = dp(5) })
                body.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
                i += if (b != null) 2 else 1
            }
        }
        body.addView(label(Tx.t("Meldungen", "Messages")))
        val evs = AdminNotes.events(this).sortedByDescending { it.id }
        if (evs.isEmpty()) body.addView(tv(Tx.t("Noch keine Meldungen.", "No messages yet."), 14f, GREY).apply { setPadding(dp(2), 0, 0, 0) })
        for (e in evs) body.addView(messageCard(e, e.id > seenAtOpen), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        AdminNotes.markRead(this)   // Seite gesehen: alles gilt als gelesen (der orange Rand bleibt für diesen Besuch)
    }
}
