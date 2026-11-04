package io.github.th3s1nc.osmandhudbridge

import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.track.RideFormat
import io.github.th3s1nc.osmandhudbridge.track.RideStats
import io.github.th3s1nc.osmandhudbridge.track.RideStore
import io.github.th3s1nc.osmandhudbridge.track.TrackPoint
import io.github.th3s1nc.osmandhudbridge.track.TrackSession

/** Fenster am Ende einer Fahrt (Karte): Speichern oder Verwerfen, bei freier Fahrt vor der Navigation auch "getrennt speichern". */
object RideDialogs {
    private fun dp(a: AppCompatActivity, v: Int) = (v * a.resources.displayMetrics.density).toInt()

    fun statsText(st: RideStats): String {
        val sb = StringBuilder()
        sb.append(Tx.t("Strecke: ", "Distance: ")).append(RideFormat.distance(st.distanceM))
        sb.append(Tx.t("\nIn Bewegung: ", "\nMoving: ")).append(RideFormat.duration(st.movingMs))
        sb.append(Tx.t("\nGesamtzeit: ", "\nTotal time: ")).append(RideFormat.duration(st.totalMs))
        sb.append(Tx.t("\nØ Tempo: ", "\nAvg speed: ")).append(st.avgKmh).append(" km/h")
        sb.append(Tx.t("\nHöchstes Tempo: ", "\nTop speed: ")).append(st.maxKmh).append(" km/h")
        if (st.hasEle) sb.append(Tx.t("\nAufstieg: ", "\nAscent: ")).append(st.ascentM).append(Tx.t(" m, Abstieg: ", " m, descent: ")).append(st.descentM).append(" m")
        if (st.hasLimit) sb.append(Tx.t("\nTempolimit überschritten: ", "\nSpeed limit exceeded: ")).append(st.overCount).append(Tx.t(" Mal", " times"))
        return sb.toString()
    }

    private fun statsOf(p: List<TrackPoint>): RideStats = TrackSession().also { it.restore(p) }.stats()

    /**
     * [points] = die ganze Fahrt, [navStartMs] = Zeitpunkt, ab dem navigiert wurde (0 = unbekannt). [onClosed] kommt, wenn das Fenster
     * ganz fertig ist (gespeichert oder verworfen).
     */
    fun showEnd(act: AppCompatActivity, points: List<TrackPoint>, navStartMs: Long, onClosed: () -> Unit) {
        val st = statsOf(points)
        val free = if (navStartMs > 0) points.filter { it.timeMs < navStartMs } else emptyList()
        val nav = if (navStartMs > 0) points.filter { it.timeMs >= navStartMs } else emptyList()
        val canSplit = free.size >= 2 && nav.size >= 2 && statsOf(free).distanceM >= 300
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(act, 24), dp(act, 8), dp(act, 24), 0) }
        box.addView(TextView(act).apply { text = statsText(st) })
        val input = EditText(act).apply {
            setSingleLine()
            setText(RideStore.defaultName(st.startMs))
            hint = Tx.t("Name der Fahrt", "Name of the ride")
        }
        box.addView(input)
        var split: CheckBox? = null
        if (canSplit) {
            split = CheckBox(act).apply {
                text = Tx.t("Getrennt speichern: freie Fahrt und Navigation als zwei Fahrten", "Save separately: free ride and navigation as two rides")
                isChecked = false
            }
            box.addView(split)
        }
        AlertDialog.Builder(act)
            .setTitle(Tx.t("Fahrt beendet", "Ride finished"))
            .setView(box)
            .setCancelable(false)
            .setPositiveButton(Tx.t("Speichern", "Save"), null)
            .setNegativeButton(Tx.t("Verwerfen", "Discard")) { _, _ -> confirmDiscard(act, points, navStartMs, onClosed) }
            .create().also { d ->
                d.setOnShowListener {
                    d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                        val name = input.text.toString().trim().ifEmpty { RideStore.defaultName(st.startMs) }
                        val ok = if (split?.isChecked == true) {
                            val a = RideStore.save(act, name + Tx.t(" (frei)", " (free)"), free, false, RideStore.KIND_FREE)
                            val b = RideStore.save(act, name + " (Navigation)", nav, true, RideStore.KIND_NAV)
                            a != null && b != null
                        } else RideStore.save(act, name, points, true, if (navStartMs > 0) RideStore.KIND_NAV else RideStore.KIND_FREE) != null
                        if (!ok) {
                            Toast.makeText(act, Tx.t("Zu kurz zum Speichern", "Too short to save"), Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(act, Tx.t("Gespeichert (siehe Einstellungen → Meine Fahrten)", "Saved (see settings → My rides)"), Toast.LENGTH_LONG).show()
                            d.dismiss()
                            onClosed()
                        }
                    }
                }
                d.show()
            }
    }

    private fun confirmDiscard(act: AppCompatActivity, points: List<TrackPoint>, navStartMs: Long, onClosed: () -> Unit) {
        AlertDialog.Builder(act)
            .setTitle(Tx.t("Fahrt wirklich verwerfen?", "Really discard the ride?"))
            .setMessage(Tx.t("Die Aufzeichnung ist danach unwiderruflich weg.", "The recording will be gone for good."))
            .setCancelable(false)
            .setPositiveButton(Tx.t("Ja, verwerfen", "Yes, discard")) { _, _ -> RideStore.discardUnfinished(act); onClosed() }
            .setNegativeButton(Tx.t("Nein", "No")) { _, _ -> showEnd(act, points, navStartMs, onClosed) }
            .show()
    }
}
