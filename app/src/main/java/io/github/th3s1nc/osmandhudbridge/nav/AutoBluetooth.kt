package io.github.th3s1nc.osmandhudbridge.nav

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.IntentSender
import android.os.Build
import io.github.th3s1nc.osmandhudbridge.BridgeBus
import io.github.th3s1nc.osmandhudbridge.limit.Tx

/** Bluetooth-Gerät für den Autostart: gekoppelte Geräte auflisten, bei Android als "Begleitgerät" anmelden und beobachten lassen. */
object AutoBluetooth {
    class Dev(val name: String, val address: String)

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 31

    private fun log(de: String, en: String) { try { BridgeBus.log(Tx.t(de, en)) } catch (_: Exception) { } }

    /** Gekoppelte Geräte (braucht unter Android 12 die Berechtigung "Bluetooth-Verbindung"); null = keine Berechtigung. */
    @SuppressLint("MissingPermission")
    fun bonded(ctx: Context): List<Dev>? = try {
        val ad: BluetoothAdapter? = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        ad?.bondedDevices.orEmpty().map { Dev(it.name?.takeIf { n -> n.isNotBlank() } ?: it.address, it.address) }.sortedBy { it.name.lowercase() }
    } catch (_: SecurityException) { null } catch (_: Exception) { emptyList() }

    @Suppress("DEPRECATION")
    private fun associated(cdm: CompanionDeviceManager, addr: String): Boolean =
        try { cdm.associations.any { it.equals(addr, ignoreCase = true) } } catch (_: Exception) { false }

    /**
     * Meldet das Gerät bei Android an. Ist es schon angemeldet, wird nur die Beobachtung gestartet ([onReady]); sonst kommt
     * [onChooser] mit dem Fenster von Android, das der Nutzer einmal bestätigt. Nach dessen Ergebnis ruft die Oberfläche [observe].
     */
    @SuppressLint("NewApi")
    fun associate(ctx: Context, addr: String, onChooser: (IntentSender) -> Unit, onReady: () -> Unit, onFail: (String) -> Unit) {
        if (!supported) { onFail(Tx.t("Erst ab Android 12", "Android 12 or newer only")); return }
        val cdm = ctx.getSystemService(CompanionDeviceManager::class.java)
        if (cdm == null) { onFail(Tx.t("Nicht verfügbar", "Not available")); return }
        if (associated(cdm, addr)) { observe(ctx, addr); onReady(); return }
        try {
            val filter = BluetoothDeviceFilter.Builder().setAddress(addr).build()
            val req = AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(true).build()
            cdm.associate(req, object : CompanionDeviceManager.Callback() {
                @Deprecated("Deprecated in Java")
                override fun onDeviceFound(chooserLauncher: IntentSender) { onChooser(chooserLauncher) }
                override fun onFailure(error: CharSequence?) { onFail(error?.toString() ?: Tx.t("Gerät nicht gefunden", "Device not found")) }
            }, null)
        } catch (e: Exception) {
            onFail(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Android soll melden, wenn das Gerät auftaucht oder verschwindet. */
    @SuppressLint("NewApi")
    fun observe(ctx: Context, addr: String) {
        if (!supported) return
        try {
            ctx.getSystemService(CompanionDeviceManager::class.java)?.startObservingDevicePresence(addr)
            log("Automation: Bluetooth-Beobachtung gestartet ($addr)", "Automation: Bluetooth watching started ($addr)")
        } catch (e: Exception) {
            log("Automation: Beobachtung nicht möglich (${e.message ?: e.javaClass.simpleName})", "Automation: watching not possible (${e.message ?: e.javaClass.simpleName})")
        }
    }

    @SuppressLint("NewApi")
    fun stopObserving(ctx: Context, addr: String) {
        if (!supported || addr.isEmpty()) return
        try {
            ctx.getSystemService(CompanionDeviceManager::class.java)?.stopObservingDevicePresence(addr)
            log("Automation: Bluetooth-Beobachtung beendet", "Automation: Bluetooth watching stopped")
        } catch (_: Exception) { }
    }
}
