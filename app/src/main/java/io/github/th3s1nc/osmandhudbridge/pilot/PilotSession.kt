package io.github.th3s1nc.osmandhudbridge.pilot

import android.content.Context
import java.io.File

/** Merkt sich im Speicher die geladene Planung, damit sie beim Drehen oder Wiederöffnen der Seite erhalten bleibt. */
object PilotSession {
    var name: String = ""
    var waypoints: List<Waypoint> = emptyList()
    var route: PilotRoute? = null

    /** Wegpunkte, mit denen die aktuelle Route berechnet wurde (nach einer Neuplanung andere als die der GPX). */
    var routeWaypoints: List<Waypoint> = emptyList()
    var profileId: String = PilotStore.profiles.first().id

    /** Profil und Vermeiden-Schalter einer Route aus dem Webplaner (gelten nur für diese Route). */
    val planner = PlannerOverride()

    /** Vermeiden-Schalter, die jetzt gelten: die der Planer-Route, sonst die eigenen. */
    fun avoid(ctx: Context): Avoid.Set = planner.avoid?.let { Avoid.Set(it) } ?: Avoid.get(ctx)

    private const val LAST = "pilot_last.gpx"

    fun load(ctx: Context, p: GpxRoute.Parsed, rawGpx: String) {
        name = p.name
        waypoints = p.waypoints
        route = null
        routeWaypoints = emptyList()
        try { File(ctx.filesDir, LAST).writeText(rawGpx) } catch (_: Exception) {}
    }

    /** Nach einem App-Neustart die zuletzt geöffnete GPX wieder laden (ohne berechnete Route). */
    fun restore(ctx: Context) {
        if (waypoints.isNotEmpty()) return
        try {
            val f = File(ctx.filesDir, LAST)
            if (!f.isFile) return
            val p = GpxRoute.parse(f.readText()) ?: return
            name = p.name; waypoints = p.waypoints
        } catch (_: Exception) {}
    }
}
