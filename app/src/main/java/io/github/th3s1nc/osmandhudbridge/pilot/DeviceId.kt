package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.os.Build
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.security.SecureRandom

/**
 * Anonyme Gerätekennung für den eigenen Server (ab Server v1.21.4): eine zufällige Zahl, die beim ersten Start erzeugt wird,
 * plus der Gerätename (z. B. "Google Pixel 7"). Der Server zeigt damit in der Verwaltung, welches Handy gerade lädt und
 * wie viele Handys zu einem Profil gehören. Nichts Persönliches, und nur an den eigenen Server, nie an Dritte.
 */
object DeviceId {
    @Volatile private var id = ""
    @Volatile private var name = ""

    fun init(ctx: Context) {
        val p = ctx.applicationContext.getSharedPreferences("pilot_device", Context.MODE_PRIVATE)
        var v = p.getString("id", null)
        if (v == null || !v.matches(Regex("[0-9a-f]{16}"))) {
            val b = ByteArray(8).also { SecureRandom().nextBytes(it) }
            v = b.joinToString("") { "%02x".format(it) }
            p.edit().putString("id", v).apply()
        }
        id = v
        name = deviceName(Build.MANUFACTURER ?: "", Build.MODEL ?: "")
    }

    /** "Google" + "Pixel 7" → "Google Pixel 7"; "samsung" + "samsung SM-S911B" → "Samsung SM-S911B". Höchstens 40 Zeichen, nur sichtbare ASCII-Zeichen. */
    fun deviceName(maker: String, model: String): String {
        val m = model.trim(); val k = maker.trim()
        val full = if (k.isEmpty() || m.startsWith(k, ignoreCase = true)) m else "$k $m"
        val s = full.filter { it.code in 32..126 }.trim().take(40)
        return if (s.isEmpty()) "Android" else s.replaceFirstChar { it.uppercase() }
    }

    fun apply(c: HttpURLConnection) {
        if (id.isEmpty()) return
        c.setRequestProperty("X-Device", id)
        c.setRequestProperty("X-Device-Name", URLEncoder.encode(name, "UTF-8").replace("+", "%20"))
    }
}
