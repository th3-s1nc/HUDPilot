package io.github.th3s1nc.osmandhudbridge.nav

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.BridgeService
import io.github.th3s1nc.osmandhudbridge.limit.Tx

/**
 * Liest Benachrichtigungen mit: Anrufe, WhatsApp und Moket (Hilfe, Begegnung, Chats) für das HUD.
 * Braucht die Freigabe "Benachrichtigungszugriff" in den Android-Einstellungen.
 * Der Klassenname stammt aus der Bridge und bleibt, damit die erteilte Freigabe nach dem Update erhalten bleibt.
 */
class OsmAndNotificationListener : NotificationListenerService() {


    override fun onListenerConnected() {
        connected = true
        instance = this
        BridgeBus.init(applicationContext)
        BridgeBus.log(Tx.t("Benachrichtigungszugriff aktiv", "Notification access active"))
        BridgeBus.changed()
    }

    override fun onListenerDisconnected() {
        connected = false
        instance = null
        BridgeBus.log(Tx.t("Benachrichtigungszugriff getrennt", "Notification access disconnected"))
        BridgeBus.changed()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == MOKET_PACKAGE) { handleMoket(n); return }
        if (n.packageName in WHATSAPP_PACKAGES) { handleMessage(n); return }
        if (n.notification.category == Notification.CATEGORY_CALL) { handleCall(n); return }
    }

    /** Moket: erkennt Hilfe, Begegnung und Chats (siehe [MoketParser]) und gibt sie weiter; alles andere nur ins Protokoll. */
    private fun handleMoket(n: StatusBarNotification) {
        val no = n.notification
        if (no.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val e = no.extras
        val title = e?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (e?.getCharSequence(Notification.EXTRA_TEXT) ?: e?.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString()
        val body = try { e?.get("body")?.toString() } catch (_: Exception) { null }
        val r = MoketParser.parse(title, text, no.channelId, n.tag, body)
        if (r == null) {
            if (no.channelId?.contains("moket-ride") == true || n.tag?.contains("Aggregate") == true) return
            logMoket(n)
            return
        }
        val sig = "$title|$text|${no.`when`}"
        if (lastText[n.key] == sig) return // Neuveröffentlichung derselben Meldung
        lastText[n.key] = sig
        if (lastText.size > 50) lastText.clear()
        val kind = when (r.kind) {
            MoketParser.Kind.HELP -> NoticeKind.MOKET_HELP
            MoketParser.Kind.HELP_MSG -> NoticeKind.MOKET_HELP_MSG
            MoketParser.Kind.ENCOUNTER -> NoticeKind.MOKET_ENCOUNTER
            MoketParser.Kind.CHAT_GROUP -> NoticeKind.MOKET_GROUP
            MoketParser.Kind.CHAT_PRIVATE -> NoticeKind.MOKET_PRIVATE
        }
        BridgeBus.log(Tx.t("Moket: ${r.kind} -> \"${r.hud}\"", "Moket: ${r.kind} -> \"${r.hud}\""))
        NoticeBus.listener?.invoke(Notice(kind, r.hud, key = r.key))
    }

    /**
     * Nicht erkannte Moket-Meldung: schreibt alles, was eine Moket-Benachrichtigung enthält, ins Protokoll (Titel, Text, Kanal, Kategorie,
     * Flags und alle Zusatzdaten), damit sich ein Filter für Hilfe, Begegnung und Chats festlegen lässt. Zeigt noch nichts am HUD.
     */
    private fun logMoket(n: StatusBarNotification) {
        val no = n.notification
        val parts = ArrayList<String>()
        parts += "id=${n.id}"
        parts += "tag=${n.tag}"
        parts += "channel=${no.channelId}"
        parts += "category=${no.category}"
        parts += "group=${no.group}"
        parts += "flags=${no.flags}"
        parts += "ticker=${no.tickerText}"
        no.actions?.let { a -> parts += "actions=" + a.joinToString("/") { it.title?.toString().orEmpty() } }
        no.extras?.let { e -> for (k in e.keySet().sorted()) parts += "$k=" + dumpValue(e.get(k)) }
        BridgeBus.log(Tx.t("Moket-Meldung: ", "Moket notification: ") + parts.joinToString(" | ").replace('\n', ' ').take(1500))
    }

    private fun dumpValue(v: Any?): String = when (v) {
        null -> "null"
        is android.os.Bundle -> "{" + v.keySet().sorted().joinToString(", ") { "$it=" + dumpValue(v.get(it)) } + "}"
        is Array<*> -> "[" + v.joinToString("; ") { dumpValue(it) } + "]"
        is Collection<*> -> "[" + v.joinToString("; ") { dumpValue(it) } + "]"
        else -> v.toString().take(300)
    }

    private val lastText = HashMap<String, String>()
    private var callKey: String? = null
    private var lastCallName: String? = null

    /** WhatsApp-Nachricht: nur Absender (Titel), Sammelmeldungen und Dauermeldungen werden übergangen. */
    private fun handleMessage(n: StatusBarNotification) {
        val no = n.notification
        BridgeBus.log(Tx.t("WhatsApp-Meldung (${n.packageName}), Flags ${no.flags}, Kategorie ${no.category}", "WhatsApp message (${n.packageName}), flags ${no.flags}, category ${no.category}"))
        if (no.flags and Notification.FLAG_GROUP_SUMMARY != 0) { BridgeBus.log(Tx.t("WhatsApp: Sammelmeldung übergangen", "WhatsApp: group summary skipped")); return }
        if (no.flags and Notification.FLAG_ONGOING_EVENT != 0) { BridgeBus.log(Tx.t("WhatsApp: Dauermeldung übergangen", "WhatsApp: ongoing notification skipped")); return }
        if (no.category == Notification.CATEGORY_CALL) { handleCall(n); return }
        val e = no.extras ?: return
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val name = NoticeBus.cleanSender(title)
        if (name == null) { BridgeBus.log(Tx.t("WhatsApp: Meldung ohne Titel übergangen", "WhatsApp: message without title skipped")); return }
        val sig = "$title|$text|${no.`when`}"
        if (lastText[n.key] == sig) { BridgeBus.log(Tx.t("WhatsApp: doppelte Meldung übergangen", "WhatsApp: duplicate message skipped")); return } // Neuveröffentlichung
        lastText[n.key] = sig
        if (lastText.size > 50) lastText.clear()
        BridgeBus.log(Tx.t("Nachricht von $name (${n.packageName})", "Message from $name (${n.packageName})"))
        NoticeBus.listener?.invoke(Notice(NoticeKind.MESSAGE, name))
    }

    /** Anruf (eingehend): über die Benachrichtigung der Telefon-App, deshalb ohne Telefon-/Kontaktberechtigung. */
    private fun handleCall(n: StatusBarNotification) {
        val no = n.notification
        val e = no.extras ?: return
        // EXTRA_CALL_TYPE (Android 12+): 1 eingehend, 2 laufend, 3 Prüfung
        val type = e.getInt("android.callType", 0)
        val incoming = type == 1 || (type == 0 && no.fullScreenIntent != null)
        if (!incoming) {
            if (callKey == n.key) {
                callKey = null
                NoticeBus.listener?.invoke(Notice(NoticeKind.CALL, clear = true))
            }
            return
        }
        val name = NoticeBus.cleanSender(e.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        if (callKey == n.key && name == lastCallName) return // Neuveröffentlichung derselben Anrufmeldung
        callKey = n.key
        lastCallName = name
        BridgeBus.log(Tx.t("Eingehender Anruf (${n.packageName})", "Incoming call (${n.packageName})"))
        NoticeBus.listener?.invoke(Notice(NoticeKind.CALL, name))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.key == callKey) {
            callKey = null
            NoticeBus.listener?.invoke(Notice(NoticeKind.CALL, clear = true))
        }
    }

    companion object {
        /** true, solange Android die Benachrichtigungen an diese App liefert (nach einem Update oft erst nach neuem Verbinden). */
        @Volatile var connected = false
        @Volatile var instance: OsmAndNotificationListener? = null

        private const val MOKET_PACKAGE = "app.moket"
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
    }
}
