package io.github.th3s1nc.osmandhudbridge.pilot

import org.json.JSONArray
import org.json.JSONObject

/**
 * Macht aus dem Protomaps-Stil (eine Quelle "protomaps", Pfad __PATH__) einen Stil für mehrere Kartendateien:
 * je Datei eine Quelle, und jede Ebene wird pro Quelle wiederholt (Ebene für Ebene abwechselnd,
 * damit z. B. die Flächen einer Region die Straßen einer anderen nicht überdecken).
 */
object StyleMerge {
    fun merge(styleJson: String, paths: List<String>): String {
        if (paths.isEmpty()) return styleJson
        val j = JSONObject(styleJson)
        val sources = j.getJSONObject("sources")
        val baseSrc = sources.getJSONObject("protomaps")
        fun srcName(k: Int) = if (k == 0) "protomaps" else "protomaps$k"
        for (k in paths.indices) {
            val s = JSONObject(baseSrc.toString())
            s.put("url", "pmtiles://file://" + paths[k])
            sources.put(srcName(k), s)
        }
        if (paths.size == 1) return j.toString()
        val layers = j.getJSONArray("layers")
        val out = JSONArray()
        for (i in 0 until layers.length()) {
            val l = layers.getJSONObject(i)
            if (l.optString("source") != "protomaps") { out.put(l); continue }
            for (k in paths.indices) {
                val c = JSONObject(l.toString())
                if (k > 0) c.put("id", l.getString("id") + "~" + k)
                c.put("source", srcName(k))
                out.put(c)
            }
        }
        j.put("layers", out)
        return j.toString()
    }
}
