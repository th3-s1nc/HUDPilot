"""Admin-Meldungen: Der Server schreibt wichtige Ereignisse (SCDB fällig, Update fertig, Fehler ...) in eine Liste.
Bestimmte Profile (vom Admin gewählt) können sie in der App abholen. Nur Standardbibliothek."""
import json, os, threading, time

# (Schlüssel, Beschriftung, Standard an?)
TYPES = [
    ("scdb", "SCDB-Blitzerliste ist fällig", True),
    ("lauf_ok", "Karten- oder Straßendaten-Update fertig", True),
    ("lauf_fehler", "Update mit Fehlern oder abgebrochen", True),
    ("haengt", "Server hängt (keine Bewegung seit 15 Minuten)", True),
    ("baustellen", "Baustellen-Abruf fehlgeschlagen", True),
    ("zertifikat", "Mobilithek-Zertifikat läuft bald ab (30 Tage vorher)", True),
    ("platte", "Festplatte über 90 % voll", True),
    ("start", "Server neu gestartet", True),
    ("profil", "Neues Profil angelegt", False),
    ("version", "Neue Server-Version nach dem Update", False),
]
KEYS = {k for k, _, _ in TYPES}

# Status-Infos, die in der App über den Meldungen stehen können (Schlüssel, Beschriftung, Standard an?)
INFOS = [
    ("version", "Server-Version und Laufzeit", True),
    ("update", "Update läuft (mit Prozent)", True),
    ("karten", "Karten: bereit und Stand", True),
    ("baustellen", "Baustellen: Stand und Anzahl", True),
    ("scdb", "Blitzerliste: Alter", True),
    ("speicher", "Speicher belegt", True),
    ("routing", "Routing online und Dauer", True),
    ("handys", "Aktive Handys und Profile", True),
    ("live", "Wer teilt gerade den Standort (Anzahl)", False),
    ("zertifikat", "Mobilithek-Zertifikat: Tage", True),
]
INFO_KEYS = [k for k, _, _ in INFOS]
MAX_EVENTS = 200


class Notes:
    def __init__(self, path):
        self.path = path; self.lock = threading.Lock()
        self.d = {"recip": [], "on": {}, "info_on": {}, "events": [], "next": 1, "last": {}, "ver": ""}
        self.status_fn = None   # wird von update.py gesetzt: liefert die Status-Kacheln
        try:
            with open(path, encoding="utf-8") as f: j = json.load(f)
            if isinstance(j, dict): self.d.update({k: j[k] for k in self.d if k in j})
        except Exception: pass

    def _save(self):
        try:
            os.makedirs(os.path.dirname(self.path), exist_ok=True)
            with open(self.path + ".new", "w", encoding="utf-8") as f: json.dump(self.d, f, ensure_ascii=False)
            os.replace(self.path + ".new", self.path)
        except Exception: pass

    def enabled(self, typ):
        if typ == "test": return True
        v = self.d["on"].get(typ)
        return bool(v) if v is not None else dict((k, dflt) for k, _, dflt in TYPES).get(typ, False)

    def add(self, typ, text, cool=0):
        """Meldung anhängen. cool = Sekunden, in denen dieselbe Art nicht noch einmal gemeldet wird. Gibt die Nummer zurück (oder None)."""
        with self.lock:
            if typ != "test" and typ not in KEYS: return None
            if not self.enabled(typ): return None
            now = int(time.time())
            if cool and now - int(self.d["last"].get(typ, 0)) < cool: return None
            n = self.d["next"]; self.d["next"] = n + 1; self.d["last"][typ] = now
            self.d["events"].append({"id": n, "ts": now, "typ": typ, "text": str(text)[:300]})
            self.d["events"] = self.d["events"][-MAX_EVENTS:]
            self._save()
            return n

    def check_version(self, ver):
        """Beim Start: neue Version melden (nur beim Wechsel)."""
        with self.lock:
            old = self.d.get("ver", ""); self.d["ver"] = ver; self._save()
        if old and old != ver: self.add("version", f"Server läuft jetzt mit Version {ver} (vorher {old}).")

    def is_recip(self, name):
        return bool(name) and name in self.d["recip"]

    def set_recip(self, name, on):
        with self.lock:
            r = [x for x in self.d["recip"] if x != name]
            if on: r.append(name)
            self.d["recip"] = r; self._save()

    def info_enabled(self, key):
        v = self.d["info_on"].get(key)
        return bool(v) if v is not None else dict((k, dflt) for k, _, dflt in INFOS).get(key, False)

    def set_info(self, key, on):
        if key not in INFO_KEYS: return False
        with self.lock: self.d["info_on"][key] = bool(on); self._save()
        return True

    def status(self):
        """Angehakte Status-Kacheln in fester Reihenfolge (leer, wenn nichts eingerichtet ist)."""
        if not self.status_fn: return []
        try: items = self.status_fn()
        except Exception: return []
        return [x for x in items if x.get("key") in INFO_KEYS and self.info_enabled(x["key"])]

    def set_type(self, key, on):
        if key not in KEYS: return False
        with self.lock: self.d["on"][key] = bool(on); self._save()
        return True

    def remove_profile(self, name):
        self.set_recip(name, False)

    def clear(self):
        with self.lock: self.d["events"] = []; self._save()

    def since(self, name, n=0):
        """Für die App. Wer nicht Empfänger ist, bekommt nichts außer erlaubt=false."""
        if not self.is_recip(name): return {"erlaubt": False, "events": [], "letzte": 0, "status": []}
        ev = [e for e in self.d["events"] if e["id"] > int(n)][-50:]
        return {"erlaubt": True, "events": ev, "letzte": self.d["next"] - 1, "status": self.status()}

    def admin(self):
        return {"recip": list(self.d["recip"]),
                "typen": [{"key": k, "label": lab, "on": self.enabled(k)} for k, lab, _ in TYPES],
                "infos": [{"key": k, "label": lab, "on": self.info_enabled(k)} for k, lab, _ in INFOS],
                "events": list(reversed(self.d["events"][-30:]))}
