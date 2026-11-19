package io.github.th3s1nc.osmandhudbridge.nav

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hält den Benachrichtigungszugriff verbunden, auch wenn die App geschlossen ist und der Dienst nicht läuft.
 * Stufe 1: requestRebind. Stufe 2 (selten): den Listener-Eintrag kurz aus- und wieder einschalten, wie es der Nutzer sonst in den
 * Einstellungen von Hand tut. Jeder Schritt steht im Protokoll, damit man sieht, was geholfen hat.
 */
object ListenerKeeper {
    private const val PREFS = "listener_keeper"
    private const val KEY_TOGGLE = "last_toggle"
    private val busy = AtomicBoolean(false)

    private fun component(ctx: Context) = ComponentName(ctx, OsmAndNotificationListener::class.java)

    fun granted(ctx: Context): Boolean =
        try { NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName) } catch (_: Exception) { false }

    fun rebind(ctx: Context) {
        try { NotificationListenerService.requestRebind(component(ctx)) } catch (_: Exception) { }
    }

    private fun log(ctx: Context, de: String, en: String) {
        try { BridgeBus.init(ctx.applicationContext); BridgeBus.log(Tx.t(de, en)) } catch (_: Exception) { }
    }

    /**
     * Prüft, ob der Zugriff erlaubt, aber nicht verbunden ist, und versucht es zu beheben. Läuft in einem eigenen Thread
     * (wartet einige Sekunden); [done] wird danach aufgerufen. Gleichzeitig läuft nur ein Versuch.
     */
    fun heal(ctx: Context, why: String, toggleGapMs: Long, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        if (!ListenerKeepLogic.needsHeal(granted(app), OsmAndNotificationListener.connected)) { done?.invoke(); return }
        if (!busy.compareAndSet(false, true)) { done?.invoke(); return }
        Thread {
            try {
                log(app, "Benachrichtigungszugriff erlaubt, aber nicht verbunden ($why): neues Verbinden angefordert", "Notification access allowed but not connected ($why): asking Android to reconnect")
                rebind(app)
                Thread.sleep(ListenerKeepLogic.WAIT_BEFORE_TOGGLE_MS)
                if (OsmAndNotificationListener.connected) { log(app, "Benachrichtigungszugriff wieder verbunden (requestRebind half)", "Notification access reconnected (requestRebind helped)"); return@Thread }
                val sp = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val now = System.currentTimeMillis()
                if (!ListenerKeepLogic.mayToggle(now, sp.getLong(KEY_TOGGLE, 0L), toggleGapMs)) {
                    log(app, "Immer noch nicht verbunden; Aus-/Einschalten des Dienstes war vor kurzem schon dran", "Still not connected; the off/on trick was tried recently")
                    return@Thread
                }
                sp.edit().putLong(KEY_TOGGLE, now).apply()
                val pm = app.packageManager; val cn = component(app)
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                Thread.sleep(ListenerKeepLogic.AFTER_TOGGLE_MS)
                rebind(app)
                Thread.sleep(ListenerKeepLogic.FINAL_WAIT_MS)
                if (OsmAndNotificationListener.connected) log(app, "Benachrichtigungszugriff wieder verbunden (Dienst aus- und eingeschaltet)", "Notification access reconnected (service switched off and on)")
                else log(app, "Benachrichtigungszugriff bleibt getrennt. Bitte in den Android-Einstellungen von Hand aus- und einschalten und die App von Schlaf- und Autostart-Sperren ausnehmen", "Notification access still disconnected. Please switch it off and on in the Android settings and exclude the app from sleep and autostart limits")
            } catch (_: Exception) {
            } finally { busy.set(false); done?.invoke() }
        }.start()
    }

    private fun pi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 61, Intent(ctx, ListenerKeepAlarm::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    /** Prüfung etwa alle 15 Minuten, auch bei geschlossener App (ohne das Handy aufzuwecken; mehrfaches Aufrufen ist harmlos). */
    fun schedule(ctx: Context) {
        try {
            val app = ctx.applicationContext
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi(app))
        } catch (_: Exception) { }
    }
}

/** Wird vom Alarm gerufen: prüft den Benachrichtigungszugriff und behebt eine Trennung. */
class ListenerKeepAlarm : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pending = goAsync()
        ListenerKeeper.heal(ctx, "Hintergrund-Prüfung", ListenerKeepLogic.TOGGLE_GAP_BG_MS) { try { pending.finish() } catch (_: Exception) { } }
    }
}
