package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import java.io.File
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.util.Locale

/**
 * Sprachausgabe der Navigation über die Android-Sprachausgabe (TextToSpeech). Läuft wie eine Navigationsansage:
 * Musik wird kurz leiser, der Ton geht dorthin, wo das Handy Audio ausgibt (zum Beispiel Bluetooth zur Intercom).
 * Einstellungen (an/aus, Stimme, Lautstärke, Tempo) liegen in den Einstellungen "pilot_voice".
 */
object PilotVoice {
    private const val PREFS = "pilot_voice"
    private var tts: TextToSpeech? = null
    private var appCtx: Context? = null
    @Volatile private var ready = false
    private var pending: String? = null
    private var focus: AudioFocusRequest? = null
    private var counter = 0
    private var loggedVoice: String? = null
    private var lastText = ""
    private var lastTextMs = 0L

    /** true, wenn für die Sprache keine installierte Stimme gefunden wurde (Hinweis in der App). */
    @Volatile var missing = false
        private set

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = prefs(ctx).getBoolean("on", true)
    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("on", on).apply() }

    /** Lautstärke der Stimme, 0.1 bis 1.0 (im Verhältnis zur Navigations-Lautstärke des Handys). */
    fun volume(ctx: Context): Float = prefs(ctx).getFloat("volume", 1f).coerceIn(0.1f, 1f)
    fun setVolume(ctx: Context, v: Float) { prefs(ctx).edit().putFloat("volume", v.coerceIn(0.1f, 1f)).apply() }

    /** Verstärkung über die Handy-Lautstärke hinaus: 0 aus, 1 laut (+6 dB), 2 sehr laut (+12 dB). */
    fun boost(ctx: Context): Int = prefs(ctx).getInt("boost", 0).coerceIn(0, 2)
    fun setBoost(ctx: Context, b: Int) { prefs(ctx).edit().putInt("boost", b.coerceIn(0, 2)).apply() }

    private var player: MediaPlayer? = null
    private var enhancer: LoudnessEnhancer? = null

    private fun stopPlayer() {
        try { enhancer?.release() } catch (_: Exception) {}
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        enhancer = null; player = null
    }

    /** Sprechtempo, 0.6 bis 1.5. */
    fun rate(ctx: Context): Float = prefs(ctx).getFloat("rate", 1f).coerceIn(0.6f, 1.5f)
    fun setRate(ctx: Context, r: Float) {
        prefs(ctx).edit().putFloat("rate", r.coerceIn(0.6f, 1.5f)).apply()
        tts?.setSpeechRate(rate(ctx))
    }

    /** Ansage-Häufigkeit: false = normal, true = viele (frühe Vorankündigung, "halten", "danach"). */
    fun many(ctx: Context): Boolean = prefs(ctx).getBoolean("many", false)
    fun many(): Boolean = appCtx?.let { many(it) } ?: false
    fun setMany(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("many", on).apply() }

    fun voiceName(ctx: Context): String? = prefs(ctx).getString("voice", null)
    fun setVoiceName(ctx: Context, name: String?) {
        prefs(ctx).edit().putString("voice", name).apply()
        configure()
    }

    /** Sprachausgabe starten (einmal), damit die erste Ansage nicht auf den Start warten muss. */
    fun ensure(ctx: Context) {
        if (tts != null) return
        appCtx = ctx.applicationContext
        tts = TextToSpeech(appCtx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                configure()
                pending?.let { p -> pending = null; speak(p, force = false) }
            } else {
                missing = true
            }
        }
    }

    private fun locale(): Locale = if (Tx.en) Locale.ENGLISH else Locale.GERMAN

    private fun configure() {
        val t = tts ?: return
        val c = appCtx ?: return
        try {
            val r = t.setLanguage(locale())
            missing = r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED
            t.setAudioAttributes(attrs())
            t.setSpeechRate(rate(c))
            // Immer eine feste Stimme: die gewählte, sonst die beste installierte. Ohne das wechselt die Sprachausgabe
            // je nach Netz und Tagesform zwischen Stimmen.
            val want = voiceName(c)
            val list = voices()
            val pick = (if (want != null) list.firstOrNull { it.name == want } else null) ?: bestVoice(list)
            if (pick != null) {
                t.voice = pick
                if (pick.name != loggedVoice) { loggedVoice = pick.name; BridgeBus.log(Tx.t("Sprachausgabe: Stimme ", "Speech: voice ") + pick.name) }
            }
        } catch (_: Exception) {
        }
    }

    /** Beste feste Stimme: passendes Land (de-DE bzw. en-GB/en-US), höchste Qualität, dann nach Name (immer dieselbe). */
    private fun bestVoice(list: List<Voice>): Voice? {
        val countries = if (Tx.en) listOf("GB", "US") else listOf("DE")
        return list.sortedWith(
            compareBy<Voice>({ if (it.locale.country in countries) 0 else 1 }, { -it.quality }, { it.name })
        ).firstOrNull()
    }

    private fun attrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Installierte Stimmen der aktuellen Sprache, die ohne Netz funktionieren. */
    fun voices(): List<Voice> = try {
        val lang = locale().language
        (tts?.voices ?: emptySet<Voice>())
            .filter { it.locale.language == lang && !it.isNetworkConnectionRequired && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
            .sortedBy { it.name }
    } catch (_: Exception) {
        emptyList()
    }

    /** Ein Satz der Navigation; stumm, wenn die Ansagen ausgeschaltet sind. */
    fun say(text: String) {
        val c = appCtx ?: return
        if (!enabled(c)) return
        // Derselbe Satz nicht nochmal innerhalb von 10 Sekunden (zusätzliche Sicherung gegen Dauerfeuer)
        val now = android.os.SystemClock.elapsedRealtime()
        if (text == lastText && now - lastTextMs < 10_000L) return
        lastText = text; lastTextMs = now
        speak(text, force = false)
    }

    /** Probeansage, auch wenn die Ansagen ausgeschaltet sind. */
    fun test(ctx: Context) {
        ensure(ctx)
        speak(Tx.t("In 300 Metern links abbiegen.", "In 300 meters turn left."), force = true)
    }

    private fun speak(text: String, force: Boolean) {
        val t = tts
        val c = appCtx
        if (t == null || c == null) return
        if (!ready) { pending = text; return }
        try {
            val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs()).build()
            focus?.let { am.abandonAudioFocusRequest(it) }
            focus = req
            am.requestAudioFocus(req)
            val id = "nav" + (++counter)
            stopPlayer()
            val boost = boost(c)
            if (boost > 0) {
                // Verstärkt: erst in eine Datei sprechen, dann mit Lautstärke-Anhebung abspielen. Klappt etwas nicht, normal sprechen.
                val file = File(c.cacheDir, "tts_$id.wav")
                var fell = false
                fun plain() {
                    if (fell) return
                    fell = true
                    try { file.delete() } catch (_: Exception) {}
                    val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume(c)) }
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { am.abandonAudioFocusRequest(req) }
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun onError(utteranceId: String?) { am.abandonAudioFocusRequest(req) }
                    })
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, params, id + "p")
                }
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != id) return
                        try {
                            val mp = MediaPlayer()
                            mp.setAudioAttributes(attrs())
                            mp.setDataSource(file.path)
                            val v = volume(c)
                            mp.setVolume(v, v)
                            mp.prepare()
                            val le = LoudnessEnhancer(mp.audioSessionId)
                            le.setTargetGain(if (boost == 1) 600 else 1200)
                            le.enabled = true
                            mp.setOnCompletionListener {
                                try { file.delete() } catch (_: Exception) {}
                                if (player === mp) stopPlayer()
                                am.abandonAudioFocusRequest(req)
                            }
                            mp.setOnErrorListener { _, _, _ -> stopPlayer(); plain(); true }
                            stopPlayer()
                            player = mp; enhancer = le
                            mp.start()
                        } catch (_: Exception) { stopPlayer(); plain() }
                    }
                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onError(utteranceId: String?) { if (utteranceId == id) plain() }
                })
                if (t.synthesizeToFile(text, Bundle(), file, id) != TextToSpeech.SUCCESS) plain()
                return
            }
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if (utteranceId == id) am.abandonAudioFocusRequest(req) }
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) { am.abandonAudioFocusRequest(req) }
            })
            val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume(c)) }
            t.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        } catch (_: Exception) {
        }
    }

    /** Laufende Ansage abbrechen (Navigation beendet). */
    fun silence() { try { tts?.stop() } catch (_: Exception) {}; stopPlayer() }

    fun shutdown() {
        try { tts?.shutdown() } catch (_: Exception) {}
        tts = null; ready = false
    }
}
