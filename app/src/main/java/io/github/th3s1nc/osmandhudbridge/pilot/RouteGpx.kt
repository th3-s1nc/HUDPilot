package io.github.th3s1nc.osmandhudbridge.pilot

import java.util.Locale

/** Schreibt die berechnete Route als GPX (Track), damit man sie in einer Karten-App ansehen oder mit Kurviger vergleichen kann. */
object RouteGpx {
    fun write(route: PilotRoute, name: String): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"HUDPilot\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("<metadata><name>").append(esc(name)).append("</name></metadata>\n")
        for (ins in route.instructions) {
            if (ins.brCmd != BrouterCmd.VIA && ins.brCmd != BrouterCmd.END) continue
            val p = route.points[ins.pointIndex.coerceIn(0, route.points.size - 1)]
            sb.append("<wpt lat=\"").append(num(p.lat)).append("\" lon=\"").append(num(p.lon)).append("\"><name>")
                .append(if (ins.brCmd == BrouterCmd.END) "Ziel / Destination" else "Zwischenziel / Waypoint").append("</name></wpt>\n")
        }
        sb.append("<trk><name>").append(esc(name)).append("</name><trkseg>\n")
        for (p in route.points) {
            sb.append("<trkpt lat=\"").append(num(p.lat)).append("\" lon=\"").append(num(p.lon)).append("\"><ele>")
                .append(String.format(Locale.US, "%.1f", p.ele)).append("</ele></trkpt>\n")
        }
        sb.append("</trkseg></trk>\n</gpx>\n")
        return sb.toString()
    }

    private fun num(v: Double) = String.format(Locale.US, "%.6f", v)

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
