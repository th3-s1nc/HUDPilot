package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.os.SystemClock
import java.util.concurrent.Executors

/**
 * Eigenen Standort mit Freunden teilen. Der Schalter gilt dauerhaft, gesendet wird aber nur, solange die App Positionen bekommt
 * (Karte offen oder HUD-Dienst läuft). Mehrere Stellen dürfen [onFix] aufrufen: eine gemeinsame Drossel sorgt für die richtigen Abstände.
 */
object LiveShare {
    private val exec = Executors.newSingleThreadExecutor()
    private val throttle = FriendsLogic.Throttle()
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)

    /** Für wie viele Freunde bist du laut letzter Antwort sichtbar? */
    @Volatile var viewers = 0

    /** Zuletzt geholte Freundesliste und Positionen (für Menü, Knopf und Karte). */
    @Volatile var friends: FriendsLogic.FriendList? = null
    @Volatile var positions: List<FriendsLogic.Pos> = emptyList()

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("live_share", false)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean("live_share", on).apply()
        throttle.reset()
        if (!on) { val app = ctx.applicationContext; exec.execute { FriendsApi.stop(app) } }
    }

    /** Neue GPS-Position (aus der Karte oder dem HUD-Dienst). */
    fun onFix(ctx: Context, lat: Double, lon: Double, kmh: Float, hdg: Float?) {
        val app = ctx.applicationContext
        if (!enabled(app) || !ServerProfile.has(app) || RegionSync.serverUrl(app).isEmpty()) return
        if (!throttle.due(SystemClock.elapsedRealtime(), kmh)) return
        exec.execute { FriendsApi.live(app, lat, lon, kmh.toDouble(), (hdg ?: 0f).toDouble())?.let { viewers = it } }
    }
}
