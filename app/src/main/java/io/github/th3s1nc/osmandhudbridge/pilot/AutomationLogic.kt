package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Automation (ab App 0.42.9): reine Entscheidungen ohne Android, damit sie getestet werden können.
 * Die Ausführung (Apps öffnen, Play senden, Vordergrund) steht in nav/Automation.
 */
object AutomationLogic {
    const val START_DELAY_DEFAULT = 0
    const val START_DELAY_MAX = 30
    const val PLAY_DELAY_DEFAULT = 5
    const val PLAY_DELAY_MAX = 30
    const val RETURN_DEFAULT = 5
    const val RETURN_MIN = 1
    const val RETURN_MAX = 15
    /** Nach einem Autostart ruft ein erneutes Öffnen der App (z. B. durch Auto-Return) nichts noch einmal aus. */
    const val COOLDOWN_MS = 120_000L

    fun clampStartDelay(v: Int): Int = v.coerceIn(0, START_DELAY_MAX)
    fun clampPlayDelay(v: Int): Int = v.coerceIn(0, PLAY_DELAY_MAX)
    fun clampReturn(v: Int): Int = v.coerceIn(RETURN_MIN, RETURN_MAX)

    /** Darf der Autostart beim Öffnen der App laufen? (0 = noch nie gelaufen; Uhr zurückgestellt zählt als "darf") */
    fun mayRun(nowMs: Long, lastMs: Long, cooldownMs: Long = COOLDOWN_MS): Boolean =
        lastMs <= 0L || nowMs < lastMs || nowMs - lastMs >= cooldownMs

    class Cfg(
        val moket: Boolean, val moketNav: Boolean, val moketRec: Boolean,
        val spotify: Boolean, val spotifyPlay: Boolean, val playDelayS: Int, val startDelayS: Int = 0,
        val ret: Boolean, val retS: Int
    )

    /** Was Moket tun soll: "start", "stop" oder null. [moketOn] = wir haben Moket gestartet und noch nicht gestoppt. */
    fun moketAction(starting: Boolean, fromNav: Boolean, cfg: Cfg, navActive: Boolean, recActive: Boolean, moketOn: Boolean): String? {
        if (!cfg.moket) return null
        if (fromNav && !cfg.moketNav) return null
        if (!fromNav && !cfg.moketRec) return null
        if (starting) return if (moketOn) null else "start"
        // Stopp erst, wenn keine der gewählten Quellen mehr läuft
        val stillNav = cfg.moketNav && navActive
        val stillRec = cfg.moketRec && recActive
        return if (moketOn && !stillNav && !stillRec) "stop" else null
    }

    enum class Kind { SPOTIFY_OPEN, SPOTIFY_PLAY, RETURN }
    class Step(val kind: Kind, val atMs: Long)

    /**
     * Schritte beim Öffnen der App: Spotify öffnen, danach (nach der Verzögerung) Play, und – wenn gewünscht – HUDPilot nach
     * der eingestellten Zeit wieder nach vorn. [moketLaunched] = Moket wurde im selben Zug geöffnet (zählt für Auto-Return mit).
     */
    fun planOpen(cfg: Cfg, moketLaunched: Boolean): List<Step> {
        val out = ArrayList<Step>()
        val open = if (cfg.spotify) clampStartDelay(cfg.startDelayS) * 1000L else 0L
        if (cfg.spotify) {
            out += Step(Kind.SPOTIFY_OPEN, open)
            if (cfg.spotifyPlay) out += Step(Kind.SPOTIFY_PLAY, open + clampPlayDelay(cfg.playDelayS) * 1000L)
        }
        if (cfg.ret && (cfg.spotify || moketLaunched)) out += Step(Kind.RETURN, open + clampReturn(cfg.retS) * 1000L)
        return out
    }

    /** Auto-Return allein (Navigation/Aufnahme startet Moket). */
    fun planReturn(cfg: Cfg): List<Step> = if (cfg.ret) listOf(Step(Kind.RETURN, clampReturn(cfg.retS) * 1000L)) else emptyList()

    fun startDelayText(s: Int, en: Boolean): String = (if (en) "Start delay: " else "Startverzögerung: ") + s + " s"
    fun delayText(s: Int, en: Boolean): String = (if (en) "Delay: " else "Verzögerung: ") + s + " s"
    fun returnText(s: Int, en: Boolean): String = (if (en) "Back after: " else "Zurück nach: ") + s + " s"
}
