package io.github.th3s1nc.osmandhudbridge

import android.app.Activity
import android.app.Application
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.core.content.ContextCompat
import io.github.th3s1nc.osmandhudbridge.pilot.PilotNav
import io.github.th3s1nc.osmandhudbridge.track.TrackRecorder

/**
 * Wie Calimoto und Motobit: Die App tut nur etwas, solange sie offen ist oder eine Fahrt läuft
 * (Navigation oder Aufzeichnung). Offen = der Dienst startet von selbst; zu und keine Fahrt = er endet,
 * Bluetooth, GPS und die Benachrichtigung sind weg und das HUD ist frei.
 */
object AppPresence {
    /** Wartezeit nach dem Schließen, damit ein Wechsel zwischen Bildschirmen nichts beendet. */
    const val GRACE_MS = 5_000L
    @Volatile var started = 0
    @Volatile var idleSince = 0L

    val visible: Boolean get() = started > 0
    fun ride(carMode: Boolean = false): Boolean = TrackRecorder.active || PilotNav.guiding || carMode
    fun needed(carMode: Boolean = false): Boolean = visible || ride(carMode)
}

/** Setzt beim Start der App die gewählte Sprache und merkt sich, ob ein Bildschirm der App offen ist. */
class HudApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLang.apply(this)
        io.github.th3s1nc.osmandhudbridge.pilot.DeviceId.init(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                AppPresence.started++
                AppPresence.idleSince = 0L
                if (AppPresence.started == 1) openedApp()
            }
            override fun onActivityStopped(a: Activity) { if (AppPresence.started > 0) AppPresence.started-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }

    /** Die App wurde geöffnet: Dienst starten, wenn alles Nötige erlaubt ist (sonst übernimmt die Übersicht mit ihren Hinweisen). */
    private fun openedApp() {
        try {
            BridgeService.userStopped = false
            if (BridgeService.running) return
            val sp = getSharedPreferences(BridgeService.PREFS, MODE_PRIVATE)
            if (BridgeService.seasonState(sp) != io.github.th3s1nc.osmandhudbridge.limit.SeasonPlan.State.IN_SEASON) return
            val hudOn = BridgeService.hudWanted(sp)
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
            if (hudOn && android.os.Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
            BridgeService.send(this)
        } catch (_: Exception) { }
    }
}
