"""Freunde und Live-Standort (reine Logik, ohne HTTP, damit sie getestet werden kann).

Freundschaft: A schickt eine Anfrage, B nimmt sie an. Danach kann jede Seite für sich einstellen, ob der andere sie sehen darf ("allow").
Live-Positionen liegen nur im Arbeitsspeicher und gelten 120 Sekunden. Gespeichert werden nur die Freundschaften und tägliche Zähler (keine Positionen).
"""
import json, os, threading, time, datetime

LIVE_TTL = 120          # Sekunden: so lange gilt eine Position ohne neue Meldung
MAX_FRIENDS = 30
MAX_OUT = 10            # offene Anfragen von einem Profil
REQ_PER_HOUR = 10       # Anfragen pro Profil und Stunde
VIEW_TTL = 30           # Sekunden: so lange gilt "A sieht gerade B"
STAT_DAYS = 60


def _key(a, b):
    x, y = sorted([a.lower(), b.lower()])
    return x + "|" + y


class Friends:
    def __init__(self, path, stats_path=None, now=time.time):
        self.path = path; self.stats_path = stats_path or (path + ".stats"); self.now = now
        self.lock = threading.Lock()
        self.links = {}           # key -> {"a","b","state","from","since","allow":{name:bool}}
        self.live = {}            # Name -> {"lat","lon","spd","hdg","ts","first"}
        self.views = {}           # (Betrachter, Ziel) -> ts
        self.reqlog = {}          # Name -> [Zeiten]
        self.days = {}            # "YYYY-MM-DD" -> Zähler
        self._stat_saved = 0
        self.load()

    # ---------- Speicher ----------
    def load(self):
        try:
            with open(self.path, encoding="utf-8") as f: self.links = json.load(f).get("links", {})
        except Exception: self.links = {}
        try:
            with open(self.stats_path, encoding="utf-8") as f: self.days = json.load(f).get("days", {})
        except Exception: self.days = {}

    def _save(self):
        try:
            os.makedirs(os.path.dirname(self.path) or ".", exist_ok=True)
            with open(self.path + ".new", "w", encoding="utf-8") as f: json.dump({"links": self.links}, f, ensure_ascii=False, indent=1)
            os.replace(self.path + ".new", self.path)
        except OSError: pass

    def _save_stats(self, force=False):
        if not force and self.now() - self._stat_saved < 60: return
        self._stat_saved = self.now()
        try:
            keep = sorted(self.days)[-STAT_DAYS:]
            self.days = {k: self.days[k] for k in keep}
            os.makedirs(os.path.dirname(self.stats_path) or ".", exist_ok=True)
            with open(self.stats_path + ".new", "w", encoding="utf-8") as f: json.dump({"days": self.days}, f)
            os.replace(self.stats_path + ".new", self.stats_path)
        except OSError: pass

    def _day(self):
        d = datetime.date.fromtimestamp(self.now()).isoformat()
        return self.days.setdefault(d, {"anfragen": 0, "angenommen": 0, "entfernt": 0, "meldungen": 0, "sekunden": 0, "spitze": 0, "nutzer": []})

    # ---------- Freundschaften ----------
    def request(self, me, other):
        """Anfrage von me an other (beide kanonische Profilnamen). Gibt "angefragt" oder "befreundet" zurück, sonst ValueError."""
        if me.lower() == other.lower(): raise ValueError("Das bist du selbst.")
        with self.lock:
            t = self.now(); lst = [x for x in self.reqlog.get(me, []) if t - x < 3600]
            if len(lst) >= REQ_PER_HOUR: raise ValueError("Zu viele Anfragen. Bitte später nochmal.")
            k = _key(me, other); l = self.links.get(k)
            if l:
                if l["state"] == "ok": raise ValueError("Ihr seid schon befreundet.")
                if l["from"].lower() == me.lower(): raise ValueError("Die Anfrage läuft schon.")
                l["state"] = "ok"; l["since"] = int(t); self._day()["angenommen"] += 1; self._save(); self._save_stats(True)
                return "befreundet"   # der andere hatte schon angefragt: gegenseitig, gleich Freunde
            if sum(1 for x in self.links.values() if x["state"] == "ok" and me.lower() in (x["a"].lower(), x["b"].lower())) >= MAX_FRIENDS: raise ValueError("Mehr als %d Freunde sind nicht möglich." % MAX_FRIENDS)
            if sum(1 for x in self.links.values() if x["state"] == "pending" and x["from"].lower() == me.lower()) >= MAX_OUT: raise ValueError("Zu viele offene Anfragen.")
            a, b = sorted([me, other], key=str.lower)
            self.links[k] = {"a": a, "b": b, "state": "pending", "from": me, "since": int(t), "allow": {}}
            lst.append(t); self.reqlog[me] = lst; self._day()["anfragen"] += 1; self._save(); self._save_stats(True)
            return "angefragt"

    def respond(self, me, other, accept):
        with self.lock:
            k = _key(me, other); l = self.links.get(k)
            if not l or l["state"] != "pending" or l["from"].lower() != other.lower(): raise ValueError("Keine offene Anfrage von diesem Profil.")
            if accept:
                l["state"] = "ok"; l["since"] = int(self.now()); self._day()["angenommen"] += 1
            else: del self.links[k]
            self._save(); self._save_stats(True)

    def remove(self, me, other):
        """Freund entfernen oder eigene Anfrage zurückziehen."""
        with self.lock:
            k = _key(me, other)
            if k not in self.links: raise ValueError("Das ist kein Freund.")
            del self.links[k]; self._day()["entfernt"] += 1; self._save(); self._save_stats(True)
            self.views.pop((me, other), None); self.views.pop((other, me), None)

    def permit(self, me, other, allow):
        with self.lock:
            l = self.links.get(_key(me, other))
            if not l or l["state"] != "ok": raise ValueError("Das ist kein Freund.")
            l.setdefault("allow", {})[me] = bool(allow); self._save()

    def remove_profile(self, name):
        with self.lock:
            for k in [k for k, l in self.links.items() if name.lower() in (l["a"].lower(), l["b"].lower())]: del self.links[k]
            self.live.pop(name, None); self._save()

    def remove_link(self, a, b):
        with self.lock:
            ok = self.links.pop(_key(a, b), None) is not None
            if ok: self._save()
            return ok

    def _fresh(self, name):
        p = self.live.get(name); t = self.now()
        return p if p and t - p["ts"] <= LIVE_TTL else None

    def listing(self, me):
        out = []; t = self.now()
        with self.lock:
            for l in self.links.values():
                if me.lower() not in (l["a"].lower(), l["b"].lower()): continue
                other = l["b"] if l["a"].lower() == me.lower() else l["a"]
                if l["state"] == "pending":
                    out.append({"name": other, "status": "outgoing" if l["from"].lower() == me.lower() else "incoming", "since": l["since"]}); continue
                al = l.get("allow", {}); p = self._fresh(other)
                row = {"name": other, "status": "friend", "since": l["since"], "i_allow": bool(al.get(me)), "they_allow": bool(al.get(other)),
                       "sharing": bool(p and al.get(other))}
                if row["sharing"]: row["spd"] = p["spd"]; row["age"] = int(t - p["ts"])
                out.append(row)
        order = {"incoming": 0, "friend": 1, "outgoing": 2}
        return sorted(out, key=lambda r: (order[r["status"]], r["name"].lower()))

    def viewers(self, me):
        """Wie viele Freunde dich laut Einstellung sehen dürfen."""
        n = 0
        with self.lock:
            for l in self.links.values():
                if l["state"] == "ok" and me.lower() in (l["a"].lower(), l["b"].lower()) and l.get("allow", {}).get(me): n += 1
        return n

    def pending_in(self, me):
        with self.lock:
            return sum(1 for l in self.links.values() if l["state"] == "pending" and l["from"].lower() != me.lower() and me.lower() in (l["a"].lower(), l["b"].lower()))

    # ---------- Live ----------
    def update(self, me, lat, lon, spd=0.0, hdg=0.0):
        lat = float(lat); lon = float(lon)
        if not (-90 <= lat <= 90 and -180 <= lon <= 180): raise ValueError("Position ungültig.")
        spd = max(0.0, min(float(spd or 0), 400.0)); hdg = float(hdg or 0) % 360
        t = self.now()
        with self.lock:
            old = self.live.get(me); d = self._day()
            if old and t - old["ts"] <= LIVE_TTL: d["sekunden"] += int(min(t - old["ts"], 30))
            self.live[me] = {"lat": round(lat, 5), "lon": round(lon, 5), "spd": round(spd, 1), "hdg": round(hdg), "ts": t}
            d["meldungen"] += 1
            if me not in d["nutzer"]: d["nutzer"].append(me)
            now_n = sum(1 for p in self.live.values() if t - p["ts"] <= LIVE_TTL)
            if now_n > d["spitze"]: d["spitze"] = now_n
            self._save_stats()

    def stop(self, me):
        with self.lock: self.live.pop(me, None)

    def positions_for(self, me):
        """Positionen aller Freunde, die me sehen darf und die gerade teilen."""
        out = []; t = self.now()
        with self.lock:
            for l in self.links.values():
                if l["state"] != "ok" or me.lower() not in (l["a"].lower(), l["b"].lower()): continue
                other = l["b"] if l["a"].lower() == me.lower() else l["a"]
                p = self._fresh(other)
                if p and l.get("allow", {}).get(other):
                    out.append({"name": other, "lat": p["lat"], "lon": p["lon"], "spd": p["spd"], "hdg": p["hdg"], "age": int(t - p["ts"])})
                    self.views[(me, other)] = t
        return out

    # ---------- Verwaltung ----------
    def admin(self):
        t = self.now()
        with self.lock:
            ok = [l for l in self.links.values() if l["state"] == "ok"]; pend = [l for l in self.links.values() if l["state"] == "pending"]
            sharing = sorted(n for n, p in self.live.items() if t - p["ts"] <= LIVE_TTL)
            sees = sorted((v, z) for (v, z), ts in self.views.items() if t - ts <= VIEW_TTL and self._fresh(z))
            days = [{"tag": k, **{x: y for x, y in v.items() if x != "nutzer"}, "nutzer": len(v.get("nutzer", []))} for k, v in sorted(self.days.items())]
            return {"freundschaften": len(ok), "offen": len(pend), "teilen_jetzt": sharing, "sieht_gerade": [{"von": v, "ziel": z} for v, z in sees],
                    "links": [{"a": l["a"], "b": l["b"], "state": l["state"], "since": l["since"]} for l in sorted(self.links.values(), key=lambda x: -x["since"])],
                    "tage": days[-30:]}
