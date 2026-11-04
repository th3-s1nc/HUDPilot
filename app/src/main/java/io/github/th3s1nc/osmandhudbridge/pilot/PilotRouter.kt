package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import btools.router.PilotRouting
import io.github.th3s1nc.osmandhudbridge.limit.Tx
import java.io.File

/** Rechnet Routen mit BRouter (im Hintergrund-Thread aufrufen, kann Sekunden bis Minuten dauern). */
object PilotRouter {
    sealed class Outcome {
        class Ok(val route: PilotRoute) : Outcome()
        class Failed(val message: String, val missingTiles: List<String> = emptyList()) : Outcome()
    }

    /** Kacheln, die für diese Wegpunkte nötig, aber noch nicht geladen sind. */
    fun missingTiles(ctx: Context, wps: List<Waypoint>): List<String> =
        PilotTiles.along(wps.map { it.lat to it.lon }).filter { !PilotStore.hasTile(ctx, it) }

    fun compute(ctx: Context, wps: List<Waypoint>, profileId: String, maxMs: Long = 120_000L, startDirection: Int = -1): Outcome {
        if (wps.size < 2) return Outcome.Failed(Tx.t("Es fehlen Punkte: mindestens Start und Ziel.", "Points are missing: at least start and destination."))
        PilotStore.ensureProfiles(ctx)
        val missing = missingTiles(ctx, wps)
        if (missing.isNotEmpty()) {
            return Outcome.Failed(Tx.t("Routendaten fehlen: ${missing.joinToString(", ")}", "Route data missing: ${missing.joinToString(", ")}"), missing)
        }
        if (!File(PilotStore.profilePath(ctx, profileId)).isFile) return Outcome.Failed(Tx.t("Profil nicht gefunden.", "Profile not found."))
        val profile = File(PilotStore.variantPath(ctx, profileId, Avoid.get(ctx)))
        val lats = DoubleArray(wps.size) { wps[it].lat }
        val lons = DoubleArray(wps.size) { wps[it].lon }
        val visit = BooleanArray(wps.size) { wps[it].visit }
        var r = PilotRouting.route(PilotStore.segmentDir(ctx), profile.path, lats, lons, visit, maxMs, startDirection)
        if (r.error != null) return Outcome.Failed(friendly(r.error))
        var avoided = 0
        // Liegt eine Sperrung auf der Route? Dann mit Sperrzonen neu rechnen; klappt das nicht, bleibt die erste Route.
        if (Baustellen.avoidEnabled(ctx) && Baustellen.enabled(ctx)) {
            if (Baustellen.sites.isEmpty()) Baustellen.loadCached(ctx)
            val closed = Baustellen.sites.filter { it.kind == "c" }
            val t = Baustellen.now()
            val hits = BaustellenLogic.onRoute(r.lats.indices.map { Pair(r.lats[it], r.lons[it]) }, closed, t)
            if (hits.isNotEmpty()) {
                val lines = BaustellenLogic.nogoLines(closed, t, r.lats, r.lons)
                val r2 = PilotRouting.route(PilotStore.segmentDir(ctx), profile.path, lats, lons, visit, maxMs, startDirection, lines)
                if (r2.error == null && r2.lats != null && r2.lats.size >= 2) {
                    val still = BaustellenLogic.onRoute(r2.lats.indices.map { Pair(r2.lats[it], r2.lons[it]) }, closed, t)
                    if (still.size < hits.size) { r = r2; avoided = hits.size - still.size }
                }
                if (avoided == 0) avoided = -1   // Umfahrung nicht möglich
            }
        }
        val route = PilotRoute.build(
            r.lats, r.lons, r.ele, r.seconds, r.ascendM,
            r.hintIndex, r.hintCmd, r.hintExit, r.waypointIndex, visit, r.hintLanes
        )
        avoidedRoute = route; avoidedCount = avoided
        return Outcome.Ok(route)
    }

    /** Ergebnis der letzten Berechnung: für welche Route, wie viele Sperrungen umfahren (>0), -1 = nicht umfahrbar, 0 = nichts zu tun. */
    @Volatile var avoidedRoute: PilotRoute? = null
    @Volatile var avoidedCount = 0

    /** BRouter-Fehlertexte verständlich machen; unbekannte bleiben im Original stehen. */
    fun friendly(raw: String): String {
        val m = raw.lowercase()
        return when {
            m.contains("datafile") && m.contains("not found") -> Tx.t("Routendaten fehlen für einen Teil der Strecke. ($raw)", "Route data is missing for part of the route. ($raw)")
            m.contains("not found") && m.contains("rd5") -> Tx.t("Routendaten fehlen für einen Teil der Strecke. ($raw)", "Route data is missing for part of the route. ($raw)")
            m.contains("no track found") -> Tx.t("Keine Route gefunden (Punkt nicht auf einer befahrbaren Straße?).", "No route found (point not on a drivable road?).")
            m.contains("timeout") || m.contains("time limit") -> Tx.t("Die Berechnung hat zu lange gedauert.", "The calculation took too long.")
            m.contains("lookup") && m.contains("version") -> Tx.t("Routendaten und lookups.dat passen nicht zusammen. Gebiete bitte neu laden. ($raw)", "Route data and lookups.dat do not match. Please download the areas again. ($raw)")
            else -> raw
        }
    }
}
