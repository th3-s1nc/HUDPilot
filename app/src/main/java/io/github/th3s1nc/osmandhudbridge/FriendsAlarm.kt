package io.github.th3s1nc.osmandhudbridge

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import io.github.th3s1nc.osmandhudbridge.pilot.AdminNotes
import io.github.th3s1nc.osmandhudbridge.pilot.AdminNotesLogic
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsApi
import io.github.th3s1nc.osmandhudbridge.pilot.FriendsLogic
import io.github.th3s1nc.osmandhudbridge.pilot.ServerProfile

/**
 * Prüft etwa alle 15 Minuten im Hintergrund, ob eine neue Freundschaftsanfrage da ist, und meldet sie als Benachrichtigung.
 * (Android erlaubt normalen Apps keinen kürzeren Abstand. Bei geöffneter App kommt die Anfrage schneller.)
 */
class FriendsAlarm : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val pending = goAsync()
        Thread {
            try { check(app) } catch (_: Exception) { } finally { pending.finish() }
        }.start()
    }

    companion object {
        private const val CHANNEL = "friends"
        private const val NOTIF_ID = 52

        private fun pi(ctx: Context): PendingIntent =
            PendingIntent.getBroadcast(ctx, 52, Intent(ctx, FriendsAlarm::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        /** Wiederholung einrichten (mehrfaches Aufrufen ist harmlos) oder beenden, wenn kein Profil da ist. */
        fun schedule(ctx: Context) {
            val app = ctx.applicationContext
            val am = app.getSystemService(AlarmManager::class.java) ?: return
            if (!ServerProfile.has(app)) { am.cancel(pi(app)); return }
            am.setInexactRepeating(
                AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pi(app)
            )
        }

        private fun seenPrefs(ctx: Context) = ctx.getSharedPreferences("pilot_server", Context.MODE_PRIVATE)

        /** Anfragen merken, die der Nutzer schon gesehen hat (in der App oder als Benachrichtigung). */
        fun seen(ctx: Context): Set<String> = seenPrefs(ctx).getStringSet("fr_seen", emptySet()) ?: emptySet()

        fun markSeen(ctx: Context, names: Set<String>) { seenPrefs(ctx).edit().putStringSet("fr_seen", names).apply() }

        fun check(ctx: Context) {
            if (!ServerProfile.has(ctx)) return
            try { val adm = AdminNotes.checkAndAnnounce(ctx); if (adm.isNotEmpty()) notifyAdmin(ctx, adm) } catch (_: Exception) { }
            val l = FriendsApi.list(ctx) ?: return
            val seen = seenPrefs(ctx).getStringSet("fr_seen", emptySet()) ?: emptySet()
            val fresh = FriendsLogic.newIncoming(seen, l.friends)
            markSeen(ctx, FriendsLogic.incomingNames(l.friends))
            if (fresh.isEmpty()) return
            notify(ctx, fresh)
        }

        private fun notify(ctx: Context, names: List<String>) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, Tx.t("Freunde", "Friends"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                ctx, 53, Intent(ctx, PilotMapLibreActivity::class.java).putExtra("open_friends", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val text = if (names.size == 1) Tx.t("${names[0]} möchte dein Freund sein.", "${names[0]} wants to be your friend.")
            else Tx.t("${names.size} neue Freundschaftsanfragen: ", "${names.size} new friend requests: ") + names.joinToString(", ")
            val n = android.app.Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("HUDPilot · " + Tx.t("Freunde", "Friends"))
                .setContentText(text).setContentIntent(open).setAutoCancel(true).build()
            try { nm.notify(NOTIF_ID, n) } catch (_: SecurityException) { }
        }

        private const val CHANNEL_ADMIN = "admin"
        private const val NOTIF_ADMIN = 54

        /** Benachrichtigung für neue Admin-Meldungen; ein Tipp öffnet die Liste im Status-Tab. */
        fun notifyAdmin(ctx: Context, fresh: List<AdminNotesLogic.Ev>) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ADMIN) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL_ADMIN, Tx.t("Admin-Meldungen", "Admin messages"), NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(
                ctx, 55, Intent(ctx, MainActivity::class.java).putExtra("open_admin", true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = android.app.Notification.Builder(ctx, CHANNEL_ADMIN)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("HUDPilot · " + Tx.t("Admin-Meldungen", "Admin messages"))
                .setContentText(AdminNotesLogic.notifyText(fresh, Tx.en)).setContentIntent(open).setAutoCancel(true).build()
            try { nm.notify(NOTIF_ADMIN, n) } catch (_: SecurityException) { }
        }
    }
}
