package io.github.th3s1nc.osmandhudbridge.pilot

/**
 * Eine Route aus dem Webplaner gilt mit dem Profil und den Vermeiden-Schaltern, mit denen sie dort geplant wurde,
 * egal was in der App eingestellt ist. Das eigene Profil wird danach wieder hergestellt. Reine Logik ohne Android.
 */
class PlannerOverride {
    /** Das eigene Profil, solange eine Planer-Route ihr eigenes benutzt (null: nichts überschrieben). */
    var own: String? = null
        private set
    /** Vermeiden-Schalter der Planer-Route (null: die eigenen der App gelten). */
    var avoid: Set<String>? = null
        private set

    val active: Boolean get() = own != null

    /** Planer-Route übernehmen. Gibt das Profil zurück, das jetzt gilt. [profile] unbekannt: das aktuelle bleibt. */
    fun begin(current: String, profile: String?, avoid: Collection<String>?, valid: Collection<String>): String {
        if (own == null) own = current
        this.avoid = avoid?.toSet()
        return if (profile != null && profile in valid) profile else current
    }

    /** Die Vermeiden-Schalter der Planer-Route fallen weg (der Nutzer hat selbst umgestellt). */
    fun dropAvoid() { avoid = null }

    /** Alles zurück auf die eigenen Einstellungen. Gibt das Profil zurück, das jetzt gilt. */
    fun end(current: String): String {
        val o = own ?: current
        own = null; avoid = null
        return o
    }
}
