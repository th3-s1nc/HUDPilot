package io.github.th3s1nc.osmandhudbridge.track

import android.content.Context

/** Merkt sich, ob die Schräglage als "Angle" (Grad) oder "Laterals" (g) angezeigt wird. Gilt für alle Fahrten, nur Anzeige. */
object LeanPref {
    private const val FILE = "lean_view"
    private const val KEY = "lateral"
    fun lateral(ctx: Context): Boolean = try { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY, false) } catch (_: Exception) { false }
    fun set(ctx: Context, lateral: Boolean) { try { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY, lateral).apply() } catch (_: Exception) { } }
}
