package io.github.th3s1nc.osmandhudbridge.nav

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import io.github.th3s1nc.osmandhudbridge.AppPresence
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.AutomationLogic
import io.github.th3s1nc.osmandhudbridge.pilot.PilotNav
import io.github.th3s1nc.osmandhudbridge.track.TrackRecorder

/**
 * Automation (ab App 0.42.9): Moket mit Navigation/Aufnahme starten und stoppen, Spotify mit der App starten (optional Play),
 * danach HUDPilot wieder nach vorn. Jeder Schritt steht im Protokoll. Die Entscheidungen stehen in [AutomationLogic].
 */
object Automation {
    const val PREFS = "automation"
    const val KEY_BT = "bt_on"
    const val KEY_BT_ADDR = "bt_addr"
    const val KEY_BT_NAME = "bt_name"
    const val KEY_MOKET = "moket_on"
    const val KEY_MOKET_NAV = "moket_nav"
    const val KEY_MOKET_REC = "moket_rec"
    const val KEY_SPOTIFY = "spotify_on"
    const val KEY_PLAY = "spotify_play"
    const val KEY_START_DELAY = "start_delay"
    const val KEY_PLAY_DELAY = "play_delay"
    const val KEY_RETURN = "return_on"
    const val KEY_RETURN_S = "return_s"
    private const val KEY_LAST_RUN = "last_run"

    const val MOKET_PKG = "app.moket"
    private const val MOKET_START = "moket://ride/start"
    private const val MOKET_STOP = "moket://ride/stop"
    const val SPOTIFY_PKG = "com.spotify.music"
    private const val CHANNEL = "automation"

    private val main = Handler(Looper.getMainLooper())
    private const val KEY_MOKET_RUNNING = "moket_running"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cfg(ctx: Context): AutomationLogic.Cfg {
        val p = prefs(ctx)
        return AutomationLogic.Cfg(
            moket = p.getBoolean(KEY_MOKET, false), moketNav = p.getBoolean(KEY_MOKET_NAV, true), moketRec = p.getBoolean(KEY_MOKET_REC, true),
            spotify = p.getBoolean(KEY_SPOTIFY, false), spotifyPlay = p.getBoolean(KEY_PLAY, false),
            playDelayS = p.getInt(KEY_PLAY_DELAY, AutomationLogic.PLAY_DELAY_DEFAULT),
            startDelayS = p.getInt(KEY_START_DELAY, AutomationLogic.START_DELAY_DEFAULT),
            ret = p.getBoolean(KEY_RETURN, false), retS = p.getInt(KEY_RETURN_S, AutomationLogic.RETURN_DEFAULT)
        )
    }

    private fun log(de: String, en: String) {
        try { BridgeBus.log(Tx.t(de, en)) } catch (_: Exception) { }
    }

    private fun installed(ctx: Context, pkg: String): Boolean =
        try { ctx.packageManager.getLaunchIntentForPackage(pkg) != null } catch (_: Exception) { false }

    // ---------- Ereignisse der App ----------

    /** Die App wurde geöffnet (kein Bildschirm war vorher offen). Höchstens alle 2 Minuten. */
    fun onAppOpened(ctx: Context) {
        val app = ctx.applicationContext
        val c = cfg(app)
        if (!c.spotify) return
        val p = prefs(app)
        val now = System.currentTimeMillis()
        if (!AutomationLogic.mayRun(now, p.getLong(KEY_LAST_RUN, 0L))) {
            log("Automation: Autostart übersprungen (lief vor weniger als 2 Minuten)", "Automation: autostart skipped (ran less than 2 minutes ago)")
            return
        }
        p.edit().putLong(KEY_LAST_RUN, now).apply()
        run(app, AutomationLogic.planOpen(c, moketLaunched = false))
    }

    fun onNavStarted(ctx: Context) = moketEvent(ctx, starting = true, fromNav = true)
    fun onNavStopped(ctx: Context) = moketEvent(ctx, starting = false, fromNav = true)
    fun onRecordStarted(ctx: Context) = moketEvent(ctx, starting = true, fromNav = false)
    fun onRecordStopped(ctx: Context) = moketEvent(ctx, starting = false, fromNav = false)

    private fun moketEvent(ctx: Context, starting: Boolean, fromNav: Boolean) {
        val app = ctx.applicationContext
        try {
            val c = cfg(app)
            // beim Beenden zählt die Quelle, die gerade endet, nicht mehr als aktiv
            val nav = PilotNav.guiding && !(fromNav && !starting)
            val rec = TrackRecorder.active && !(!fromNav && !starting)
            val running = prefs(app).getBoolean(KEY_MOKET_RUNNING, false)
            val act = AutomationLogic.moketAction(starting, fromNav, c, nav, rec, running) ?: return
            val ok = launchMoket(app, act)
            if (ok) {
                prefs(app).edit().putBoolean(KEY_MOKET_RUNNING, act == "start").apply()
                if (act == "start") run(app, AutomationLogic.planReturn(c))
            }
        } catch (e: Exception) {
            log("Automation: Moket-Fehler (${e.message ?: e.javaClass.simpleName})", "Automation: Moket error (${e.message ?: e.javaClass.simpleName})")
        }
    }

