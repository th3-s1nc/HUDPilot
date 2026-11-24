package io.github.th3s1nc.osmandhudbridge.nav

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx

/**
 * Meldet Android, wenn das gewählte Bluetooth-Gerät (z. B. das Sena) in Reichweite verbindet. Dann öffnet sich HUDPilot.
 * Das HUD hat damit nichts zu tun, das verbindet der Nutzer selbst. Läuft nur, wenn der Schalter "Autostart bei Verbindung" an ist.
 */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
class AutoStartService : CompanionDeviceService() {
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onDeviceAppeared(address: String) {
        if (Build.VERSION.SDK_INT >= 33) return   // dort kommt der neuere Aufruf
        appeared(this, address)
    }

    @androidx.annotation.RequiresApi(33)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        val addr = try { associationInfo.deviceMacAddress?.toString() } catch (_: Exception) { null }
        appeared(this, addr)
    }

    companion object {
        fun appeared(ctx: Context, address: String?) {
            try {
                BridgeBus.init(ctx.applicationContext)
                val p = ctx.applicationContext.getSharedPreferences(Automation.PREFS, Context.MODE_PRIVATE)
                if (!p.getBoolean(Automation.KEY_BT, false)) return
                val want = p.getString(Automation.KEY_BT_ADDR, "") ?: ""
                if (address != null && want.isNotEmpty() && !address.equals(want, ignoreCase = true)) return
                BridgeBus.log(Tx.t("Automation: ${p.getString(Automation.KEY_BT_NAME, "Gerät")} verbunden, HUDPilot wird geöffnet", "Automation: ${p.getString(Automation.KEY_BT_NAME, "device")} connected, opening HUDPilot"))
                val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                ctx.startActivity(open)
            } catch (e: Exception) {
                try { BridgeBus.log(Tx.t("Automation: Öffnen gescheitert (${e.message ?: e.javaClass.simpleName})", "Automation: opening failed (${e.message ?: e.javaClass.simpleName})")) } catch (_: Exception) { }
            }
        }
    }
}
