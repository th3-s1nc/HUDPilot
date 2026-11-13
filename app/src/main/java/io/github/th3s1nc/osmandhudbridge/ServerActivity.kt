package io.github.th3s1nc.osmandhudbridge

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.AutoSync
import io.github.th3s1nc.osmandhudbridge.pilot.Favs
import io.github.th3s1nc.osmandhudbridge.pilot.PasswordRules
import io.github.th3s1nc.osmandhudbridge.pilot.RegionLogic
import io.github.th3s1nc.osmandhudbridge.pilot.RegionSync
import io.github.th3s1nc.osmandhudbridge.pilot.ServerProfile
import io.github.th3s1nc.osmandhudbridge.pilot.SettingsBackup
import java.util.concurrent.Executors

/** Server: Adresse (zuhause / unterwegs), Profil, Abgleich und Sicherung der Einstellungen. */
class ServerActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var etUrl: EditText
    private lateinit var etAway: EditText
    private lateinit var tvConn: TextView
    private lateinit var tvStatus: TextView
    private lateinit var profBox: LinearLayout
    private lateinit var syncBox: LinearLayout

    private val GREY = Color.parseColor("#A8A8B0")
    private val ACC = Color.parseColor("#FF7A00")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun tv(t: String, size: Float = 14f, color: Int = Color.WHITE) = TextView(this).apply { text = t; textSize = size; setTextColor(color) }
    private fun btn(t: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(Color.WHITE)
        setBackgroundColor(if (primary) ACC else Color.parseColor("#33333A")); setOnClickListener { onClick() }
    }
    private fun edit(hint: String, text: String) = EditText(this).apply {
        setText(text); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); this.hint = hint
        inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI or android.text.InputType.TYPE_CLASS_TEXT; setSingleLine()
    }
    private fun head(t: String) = tv(t, 17f, ACC).apply { setTypeface(typeface, android.graphics.Typeface.BOLD); setPadding(0, dp(22), 0, dp(4)) }
    private fun toast(t: String) = runOnUiThread { if (!isFinishing && !isDestroyed) Toast.makeText(this, t, Toast.LENGTH_LONG).show() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#14141A")); fitsSystemWindows = true }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(16), dp(4)) }
        top.addView(tv("←", 24f).apply { setPadding(dp(8), dp(8), dp(16), dp(8)); setOnClickListener { finish() } })
        top.addView(tv(Tx.t("Server", "Server"), 20f).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) })
        root.addView(top)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), dp(28)) }
        root.addView(ScrollView(this).apply { addView(body) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        tvStatus = tv("", 14f, GREY)
        body.addView(tvStatus)

        body.addView(head(Tx.t("Adresse", "Address")))
        body.addView(tv(Tx.t("Zuhause (Heim-WLAN), z. B. 192.168.1.10", "At home (home Wi-Fi), e.g. 192.168.1.10"), 13f, GREY))
        etUrl = edit("http://192.168.1.10", RegionSync.homeUrl(this)); body.addView(etUrl)
        body.addView(tv(Tx.t("Unterwegs (von außen, https), z. B. navi.beispiel.duckdns.org", "On the go (from outside, https), e.g. navi.beispiel.duckdns.org"), 13f, GREY).apply { setPadding(0, dp(10), 0, 0) })
        etAway = edit("https://navi.beispiel.duckdns.org", RegionSync.awayUrl(this)); body.addView(etAway)
        body.addView(tv(Tx.t("Die App nimmt das Heimnetz, wenn es erreichbar ist, sonst die Adresse unterwegs. Unterwegs gehen Touren, Fahrten und Einstellungen; Karten nur, wenn der Server das erlaubt.",
            "The app uses the home network when reachable, otherwise the address on the go. On the go: tours, rides and settings; maps only if the server allows it."), 12f, GREY).apply { setPadding(0, dp(4), 0, dp(6)) })
        body.addView(btn(Tx.t("Speichern und verbinden", "Save and connect"), true) { connect() }, LinearLayout.LayoutParams(-1, -2))
        tvConn = tv("", 13f, GREY).apply { setPadding(0, dp(6), 0, 0) }
        body.addView(tvConn)

        body.addView(head(Tx.t("Profil", "Profile")))
        profBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(profBox)

        body.addView(head(Tx.t("Abgleich", "Sync")))
        syncBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(syncBox)
        render()
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        tvStatus.text = ServerProfile.statusLine(this)
        profBox.removeAllViews(); syncBox.removeAllViews()
        if (ServerProfile.has(this)) {
            profBox.addView(tv(Tx.t("Angemeldet als ", "Signed in as ") + ServerProfile.name(this), 15f))
            profBox.addView(btn(Tx.t("Passwort ändern …", "Change password …"), false) { passwordDialog() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
            profBox.addView(btn(Tx.t("Anderes Profil / Abmelden …", "Other profile / sign out …"), false) { profileDialog() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        } else {
            profBox.addView(tv(Tx.t("Noch kein Profil. Mit eigenem Namen und Passwort anmelden oder neu anlegen (Einladungscode vom Server-Betreiber).", "No profile yet. Sign in with your name and password or create one (invitation code from the server owner)."), 13f, GREY))
            profBox.addView(btn(Tx.t("Anmelden / Neu anlegen …", "Sign in / create …"), true) { profileDialog() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
        }
        val cb = CheckBox(this).apply {
            text = Tx.t("Automatisch abgleichen", "Sync automatically"); setTextColor(Color.WHITE)
            isChecked = AutoSync.enabled(this@ServerActivity)
            setOnCheckedChangeListener { _, on -> AutoSync.setEnabled(this@ServerActivity, on) }
        }
        syncBox.addView(cb)
        syncBox.addView(tv(Tx.t("Neue Fahrten gehen sofort zum Server (auch über mobile Daten), Einstellungen gleichen sich beim Öffnen und nach Änderungen ab. Bei Unterschieden auf beiden Seiten fragt die App.",
            "New rides go to the server right away (also on mobile data), settings sync on opening and after changes. If both sides differ, the app asks."), 12f, GREY))
        syncBox.addView(btn(Tx.t("Jetzt abgleichen", "Sync now"), true) { syncNow() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        syncBox.addView(btn(Tx.t("Einstellungen sichern", "Back up settings"), false) { backup() }, LinearLayout.LayoutParams(-1, -2))
        syncBox.addView(btn(Tx.t("Einstellungen holen", "Restore settings"), false) { restore() }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun connect() {
        val url = RegionLogic.normalizeUrl(etUrl.text.toString()); val away = RegionLogic.normalizeAwayUrl(etAway.text.toString())
        if (url.isEmpty() && away.isEmpty()) { toast(Tx.t("Bitte eine Serveradresse eintragen.", "Please enter a server address.")); return }
        RegionSync.setServerUrl(this, url); RegionSync.setAwayUrl(this, away); etUrl.setText(url); etAway.setText(away)
        tvConn.setTextColor(GREY); tvConn.text = Tx.t("Verbinde …", "Connecting …")
        exec.execute {
            var err: String? = null; var ok = false; var blocked = false; var via = ""
            fun tryUrl(u: String, label: String) {
                try { RegionSync.fetchManifest(u); ok = true; via = label }
                catch (e: RegionSync.ExternBlocked) { blocked = true; via = label }
                catch (e: Exception) { err = e.message ?: e.javaClass.simpleName }
            }
            if (url.isNotEmpty()) tryUrl(url, Tx.t("Heimnetz", "home network"))
            if (!ok && !blocked && away.isNotEmpty()) tryUrl(away, Tx.t("unterwegs", "on the go"))
            main.post {
                if (isFinishing || isDestroyed) return@post
                when {
                    ok -> { tvConn.setTextColor(Color.parseColor("#2E9E5B")); tvConn.text = Tx.t("Verbunden", "Connected") + " ($via)." }
                    blocked -> { tvConn.setTextColor(Color.parseColor("#E0A100")); tvConn.text = Tx.t("Server erreichbar ($via). Karten gibt es hier nur im Heimnetz; Touren, Fahrten und Einstellungen gehen.", "Server reachable ($via). Maps only at home; tours, rides and settings work.") }
                    else -> { tvConn.setTextColor(Color.parseColor("#D14343")); tvConn.text = Tx.t("Server nicht erreichbar (", "Server not reachable (") + err + ")." }
                }
                render()
            }
        }
    }

    private fun profileDialog() {
        if (RegionSync.serverUrl(this).isEmpty()) { toast(Tx.t("Erst die Serveradresse eintragen und verbinden.", "Enter the server address and connect first.")); return }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        col.addView(TextView(this).apply {
            text = Tx.t("Eigener Name und Passwort. Neu hier? Name wählen und den Einladungscode vom Server-Betreiber eintragen, dann „Neu anlegen\".",
                "Your own name and password. New here? Pick a name and enter the invitation code from the server owner, then \"Create\".")
        })
        val eName = EditText(this).apply { hint = Tx.t("Name", "Name"); setSingleLine(true); setText(ServerProfile.name(this@ServerActivity)) }
        val eCode = EditText(this).apply { hint = Tx.t("Passwort (beim Anlegen: Einladungscode)", "Password (when creating: invitation code)"); setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS }
        val cShow = android.widget.CheckBox(this).apply {
            text = Tx.t("Anzeigen", "Show")
            setOnCheckedChangeListener { _, on ->
                val sel = eCode.selectionEnd
                eCode.inputType = android.text.InputType.TYPE_CLASS_TEXT or (if (on) android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                eCode.setSelection(sel.coerceIn(0, eCode.text.length))
            }
        }
        col.addView(eName); col.addView(eCode); col.addView(cShow)
        fun run(create: Boolean) {
            val n = eName.text.toString(); val c = eCode.text.toString()
            if (n.isBlank() || c.isBlank()) { toast(Tx.t("Name und Passwort eintragen.", "Enter name and password.")); return }
            exec.execute {
                val r = if (create) ServerProfile.create(this, n, c) else ServerProfile.login(this, n, c)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (!r.ok) { Toast.makeText(this, r.text, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                    if (create) AlertDialog.Builder(this)
                        .setTitle(Tx.t("Profil angelegt", "Profile created"))
                        .setMessage(Tx.t("Dein Passwort (bitte merken, zum Beispiel für den Planer am PC):\n\n", "Your password (please remember it, e.g. for the planner on the PC):\n\n") + r.code)
                        .setPositiveButton("OK", null).show()
                    else Toast.makeText(this, Tx.t("Angemeldet als ", "Signed in as ") + ServerProfile.name(this), Toast.LENGTH_SHORT).show()
                    render()
                    Favs.syncAsync(this)
                    exec.execute {
                        ServerProfile.syncNow(this)
                        // Beim ersten Abgleich nach der Anmeldung entscheidet die Karte (fragt bei Unterschieden)
                    }
                }
            }
        }
        val b = AlertDialog.Builder(this).setTitle(Tx.t("Profil auf dem Server", "Profile on the server")).setView(col)
            .setPositiveButton(Tx.t("Anmelden", "Sign in")) { _, _ -> run(false) }
            .setNeutralButton(Tx.t("Neu anlegen", "Create")) { _, _ -> run(true) }
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null)
        if (ServerProfile.has(this)) b.setNeutralButton(Tx.t("Abmelden", "Sign out")) { _, _ -> ServerProfile.clear(this); render() }
        b.show()
    }

    /** Passwort ändern: bisheriges Passwort, neues Passwort, Wiederholung. Die Anmeldung auf diesem Gerät bleibt bestehen. */
    private fun passwordDialog() {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        col.addView(TextView(this).apply {
            text = Tx.t("Mindestens ${PasswordRules.MIN} Zeichen, Groß- und Kleinschreibung zählt. Keine Umlaute, kein | und kein ;",
                "At least ${PasswordRules.MIN} characters, upper and lower case matter. No umlauts, no | and no ;")
        })
        fun field(h: String) = EditText(this).apply {
            hint = h; setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        val eOld = field(Tx.t("Bisheriges Passwort", "Current password"))
        val eNew = field(Tx.t("Neues Passwort", "New password"))
        val eRep = field(Tx.t("Neues Passwort wiederholen", "Repeat new password"))
        col.addView(eOld); col.addView(eNew); col.addView(eRep)
        val d = AlertDialog.Builder(this).setTitle(Tx.t("Passwort ändern", "Change password")).setView(col)
            .setPositiveButton(Tx.t("Ändern", "Change"), null)
            .setNegativeButton(Tx.t("Abbrechen", "Cancel"), null).create()
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val o = eOld.text.toString(); val n = eNew.text.toString()
                val err = PasswordRules.check(o, n, eRep.text.toString())
                if (err != null) { toast(err); return@setOnClickListener }
                exec.execute {
                    val r = ServerProfile.changePassword(this, o, n)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (!r.ok) { Toast.makeText(this, r.text, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                        d.dismiss(); render()
                        Toast.makeText(this, Tx.t("Passwort geändert.", "Password changed."), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        d.show()
    }

    private fun syncNow() {
        if (!ServerProfile.has(this)) { toast(Tx.t("Erst anmelden.", "Sign in first.")); return }
        exec.execute {
            val line = ServerProfile.syncNow(this)
            val o = try { AutoSync.syncSettings(this) } catch (_: Exception) { AutoSync.Outcome.None }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                when (o) {
                    is AutoSync.Outcome.Conflict -> conflict(o.remote)
                    is AutoSync.Outcome.Restored -> Toast.makeText(this, Tx.t("Einstellungen vom Server übernommen (beim nächsten Öffnen der Karte aktiv).", "Settings taken from the server (active next time the map opens)."), Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(this, line, Toast.LENGTH_SHORT).show()
                }
                render()
            }
        }
    }

    private fun conflict(r: SettingsBackup.Result) {
        AlertDialog.Builder(this)
            .setTitle(Tx.t("Einstellungen unterschiedlich", "Settings differ"))
            .setMessage(Tx.t("Auf dem Server liegen andere Einstellungen (", "The server has different settings (") + SettingsBackup.describe(r) + Tx.t(") als auf diesem Handy. Welche sollen gelten?", ") than this phone. Which should apply?"))
            .setPositiveButton(Tx.t("Vom Server", "From server")) { _, _ -> AutoSync.takeRemote(this, r); toast(Tx.t("Eingespielt.", "Applied.")) }
            .setNegativeButton(Tx.t("Von diesem Handy", "From this phone")) { _, _ -> exec.execute { AutoSync.takeLocal(this); toast(Tx.t("Gesichert.", "Saved.")) } }
            .setNeutralButton(Tx.t("Später", "Later"), null)
            .show()
    }

    private fun backup() {
        exec.execute {
            val r = SettingsBackup.upload(this)
            if (r.ok) { val f = SettingsBackup.fetch(this); AutoSync.markSynced(this, if (f.ok) f.whenSec else 0L) }
            toast(r.text)
        }
    }

    private fun restore() {
        exec.execute {
            val r = SettingsBackup.fetch(this)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (!r.ok) { Toast.makeText(this, r.text, Toast.LENGTH_LONG).show(); return@runOnUiThread }
                val data = r.data
                if (r.whenSec <= 0L || data == null) { Toast.makeText(this, Tx.t("Auf dem Server liegt noch keine Sicherung.", "No backup on the server yet."), Toast.LENGTH_LONG).show(); return@runOnUiThread }
                AlertDialog.Builder(this)
                    .setTitle(Tx.t("Einstellungen vom Server übernehmen?", "Use settings from the server?"))
                    .setMessage(Tx.t("Sicherung vom ", "Backup from ") + SettingsBackup.describe(r) + "\n\n" +
                        Tx.t("Die Einstellungen dieses Handys werden ersetzt (Serveradresse, HUD-Verbindung und Karten bleiben).", "This phone's settings are replaced (server address, HUD connection and maps stay)."))
                    .setPositiveButton(Tx.t("Übernehmen", "Use")) { _, _ -> AutoSync.takeRemote(this, r); Toast.makeText(this, Tx.t("Eingespielt.", "Applied."), Toast.LENGTH_SHORT).show() }
                    .setNegativeButton(Tx.t("Nein", "No"), null).show()
            }
        }
    }
}