    // ---------- Ausführung ----------

    private fun run(ctx: Context, steps: List<AutomationLogic.Step>) {
        for (s in steps) main.postDelayed({
            try {
                when (s.kind) {
                    AutomationLogic.Kind.SPOTIFY_OPEN -> openSpotify(ctx)
                    AutomationLogic.Kind.SPOTIFY_PLAY -> playSpotify(ctx, 1)
                    AutomationLogic.Kind.RETURN -> bringBack(ctx)
                }
            } catch (e: Exception) {
                log("Automation: Schritt ${s.kind} gescheitert (${e.message ?: e.javaClass.simpleName})", "Automation: step ${s.kind} failed (${e.message ?: e.javaClass.simpleName})")
            }
        }, s.atMs)
    }

    private fun launchMoket(ctx: Context, action: String): Boolean {
        if (!installed(ctx, MOKET_PKG)) {
            log("Automation: Moket ist nicht installiert", "Automation: Moket is not installed")
            return false
        }
        val link = if (action == "start") MOKET_START else MOKET_STOP
        return try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(link)).setPackage(MOKET_PKG).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            log("Automation: Moket-Link gesendet ($link)", "Automation: Moket link sent ($link)")
            true
        } catch (e: Exception) {
            log("Automation: Moket nicht erreichbar (${e.message ?: e.javaClass.simpleName})", "Automation: Moket not reachable (${e.message ?: e.javaClass.simpleName})")
            false
        }
    }

    private fun openSpotify(ctx: Context) {
        if (!installed(ctx, SPOTIFY_PKG)) {
            log("Automation: Spotify ist nicht installiert", "Automation: Spotify is not installed")
            return
        }
        val i = ctx.packageManager.getLaunchIntentForPackage(SPOTIFY_PKG) ?: return
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        log("Automation: Spotify geöffnet", "Automation: Spotify opened")
    }

    /** Play an Spotify: über dessen Mediensitzung (Benachrichtigungszugriff), sonst als Medientaste direkt an Spotify. Bis zu 3 Versuche. */
    private fun playSpotify(ctx: Context, attempt: Int) {
        var done = false
        try {
            val msm = ctx.getSystemService(MediaSessionManager::class.java)
            val list = msm?.getActiveSessions(ComponentName(ctx, OsmAndNotificationListener::class.java)).orEmpty()
            val c = list.firstOrNull { it.packageName == SPOTIFY_PKG }
            if (c != null) {
                c.transportControls.play()
                log("Automation: Play an Spotify gesendet (Versuch $attempt)", "Automation: play sent to Spotify (attempt $attempt)")
                done = true
            }
        } catch (e: SecurityException) {
            log("Automation: Play nicht möglich, Benachrichtigungszugriff fehlt", "Automation: play not possible, notification access missing")
        } catch (e: Exception) {
            log("Automation: Play-Fehler (${e.message ?: e.javaClass.simpleName})", "Automation: play error (${e.message ?: e.javaClass.simpleName})")
        }
        if (done) return
        if (attempt < 3) {
            log("Automation: Spotify hat noch keine Mediensitzung (Versuch $attempt), neuer Versuch in 2 s", "Automation: Spotify has no media session yet (attempt $attempt), retrying in 2 s")
            main.postDelayed({ try { playSpotify(ctx, attempt + 1) } catch (_: Exception) { } }, 2000L)
            return
        }
        try {
            for (down in listOf(true, false)) {
                val ev = KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                ctx.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(SPOTIFY_PKG).putExtra(Intent.EXTRA_KEY_EVENT, ev))
            }
            log("Automation: keine Sitzung gefunden, Medientaste Play direkt an Spotify gesendet", "Automation: no session found, sent media key Play straight to Spotify")
        } catch (e: Exception) {
            log("Automation: Play gescheitert (${e.message ?: e.javaClass.simpleName})", "Automation: play failed (${e.message ?: e.javaClass.simpleName})")
        }
    }

    /** HUDPilot wieder nach vorn. Verweigert Android das (Hintergrundstart), gibt es eine Benachrichtigung zum Antippen. */
    private fun bringBack(ctx: Context) {
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try { ctx.startActivity(open) } catch (_: Exception) { }
        main.postDelayed({
            if (AppPresence.visible) {
                log("Automation: HUDPilot wieder vorn (Auto-Return)", "Automation: HUDPilot back in front (auto-return)")
            } else {
                log("Automation: Vordergrund von Android verweigert, Benachrichtigung zum Antippen gesendet", "Automation: foreground refused by Android, tap notification sent")
                notifyOpen(ctx, open)
            }
        }, 1500L)
    }

    private fun notifyOpen(ctx: Context, open: Intent) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Automation", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(ctx, 9201, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentTitle("HUDPilot")
                .setContentText(Tx.t("Zum Zurückkehren antippen", "Tap to return"))
                .setAutoCancel(true).setContentIntent(pi).build()
            nm.notify(9201, n)
        } catch (_: Exception) { }
    }
}
