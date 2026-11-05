"""HUDPilot Updater (th3_s1nc): baut Regionen-Karten (.pmtiles) + Routendaten (.rd5), mit Statusseite,
Download-Bremse und Zeitfenster. Alles in einem Container auf Port 8085.
Fehler stehen im Protokoll der Verwaltung."""
import urllib.error, urllib.parse, collections, datetime, gzip, hmac, json, math, os, socket, socketserver, subprocess, sys, threading, time, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DATA = os.environ.get("DATA_DIR", "/data"); PUB = f"{DATA}/public"; TMP = f"{DATA}/tmp"
HERE = os.path.dirname(os.path.abspath(__file__))
PLANET_BASE = os.environ.get("PLANET_BASE", "https://build.protomaps.com")
PLANET_URL = os.environ.get("PLANET_URL", "")
BROUTER = os.environ.get("BROUTER_BASE", "https://brouter.de/brouter/segments4")
MAXZOOM = os.environ.get("MAXZOOM", "15")
GEOFABRIK = os.environ.get("GEOFABRIK_BASE", "https://download.geofabrik.de")   # Datei: {GEOFABRIK}/{pbf}-latest.osm.pbf
PORT = int(os.environ.get("PORT", "8085")); PROXY_PORT = int(os.environ.get("PROXY_PORT", "8087"))
ADMIN_PW = os.environ.get("ADMIN_PASSWORD", "")
import hashlib, re, secrets, shutil
import baustellen as BSM
import friends as FR
import notify as NT
SESS_TOKEN = hmac.new(ADMIN_PW.encode(), b"hudsess", hashlib.sha256).hexdigest() if ADMIN_PW else ""
VERSION = "1.36.0"; STARTED = int(time.time())
UA = f"hudpilot-server/{VERSION} (private use)"
LIMITS_DIR = os.environ.get("LIMITS_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "limits"))   # Import-Code der App (Klassen-Ordner, gebaut mit limits-tool/build.sh)
LIMITS_MAIN = "io.github.th3s1nc.osmandhudbridge.limit.LimitsCliKt"
LIMITS_XMX = os.environ.get("LIMITS_XMX", "2g")   # Arbeitsspeicher für das Berechnen der Straßendaten (Bayern: 2g reicht in der Regel)
NPM_IPS = {x.strip() for x in os.environ.get("NPM_IPS", "").split(",") if x.strip()}   # Anfragen von diesen Adressen (Nginx Proxy Manager) gelten als "von außen"
NEED_DAYS = int(os.environ.get("NEED_DAYS", "90"))   # nur Regionen, die das Handy in dieser Zeit gemeldet hat, werden aktuell gehalten
BASE_REGIONS = json.load(open(os.path.join(HERE, "regions.json"), encoding="utf-8"))   # mitgeliefert (Deutschland, Österreich, Schweiz, Tschechien)
BASE_IDS = [r["id"] for r in BASE_REGIONS]
EXTRA_FILE = f"{DATA}/regions_extra.json"   # von "Regionsliste aus Geofabrik holen" erzeugt (Europa), bleibt beim Neustart erhalten
REGIONS = []; BYID = {}
LAND_NAMES = {"DE": "Deutschland", "AT": "Österreich", "CH": "Schweiz", "CZ": "Tschechien"}
# Länder für die Europa-Liste: (Kürzel, deutscher Name, Geofabrik-Kennungen in Reihenfolge der Vorliebe). DE, AT, CH, CZ stehen schon in regions.json.
WANTED = [
    ("BE", "Belgien", ["belgium"]), ("BG", "Bulgarien", ["bulgaria"]), ("HR", "Kroatien", ["croatia"]), ("CY", "Zypern", ["cyprus"]),
    ("DK", "Dänemark", ["denmark"]), ("EE", "Estland", ["estonia"]), ("FI", "Finnland", ["finland"]), ("FR", "Frankreich", ["france"]),
    ("GR", "Griechenland", ["greece"]), ("IE", "Irland (mit Nordirland)", ["ireland-and-northern-ireland"]), ("IT", "Italien", ["italy"]),
    ("LV", "Lettland", ["latvia"]), ("LT", "Litauen", ["lithuania"]), ("LU", "Luxemburg", ["luxembourg"]), ("MT", "Malta", ["malta"]),
    ("NL", "Niederlande", ["netherlands"]), ("NO", "Norwegen", ["norway"]), ("PL", "Polen", ["poland"]), ("PT", "Portugal", ["portugal"]),
    ("RO", "Rumänien", ["romania"]), ("SE", "Schweden", ["sweden"]), ("SK", "Slowakei", ["slovakia"]), ("SI", "Slowenien", ["slovenia"]),
    ("ES", "Spanien", ["spain"]), ("HU", "Ungarn", ["hungary"]), ("GB", "Großbritannien", ["great-britain", "united-kingdom"]),
    ("TR", "Türkei", ["turkey"]),
]
LAND_NAMES.update({iso: name for iso, name, _ in WANTED})

def reload_regions():
    """Basisliste plus erzeugte Regionen (ungültige oder doppelte Einträge werden übergangen). Ändert die Listen an Ort und Stelle."""
    extra = []
    try: extra = json.load(open(EXTRA_FILE, encoding="utf-8"))
    except Exception: pass
    out = list(BASE_REGIONS); seen = set(BASE_IDS)
    for r in extra if isinstance(extra, list) else []:
        try:
            rid = str(r["id"]); bb = [float(x) for x in r["bbox"]]
            if rid in seen or not re.match(r"^[a-z0-9-]+$", rid) or len(bb) != 4 or not r["pbf"] or not r["name"] or not r["land"]: continue
            out.append({"id": rid, "name": str(r["name"]), "land": str(r["land"]).upper(), "bbox": bb, "pbf": str(r["pbf"])})
            seen.add(rid)
        except (KeyError, TypeError, ValueError): continue
    REGIONS[:] = out
    BYID.clear(); BYID.update({r["id"]: r for r in out})
    for r in out: LAND_NAMES.setdefault(r["land"], r["land"])
reload_regions()

# Protokoll-Kategorien (im Admin einzeln abhakbar). Start, Ende, Abbruch, Warnungen und Fehler werden immer protokolliert.
LOG_CATS = [("download", "Downloads (Größe, Dauer, Tempo)"), ("karte", "Karte und Routen"), ("orte", "Orte"),
            ("strassen", "Straßendaten (Mengen)"), ("blitzer", "Blitzer und SCDB-Liste"), ("handy", "Handy-Anfragen"),
            ("anmeldung", "Anmeldungen und Sync"), ("aufraeumen", "Aufräumen"), ("freunde", "Freunde und Live-Standort")]
LOG_PRESETS = {"kurz": [], "normal": ["download", "karte", "orte", "strassen", "blitzer", "freunde"], "ausfuehrlich": [k for k, _ in LOG_CATS]}

# ---------- Einstellungen (werden in /data/config.json gespeichert) ----------
CFG_LOCK = threading.Lock()
CFG = {"limit_in_mbit": 0.0, "limit_out_mbit": 0.0, "win_start": 1, "win_end": 6,
       "every_days": int(os.environ.get("UPDATE_EVERY_DAYS", "30")),
       "log_level": "normal", "log_cats": list(LOG_PRESETS["normal"]), "extern_karten": False,
       "br_mem_mb": 4096, "br_threads": 8,   # Rechenleistung für BRouter (Routenberechnung)
       "nur_gebaute": True, "versteckt": [],   # Sichtbarkeit in der App: nur gebaute Regionen anbieten; einzelne Regionen ausblenden
       "regions": list(BASE_IDS)}   # erzeugte Europa-Regionen sind nie von selbst angehakt
if os.environ.get("REGIONS", "ALL") != "ALL":
    CFG["regions"] = [x.strip() for x in os.environ["REGIONS"].split(",") if x.strip() in BYID]

def load_cfg():
    try: CFG.update(json.load(open(f"{DATA}/config.json", encoding="utf-8")))
    except Exception: pass
def save_cfg():
    os.makedirs(DATA, exist_ok=True)
    json.dump(CFG, open(f"{DATA}/config.json", "w", encoding="utf-8"), indent=1)

def in_window(h):
    s, e = int(CFG["win_start"]), int(CFG["win_end"])
    if s == e: return True
    return s <= h < e if s < e else (h >= s or h < e)
def cur_limit_mbit():
    return float(CFG["limit_in_mbit"] if in_window(datetime.datetime.now().hour) else CFG["limit_out_mbit"])

# ---------- Zustand + Log ----------
S = {"state": "idle", "current": None, "done": 0, "total": 0, "started": None, "detail": "", "stop": False, "errors": {},
     "pct": None, "frac": 0.0, "step": None, "beat": 0.0, "stages": []}
STATE_FILE = f"{DATA}/state.json"
LOG = collections.deque(maxlen=2000)
LOG_FILE = f"{DATA}/log.txt"; LOG_MAX = 500_000   # zwei Dateien (log.txt und log.txt.1) mit höchstens je 500 kB: zusammen etwa 1 MB, die ältesten Zeilen fallen weg
_LOG_LOCK = threading.Lock(); _LOG_BYTES = [0]
HANG_S = int(os.environ.get("HANG_S", "300"))   # so lange darf sich nichts bewegen, bevor die Seite "hängt vermutlich" zeigt

def log(*a, cat=None):
    """Protokollzeile. Mit cat nur, wenn die Kategorie im Admin angehakt ist; ohne cat immer (Start, Ende, Fehler)."""
    if cat and cat not in CFG.get("log_cats", []): return
    line = datetime.datetime.now().strftime("%F %T ") + " ".join(str(x) for x in a)
    with _LOG_LOCK:
        LOG.append(line); print(line, flush=True)
        try:
            os.makedirs(DATA, exist_ok=True)
            with open(LOG_FILE, "a", encoding="utf-8") as f: f.write(line + "\n")
            _LOG_BYTES[0] += len(line) + 1
            if _LOG_BYTES[0] > LOG_MAX:
                os.replace(LOG_FILE, LOG_FILE + ".1"); _LOG_BYTES[0] = 0
        except OSError: pass
def logv(*a):
    """Zwischenzeilen (zum Beispiel "Baue Bayern"): nur wenn irgendeine Kategorie angehakt ist (Stufe "Kurz" zeigt sie nicht)."""
    if CFG.get("log_cats"): log(*a)
def log_load():
    lines = []
    for fn in (LOG_FILE + ".1", LOG_FILE):
        try:
            with open(fn, encoding="utf-8", errors="replace") as f: lines += f.read().splitlines()
        except OSError: pass
    LOG.extend(lines[-2000:])
    try: _LOG_BYTES[0] = os.path.getsize(LOG_FILE)
    except OSError: _LOG_BYTES[0] = 0
def log_clear():
    with _LOG_LOCK:
        LOG.clear(); _LOG_BYTES[0] = 0
        for fn in (LOG_FILE, LOG_FILE + ".1"):
            try: os.remove(fn)
            except OSError: pass
def fmt_size(b):
    return f"{b / 1e9:.2f} GB" if b >= 1e9 else f"{b / 1e6:.0f} MB" if b >= 1e6 else f"{b / 1e3:.0f} kB"
def nf(n):
    return f"{int(n):,}".replace(",", ".")
def fmt_dur(s):
    s = int(s); return f"{s // 60}:{s % 60:02d} Min" if s >= 60 else f"{s} Sek"

# ---------- Fortschritt: jeder Schritt meldet Text und Prozent; die Seite zeigt beides ----------
STAGES_FULL = [("karte", 25), ("routen", 10), ("download", 20), ("filter", 10), ("schreiben", 3), ("strassen", 32)]   # Gewicht der Schritte pro Region (für den Gesamtbalken)
STAGES_POI = [("download", 30), ("filter", 15), ("schreiben", 5), ("strassen", 50)]
def set_detail(text, pct=None):
    if text != S["detail"] or pct != S["pct"]: S["beat"] = time.time()
    S["detail"] = text; S["pct"] = pct
def stage(key, text, pct=None):
    """Meldet den aktuellen Schritt der laufenden Region (pct 0 bis 100, None = Fortschritt unbekannt)."""
    st = S["stages"]; tot = sum(w for _, w in st) or 1; off = 0; idx = 0; w = 0
    for i, (k, wt) in enumerate(st):
        if k == key: idx = i; w = wt; break
        off += wt
    S["frac"] = min(1.0, (off + w * (pct or 0) / 100.0) / tot)
    S["step"] = [idx + 1, len(st)]
    set_detail(text, pct)

# ---------- Programme starten, mit sofortigem Abbrechen ----------
class Aborted(BaseException):
    """Der Lauf wurde mit "Abbrechen" beendet. Ist absichtlich keine normale Exception, damit "except Exception" sie nicht als Fehler zählt."""
PROCS = []; PROCS_LOCK = threading.Lock()
def kill_proc(p):
    try: p.terminate()
    except Exception: pass
    try: p.wait(3)
    except Exception:
        try: p.kill()
        except Exception: pass
def cpu_ticks(pid):
    try:
        s = open(f"/proc/{pid}/stat").read(); f = s[s.rindex(")") + 2:].split()
        return int(f[11]) + int(f[12])
    except Exception: return -1
def run_proc(cmd, env=None, on_line=None, tick=None):
    """Startet ein Programm und wartet darauf. "Abbrechen" beendet es sofort (raise Aborted). Ausgabezeilen gehen an on_line, tick() wird alle 0,4 s gerufen.
    Gibt (Rückgabewert, letzte Ausgabezeilen) zurück. Solange das Programm Rechenzeit verbraucht, gilt der Server als "in Bewegung"."""
    if S["stop"]: raise Aborted()
    p = subprocess.Popen(cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    with PROCS_LOCK: PROCS.append(p)
    out = collections.deque(maxlen=12)
    def rd():
        buf = b""; fd = p.stdout.fileno()
        while True:
            try: c = os.read(fd, 4096)
            except OSError: break
            if not c: break
            buf += c; parts = buf.replace(b"\r", b"\n").split(b"\n"); buf = parts[-1]
            for x in parts[:-1]:
                t = x.decode("utf8", "replace").strip()
                if not t: continue
                out.append(t)
                if on_line:
                    try: on_line(t)
                    except Exception: pass
    th = threading.Thread(target=rd, daemon=True); th.start()
    last = -1
    try:
        while p.poll() is None:
            if S["stop"]: kill_proc(p); raise Aborted()
            if tick:
                try: tick()
                except Exception: pass
            c = cpu_ticks(p.pid)
            if c != last: last = c; S["beat"] = time.time()
            time.sleep(0.4)
        th.join(3)
    finally:
        with PROCS_LOCK:
            if p in PROCS: PROCS.remove(p)
        if p.poll() is None: kill_proc(p)
    if S["stop"]: raise Aborted()
    return p.returncode, list(out)
def load_state():
    try: return json.load(open(STATE_FILE))
    except Exception: return {}
def save_state(**kw):
    st = load_state(); st.update(kw); json.dump(st, open(STATE_FILE, "w"))

# ---------- Download-Bremse: kleiner lokaler Proxy, durch den pmtiles und curl laufen ----------
class Throttle:
    def __init__(self): self.lock = threading.Lock(); self.t = 0.0
    def take(self, n):
        rate = cur_limit_mbit() * 1e6 / 8
        if rate <= 0: return
        with self.lock:
            now = time.monotonic(); self.t = max(self.t, now) + n / rate; wait = self.t - now
        if wait > 0.25: time.sleep(wait - 0.25)
THROTTLE = Throttle()

class ProxyH(socketserver.BaseRequestHandler):
    def handle(self):
        c = self.request; buf = b""
        try:
            while b"\r\n\r\n" not in buf and len(buf) < 8192:
                d = c.recv(4096)
                if not d: return
                buf += d
            first = buf.split(b"\r\n", 1)[0].decode("latin1").split()
            if len(first) < 2 or first[0] != "CONNECT":
                c.sendall(b"HTTP/1.1 405 Method Not Allowed\r\n\r\n"); return
            host, _, port = first[1].rpartition(":")
            up = socket.create_connection((host, int(port)), timeout=30)
        except Exception:
            try: c.sendall(b"HTTP/1.1 502 Bad Gateway\r\n\r\n")
            except Exception: pass
            return
        c.sendall(b"HTTP/1.1 200 Connection Established\r\n\r\n"); up.settimeout(None)
        def up_pipe():
            try:
                while True:
                    d = c.recv(65536)
                    if not d: break
                    up.sendall(d)
            except Exception: pass
            try: up.shutdown(socket.SHUT_WR)
            except Exception: pass
        threading.Thread(target=up_pipe, daemon=True).start()
        try:
            while True:
                d = up.recv(16384)
                if not d: break
                THROTTLE.take(len(d)); c.sendall(d)
        except Exception: pass
        finally:
            try: up.close()
            except Exception: pass
class ProxyServer(socketserver.ThreadingMixIn, socketserver.TCPServer):
    daemon_threads = True; allow_reuse_address = True

def sub_env():
    e = dict(os.environ); p = f"http://127.0.0.1:{PROXY_PORT}"
    e.update(HTTPS_PROXY=p, https_proxy=p); e.pop("NO_PROXY", None); e.pop("no_proxy", None); return e

# ---------- Bauen ----------
def head_ok(url):
    try: urllib.request.urlopen(urllib.request.Request(url, method="HEAD", headers={"User-Agent": UA}), timeout=30).close(); return True
    except Exception: return False
def find_planet():
    if PLANET_URL: return PLANET_URL
    d = datetime.date.today()
    for i in range(10):
        if S["stop"]: raise Aborted()
        u = f"{PLANET_BASE}/{(d - datetime.timedelta(days=i)).strftime('%Y%m%d')}.pmtiles"
        set_detail(f"Suche aktuelle Weltkarte ({i + 1} von 10) …", int(100 * i / 10))
        if head_ok(u): return u
    raise RuntimeError("Kein aktueller Protomaps-Build gefunden (PLANET_URL setzen, siehe maps.protomaps.com/builds)")
def tiles_for(b):
    w, s, e, n = b; out = []
    for lon in range(math.floor(w / 5) * 5, math.floor(e / 5) * 5 + 1, 5):
        for lat in range(math.floor(s / 5) * 5, math.floor(n / 5) * 5 + 1, 5):
            out.append(f"{'E' if lon >= 0 else 'W'}{abs(lon)}_{'N' if lat >= 0 else 'S'}{abs(lat)}")
    return out
def content_length(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, method="HEAD", headers={"User-Agent": UA}), timeout=30) as r:
            return int(r.headers.get("Content-Length") or 0)
    except Exception: return 0
def fetch_file(url, dst, key, label, extra=(), part=None):
    """Datei per curl holen, mit Fortschritt (Größe vom Server, Tempo, Restzeit). part=(i, n): Datei i von n teilt sich einen Balken.
    Gibt (Bytes, Sekunden) zurück. Bricht sofort ab, wenn "Abbrechen" gedrückt wird."""
    total = content_length(url); t0 = time.time(); st = {"t": t0, "b": 0, "v": 0.0}
    try: os.remove(dst)
    except OSError: pass
    def show(b, sp):
        txt = f"{label}: {fmt_size(b)}" + (f" von {fmt_size(total)}" if total else "") + (f" · {sp / 1e6:.1f} MB/s" if sp > 0 else "")
        if total and sp > 0 and b < total: txt += f" · noch ca. {fmt_dur((total - b) / sp)}"
        pc = min(99, int(100 * b / total)) if total else None
        if part:
            txt = f"{label} (Datei {part[0] + 1} von {part[1]}): {fmt_size(b)}" + (f" von {fmt_size(total)}" if total else "")
            pc = int((part[0] * 100 + (pc or 0)) / part[1])
        stage(key, txt, pc)
    show(0, 0)
    def tick():
        try: b = os.path.getsize(dst)
        except OSError: b = 0
        now = time.time(); dt = now - st["t"]
        if dt >= 1.0:
            v = (b - st["b"]) / dt; st["v"] = v if st["v"] == 0 else 0.6 * st["v"] + 0.4 * v; st["t"] = now; st["b"] = b
        show(b, st["v"])
    rc, out = run_proc(["curl", "-fsSL", "-A", UA, "--retry", "3", "-o", dst, *extra, url], env=sub_env(), tick=tick)
    if rc != 0: raise RuntimeError(f"{label}: curl-Fehler {rc}")
    try: b = os.path.getsize(dst)
    except OSError: b = 0
    return b, time.time() - t0
def fetch_rd5(name, i=0, n=1):
    dst = f"{PUB}/rd5/{name}.rd5"; os.makedirs(os.path.dirname(dst), exist_ok=True); part = dst + ".part"
    try:
        b, sec = fetch_file(f"{BROUTER}/{name}.rd5", part, "routen", f"Routendaten {name}", ["-z", dst] if os.path.exists(dst) else [], (i, n))
        if os.path.exists(part):
            if os.path.getsize(part) > 0:
                os.replace(part, dst); log(f"Routendaten neu: {name} ({fmt_size(b)} in {fmt_dur(sec)})", cat="karte")
            else: os.remove(part)
    finally:
        if os.path.exists(part):
            try: os.remove(part)
            except OSError: pass
def extract(planet, tmp, bbox, label):
    w, s, e, n = bbox
    def on_line(t):
        m = re.search(r"(\d{1,3})\s*%", t)
        pc = min(100, int(m.group(1))) if m else S["pct"]
        stage("karte", f"Karte {label}: " + (f"{pc} %" if m else t[-90:]), pc)
    stage("karte", f"Karte {label}: starte …", 0)
    rc, out = run_proc(["pmtiles", "extract", planet, tmp, f"--bbox={w},{s},{e},{n}", f"--maxzoom={MAXZOOM}"], env=sub_env(), on_line=on_line)
    if rc != 0: raise RuntimeError(f"pmtiles extract Fehler {rc}: {out[-1] if out else ''}")

# ---------- POI-Dateien aus Geofabrik (OpenStreetMap-Auszug) ----------
POI_FILTERS = ["nw/amenity=fuel,charging_station,restaurant,cafe,pub,biergarten,fast_food,bar,ice_cream,parking,motorcycle_parking",
               "nw/tourism=hotel,guest_house,hostel,motel,camp_site,caravan_site,viewpoint",
               "nw/shop=motorcycle"]
POI_KINDS = {"fuel", "charging_station", "restaurant", "cafe", "pub", "biergarten", "fast_food", "bar", "ice_cream", "parking",
             "motorcycle_parking", "hotel", "guest_house", "hostel", "motel", "camp_site", "caravan_site", "viewpoint", "motorcycle_shop"}
PARK_SKIP_ACCESS = {"private", "customers", "permit", "delivery", "no"}
PARK_SKIP_TYPE = {"street_side", "lane", "on_kerb", "half_on_kerb", "layby", "rooftop"}

def _clean(x):
    return str(x).replace("\t", " ").replace("\n", " ").replace("\r", " ").strip()

def poi_rows(lines):
    """Zeilen aus 'osmium export -f geojsonseq' -> (art, name, lat, lon, strasse, ort). Flächen werden durch ihren Mittelpunkt ersetzt."""
    for ln in lines:
        ln = ln.lstrip("\x1e").strip()
        if not ln: continue
        try: f = json.loads(ln)
        except ValueError: continue
        g = f.get("geometry") or {}; p = f.get("properties") or {}
        t = g.get("type"); c = g.get("coordinates")
        if t == "Point": lon, lat = c[0], c[1]
        elif t in ("Polygon", "MultiPolygon"):
            ring = c[0] if t == "Polygon" else c[0][0]
            if not ring: continue
            lon = sum(q[0] for q in ring) / len(ring); lat = sum(q[1] for q in ring) / len(ring)
        else: continue
        kind = p.get("amenity") or p.get("tourism")
        if not kind and p.get("shop") == "motorcycle": kind = "motorcycle_shop"
        if kind not in POI_KINDS: continue
        if kind == "parking" and (p.get("access") in PARK_SKIP_ACCESS or p.get("parking") in PARK_SKIP_TYPE): continue
        name = p.get("name") or p.get("brand") or p.get("operator") or ""
        street = (str(p.get("addr:street", "")) + " " + str(p.get("addr:housenumber", ""))).strip()
        yield (kind, _clean(name), round(lat, 5), round(lon, 5), _clean(street), _clean(p.get("addr:city", "")))

POI_CATS = {"fuel": {"fuel"}, "food": {"restaurant", "cafe", "pub", "biergarten", "fast_food", "bar", "ice_cream"},
            "hotel": {"hotel", "guest_house", "hostel", "motel"}, "parking": {"parking", "motorcycle_parking"}}

def laender_boxen():
    """Gebaute Regionen mit Land und Rahmen (für den Planer: Rundtour im Land bleiben)."""
    return [{"land": r["land"], "bbox": r["bbox"]} for r in REGIONS if os.path.exists(f"{PUB}/maps/{r['id']}.pmtiles")]

def poi_search(cat, lat, lon, km=15.0, limit=40):
    """Orte einer Kategorie im Umkreis aus den gebauten POI-Dateien (pois/<id>.tsv.gz). Sortiert nach Entfernung.
    Gibt (Treffer, Zahl durchsuchter Regionen) zurück; Treffer: {name, art, lat, lon, strasse, ort, km}."""
    kinds = POI_CATS.get(cat)
    if not kinds: raise ValueError("Unbekannte Kategorie.")
    km = max(1.0, min(60.0, float(km)))
    dlat = km / 110.54; dlon = km / (111.32 * max(0.05, math.cos(math.radians(lat))))
    w, s, e, n = lon - dlon, lat - dlat, lon + dlon, lat + dlat
    hits = []; seen = set(); searched = 0
    for r in REGIONS:
        f = f"{PUB}/pois/{r['id']}.tsv.gz"; b = r["bbox"]
        if not os.path.exists(f) or b[2] < w or b[0] > e or b[3] < s or b[1] > n: continue
        searched += 1
        try:
            with gzip.open(f, "rt", encoding="utf-8", errors="replace") as fh:
                for ln in fh:
                    if ln.startswith("#"): continue
                    q = ln.rstrip("\n").split("\t")
                    if len(q) < 6 or q[0] not in kinds: continue
                    try: la = float(q[2]); lo = float(q[3])
                    except ValueError: continue
                    if not (s <= la <= n and w <= lo <= e): continue
                    key = (q[0], round(la, 4), round(lo, 4))
                    if key in seen: continue
                    seen.add(key)
                    d = math.hypot((lo - lon) * 111.32 * math.cos(math.radians(lat)), (la - lat) * 110.54)
                    if d > km: continue
                    hits.append({"name": q[1], "art": q[0], "lat": la, "lon": lo, "strasse": q[4], "ort": q[5], "km": round(d, 1)})
        except OSError: pass
    hits.sort(key=lambda x: x["km"])
    return hits[:max(1, min(100, limit))], searched

def build_limits(r, pbf):
    """Straßendaten der Region (Tempolimits, Ortsschilder, Blitzer, Bahnübergänge, Zebrastreifen, Verkehrsberuhigung) mit dem Import-Code
    der App aus der Geofabrik-Datei bauen und als public/limits/<id>.zip ablegen. Gibt die Zahl der Kacheln zurück (None: nicht gebaut)."""
    if not os.path.isdir(LIMITS_DIR):
        log("Straßendaten: Ordner limits fehlt, übersprungen"); return None
    out = f"{PUB}/limits/{r['id']}.zip"; d = f"{TMP}/{r['id']}.limits"; t0 = time.time()
    shutil.rmtree(d, ignore_errors=True); os.makedirs(f"{PUB}/limits", exist_ok=True)
    info = {"stats": ""}
    def on_line(line):
        if line.startswith("PROGRESS "):
            q = line.split(" ", 3)
            try: pc = max(0, min(99, int(float(q[1]))))
            except ValueError: pc = S["pct"]
            stage("strassen", f"Straßendaten {r['name']}: {q[1]} %" + (f" ({q[3]})" if len(q) > 3 else ""), pc)
        elif line.startswith("STATS "): info["stats"] = line[6:].strip()
    try:
        stage("strassen", f"Straßendaten {r['name']}: starte …", 0)
        rc, tail = run_proc(["java", f"-Xmx{LIMITS_XMX}", "-Dfile.encoding=UTF-8", "-cp", LIMITS_DIR, LIMITS_MAIN, "roads", pbf, d], on_line=on_line)
        if rc != 0: raise RuntimeError(f"Straßendaten {r['name']}: Fehler {rc}: {' | '.join(tail)[-300:]}")
        stage("strassen", f"Straßendaten {r['name']}: packe …", 99)
        import zipfile
        n = 0
        with zipfile.ZipFile(out + ".new", "w", zipfile.ZIP_STORED) as z:   # die Kacheln sind schon gzip-gepackt
            for fn in sorted(os.listdir(d)):
                if fn.startswith("tile3_") and fn.endswith(".json"):
                    if S["stop"]: raise Aborted()
                    z.write(os.path.join(d, fn), fn); n += 1
        os.replace(out + ".new", out)
        st = load_state(); li = st.get("limits_info", {}); li[r["id"]] = {"tiles": n, "stats": info["stats"]}; save_state(limits_info=li)
        v = [x for x in info["stats"].split(",") if x.strip().isdigit()]
        zz = dict(zip(STAT_KEYS, v)) if len(v) == len(STAT_KEYS) else {}
        mengen = f" · {nf(zz['strassen'])} Straßen, {nf(zz['mit_limit'])} mit Tempolimit, {nf(zz['blitzer_osm'])} Blitzer" if zz else ""
        log(f"Straßendaten {r['name']}: {n} Kacheln, {fmt_size(os.path.getsize(out))} in {fmt_dur(time.time() - t0)}{mengen}", cat="strassen")
        return n
    finally:
        shutil.rmtree(d, ignore_errors=True)
        try: os.remove(out + ".new")
        except OSError: pass

def osm_step(cmd, key, text, lo, hi):
    """osmium-Schritt mit Prozentanzeige (--progress); der Balken läuft dabei von lo bis hi Prozent."""
    def on_line(t):
        m = re.search(r"(\d{1,3})\s*%", t)
        if m: stage(key, f"{text}: {m.group(1)} %", int(lo + (hi - lo) * min(100, int(m.group(1))) / 100))
    stage(key, f"{text} …", lo)
    rc, out = run_proc(cmd[:2] + ["--progress"] + cmd[2:], on_line=on_line)
    if rc != 0 and any("progress" in x.lower() for x in out):   # ältere osmium-Version ohne --progress: ohne Prozentanzeige nochmal
        rc, out = run_proc(cmd)
    if rc != 0: raise RuntimeError(f"{text}: Fehler {rc}: {' | '.join(out[-3:])[-200:]}")

def build_pois(r, planet_unused=None):
    """Lädt den Geofabrik-Auszug der Region, filtert die POIs und schreibt public/pois/<id>.tsv.gz. Gibt die Anzahl zurück.
    Aus derselben Datei entstehen danach die Straßendaten (build_limits)."""
    os.makedirs(f"{PUB}/pois", exist_ok=True); os.makedirs(TMP, exist_ok=True)
    pbf = f"{TMP}/{r['id']}.osm.pbf"; flt = f"{TMP}/{r['id']}.poi.osm.pbf"; gj = f"{TMP}/{r['id']}.poi.geojsonseq"
    out = f"{PUB}/pois/{r['id']}.tsv.gz"; name = r["name"]
    try:
        b, sec = fetch_file(f"{GEOFABRIK}/{r['pbf']}-latest.osm.pbf", pbf, "download", f"Download {name}")
        log(f"Download {name}: {fmt_size(b)} in {fmt_dur(sec)}" + (f" ({b / max(sec, 1) / 1e6:.1f} MB/s)" if b else ""), cat="download")
        t0 = time.time()
        osm_step(["osmium", "tags-filter", pbf, *POI_FILTERS, "-o", flt, "--overwrite"], "filter", f"Orte filtern {name} (1 von 2)", 0, 50)
        osm_step(["osmium", "export", flt, "-f", "geojsonseq", "--geometry-types=point,polygon", "-o", gj, "--overwrite"], "filter", f"Orte filtern {name} (2 von 2)", 50, 100)
        stage("schreiben", f"Orte {name}: schreibe …", 0)
        n = 0; size = max(os.path.getsize(gj), 1); seen = [0]
        def counted(fh):
            for ln in fh:
                seen[0] += len(ln); yield ln
        with open(gj, encoding="utf8", errors="replace") as fh, gzip.open(out + ".new", "wt", encoding="utf8") as o:
            o.write("# art\tname\tlat\tlon\tstrasse\tort\n")
            for kind, nm, lat, lon, street, city in poi_rows(counted(fh)):
                o.write(f"{kind}\t{nm}\t{lat}\t{lon}\t{street}\t{city}\n"); n += 1
                if n % 5000 == 0:
                    if S["stop"]: raise Aborted()
                    stage("schreiben", f"Orte {name}: {nf(n)} geschrieben", min(99, int(100 * seen[0] / size)))
        os.replace(out + ".new", out)
        st = load_state(); pc = st.get("poi_count", {}); pc[r["id"]] = n; save_state(poi_count=pc)
        log(f"Orte {name}: {nf(n)} Orte in {fmt_dur(time.time() - t0)}", cat="orte")
        if not S["stop"]:
            try: build_limits(r, pbf)
            except Exception as ex:
                log("FEHLER Straßendaten", r["id"], ex); S["errors"][r["id"]] = "Straßendaten: " + str(ex)[:200]
        return n
    finally:
        freed = 0
        for f in (pbf, flt, gj, out + ".new"):
            try: freed += os.path.getsize(f); os.remove(f)
            except OSError: pass
        if freed: log(f"Aufgeräumt: Arbeitsdateien von {name} entfernt ({fmt_size(freed)})", cat="aufraeumen")

def cleanup_tmp():
    """Reste eines abgebrochenen oder abgestürzten Laufs im Arbeitsordner entfernen (sonst belegen sie Platz)."""
    n = 0; size = 0
    try: names = os.listdir(TMP)
    except OSError: return
    for fn in names:
        p = os.path.join(TMP, fn)
        try:
            if os.path.isdir(p):
                for root, _, fs in os.walk(p):
                    for f in fs: size += os.path.getsize(os.path.join(root, f))
                shutil.rmtree(p, ignore_errors=True)
            else: size += os.path.getsize(p); os.remove(p)
            n += 1
        except OSError: pass
    if n: log(f"Aufgeräumt: {n} Rest(e) vom letzten Lauf entfernt ({fmt_size(size)})", cat="aufraeumen")

def app_sichtbar(regionen, alle, versteckt, nur_gebaute):
    """Was die App sehen darf: ausgeblendete Regionen fallen weg; bei "nur gebaute" bleiben in der Liste "alle" nur Regionen mit fertiger Karte."""
    hid = set(versteckt or [])
    reg = [r for r in regionen if r["id"] not in hid]
    al = [a for a in alle if a["id"] not in hid and (a.get("bereit") or not nur_gebaute)]
    return reg, al

def write_manifest():
    regs = []
    for r in REGIONS:
        mp = f"{PUB}/maps/{r['id']}.pmtiles"
        if not os.path.exists(mp): continue
        rd = []
        for t in tiles_for(r["bbox"]):
            p = f"{PUB}/rd5/{t}.rd5"
            if os.path.exists(p): rd.append({"datei": f"rd5/{t}.rd5", "bytes": os.path.getsize(p), "stand": int(os.path.getmtime(p))})
        regs.append({"id": r["id"], "name": r["name"], "land": r["land"], "landname": LAND_NAMES.get(r["land"], r["land"]), "bbox": r["bbox"],
                     "karte": {"datei": f"maps/{r['id']}.pmtiles", "bytes": os.path.getsize(mp), "stand": int(os.path.getmtime(mp))}, "routen": rd})
    st = load_state()
    for e in regs:
        pp = f"{PUB}/pois/{e['id']}.tsv.gz"
        if os.path.exists(pp):
            e["pois"] = {"datei": f"pois/{e['id']}.tsv.gz", "bytes": os.path.getsize(pp), "stand": int(os.path.getmtime(pp)),
                         "anzahl": st.get("poi_count", {}).get(e["id"], 0)}
    for e in regs:
        lp = f"{PUB}/limits/{e['id']}.zip"
        if os.path.exists(lp):
            li = st.get("limits_info", {}).get(e["id"], {})
            e["strassen"] = {"datei": f"limits/{e['id']}.zip", "bytes": os.path.getsize(lp), "stand": int(os.path.getmtime(lp)),
                             "kacheln": li.get("tiles", 0), "zahlen": li.get("stats", "")}
    alle = [{"id": r["id"], "name": r["name"], "land": r["land"], "landname": LAND_NAMES.get(r["land"], r["land"]), "bbox": r["bbox"],
             "bereit": os.path.exists(f"{PUB}/maps/{r['id']}.pmtiles")} for r in REGIONS]
    regs, alle = app_sichtbar(regs, alle, CFG.get("versteckt", []), CFG.get("nur_gebaute", True))
    m = {"erzeugt": datetime.datetime.now().isoformat(timespec="seconds"), "quelle": st.get("planet", ""), "regionen": regs, "alle": alle}
    cp = f"{PUB}/cameras.csv"
    if os.path.exists(cp):
        m["blitzer"] = {"datei": "cameras.csv", "bytes": os.path.getsize(cp), "stand": int(os.path.getmtime(cp)), "anzahl": st.get("scdb", {}).get("anzahl", 0)}
    json.dump(m, open(f"{PUB}/manifest.json.new", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    os.replace(f"{PUB}/manifest.json.new", f"{PUB}/manifest.json")


# ---------- Gebaute Region vom Server löschen ----------
def region_delete(rid):
    """Karte, Orte und Straßendaten einer Region vom Server löschen. Routen-Kacheln (.rd5) nur, wenn keine andere gebaute Region sie braucht.
    Gibt die freigegebenen Bytes zurück. Handys, die die Region schon geladen haben, behalten sie."""
    r = BYID.get(rid)
    if not r: raise ValueError("Unbekannte Region.")
    if S["state"] == "running": raise ValueError("Während eines Baus nicht möglich. Bitte nach dem Bau noch einmal versuchen.")
    with PEND_LOCK:
        if rid in PENDING: raise ValueError("Die Region steht in der Warteschlange.")
    freed = 0
    def rm(p):
        nonlocal freed
        try: sz = os.path.getsize(p); os.remove(p); freed += sz
        except OSError: pass
    rm(f"{PUB}/maps/{rid}.pmtiles"); rm(f"{PUB}/pois/{rid}.tsv.gz"); rm(f"{PUB}/limits/{rid}.zip")
    used = set()
    for o in REGIONS:
        if o["id"] != rid and os.path.exists(f"{PUB}/maps/{o['id']}.pmtiles"): used |= set(tiles_for(o["bbox"]))
    for t in tiles_for(r["bbox"]):
        if t not in used: rm(f"{PUB}/rd5/{t}.rd5")
    st = load_state()
    pc = st.get("poi_count", {}); pc.pop(rid, None); li = st.get("limits_info", {}); li.pop(rid, None); nd = st.get("needed", {}); nd.pop(rid, None)
    save_state(poi_count=pc, limits_info=li, needed=nd)
    with CFG_LOCK:
        CFG["regions"] = [i for i in CFG["regions"] if i != rid]; save_cfg()
    S["errors"].pop(rid, None)
    write_manifest()
    log(f"Region gelöscht: {r['name']} ({fmt_size(freed)} frei)", cat="aufraeumen")
    return freed

# ---------- Europa-Liste aus der Geofabrik-Übersicht ----------
_PBF_URL = re.compile(r"^https?://[^/]+/(.+)-latest\.osm\.pbf$")

def _pbf_path(f):
    u = (f.get("properties", {}).get("urls") or {}).get("pbf") or ""
    m = _PBF_URL.match(u)
    return m.group(1) if m else ""

def _bbox(f):
    """Rechteck (West, Süd, Ost, Nord) aus dem Umriss. Teile, die mehr als 3 Grad vom größten Landesteil entfernt liegen (Spitzbergen,
    Kanaren, Azoren), zählen nicht mit, sonst würde z. B. Spitzbergen das Rechteck von Norwegen riesig machen."""
    g = f.get("geometry") or {}
    polys = g.get("coordinates") if g.get("type") == "MultiPolygon" else [g.get("coordinates")] if g.get("type") == "Polygon" else []
    boxes = []
    for pg in polys:
        try: ring = pg[0]; xs = [float(p[0]) for p in ring]; ys = [float(p[1]) for p in ring]
        except (TypeError, IndexError, ValueError): continue
        if xs and ys: boxes.append((min(xs), min(ys), max(xs), max(ys)))
    if not boxes: return None
    big = max(boxes, key=lambda b: max(b[2] - b[0], 0.01) * max(b[3] - b[1], 0.01))
    near = (big[0] - 3, big[1] - 3, big[2] + 3, big[3] + 3)
    keep = [b for b in boxes if not (b[2] < near[0] or b[0] > near[2] or b[3] < near[1] or b[1] > near[3])]
    w = min(b[0] for b in keep); s = min(b[1] for b in keep); e = max(b[2] for b in keep); n = max(b[3] for b in keep)
    return [math.floor(w * 100) / 100, math.floor(s * 100) / 100, math.ceil(e * 100) / 100, math.ceil(n * 100) / 100]

def regions_from_index(index, base=None):
    """Aus der Geofabrik-Übersicht (index-v1.json) die Regionen der Länder in WANTED. Pro Land die Unterregionen, die Geofabrik anbietet;
    hat ein Land keine, bleibt das Land als eine Region. Länder, die schon in der Basisliste stehen, werden übergangen.
    Gibt (Regionen, fehlende Länder) zurück."""
    base = BASE_REGIONS if base is None else base
    have_lands = {r["land"] for r in base}; used = {r["id"] for r in base}
    by_id = {}; kids = collections.defaultdict(list)
    for f in index.get("features", []):
        p = f.get("properties", {}); i = p.get("id")
        if not i: continue
        by_id[i] = f
        if p.get("parent"): kids[p["parent"]].append(f)
    out = []; missing = []
    for iso, deu, cands in WANTED:
        if iso in have_lands: continue
        cid = next((c for c in cands if c in by_id), None)
        if not cid: missing.append(deu); continue
        parts = [k for k in kids.get(cid, []) if _pbf_path(k) and _bbox(k)]
        whole = not parts
        if whole: parts = [by_id[cid]]
        for f in sorted(parts, key=lambda x: str(x["properties"].get("name", ""))):
            pbf = _pbf_path(f); bb = _bbox(f)
            if not pbf or not bb: continue
            gid = re.sub(r"[^a-z0-9-]", "", str(f["properties"]["id"]).lower())
            rid = iso.lower() if whole else f"{iso.lower()}-{gid}"
            k = 2
            while rid in used: rid = f"{rid}-{k}"; k += 1
            used.add(rid)
            out.append({"id": rid, "name": deu if whole else str(f["properties"].get("name", gid)), "land": iso, "bbox": bb, "pbf": pbf})
    return out, missing

REG_LOCK = threading.Lock()
def regions_fetch():
    """Holt die Geofabrik-Übersicht, erzeugt daraus die Europa-Liste, speichert sie und lädt sie. Gibt (Anzahl, fehlende Länder) zurück."""
    if S["state"] == "running": raise ValueError("Während eines Baus nicht möglich. Bitte nach dem Bau noch einmal drücken.")
    if not REG_LOCK.acquire(blocking=False): raise ValueError("Die Liste wird gerade geholt.")
    try:
        url = f"{GEOFABRIK}/index-v1.json"
        log(f"Regionsliste: hole {url} …", cat="download")
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=120) as r: raw = r.read(120_000_000)
            idx = json.loads(raw)
        except Exception as ex:
            raise RuntimeError(f"Geofabrik nicht erreichbar oder Antwort unlesbar: {str(ex)[:200]}")
        out, missing = regions_from_index(idx)
        if not out: raise RuntimeError("In der Geofabrik-Übersicht wurde kein passendes Land gefunden (Format geändert?).")
        os.makedirs(DATA, exist_ok=True)
        tmp = EXTRA_FILE + ".new"
        json.dump(out, open(tmp, "w", encoding="utf-8"), ensure_ascii=False, indent=1); os.replace(tmp, EXTRA_FILE)
        reload_regions(); write_manifest()
        log(f"Regionsliste: {len(out)} Regionen erzeugt, {len({r['land'] for r in out})} Länder" + (f"; nicht gefunden: {', '.join(missing)}" if missing else ""), cat="download")
        return len(out), missing
    finally:
        REG_LOCK.release()

def regions_info():
    try: stand = int(os.path.getmtime(EXTRA_FILE)); n = len(json.load(open(EXTRA_FILE, encoding="utf-8")))
    except Exception: stand = 0; n = 0
    return {"extra": n, "stand": stand, "gesamt": len(REGIONS)}

# ---------- SCDB-Blitzerliste (GPX, von Hand im Admin hochgeladen) ----------
SCDB_DIR = f"{DATA}/scdb"; SCDB_LOCK = threading.Lock(); SCDB_LINK = "https://www.scdb.info/my/"; SCDB_REMIND_DAYS = 30

def scdb_info():
    st = load_state().get("scdb", {})
    files = sorted(os.listdir(SCDB_DIR)) if os.path.isdir(SCDB_DIR) else []
    stand = int(st.get("stand", 0))
    return {"dateien": files, "anzahl": st.get("anzahl", 0), "gelesen": st.get("gelesen", 0), "stand": stand, "link": SCDB_LINK,
            "erinnern_tage": SCDB_REMIND_DAYS, "alter_tage": int((time.time() - stand) // 86400) if stand else None,
            "faellig": bool(stand and time.time() - stand >= SCDB_REMIND_DAYS * 86400), "fehler": st.get("fehler", "")}

def scdb_store(name, raw):
    """Eine hochgeladene Datei ablegen: GPX direkt, ZIP wird ausgepackt (nur .gpx-Dateien). Gibt die Zahl der gespeicherten GPX-Dateien zurück."""
    import io, zipfile
    os.makedirs(SCDB_DIR, exist_ok=True); n = 0
    def put(fn, data):
        fn = re.sub(r"[^A-Za-z0-9._-]", "_", os.path.basename(fn))[-80:]
        if not fn.lower().endswith(".gpx") or (b"<wpt" not in data[:5_000_000] and b"<gpx" not in data[:2000]): return 0
        with open(os.path.join(SCDB_DIR, fn), "wb") as f: f.write(data)
        return 1
    if raw[:2] == b"PK":
        with zipfile.ZipFile(io.BytesIO(raw)) as z:
            for i in z.infolist():
                if i.is_dir() or not i.filename.lower().endswith(".gpx") or i.file_size > 60_000_000: continue
                n += put(i.filename, z.read(i))
    else: n += put(name if name.lower().endswith(".gpx") else name + ".gpx", raw)
    return n

def scdb_rebuild():
    """Alle abgelegten GPX-Dateien mit dem Import-Code der App zusammenführen (Doppelte raus) -> public/cameras.csv."""
    with SCDB_LOCK:
        files = sorted(os.path.join(SCDB_DIR, f) for f in os.listdir(SCDB_DIR)) if os.path.isdir(SCDB_DIR) else []
        cp = f"{PUB}/cameras.csv"
        if not files:
            try: os.remove(cp)
            except OSError: pass
            save_state(scdb={}); write_manifest(); return 0
        if not os.path.isdir(LIMITS_DIR): raise RuntimeError("Ordner limits fehlt im Container")
        r = subprocess.run(["java", "-Xmx512m", "-Dfile.encoding=UTF-8", "-cp", LIMITS_DIR, LIMITS_MAIN, "cameras", cp + ".new", *files], capture_output=True, text=True, encoding="utf8", errors="replace")
        if r.returncode != 0: raise RuntimeError("Blitzerliste: " + (r.stderr or r.stdout).strip()[-300:])
        m = re.search(r"CAMERAS (\d+) PARSED (\d+)", r.stdout)
        if not m: raise RuntimeError("Blitzerliste: keine Ausgabe vom Import")
        if int(m.group(1)) == 0:
            try: os.remove(cp + ".new")
            except OSError: pass
            raise ValueError("In den Dateien wurden keine Blitzer gefunden (GPX mit Wegpunkten erwartet).")
        os.replace(cp + ".new", cp)
        save_state(scdb={"anzahl": int(m.group(1)), "gelesen": int(m.group(2)), "stand": int(time.time()), "fehler": ""})
        write_manifest(); log(f"SCDB-Liste: {nf(m.group(1))} Blitzer aus {len(files)} Datei(en)", cat="blitzer")
        return int(m.group(1))

LOGINLOG = {}
NEEDLOG = {}          # damit dieselbe Handy-Meldung nicht alle paar Sekunden im Protokoll steht
PENDING = []          # Regionen, die das Handy angefordert hat und die noch gebaut werden müssen
PEND_LOCK = threading.Lock()
def needed_map():
    return load_state().get("needed", {})
def mark_needed(ids):
    nd = needed_map(); now = int(time.time())
    for i in ids: nd[i] = now
    save_state(needed=nd)
def region_state(i):
    if S["state"] == "running" and S["current"] == BYID[i]["name"]: return "baut"
    if os.path.exists(f"{PUB}/maps/{i}.pmtiles"): return "fertig"
    return "wartet" if (i in PENDING or S["state"] == "running") else "neu"
def scheduled_ids():
    """Regionen, die das Handy in den letzten NEED_DAYS Tagen gemeldet hat. Wurde noch nie etwas gemeldet: alle aktiven."""
    nd = needed_map()
    if not nd: return list(CFG["regions"])
    lim = time.time() - NEED_DAYS * 86400
    return [r["id"] for r in REGIONS if nd.get(r["id"], 0) >= lim and os.path.exists(f"{PUB}/maps/{r['id']}.pmtiles")]
def request_build(ids):
    """Handy meldet Regionen; fehlende werden gebaut (sofort oder nach dem laufenden Lauf)."""
    ids = [i for i in ids if i in BYID]; mark_needed(ids)
    todo = [i for i in ids if not os.path.exists(f"{PUB}/maps/{i}.pmtiles")]
    if not todo: return
    with PEND_LOCK:
        for i in todo:
            if i not in PENDING: PENDING.append(i)
    kick_pending()
def kick_pending():
    with PEND_LOCK:
        if S["state"] == "running" or not PENDING: return
        ids = list(PENDING); PENDING.clear()
    start_run(ids)

RUN_LOCK = threading.Lock()
def run_once(ids, only=None):
    if not RUN_LOCK.acquire(blocking=False): return
    try:
        os.makedirs(f"{PUB}/maps", exist_ok=True); os.makedirs(TMP, exist_ok=True)
        regions = [BYID[i] for i in ids if i in BYID]
        S.update(state="running", total=len(regions), done=0, started=int(time.time()), stop=False, detail="Starte …", errors={}, current=None,
                 pct=None, frac=0.0, step=None, beat=time.time(), stages=STAGES_POI if only == "poi" else STAGES_FULL)
        log(f"Start: {len(regions)} Region(en), {'nur Straßendaten und Orte' if only == 'poi' else 'alles'}" + (": " + ", ".join(r["name"] for r in regions) if len(regions) <= 4 else ""))
        cleanup_tmp()
        if only == "poi": planet = None
        else:
            try: planet = find_planet()
            except Exception as ex:
                log("FEHLER:", ex); S["errors"]["_"] = str(ex); return
            logv("Weltkarte:", planet); save_state(planet=planet)
        for r in regions:
            if S["stop"]: break
            S["current"] = r["name"]; S["frac"] = 0.0; S["pct"] = None; out = f"{PUB}/maps/{r['id']}.pmtiles"; tmp = f"{TMP}/{r['id']}.pmtiles"
            logv("Baue", r["name"] + (" (nur Straßendaten und Orte)" if only == "poi" else ""))
            if only != "poi":
                try:
                    t1 = time.time(); extract(planet, tmp, r["bbox"], r["name"])
                    if S["stop"]: raise Aborted()
                    os.replace(tmp, out); log(f"Karte {r['name']}: {fmt_size(os.path.getsize(out))} in {fmt_dur(time.time() - t1)}", cat="karte")
                except Exception as ex:
                    log("FEHLER Karte", r["id"], ex); S["errors"][r["id"]] = str(ex)
                finally:
                    if os.path.exists(tmp): os.remove(tmp)
                tl = tiles_for(r["bbox"])
                for i, t in enumerate(tl):
                    if S["stop"]: break
                    try: fetch_rd5(t, i, len(tl))
                    except Exception as ex: log("FEHLER", ex); S["errors"][r["id"]] = str(ex)
            if not S["stop"]:
                try: build_pois(r)
                except Exception as ex:
                    log("FEHLER POI", r["id"], ex); S["errors"][r["id"]] = "POI: " + str(ex)[:200]
            if S["stop"]: break
            S["done"] += 1; S["frac"] = 0.0; S["pct"] = None; write_manifest()
        else:
            if only != "poi": save_state(last_run=int(time.time()))
        write_manifest()
        dur = fmt_dur(time.time() - (S["started"] or time.time()))
        if S["stop"]: log(f"Abgebrochen nach {dur} ({S['done']} von {S['total']} Regionen fertig)")
        else: log(f"Fertig: {S['done']} von {S['total']} Regionen in {dur}" + (f", {len(S['errors'])} mit Fehlern" if S["errors"] else ""))
    except Aborted:
        log(f"Abgebrochen ({S['done']} von {S['total']} Regionen fertig)")
        try: write_manifest()
        except Exception as ex: log("Manifest-Fehler:", ex)
    except Exception as ex:
        log("FEHLER Lauf:", ex)
    finally:
        try:
            save_state(last_result={"ende": int(time.time()), "dauer": int(time.time() - (S["started"] or time.time())), "fertig": S["done"], "gesamt": S["total"],
                                    "nur": only or "", "abgebrochen": bool(S["stop"]), "fehler": dict(S["errors"])})
        except Exception as ex: log("Ergebnis speichern:", ex)
        try:
            dur = fmt_dur(time.time() - (S["started"] or time.time()))
            if S["stop"]: NOTES.add("lauf_fehler", f"Update abgebrochen ({S['done']} von {S['total']} Regionen fertig).")
            elif S["errors"]: NOTES.add("lauf_fehler", f"Update fertig mit Fehlern: {len(S['errors'])} von {S['total']} Regionen ({dur}).")
            else: NOTES.add("lauf_ok", f"Update fertig: {S['done']} von {S['total']} Regionen in {dur}.")
        except Exception as ex: log("Meldung Update:", ex)
        S.update(state="idle", current=None, detail="", pct=None, frac=0.0, step=None); RUN_LOCK.release()
        try: kick_pending()
        except Exception as ex: log("Warteschlange-Fehler:", ex)

def start_run(ids, only=None):
    if S["state"] == "running": return False
    S["state"] = "running"; threading.Thread(target=run_once, args=(ids, only), daemon=True).start(); return True

def scheduler():
    while True:
        time.sleep(60)
        try:
            last = load_state().get("last_run", 0)
            due = time.time() - last >= int(CFG["every_days"]) * 86400
            if S["state"] == "idle" and due and in_window(datetime.datetime.now().hour): start_run(scheduled_ids())
            bs_daily()
            notify_tick()
            if time.time() - DEV_CLEAN_AT[0] > 86400: dev_cleanup()
        except Exception as ex: log("Zeitplan-Fehler:", ex)

# ---------- Routenberechnung (BRouter) + Touren + Planer ----------
BR_JAR = os.environ.get("BROUTER_JAR", "/opt/brouter/brouter.jar"); BR_PORT = int(os.environ.get("BROUTER_PORT", "17777"))
BR_PROF = f"{DATA}/brouter/profiles2"; BR_PROC = {"p": None}
PROFILES = ["motorrad_landstrasse", "motorrad_kurvig", "motorrad_schnell", "motorrad_schnellste", "auto"]
AVOID_KEYS = ["motorway", "ferry", "toll", "unpaved"]   # "Vermeiden"-Schalter, wie in der App

def avoid_clean(v):
    """Liste oder Komma-Text -> sortierte Liste gültiger Schalter."""
    if isinstance(v, str): v = v.split(",")
    return [k for k in AVOID_KEYS if isinstance(v, (list, tuple)) and k in [str(x).strip() for x in v]]

def variant_profile(prof, avoid):
    """Profilname für die Schalter: legt <profil>__<code>.brf an (BRouter merkt sich Profile je Dateiname)."""
    avoid = avoid_clean(avoid)
    if not avoid: return prof
    base = os.path.join(BR_PROF, prof + ".brf")
    if not os.path.exists(base): return prof
    name = prof + "__" + "".join(k[0] + ("1" if k in avoid else "0") for k in AVOID_KEYS)
    out = os.path.join(BR_PROF, name + ".brf")
    if not os.path.exists(out) or os.path.getmtime(out) < os.path.getmtime(base):
        txt = open(base, encoding="utf-8").read()
        for k in AVOID_KEYS:
            txt = re.sub(r"(?m)^assign avoid_%s\s*=\s*\d.*$" % k, "assign avoid_%s = %d" % (k, 1 if k in avoid else 0), txt)
        open(out, "w", encoding="utf-8").write(txt)
    return name

def prepare_brouter_profiles():
    """Profile (mitgeliefert) in /data kopieren; lookups.dat frisch von brouter.de, damit sie zu den Routendaten passt."""
    import shutil
    os.makedirs(BR_PROF, exist_ok=True); os.makedirs(f"{DATA}/brouter/custom", exist_ok=True)
    src = "/opt/brouter/profiles2"
    if os.path.isdir(src):
        for n in os.listdir(src):
            d = os.path.join(BR_PROF, n)
            if n.endswith(".brf") or not os.path.exists(d): shutil.copy(os.path.join(src, n), d)
    for n in os.listdir(BR_PROF):   # alte Vermeiden-Varianten verwerfen (Profile wurden gerade erneuert)
        if "__" in n and n.endswith(".brf"):
            try: os.remove(os.path.join(BR_PROF, n))
            except OSError: pass
    try:
        tmp = os.path.join(BR_PROF, "lookups.dat.new")
        r = subprocess.run(["curl", "-fsSL", "-A", UA, "-o", tmp, "https://brouter.de/brouter/profiles2/lookups.dat"], env=sub_env(), capture_output=True, timeout=120)
        if r.returncode == 0 and os.path.getsize(tmp) > 1000: os.replace(tmp, os.path.join(BR_PROF, "lookups.dat")); log("lookups.dat aktualisiert")
        elif os.path.exists(tmp): os.remove(tmp)
    except Exception as ex: log("lookups.dat nicht geholt (nehme mitgelieferte):", ex)

def start_brouter():
    p = BR_PROC["p"]
    if p and p.poll() is None: return
    if not os.path.exists(BR_JAR): return
    os.makedirs(f"{PUB}/rd5", exist_ok=True)
    mem = max(512, min(16384, int(CFG.get("br_mem_mb", 4096)))); thr = max(1, min(32, int(CFG.get("br_threads", 8))))
    BR_PROC["p"] = subprocess.Popen(["java", f"-Xmx{mem}M", "-DmaxRunningTime=180", "-cp", BR_JAR, "btools.server.RouteServer",
        f"{PUB}/rd5", BR_PROF, f"{DATA}/brouter/custom", str(BR_PORT), str(thr), "127.0.0.1"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    BR_PROC.update(since=int(time.time()), mem=mem, threads=thr)
    log(f"BRouter gestartet (Port {BR_PORT}, {mem} MB, {thr} Berechnungen gleichzeitig)", cat="karte")
    threading.Thread(target=warm_brouter, daemon=True).start()

def stop_brouter():
    p = BR_PROC["p"]
    if p and p.poll() is None:
        p.terminate()
        try: p.wait(10)
        except Exception: p.kill()

def warm_brouter():
    """Nach dem Start einmal eine kurze Probestrecke rechnen, damit die erste echte Anfrage nicht auf das Laden der Routendaten wartet."""
    try:
        time.sleep(3)
        names = sorted(n for n in os.listdir(f"{PUB}/rd5") if n.endswith(".rd5")) if os.path.isdir(f"{PUB}/rd5") else []
        if not names: return
        m = re.match(r"E(\d+)_N(\d+)\.rd5$", names[0])
        if not m: return
        lon, lat = int(m.group(1)) + 2.5, int(m.group(2)) + 2.5
        url = f"http://127.0.0.1:{BR_PORT}/brouter?" + urllib.parse.urlencode({"lonlats": f"{lon},{lat}|{lon + 0.02},{lat + 0.02}", "profile": PROFILES[0], "alternativeidx": "0", "format": "geojson"})
        try: urllib.request.urlopen(url, timeout=120).read()
        except Exception: pass
    except Exception: pass

def bs_avoid(body, ll, prof):
    """Liegt eine gültige Sperrung auf der Route, mit Sperrzonen neu rechnen. Fügt dem Ergebnis "sperrung" hinzu:
    umfahren = Anzahl umfahrener Sperrungen (-1 = nicht umfahrbar), bleibt = Texte der Sperrungen auf der Route."""
    try:
        j = json.loads(body); lst = json.load(open(BS_LIST, encoding="utf-8"))
        ref = lst.get("ref") if lst.get("test") else int(time.time())
        sites = BSM.closed_now(lst.get("items", []), ref or int(time.time()))
        if not sites: return body
        route = [[c[1], c[0]] for c in j["features"][0]["geometry"]["coordinates"]]
        h1 = BSM.hits(route, sites)
        if not h1: return body
        poly = BSM.nogo_param(sites, route)
        umf = -1
        if poly:
            url = f"http://127.0.0.1:{BR_PORT}/brouter?" + urllib.parse.urlencode({"lonlats": ll, "profile": prof, "alternativeidx": "0", "format": "geojson", "polylines": poly})
            try:
                with urllib.request.urlopen(url, timeout=200) as r: j2 = json.loads(r.read())
                route2 = [[c[1], c[0]] for c in j2["features"][0]["geometry"]["coordinates"]]
                h2 = BSM.hits(route2, sites)
                if len(h2) < len(h1): j, h1, umf = j2, h2, len(h1) - len(h2)
            except Exception: pass
        j["sperrung"] = {"umfahren": umf, "bleibt": [x.get("t", "").split("\n")[0][:80] for x in h1[:3]], "n": len(h1)}
        log("Webplaner: %s Sperrung(en) %s" % (len(h1) if umf < 0 else umf, "nicht umfahrbar" if umf < 0 else "umfahren"), cat="handy")
        return json.dumps(j).encode()
    except Exception as e:
        log("Webplaner: Sperrungs-Prüfung fehlgeschlagen:", str(e)[:100])
        return body

def clean_nogos(v, limit=80):
    """Sperrkreise für BRouter ("lon,lat,radius|..."): nur gültige Zahlen, Radius 50 bis 5000 m, höchstens [limit] Kreise. Leer, wenn nichts Brauchbares."""
    out = []
    for part in str(v or "").split("|"):
        q = part.split(",")
        if len(q) != 3: continue
        try: lo, la, r = float(q[0]), float(q[1]), float(q[2])
        except ValueError: continue
        if not (-180 <= lo <= 180 and -90 <= la <= 90 and 50 <= r <= 5000): continue
        out.append(f"{lo:.6f},{la:.6f},{r:.0f}")
        if len(out) >= limit: break
    return "|".join(out)

RT_LOG = collections.deque(maxlen=200)   # letzte Routenberechnungen (nur im Arbeitsspeicher)
RT_STAT = {"tag": "", "n": 0, "ms": 0, "max": 0, "langsam": 0, "aktiv": 0, "fehler": 0}
RT_LOCK = threading.Lock()
RT_SLOW_MS = 5000

def rt_start():
    with RT_LOCK:
        RT_STAT["aktiv"] += 1

def rt_done(prof, pts, code, ms, body):
    km = None
    if code == 200 and body:
        m = re.search(rb'"track-length"\s*:\s*"?(\d+)', body[:6000])
        if m: km = round(int(m.group(1)) / 1000.0, 1)
    with RT_LOCK:
        RT_STAT["aktiv"] = max(0, RT_STAT["aktiv"] - 1)
        day = datetime.date.today().isoformat()
        if RT_STAT["tag"] != day: RT_STAT.update(tag=day, n=0, ms=0, max=0, langsam=0, fehler=0)
        RT_STAT["n"] += 1; RT_STAT["ms"] += ms; RT_STAT["max"] = max(RT_STAT["max"], ms)
        if ms >= RT_SLOW_MS: RT_STAT["langsam"] += 1
        if code != 200: RT_STAT["fehler"] += 1
        RT_LOG.append({"ts": int(time.time()), "prof": prof.split("__")[0], "punkte": pts, "km": km, "ms": ms, "code": code})

def routing_info():
    with RT_LOCK:
        day = RT_STAT["tag"] == datetime.date.today().isoformat(); n = RT_STAT["n"] if day else 0
        heute = {"n": n, "avg_ms": round(RT_STAT["ms"] / n) if n else 0, "max_ms": RT_STAT["max"] if day else 0,
                 "langsam": RT_STAT["langsam"] if day else 0, "fehler": RT_STAT["fehler"] if day else 0}
        last = list(RT_LOG)[-20:][::-1]; aktiv = RT_STAT["aktiv"]
    p = BR_PROC["p"]
    return {"aktiv": aktiv, "heute": heute, "langsam_ab_ms": RT_SLOW_MS, "letzte": last,
            "br": {"laeuft": bool(p and p.poll() is None), "seit": BR_PROC.get("since", 0), "mem_mb": BR_PROC.get("mem", 0), "threads": BR_PROC.get("threads", 0)},
            "cfg": {"mem_mb": int(CFG.get("br_mem_mb", 4096)), "threads": int(CFG.get("br_threads", 8))}}

def route_proxy(query):
    """Reicht /api/route an BRouter weiter und misst die Dauer. Gibt (Statuscode, Bytes) zurück."""
    t0 = time.time(); q = urllib.parse.parse_qs(query)
    prof = q.get("profile", [PROFILES[0]])[0]; pts = q.get("lonlats", [""])[0].count("|") + 1
    rt_start()
    code, body = 500, b""
    try:
        code, body = route_proxy_raw(query)
        return code, body
    finally:
        rt_done(prof if prof in PROFILES else PROFILES[0], pts, code, int((time.time() - t0) * 1000), body)

def route_proxy_raw(query):
    """Reicht /api/route an BRouter weiter. Gibt (Statuscode, Bytes) zurück."""
    q = urllib.parse.parse_qs(query)
    ll = q.get("lonlats", [""])[0]; prof = q.get("profile", [PROFILES[0]])[0]
    if prof not in PROFILES: prof = PROFILES[0]
    prof = variant_profile(prof, q.get("avoid", [""])[0])
    if not ll or ll.count("|") < 1: return 400, json.dumps({"fehler": "Mindestens Start und Ziel nötig."}).encode()
    start_brouter()
    par = {"lonlats": ll, "profile": prof, "alternativeidx": "0", "format": "geojson"}
    ng = clean_nogos(q.get("nogos", [""])[0])
    if ng: par["nogos"] = ng   # Rundtour: Hinweg sperren, damit der Rückweg eine andere Strecke nimmt
    url = f"http://127.0.0.1:{BR_PORT}/brouter?" + urllib.parse.urlencode(par)
    try:
        with urllib.request.urlopen(url, timeout=200) as r: body = r.read()
        if q.get("sperr", [""])[0] == "1": body = bs_avoid(body, ll, prof)
        return 200, body
    except urllib.error.HTTPError as e:
        return 422, json.dumps({"fehler": e.read().decode("utf8", "replace").strip()[:300] or f"BRouter HTTP {e.code}"}).encode()
    except Exception as ex:
        return 503, json.dumps({"fehler": "BRouter antwortet nicht (startet noch?): " + str(ex)[:120]}).encode()

# ---------- Baustellen und Sperrungen (Mobilithek) ----------
BSD = f"{DATA}/baustellen"; BS_LIST = f"{BSD}/liste.json"; BS_META = f"{BSD}/meta.json"; BS_GEOM = f"{BSD}/geom.json"; BS_TEST = f"{BSD}/test.xml"
BS_CFG = f"{DATA}/mobilithek/config.json"; BS_P12 = f"{DATA}/mobilithek/client.p12"
BSS = {"state": "idle", "pct": 0, "text": "", "stop": False}
BS_LOCK = threading.Lock()

def bs_meta():
    try: return json.load(open(BS_META, encoding="utf-8"))
    except Exception: return {}

def bs_source():
    """live (Abo + Zertifikat vorhanden), test (Testdatei) oder none."""
    try:
        c = json.load(open(BS_CFG, encoding="utf-8"))
        if c.get("abo") and c.get("pass") is not None and os.path.isfile(BS_P12): return "live", {"abo": str(c["abo"]).strip(), "pass": str(c["pass"]), "p12": BS_P12}
    except Exception: pass
    if os.path.isfile(BS_TEST): return "test", None
    return "none", None

def bs_status():
    m = bs_meta(); q, _ = bs_source()
    return {"state": BSS["state"], "pct": BSS["pct"], "text": BSS["text"], "quelle": q, "stand": m.get("stand", 0), "ref": m.get("ref", 0), "n": m.get("n", 0),
            "n_gesperrt": m.get("n_c", 0), "geom": m.get("geom", 0), "geprueft": m.get("geprueft", 0), "fehler": m.get("fehler", ""), "bytes": m.get("bytes", 0),
            "letzte_quelle": m.get("quelle", "")}

def bs_set(pct, text):
    BSS["pct"] = max(0, min(100, int(pct))); BSS["text"] = text

def bs_route(a, b):
    """Echte Straße zwischen zwei Punkten über BRouter (None, wenn nicht möglich, zum Beispiel ohne Routendaten für die Gegend)."""
    start_brouter()
    ll = f"{a[1]},{a[0]}|{b[1]},{b[0]}"
    url = f"http://127.0.0.1:{BR_PORT}/brouter?" + urllib.parse.urlencode({"lonlats": ll, "profile": "kuerzeste", "alternativeidx": "0", "format": "geojson"})
    try:
        with urllib.request.urlopen(url, timeout=20) as r: j = json.loads(r.read())
        return [[c[1], c[0]] for c in j["features"][0]["geometry"]["coordinates"]]
    except Exception: return None

def bs_run(force=False):
    """Abrufen (oder Testdatei lesen), umrechnen, Straßen suchen, speichern. Läuft im eigenen Thread."""
    if not BS_LOCK.acquire(blocking=False): return False
    BSS.update(state="running", stop=False)
    def work():
        t0 = time.time(); m = bs_meta()
        try:
            os.makedirs(BSD, exist_ok=True)
            q, cfg = bs_source()
            if q == "none": raise RuntimeError("Keine Quelle: weder Abo mit Zertifikat unter /data/mobilithek noch Testdatei /data/baustellen/test.xml.")
            src = BS_TEST; lm = m.get("lm", "")
            if q == "live":
                bs_set(3, "Mobilithek abrufen …"); log("Baustellen: Abruf bei der Mobilithek", cat="karte")
                src = f"{BSD}/live.xml"
                st, lm2 = BSM.fetch_live(cfg, src, "" if force else lm)
                if st == "unveraendert":
                    m.update(geprueft=int(time.time()), fehler=""); json.dump(m, open(BS_META, "w", encoding="utf-8")); log("Baustellen: unverändert seit dem letzten Abruf", cat="karte"); bs_set(100, "Unverändert."); return
                if st != "neu": raise RuntimeError(st)
                lm = lm2
            if BSS["stop"]: raise RuntimeError("Abgebrochen.")
            bs_set(8, "Datei lesen …")
            items, pub = BSM.parse(src, progress=lambda f: bs_set(8 + f * 42, "Datei lesen …"))
            ref = int(time.time()) if q == "live" else (pub or int(time.time()))
            act = BSM.active(items, ref)
            try: cache = json.load(open(BS_GEOM, encoding="utf-8"))
            except Exception: cache = {}
            bs_set(50, f"Straßen suchen (0 von {len(act)}) …")
            ok, fail = BSM.add_geometry(act, bs_route, cache, progress=lambda f: bs_set(50 + f * 45, f"Straßen suchen ({int(f * len(act))} von {len(act)}) …"), stop=lambda: BSS["stop"])
            if BSS["stop"]: raise RuntimeError("Abgebrochen.")
            bs_set(96, "Speichern …")
            if len(cache) > 20000: cache = dict(list(cache.items())[-10000:])
            json.dump(cache, open(BS_GEOM + ".new", "w", encoding="utf-8")); os.replace(BS_GEOM + ".new", BS_GEOM)
            out = {"stand": int(time.time()), "ref": ref, "test": q != "live", "n": len(act), "items": act}
            raw = json.dumps(out, ensure_ascii=False, separators=(",", ":"))
            open(BS_LIST + ".new", "w", encoding="utf-8").write(raw); os.replace(BS_LIST + ".new", BS_LIST)
            m = {"stand": out["stand"], "ref": ref, "n": len(act), "n_c": sum(1 for x in act if x["k"] == "c"), "geom": ok, "geprueft": int(time.time()), "lm": lm,
                 "fehler": "", "bytes": len(raw.encode("utf-8")), "quelle": q}
            json.dump(m, open(BS_META, "w", encoding="utf-8"))
            if q == "live":
                try: os.remove(src)
                except Exception: pass
            log(f"Baustellen: {len(act)} Meldungen ({m['n_c']} Sperrungen), Straße gefunden bei {ok}, gerade Linie bei {fail}, {round(time.time() - t0)} s, Quelle {q}", cat="karte")
            bs_set(100, "Fertig.")
        except Exception as ex:
            m = bs_meta(); m["fehler"] = str(ex)[:300]; m["geprueft"] = int(time.time())
            try: json.dump(m, open(BS_META, "w", encoding="utf-8"))
            except Exception: pass
            log("Baustellen FEHLER:", ex); bs_set(0, "Fehler: " + str(ex)[:200])
            if str(ex) != "Abgebrochen.": NOTES.add("baustellen", "Baustellen-Abruf fehlgeschlagen: " + str(ex)[:160], cool=6 * 3600)
        finally:
            BSS["state"] = "idle"; BS_LOCK.release()
    threading.Thread(target=work, daemon=True).start(); return True

CERT_AT = [0.0]

def cert_days_left():
    """Tage bis zum Ablauf des Mobilithek-Zertifikats (None, wenn nicht lesbar). Nutzt keytool aus dem Java des Containers."""
    q, cfg = bs_source()
    if q != "live": return None
    try:
        r = subprocess.run(["keytool", "-list", "-v", "-keystore", cfg["p12"], "-storetype", "PKCS12", "-storepass", cfg["pass"]],
                           capture_output=True, text=True, timeout=30)
    except Exception: return None
    months = {m: i + 1 for i, m in enumerate("Jan Feb Mar Apr May Jun Jul Aug Sep Oct Nov Dec".split())}
    best = None
    for m in re.finditer(r"until:\s+\w{3}\s+(\w{3})\s+(\d{1,2})\s+[\d:]+\s+\S+\s+(\d{4})", r.stdout or ""):
        try: t = datetime.date(int(m.group(3)), months[m.group(1)], int(m.group(2)))
        except Exception: continue
        d = (t - datetime.date.today()).days
        best = d if best is None else min(best, d)
    return best

def notify_tick():
    """Jede Minute: Zustände prüfen, die eine Meldung wert sind (jede Art meldet sich nur alle paar Stunden oder Tage)."""
    try:
        if scdb_info().get("faellig"): NOTES.add("scdb", "Die SCDB-Blitzerliste ist älter als %d Tage. Bitte neu holen: scdb.info/my" % SCDB_REMIND_DAYS, cool=7 * 86400)
        if S["state"] == "running" and not S["stop"] and time.time() - (S["beat"] or S["started"] or time.time()) > 900:
            NOTES.add("haengt", "Der Server hängt vermutlich: seit über 15 Minuten keine Bewegung beim Update.", cool=6 * 3600)
        d = disk()
        if d["gesamt"] and 1 - d["frei"] / d["gesamt"] >= 0.9: NOTES.add("platte", "Die Festplatte des Servers ist zu über 90 %% voll (%d %% belegt)." % round(100 * (1 - d["frei"] / d["gesamt"])), cool=86400)
        if time.time() - CERT_AT[0] > 86400:
            CERT_AT[0] = time.time(); n = cert_days_left()
            if n is not None and n <= 30: NOTES.add("zertifikat", ("Das Mobilithek-Zertifikat läuft in %d Tagen ab. Bitte ein neues anfordern." % n) if n >= 0 else "Das Mobilithek-Zertifikat ist abgelaufen.", cool=7 * 86400)
    except Exception as ex: log("Meldungs-Prüfung:", ex)

def bs_daily():
    """Einmal pro Tag, nur mit echter Quelle (die Testdatei ändert sich nicht)."""
    q, _ = bs_source()
    if q != "live" or BSS["state"] != "idle": return
    if time.time() - bs_meta().get("geprueft", 0) >= 86400 and in_window(datetime.datetime.now().hour): bs_run()

# ---------- Profile (jede Person hat eigene Touren, Fahrten und Ablage) ----------
CUR = threading.local()
PLOCK = threading.Lock()
PFILE = f"{DATA}/profiles.json"
PST = {"invite": "", "profiles": {}}
NAME_RE = re.compile(r"^[\w\- ]{2,24}$", re.UNICODE)
FAILS = {}   # IP -> [Fehlversuche, Zeit des ersten]
CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

FRI = FR.Friends(f"{DATA}/friends.json")   # Freunde und Live-Standort
NOTES = NT.Notes(f"{DATA}/notify.json")   # Admin-Meldungen

def _ago(ts):
    if not ts: return "noch nie"
    d = max(0, int(time.time() - ts))
    if d < 90: return "gerade eben"
    if d < 3600: return "vor %d Min." % (d // 60)
    if d < 86400: return "vor %d Std." % (d // 3600)
    n = d // 86400
    return "vor 1 Tag" if n == 1 else "vor %d Tagen" % n

def _dauer(sec):
    sec = int(sec)
    if sec < 3600: return "%d Min." % max(1, sec // 60)
    if sec < 86400: return "%d Std." % (sec // 3600)
    n = sec // 86400
    return "1 Tag" if n == 1 else "%d Tagen" % n

def build_status():
    """Status-Kacheln für die Admin-Meldungen in der App. stufe: ok, warn, bad oder run. Nie Passwörter, Adressen oder Namen."""
    out = []; now = time.time()
    def add(key, label, wert, info="", stufe="ok", pct=None):
        x = {"key": key, "label": label, "wert": wert, "info": info, "stufe": stufe}
        if pct is not None: x["pct"] = pct
        out.append(x)
    add("version", "Server", "v" + VERSION, "läuft seit " + _dauer(now - STARTED))
    if S["state"] == "running":
        gp = int(100 * (S["done"] + S["frac"]) / S["total"]) if S["total"] else 0
        add("update", "Update", "Abbruch läuft" if S["stop"] else "läuft: %s %d %%" % (S["current"] or "…", gp), "", "run", gp)
    else:
        st = load_state(); lr = st.get("last_run")
        add("update", "Update", "keins aktiv", ("zuletzt " + _ago(lr)) if lr else "noch nie gelaufen")
    s_ = stats(); bereit = s_["regionen_bereit"]
    lr = load_state().get("last_run")
    add("karten", "Karten", "%d von %d bereit" % (bereit, s_["regionen_gesamt"]), ("Stand " + _ago(lr)) if lr else "", "ok" if bereit else "warn")
    b = bs_status()
    if b["fehler"]: add("baustellen", "Baustellen", "Fehler", b["fehler"][:60], "bad")
    elif not b["stand"]: add("baustellen", "Baustellen", "keine Liste", "noch nicht abgerufen", "warn")
    else: add("baustellen", "Baustellen", "%d Sperrungen" % b["n_gesperrt"], "%d Meldungen · %s" % (b["n"], _ago(b["stand"])), "warn" if now - b["stand"] > 3 * 86400 else "ok")
    sc = scdb_info()
    if sc.get("alter_tage") is None: add("scdb", "Blitzerliste", "keine Liste", "bitte hochladen", "warn")
    else: add("scdb", "Blitzerliste", "%d Tage alt" % sc["alter_tage"], "fällig" if sc.get("faellig") else "", "warn" if sc.get("faellig") else "ok")
    d = disk()
    if d["gesamt"]:
        pr = round(100 * (1 - d["frei"] / d["gesamt"]))
        add("speicher", "Speicher", "%d %% belegt" % pr, "%s frei" % fmt_size(d["frei"]), "bad" if pr >= 90 else ("warn" if pr >= 80 else "ok"), pr)
    r = routing_info()
    if not r["br"]["laeuft"]: add("routing", "Routing", "offline", "startet bei Bedarf", "warn")
    else:
        h = r["heute"]; add("routing", "Routing", "online", ("Ø %.1f s heute" % (h["avg_ms"] / 1000.0)) if h["n"] else "bereit")
    with DEV_LOCK: aktive = len([1 for v in DEVS.values() if now - v.get("seen", 0) < 600])
    add("handys", "Handys", "%d aktiv" % aktive, "%d Profile" % len(PST["profiles"]))
    try: n = len(FRI.admin().get("teilen_jetzt", []))
    except Exception: n = 0
    add("live", "Live-Standort", ("%d teilen" % n) if n else "niemand", "")
    dl = cert_days_left()
    if dl is not None: add("zertifikat", "Zertifikat", ("noch %d Tage" % dl) if dl >= 0 else "abgelaufen", "Mobilithek", "bad" if dl < 0 else ("warn" if dl <= 30 else "ok"))
    return out

NOTES.status_fn = build_status

def new_code(n=8): return "".join(secrets.choice(CODE_CHARS) for _ in range(n))
def save_profiles():
    os.makedirs(DATA, exist_ok=True)
    json.dump(PST, open(PFILE + ".new", "w", encoding="utf-8"), ensure_ascii=False, indent=1); os.replace(PFILE + ".new", PFILE)
def load_profiles():
    try: PST.update(json.load(open(PFILE, encoding="utf-8")))
    except Exception: pass
    if not PST.get("invite"):
        PST["invite"] = os.environ.get("INVITE_CODE", "") or new_code(6); log("Einladungscode für neue Profile:", PST["invite"]); save_profiles()
def slugify(name):
    t = name.lower()
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("ß", "ss")): t = t.replace(a, b)
    return re.sub(r"[^a-z0-9_-]+", "_", t).strip("_") or "profil"
def profile_dir(slug): return f"{DATA}/profiles/{slug}"
def cur_dir():
    slug = getattr(CUR, "slug", None)
    if not slug: raise PermissionError("kein Profil")
    return profile_dir(slug)
def tours_dir(): return cur_dir() + "/tours"
def rides_dir(): return cur_dir() + "/rides"
def outbox_file(): return cur_dir() + "/outbox.json"
def settings_file(): return cur_dir() + "/settings.json"
def favs_file(): return cur_dir() + "/favs.json"
def favs_get():
    try: return json.load(open(favs_file(), encoding="utf-8")).get("favs", [])
    except Exception: return []
def _fav_clean(o):
    try:
        i = str(o["id"])[:40]; la = float(o["lat"]); lo = float(o["lon"])
        if not i or not (-90 <= la <= 90 and -180 <= lo <= 180): return None
        return {"id": i, "name": str(o.get("name", ""))[:80], "lat": round(la, 6), "lon": round(lo, 6), "home": bool(o.get("home")), "ts": int(o.get("ts", 0)), "del": bool(o.get("del"))}
    except Exception: return None
def favs_merge(incoming):
    """Favoriten je Eintrag zusammenführen: die jüngere Änderung (ts) gewinnt. Gelöschte bleiben 180 Tage als Marke, damit das Löschen auch das andere Handy erreicht."""
    cur = {f["id"]: f for f in favs_get()}
    for o in incoming or []:
        c = _fav_clean(o)
        if c and (c["id"] not in cur or c["ts"] >= cur[c["id"]]["ts"]): cur[c["id"]] = c
    lim = int(time.time()) - 180 * 86400
    out = sorted([f for f in cur.values() if not (f["del"] and f["ts"] < lim)], key=lambda f: -f["ts"])[:500]
    os.makedirs(cur_dir(), exist_ok=True)
    json.dump({"favs": out}, open(favs_file(), "w", encoding="utf-8"), ensure_ascii=False)
    return out
def settings_get():
    try: return json.load(open(settings_file(), encoding="utf-8"))
    except Exception: return {"ts": 0}
def settings_save(body):
    data = body.get("data")
    if not isinstance(data, dict) or len(json.dumps(data)) > 600_000: raise ValueError("Einstellungen fehlen oder sind zu groß")
    os.makedirs(cur_dir(), exist_ok=True)
    if os.path.exists(settings_file()):
        try: os.replace(settings_file(), cur_dir() + "/settings.prev.json")   # eine Sicherung davor aufheben
        except OSError: pass
    json.dump({"ts": int(time.time()), "device": str(body.get("device") or "")[:60], "data": data}, open(settings_file(), "w", encoding="utf-8"), ensure_ascii=False)
def _lim(key): return 5 if key.startswith("x:") else 10   # von außen strenger als im Heimnetz
LOCK_S = 900                                                   # Sperrzeit und Zählfenster: 15 Minuten
def blocked(key):
    f = FAILS.get(key)
    if not f: return False
    if time.time() - f[1] > LOCK_S: FAILS.pop(key, None); return False
    return f[0] >= _lim(key)
def fail(key):
    f = FAILS.get(key)
    if not f or time.time() - f[1] > LOCK_S: FAILS[key] = [1, time.time()]
    else: f[0] += 1
    if FAILS[key][0] == _lim(key): log(f"WARNUNG: Zu viele falsche Anmeldungen von {key.replace('x:', '')}{' (extern)' if key.startswith('x:') else ''}, gesperrt für 15 Minuten")
def find_profile(name):
    n = str(name).strip().lower()
    for k, v in PST["profiles"].items():
        if k.lower() == n: return k, v
    return None, None
def profile_login(name, code):
    k, v = find_profile(name)
    if not v or v.get("disabled") or not hmac.compare_digest(str(code).strip().upper(), v["code"]): return None
    now = int(time.time())
    if now - v.get("seen", 0) > 60:
        with PLOCK: v["seen"] = now; save_profiles()
    return k, v["slug"]
def profile_create(name, invite):
    name = str(name).strip()
    if not NAME_RE.match(name): raise ValueError("Name: 2 bis 24 Zeichen (Buchstaben, Zahlen, Leerzeichen, - und _).")
    if not hmac.compare_digest(str(invite).strip().upper(), str(PST["invite"]).upper()): raise PermissionError("Der Einladungscode stimmt nicht.")
    return _profile_add(name)
def _profile_add(name):
    with PLOCK:
        k, _ = find_profile(name); slug = slugify(name)
        if k or any(v["slug"] == slug for v in PST["profiles"].values()): raise ValueError("Diesen Namen gibt es schon.")
        code = new_code(); now = int(time.time())
        PST["profiles"][name] = {"slug": slug, "code": code, "created": now, "seen": now, "disabled": False}; save_profiles()
    os.makedirs(profile_dir(slug), exist_ok=True); log("Profil angelegt:", name)
    try: NOTES.add("profil", f"Neues Profil angelegt: {name}")
    except Exception: pass
    return code
def migrate_old():
    """Erster Start mit v1.11: bisherige Touren, Fahrten und Ablage gehören zum Profil th3_s1nc."""
    old = [(f"{DATA}/tours", "tours"), (f"{DATA}/rides", "rides"), (f"{DATA}/outbox.json", "outbox.json")]
    if not any(os.path.exists(a) for a, _ in old): return
    name = "th3_s1nc"
    if find_profile(name)[0] is None: _profile_add(name)
    d = profile_dir(PST["profiles"][name]["slug"]); os.makedirs(d, exist_ok=True)
    for a, b in old:
        if os.path.exists(a) and not os.path.exists(f"{d}/{b}"):
            try: os.rename(a, f"{d}/{b}")
            except OSError as ex: log("Umzug", a, ex)
    log("Bisherige Touren und Fahrten ins Profil", name, "übernommen")
def profile_info():
    out = []
    for k, v in PST["profiles"].items():
        d = profile_dir(v["slug"]); n_t = n_r = size = 0
        try: n_t = len([x for x in os.listdir(d + "/tours") if x.endswith(".json")])
        except OSError: pass
        try: n_r = len([x for x in os.listdir(d + "/rides") if x.endswith(".json")])
        except OSError: pass
        for root, _, fs in os.walk(d):
            for x in fs:
                try: size += os.path.getsize(os.path.join(root, x))
                except OSError: pass
        out.append({"name": k, "code": v["code"], "created": v.get("created", 0), "seen": v.get("seen", 0), "disabled": bool(v.get("disabled")), "handys": dev_count(k), "geraete": dev_list(k), "touren": n_t, "fahrten": n_r, "bytes": size})
    return sorted(out, key=lambda x: x["name"].lower())
def count_all():
    t = r = 0
    for p in profile_info(): t += p["touren"]; r += p["fahrten"]
    return t, r

# ---------- Handys (Gerätekennung aus der App) und laufende Downloads ----------
DEV_FILE = f"{DATA}/devices.json"; DEVS = {}; DEV_LOCK = threading.Lock(); DEV_SAVED = [0.0]
def dev_load():
    try:
        with open(DEV_FILE, encoding="utf-8") as f: DEVS.update(json.load(f))
    except Exception: pass
def dev_save(force=False):
    if time.time() - DEV_SAVED[0] < 30 and not force: return
    DEV_SAVED[0] = time.time()
    try:
        tmp = DEV_FILE + ".new"
        with open(tmp, "w", encoding="utf-8") as f: json.dump(DEVS, f, ensure_ascii=False)
        os.replace(tmp, DEV_FILE)
    except Exception as ex: log("Handy-Liste nicht gespeichert:", ex)
def dev_note(h, prof=None):
    """Merkt sich das Handy (zufällige Kennung + Gerätename aus der App). Gibt die Kennung zurück oder ""."""
    did = h.headers.get("X-Device", "").strip().lower()
    if not re.fullmatch(r"[0-9a-f]{8,16}", did): return ""
    name = "".join(c for c in urllib.parse.unquote(h.headers.get("X-Device-Name", "")) if c.isprintable())[:40].strip()
    now = time.time()
    with DEV_LOCK:
        d = DEVS.get(did)
        if d is None:
            if len(DEVS) >= 500: del DEVS[min(DEVS, key=lambda k: DEVS[k].get("seen", 0))]
            d = DEVS[did] = {"name": name, "profile": "", "first": now}
        d["seen"] = now; d["ip"] = h.real_ip(); d["extern"] = h.extern()
        if name: d["name"] = name
        if prof: d["profile"] = prof
        dev_save()
    return did
DEV_KEEP_DAYS = int(os.environ.get("DEV_KEEP_DAYS", "90"))   # so lange gilt ein Handy als "da"; danach wird es vergessen
DEV_CLEAN_AT = [0.0]
def dev_cleanup():
    """Handys, die länger als DEV_KEEP_DAYS nicht da waren, ganz aus der Liste nehmen. Gibt die Anzahl zurück."""
    lim = time.time() - DEV_KEEP_DAYS * 86400
    with DEV_LOCK:
        old = [k for k, d in DEVS.items() if d.get("seen", 0) < lim]
        for k in old: del DEVS[k]
        if old: dev_save(True)
    DEV_CLEAN_AT[0] = time.time()
    if old: log(f"Handys aufgeräumt: {len(old)} Eintrag/Einträge vergessen (länger als {DEV_KEEP_DAYS} Tage nicht da)", cat="aufraeumen")
    return len(old)
def dev_list(profile):
    """Die Handys eines Profils, zuletzt gesehen zuerst."""
    with DEV_LOCK:
        l = [{"id": k, "name": d.get("name", "") or "Android", "seen": int(d.get("seen", 0)), "first": int(d.get("first", 0))}
             for k, d in DEVS.items() if d.get("profile") == profile]
    return sorted(l, key=lambda x: -x["seen"])
def dev_remove(did):
    """Ein Handy aus der Liste nehmen (meldet es sich wieder, taucht es neu auf). Gibt True zurück, wenn es das gab."""
    with DEV_LOCK:
        if did not in DEVS: return False
        name = DEVS[did].get("name", "")
        del DEVS[did]; dev_save(True)
    log(f"Handy entfernt: {name or did}", cat="aufraeumen")
    return True
def dev_count(profile):
    lim = time.time() - DEV_KEEP_DAYS * 86400
    with DEV_LOCK: return sum(1 for d in DEVS.values() if d.get("profile") == profile and d.get("seen", 0) >= lim)

XFER = {}; XLOCK = threading.Lock()
XKIND = {"maps": "Karte", "pois": "Orte", "rd5": "Straßendaten", "limits": "Tempolimits"}
def xfer_label(path):
    parts = path.strip("/").split("/"); stem = parts[-1].split(".")[0]
    r = BYID.get(stem); return (XKIND.get(parts[0], parts[0]) + " " + (r["name"] if r else stem)).strip()
def xfer_start(h, path, a, size):
    if path.strip("/").split("/")[0] not in XKIND: return None   # Inhaltsliste, Blitzerliste usw.: kein Download, der zählt
    did = h.headers.get("X-Device", "").strip().lower(); who = did if re.fullmatch(r"[0-9a-f]{8,16}", did) else "ip:" + h.real_ip()
    key = (who, path); now = time.time()
    with XLOCK:
        e = XFER.get(key)
        if e is None or (not e["aktiv"] and now - e["ende"] > 30):
            e = XFER[key] = {"dev": who if not who.startswith("ip:") else "", "ip": h.real_ip(), "extern": h.extern(), "datei": xfer_label(path),
                              "size": size, "t0": now, "last": now, "total": 0, "speed": 0.0, "mt": now, "mb": 0, "aktiv": 0, "ende": 0, "pos": a, "base": a, "reqsent": 0}
        e["aktiv"] += 1; e["base"] = a; e["reqsent"] = 0; e["ende"] = 0; e["size"] = size
        for k in [k for k, v in XFER.items() if not v["aktiv"] and now - v["ende"] > 120]: del XFER[k]
    return e
def xfer_add(e, n):
    if e is None: return
    now = time.time()
    with XLOCK:
        e["total"] += n; e["reqsent"] += n; e["pos"] = e["base"] + e["reqsent"]; e["last"] = now
        if now - e["mt"] >= 1.0:
            cur = (e["total"] - e["mb"]) / (now - e["mt"]); e["speed"] = cur if e["speed"] == 0 else 0.5 * e["speed"] + 0.5 * cur
            e["mt"] = now; e["mb"] = e["total"]
def xfer_end(e):
    if e is None: return
    with XLOCK:
        e["aktiv"] = max(0, e["aktiv"] - 1)
        if not e["aktiv"]: e["ende"] = time.time(); e["speed"] = 0.0
def aktiv_info():
    now = time.time(); out = []
    with XLOCK:
        for e in XFER.values():
            if e["aktiv"] and now - e["last"] > 15: continue   # hängt: nicht mehr als aktiv zeigen
            out.append({"datei": e["datei"], "dev": e["dev"], "ip": e["ip"], "extern": e["extern"], "laeuft": bool(e["aktiv"]), "speed": e["speed"],
                        "pct": round(100.0 * min(e["pos"], e["size"]) / e["size"]) if e["size"] else 0, "bytes": e["total"], "vor": int(now - (e["last"] if e["aktiv"] else e["ende"]))})
    with DEV_LOCK:
        for x in out:
            d = DEVS.get(x["dev"]) if x["dev"] else None
            x["name"] = d.get("name", "") if d else ""; x["profil"] = d.get("profile", "") if d else ""
        geraete = [dict(id=k, **v) for k, v in DEVS.items() if now - v.get("seen", 0) < 600]
    out.sort(key=lambda x: (not x["laeuft"], x["vor"]))
    geraete.sort(key=lambda x: -x.get("seen", 0))
    return {"downloads": out, "geraete": geraete}

def _tour_path(i): return f"{tours_dir()}/{i}.json"
def tour_list():
    os.makedirs(tours_dir(), exist_ok=True); out = []
    for n in os.listdir(tours_dir()):
        if not n.endswith(".json"): continue
        try:
            t = json.load(open(f"{tours_dir()}/{n}", encoding="utf-8")); out.append({"id": t["id"], "name": t["name"], "profile": t.get("profile"), "avoid": t.get("avoid", []), "points": len(t.get("points", [])), "updated": t.get("updated", 0)})
        except Exception: pass
    return sorted(out, key=lambda x: -x["updated"])
MAX_TOUR_PTS = 300   # gefahrene Fahrten werden mit vielen Punkten übernommen, damit die Route genau der Spur folgt
def clean_points(pts):
    out = []
    for p in pts or []:
        la, lo = float(p[0]), float(p[1])
        if not (-90 <= la <= 90 and -180 <= lo <= 180): raise ValueError("Punkt außerhalb")
        out.append([round(la, 6), round(lo, 6)])
    if len(out) < 2 or len(out) > MAX_TOUR_PTS: raise ValueError(f"2 bis {MAX_TOUR_PTS} Punkte (Start, Zwischenpunkte, Ziel)")
    return out
def tour_save(body):
    pts = clean_points(body.get("points")); name = str(body.get("name") or "Tour").strip()[:80] or "Tour"
    prof = body.get("profile") if body.get("profile") in PROFILES else PROFILES[0]
    tid = str(body.get("id") or "")
    if not tid.isalnum(): tid = f"t{int(time.time() * 1000)}"
    os.makedirs(tours_dir(), exist_ok=True)
    json.dump({"id": tid, "name": name, "profile": prof, "avoid": avoid_clean(body.get("avoid")), "points": pts, "updated": int(time.time())}, open(_tour_path(tid), "w", encoding="utf-8"), ensure_ascii=False)
    return tid
def tour_get(tid):
    if not str(tid).isalnum(): return None
    try: return json.load(open(_tour_path(tid), encoding="utf-8"))
    except Exception: return None
def outbox_set(body):
    os.makedirs(cur_dir(), exist_ok=True)
    pts = clean_points(body.get("points")); prof = body.get("profile") if body.get("profile") in PROFILES else PROFILES[0]
    json.dump({"ts": int(time.time()), "name": str(body.get("name") or "Tour").strip()[:80] or "Tour", "profile": prof, "avoid": avoid_clean(body.get("avoid")), "points": pts},
              open(outbox_file(), "w", encoding="utf-8"), ensure_ascii=False)
def outbox_get():
    try: return json.load(open(outbox_file(), encoding="utf-8"))
    except Exception: return {}

# ---------- Gefahrene Fahrten (vom Handy hochgeladen) ----------
OVER_MARGIN = 3; STILL_KMH = 2.0

def _hav(a, b, c, d):
    r = math.pi / 180; dl = (c - a) * r; dn = (d - b) * r
    x = math.sin(dl / 2) ** 2 + math.cos(a * r) * math.cos(c * r) * math.sin(dn / 2) ** 2
    return 2 * 6371000 * math.asin(math.sqrt(x))

def parse_pts(text):
    """Zeilen 'zeitMs,breite,länge,höhe,tempo,limit,schräglage' -> Liste von Zeilen (None für fehlende Werte)."""
    rows = []
    for ln in text.splitlines():
        c = ln.strip().split(",")
        if len(c) < 5: continue
        try:
            t = int(c[0]); la = float(c[1]); lo = float(c[2]); sp = float(c[4])
        except ValueError: continue
        def f(i):
            try: return float(c[i]) if i < len(c) and c[i] != "" else None
            except ValueError: return None
        rows.append([t, la, lo, f(3), sp, int(f(5) or 0), f(6)])
    return rows

def analyse(rows):
    """Kennzahlen und Reihen einer Fahrt. Gleiche Regeln wie in der App (Tempo ab 2 km/h = Bewegung, Überschreitung ab Limit + 3)."""
    n = len(rows); dist = [0.0] * n; moving = 0; d = 0.0; mx = 0.0
    over = 0; is_over = False
    for i in range(n):
        t, la, lo, ele, sp, lim, lean = rows[i]
        mx = max(mx, sp)
        if i > 0:
            p = rows[i - 1]; dd = _hav(p[1], p[2], la, lo); dt = t - p[0]
            if sp >= STILL_KMH and 0.5 <= dd <= 500: d += dd
            if sp >= STILL_KMH and 1 <= dt <= 10000: moving += dt
        dist[i] = d
        if lim > 0:
            if not is_over and sp > lim + OVER_MARGIN: is_over = True; over += 1
            elif is_over and sp <= lim: is_over = False
    # Beschleunigung (m/s², aus dem Tempo, 3 Punkte geglättet; Lücken ab 5 s = Pause)
    raw = [0.0] * n
    for i in range(1, n - 1):
        dt = rows[i + 1][0] - rows[i - 1][0]
        if dt <= 0 or rows[i][0] - rows[i - 1][0] > 5000 or rows[i + 1][0] - rows[i][0] > 5000: continue
        raw[i] = (rows[i + 1][4] - rows[i - 1][4]) / 3.6 / (dt / 1000)
    acc = [(raw[max(i - 1, 0)] + raw[i] + raw[min(i + 1, n - 1)]) / 3 for i in range(n)]
    # Höhenmeter: 5-Punkte-Mittel, Schwelle 3 m
    el = [r[3] for r in rows]; asc = desc = 0.0
    if sum(1 for v in el if v is not None) > 5:
        sm = []
        for i in range(n):
            w = [v for v in el[max(0, i - 2):i + 3] if v is not None]; sm.append(sum(w) / len(w) if w else None)
        ref = next((v for v in sm if v is not None), 0)
        for v in sm:
            if v is None: continue
            if v - ref >= 3: asc += v - ref; ref = v
            elif ref - v >= 3: desc += ref - v; ref = v
    lean = [r[6] for r in rows if r[6] is not None]
    start, end = rows[0][0], rows[-1][0]
    st = {"start": start, "end": end, "total_ms": max(0, end - start), "moving_ms": moving, "dist_m": int(d), "max_kmh": int(round(mx)),
          "avg_kmh": int(round(d / 1000 / (moving / 3600000))) if moving > 0 else 0, "points": n, "asc_m": int(round(asc)), "desc_m": int(round(desc)),
          "over": over, "has_ele": any(v is not None for v in el), "has_limit": any(r[5] > 0 for r in rows), "has_lean": bool(lean),
          "lean_left": int(round(max([v for v in lean if v > 0], default=0))), "lean_right": int(round(-min([v for v in lean if v < 0], default=0))),
          "max_acc": round(max(acc, default=0), 2), "max_brake": round(-min(acc, default=0), 2)}
    return st, dist, acc

def _ride_ok(i): return str(i).isalnum()
def ride_meta(i):
    try: return json.load(open(f"{rides_dir()}/{i}.json", encoding="utf-8"))
    except Exception: return None
def ride_upload(body):
    rid = str(body.get("id") or ""); pts = str(body.get("pts") or "")
    if not _ride_ok(rid): raise ValueError("ungültige Nummer")
    rows = parse_pts(pts)
    if len(rows) < 2: raise ValueError("zu wenige Punkte")
    os.makedirs(rides_dir(), exist_ok=True)
    old = ride_meta(rid); name = str(body.get("name") or (old or {}).get("name") or "Fahrt").strip()[:80] or "Fahrt"
    st, _, _ = analyse(rows)
    kind = str(body.get("kind") or (old or {}).get("kind") or "free")
    if kind not in ("nav", "free"): kind = "free"
    open(f"{rides_dir()}/{rid}.pts", "w", encoding="utf-8").write(pts)
    meta = {"id": rid, "name": name, "kind": kind, "uploaded": int(time.time()), "stats": st}
    if body.get("demo"): meta["demo"] = True
    json.dump(meta, open(f"{rides_dir()}/{rid}.json", "w", encoding="utf-8"), ensure_ascii=False)
def ride_list():
    os.makedirs(rides_dir(), exist_ok=True); out = []
    for n in os.listdir(rides_dir()):
        if n.endswith(".json"):
            m = ride_meta(n[:-5])
            if m: out.append(m)
    return sorted(out, key=lambda m: -m["stats"]["start"])
PREV = {}
def ride_preview(rid):
    """Grobe Route (etwa 48 Punkte) für die Vorschau-Bilder; wird gemerkt, bis sich die Datei ändert."""
    if not _ride_ok(rid): return None
    fn = f"{rides_dir()}/{rid}.pts"
    try: mt = os.path.getmtime(fn)
    except OSError: return None
    key = (cur_dir(), rid)
    if key in PREV and PREV[key][0] == mt: return PREV[key][1]
    try: rows = parse_pts(open(fn, encoding="utf-8").read())
    except Exception: return None
    if len(rows) < 2: return None
    step = max(1, len(rows) // 48)
    pts = [[round(r[1], 5), round(r[2], 5)] for r in rows[::step]]
    if pts[-1] != [round(rows[-1][1], 5), round(rows[-1][2], 5)]: pts.append([round(rows[-1][1], 5), round(rows[-1][2], 5)])
    PREV[key] = (mt, pts); return pts
def ride_detail(rid):
    m = ride_meta(rid) if _ride_ok(rid) else None
    if not m: return None
    try: rows = parse_pts(open(f"{rides_dir()}/{rid}.pts", encoding="utf-8").read())
    except Exception: return None
    st, dist, acc = analyse(rows); t0 = rows[0][0]
    return {"id": rid, "name": m["name"], "kind": m.get("kind", "free"), "demo": bool(m.get("demo")), "stats": st,
            "t": [round((r[0] - t0) / 1000, 1) for r in rows], "lat": [r[1] for r in rows], "lon": [r[2] for r in rows],
            "ele": [None if r[3] is None else round(r[3], 1) for r in rows], "speed": [round(r[4], 1) for r in rows],
            "limit": [r[5] or None for r in rows], "lean": [None if r[6] is None else round(r[6], 1) for r in rows],
            "acc": [round(a, 2) for a in acc], "dist": [round(x) for x in dist]}
def ride_gpx(rid):
    d = ride_detail(rid)
    if not d: return None
    rows = parse_pts(open(f"{rides_dir()}/{rid}.pts", encoding="utf-8").read())
    esc = lambda x: str(x).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    out = ['<?xml version="1.0" encoding="UTF-8"?>\n<gpx version="1.1" creator="HUDPilot" xmlns="http://www.topografix.com/GPX/1/1"><trk><name>' + esc(d["name"]) + "</name><trkseg>"]
    for t, la, lo, ele, sp, lim, lean in rows:
        iso = datetime.datetime.fromtimestamp(t / 1000, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        out.append(f'<trkpt lat="{la}" lon="{lo}">' + (f"<ele>{ele:.0f}</ele>" if ele is not None else "") + f"<time>{iso}</time></trkpt>")
    out.append("</trkseg></trk></gpx>"); return "\n".join(out)
def ride_csv(rid):
    d = ride_detail(rid)
    if not d: return None
    rows = parse_pts(open(f"{rides_dir()}/{rid}.pts", encoding="utf-8").read())
    num = lambda v, k: ("%.*f" % (k, v)).replace(".", ",")
    out = ["\ufeffZeit;Breite;Länge;Höhe m;Tempo km/h;Limit km/h;Schräglage °"]
    for t, la, lo, ele, sp, lim, lean in rows:
        out.append(";".join([datetime.datetime.fromtimestamp(t / 1000).strftime("%d.%m.%Y %H:%M:%S"), num(la, 6), num(lo, 6),
                             "" if ele is None else num(ele, 0), str(round(sp)), str(lim) if lim else "", "" if lean is None else str(round(lean))]))
    return "\r\n".join(out) + "\r\n"
def ride_rename(rid, name):
    m = ride_meta(rid) if _ride_ok(rid) else None
    if not m: return False
    m["name"] = str(name).strip()[:80] or m["name"]; json.dump(m, open(f"{rides_dir()}/{rid}.json", "w", encoding="utf-8"), ensure_ascii=False); return True
DEMO_SPECS = [("Beispiel: Allgäu (kurz)", 47.57, 10.70, 25, 11, -1, "free"),
              ("Beispiel: Eifel (mit Pause)", 50.37, 6.95, 55, 22, 25, "nav"),
              ("Beispiel: Schwäbische Alb (lang)", 48.40, 9.40, 95, 33, -1, "nav"),
              ("Beispiel: Feierabendrunde", 49.75, 11.10, 40, 44, -1, "free")]
def demo_points(lat, lon, minutes, seed, pause_min, start_ms):
    """Erfundene Fahrt (nicht auf echten Straßen), eine Zeile pro Sekunde, wie die Beispielfahrten der App."""
    import random
    rnd = random.Random(seed); heading = rnd.random() * 2 * math.pi
    v = 0.0; t_ms = start_ms; limit = 70; limit_left = 120; lean = 0.0
    p1, p2, p3 = (rnd.random() * 6.28 for _ in range(3)); out = []
    for s in range(minutes * 60):
        if pause_min > 0 and s == pause_min * 60: t_ms += 12 * 60000; v = 0.0
        limit_left -= 1
        if limit_left <= 0: limit = rnd.choice([50, 70, 70, 60, 100, 80]); limit_left = 60 + rnd.randrange(240)
        t = s / 10.0
        k = 0.004 * math.sin(t / 7 + p1) + 0.006 * math.sin(t / 3.1 + p2) * max(0.0, math.sin(t / 25 + p3))
        if s % 200 < 10: k += 0.02 * (1 if (s // 200) % 2 == 0 else -1)
        ka = abs(k); v_curve = 40.0 if ka < 1e-4 else math.sqrt(4.5 / ka)
        v_want = min(limit / 3.6 * (1.12 if 100 <= s % 400 <= 140 else 1.0), v_curve, 38.0)
        dv = max(-3.5, min(2.5, v_want - v)); v = max(0.0, v + dv * 0.6 + (rnd.random() - 0.5) * 0.3)
        heading += v * k
        lat += v * math.cos(heading) / 111320.0; lon += v * math.sin(heading) / (111320.0 * math.cos(math.radians(lat)))
        lean += (math.degrees(math.atan(v * v * k / 9.81)) - lean) * 0.5 + (rnd.random() - 0.5) * 2.0
        ele = 420.0 + 150.0 * math.sin(s / 260.0 + p2) + 60.0 * math.sin(s / 61.0 + p1) + rnd.random()
        out.append(f"{t_ms},{lat:.6f},{lon:.6f},{ele:.1f},{v * 3.6:.1f},{limit},{f'{lean:.1f}' if v * 3.6 >= 15 else ''}")
        t_ms += 1000
    return "\n".join(out) + "\n"
def demo_create():
    """Legt die Beispielfahrten an (Nummern demo1 ...), die Zeiten liegen in den letzten Tagen."""
    now = int(time.time() * 1000)
    for i, (name, la, lo, mi, seed, pa, kind) in enumerate(DEMO_SPECS):
        start = now - (i + 1) * 26 * 3600 * 1000 - mi * 60000
        ride_upload({"id": f"demo{i + 1}", "name": name, "kind": kind, "demo": True, "pts": demo_points(la, lo, mi, seed, pa, start)})
def demo_delete():
    for n in ride_list():
        if n.get("demo"): ride_delete(n["id"])
def ride_delete(rid):
    if _ride_ok(rid):
        for ext in ("json", "pts"):
            try: os.remove(f"{rides_dir()}/{rid}.{ext}")
            except OSError: pass
def tour_rename(tid, name):
    t = tour_get(tid)
    if not t: return False
    t["name"] = str(name).strip()[:80] or t["name"]; json.dump(t, open(_tour_path(tid), "w", encoding="utf-8"), ensure_ascii=False); return True

# ---------- Webserver ----------
def disk():
    try: u = os.statvfs(DATA); return {"frei": u.f_bavail * u.f_frsize, "gesamt": u.f_blocks * u.f_frsize}
    except Exception: return {"frei": 0, "gesamt": 0}
def used_bytes():
    n = 0
    for root, _, fs in os.walk(PUB):
        for f in fs:
            try: n += os.path.getsize(os.path.join(root, f))
            except OSError: pass
    return n
STAT_KEYS = ["strassen", "mit_limit", "mit_zone", "beleuchtet", "tunnel", "bruecken", "ortsschilder", "temposchilder", "blitzer_osm", "bahnuebergaenge", "ueberwege", "zebrastreifen", "beruhigung"]
def strassen_summe():
    """Zahlen aus den Straßendaten aller gebauten Regionen (aus dem Bau gemerkt). An Regionsgrenzen kann etwas doppelt zählen."""
    st = load_state(); tot = {k: 0 for k in STAT_KEYS}; n = 0
    for r in REGIONS:
        if not os.path.exists(f"{PUB}/limits/{r['id']}.zip"): continue
        v = [int(x) for x in str(st.get("limits_info", {}).get(r["id"], {}).get("stats", "")).split(",") if x.strip().isdigit()]
        if len(v) != len(STAT_KEYS): continue
        n += 1
        for k, x in zip(STAT_KEYS, v): tot[k] += x
    tot["regionen"] = n; tot["blitzer_scdb"] = st.get("scdb", {}).get("anzahl", 0); tot["orte"] = sum(st.get("poi_count", {}).values())
    return tot

def status():
    st = load_state(); rows = []
    for r in REGIONS:
        mp = f"{PUB}/maps/{r['id']}.pmtiles"; has = os.path.exists(mp)
        rd = sum(os.path.getsize(f"{PUB}/rd5/{t}.rd5") for t in tiles_for(r["bbox"]) if os.path.exists(f"{PUB}/rd5/{t}.rd5"))
        pp = f"{PUB}/pois/{r['id']}.tsv.gz"; lp = f"{PUB}/limits/{r['id']}.zip"
        rows.append({"id": r["id"], "name": r["name"], "land": r["land"], "landname": LAND_NAMES.get(r["land"], r["land"]), "aktiv": r["id"] in CFG["regions"], "versteckt": r["id"] in CFG.get("versteckt", []), "gebraucht": needed_map().get(r["id"], 0), "wartet": r["id"] in PENDING,
                     "strassen_bytes": os.path.getsize(lp) if os.path.exists(lp) else 0, "strassen_stand": int(os.path.getmtime(lp)) if os.path.exists(lp) else 0,
                     "poi_bytes": os.path.getsize(pp) if os.path.exists(pp) else 0, "poi_anzahl": st.get("poi_count", {}).get(r["id"], 0),
                     "karte_bytes": os.path.getsize(mp) if has else 0, "stand": int(os.path.getmtime(mp)) if has else 0,
                     "routen_bytes": rd, "fehler": S["errors"].get(r["id"], "")})
    nxt = None
    if st.get("last_run"): nxt = st["last_run"] + int(CFG["every_days"]) * 86400
    return {"state": S["state"], "current": S["current"], "done": S["done"], "total": S["total"], "started": S["started"],
            "detail": S["detail"], "fehler_lauf": S["errors"].get("_", ""), "planet": st.get("planet", ""),
            "last_run": st.get("last_run"), "next_run": nxt, "cfg": CFG, "limit_jetzt_mbit": cur_limit_mbit(),
            "im_fenster": in_window(datetime.datetime.now().hour), "regionen": rows,
            "stop": S["stop"], "pct": S["pct"], "step": S["step"], "beat": int(S["beat"]), "hang_s": HANG_S,
            "gesamt_pct": int(100 * (S["done"] + S["frac"]) / S["total"]) if S["total"] else 0,
            "disk": disk(), "belegt": used_bytes(), "admin": bool(ADMIN_PW), "jetzt": int(time.time()), "scdb": scdb_info(),
            "version": VERSION, "gestartet": STARTED, "regionsliste": regions_info(), "limits_jar": os.path.isdir(LIMITS_DIR), "ergebnis": st.get("last_result"), "summe": strassen_summe()}

def stats():
    st = load_state(); built = 0; pois = 0
    for r in REGIONS:
        if os.path.exists(f"{PUB}/maps/{r['id']}.pmtiles"): built += 1
        pois += st.get("poi_count", {}).get(r["id"], 0)
    p = BR_PROC["p"]
    return {"regionen_bereit": built, "regionen_gesamt": len(REGIONS), "bytes": used_bytes(), "orte": pois, "profile": len(PST["profiles"]),
            "letzter_bau": st.get("last_run"), "state": S["state"], "routing": bool(p and p.poll() is None), "version": VERSION,
            "laender": len({r["land"] for r in REGIONS if os.path.exists(f"{PUB}/maps/{r['id']}.pmtiles")})}

PROF_GET = ("/api/poi", "/api/tours", "/api/tour", "/api/outbox", "/api/rides", "/api/ride", "/api/ride/gpx", "/api/ride/csv", "/api/ride/preview", "/api/me", "/api/settings", "/api/favs", "/api/baustellen", "/api/laender", "/api/friends", "/api/friends/live", "/api/notify")
PROF_POST = ("/api/tour/save", "/api/tour/delete", "/api/tour/rename", "/api/phone", "/api/ride/upload", "/api/ride/delete", "/api/ride/rename", "/api/ride/demo", "/api/settings/save", "/api/favs/save", "/api/friends/request", "/api/friends/respond", "/api/friends/remove", "/api/friends/permit", "/api/live", "/api/live/stop")
EXT_PAGES = ("/", "/index.html", "/logo.png", "/favicon.ico", "/mein", "/mein.html", "/planer", "/planer.html", "/touren", "/touren.html", "/api/route")
KARTEN_PFADE = ("/manifest.json", "/maps/", "/pois/", "/limits/", "/rd5/", "/cameras.csv", "/api/need")   # große Dateien: von außen nur mit Schalter "Karten extern ladbar"
DENYLOG = {}

class H(BaseHTTPRequestHandler):
    server_version = "hudserver"
    def log_message(self, *a): pass
    def send_json(self, obj, code=200):
        b = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.send_header("Cache-Control", "no-store"); self.end_headers()
        if self.command != "HEAD": self.wfile.write(b)
    def do_HEAD(self): self.do_GET()
    def extern(self):
        """True, wenn die Anfrage von außen kommt (über den Nginx Proxy Manager). Im Zweifel gilt sie als extern: schon ein Weiterleitungs-Kopf reicht."""
        h = self.headers
        return self.client_address[0] in NPM_IPS or bool(h.get("X-Forwarded-For") or h.get("X-Real-IP") or h.get("Forwarded") or h.get("X-Forwarded-Host"))
    def real_ip(self):
        h = self.headers; v = h.get("X-Real-IP", "").strip()
        if not v and h.get("X-Forwarded-For"): v = h["X-Forwarded-For"].split(",")[-1].strip()   # der letzte Eintrag stammt vom Proxy
        return (v or self.client_address[0])[:64]
    def ipkey(self): return ("x:" if self.extern() else "") + self.real_ip()
    def deny(self, path, karten=False):
        """Von außen nicht erlaubt: Verwaltung und alles andere sieht wie "nicht gefunden" aus, Karten melden den Grund."""
        k = (self.real_ip(), path.split("?")[0])
        if time.time() - DENYLOG.get(k, 0) > 600:
            DENYLOG[k] = time.time(); log(f"Extern abgewiesen: {k[1]} von {k[0]}", cat="anmeldung")
            if len(DENYLOG) > 500: DENYLOG.clear()
        if karten: return self.send_json({"fehler": "Karten und Straßendaten gibt es nur im Heimnetz."}, 403)
        return self.send_json({"fehler": "nicht gefunden"}, 404)
    def gate_get(self, path):
        """Prüft Anfragen von außen. True = darf weiter."""
        if not self.extern() or path in EXT_PAGES or path in PROF_GET: return True
        if any(path == x or (x.endswith("/") and path.startswith(x)) for x in KARTEN_PFADE):
            if CFG.get("extern_karten"): return True
            self.deny(path, True); return False
        self.deny(path); return False
    def prof(self):
        """Profil der Anfrage (Kopfzeilen X-Profile und X-Code oder Cookie hudprof). Setzt CUR.slug; False = nicht angemeldet."""
        CUR.slug = None; CUR.name = None
        name = urllib.parse.unquote(self.headers.get("X-Profile", "")); code = self.headers.get("X-Code", "")
        if not name:
            for part in self.headers.get("Cookie", "").split(";"):
                k, _, v = part.strip().partition("=")
                if k == "hudprof": name, _, code = urllib.parse.unquote(v).partition("|")
        if not name: return False
        ip = self.ipkey(); who = self.real_ip() + (" extern" if self.extern() else "")
        if blocked(ip): return False
        r = profile_login(name, code)
        if not r:
            fail(ip); log(f"Anmeldung fehlgeschlagen: Profil „{name}“ von {who}", cat="anmeldung"); return False
        CUR.name, CUR.slug = r
        dev_note(self, r[0])
        if time.time() - LOGINLOG.get(r[0], 0) > 600:
            LOGINLOG[r[0]] = time.time(); log(f"Profil angemeldet: {r[0]} ({who})", cat="anmeldung")
        return True
    def need_profile(self):
        if blocked(self.ipkey()): self.send_json({"fehler": "Zu viele falsche Anmeldungen. Bitte in 15 Minuten nochmal."}, 429); return False
        if self.prof(): return True
        self.send_json({"fehler": "Bitte mit Profil anmelden (Name und Code)."}, 401); return False
    def do_GET(self):
        path = self.path.split("?")[0]
        if not self.gate_get(path): return
        if self.command != "HEAD": dev_note(self)
        if path in PROF_GET and not self.need_profile(): return
        if path in ("/", "/index.html"): self.send_page("landing.html")
        elif path in ("/admin", "/admin.html"): self.send_page("index.html" if self.authed() else "login.html")
        elif path in ("/logo.png", "/favicon.ico"):
            b = open(os.path.join(HERE, "static", "logo.png"), "rb").read()
            self.send_response(200); self.send_header("Content-Type", "image/png"); self.send_header("Content-Length", str(len(b)))
            self.send_header("Cache-Control", "max-age=3600"); self.end_headers()
            if self.command != "HEAD": self.wfile.write(b)
        elif path == "/api/stats":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(stats())
        elif path == "/api/baustellen":
            try: b = open(BS_LIST, "rb").read()
            except Exception: return self.send_json({"stand": 0, "n": 0, "items": []})
            gz = "gzip" in self.headers.get("Accept-Encoding", "")
            if gz: b = gzip.compress(b, 5)
            if self.command != "HEAD": log("Handy holt Baustellen (%d kB%s)" % (len(b) // 1024, ", gzip" if gz else ""), cat="handy")
            self.send_response(200); self.send_header("Content-Type", "application/json; charset=utf-8")
            if gz: self.send_header("Content-Encoding", "gzip")
            self.send_header("Content-Length", str(len(b))); self.send_header("Cache-Control", "no-store"); self.end_headers()
            if self.command != "HEAD": self.wfile.write(b)
        elif path == "/api/admin/baustellen":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(bs_status())
        elif path == "/api/settings": self.send_json(settings_get())
        elif path == "/api/favs": self.send_json({"favs": favs_get()})
        elif path == "/api/me":
            self.send_json({"name": CUR.name, "touren": len(tour_list()), "fahrten": len(ride_list()), "jetzt": int(time.time())})
        elif path == "/api/admin/log":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            q = urllib.parse.parse_qs(self.path.partition("?")[2]); nur = q.get("nur", [""])[0] == "fehler"
            try: n = max(10, min(2000, int(q.get("n", ["400"])[0])))
            except ValueError: n = 400
            lines = [x for x in LOG if (not nur) or "FEHLER" in x or "WARNUNG" in x]
            self.send_json({"zeilen": lines[-n:], "gesamt": len(LOG), "level": CFG.get("log_level", "normal"), "cats": CFG.get("log_cats", []),
                            "kategorien": [[k, v] for k, v in LOG_CATS], "stufen": list(LOG_PRESETS)})
        elif path == "/api/admin/aktiv":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(aktiv_info())
        elif path == "/api/admin/profiles":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json({"profile": [{**{k: v for k, v in x.items() if k not in ("touren", "fahrten")}, "meldungen": NOTES.is_recip(x["name"])} for x in profile_info()], "invite": PST["invite"]})   # Touren und Fahrten sieht nur der User selbst
        elif path == "/api/friends": self.send_json({"friends": FRI.listing(CUR.name), "offen": FRI.pending_in(CUR.name), "sicht": FRI.viewers(CUR.name)})
        elif path == "/api/notify":
            try: n = int(urllib.parse.parse_qs(self.path.partition("?")[2]).get("since", ["0"])[0])
            except Exception: n = 0
            self.send_json(NOTES.since(CUR.name, n))
        elif path == "/api/admin/notify":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(NOTES.admin())
        elif path == "/api/admin/routing":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(routing_info())
        elif path == "/api/friends/live": self.send_json({"positions": FRI.positions_for(CUR.name)})
        elif path == "/api/admin/friends":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(FRI.admin())
        elif path in ("/mein", "/mein.html"): self.send_page("mein.html")
        elif path in ("/planer", "/planer.html"): self.send_page("planer.html")
        elif path == "/api/route":
            code, b = route_proxy(self.path.partition("?")[2])
            self.send_response(code); self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(b))); self.send_header("Cache-Control", "no-store"); self.end_headers()
            if self.command != "HEAD": self.wfile.write(b)
        elif path == "/api/poi":
            q = urllib.parse.parse_qs(self.path.partition("?")[2])
            try:
                hits, n = poi_search(q.get("cat", [""])[0], float(q.get("lat", ["0"])[0]), float(q.get("lon", ["0"])[0]), float(q.get("km", ["15"])[0]))
                self.send_json({"orte": hits, "regionen": n})
            except ValueError as ex: self.send_json({"fehler": str(ex)}, 400)
        elif path == "/api/laender": self.send_json({"regionen": laender_boxen()})
        elif path == "/api/tours": self.send_json({"touren": tour_list()})
        elif path == "/api/tour":
            t = tour_get(urllib.parse.parse_qs(self.path.partition("?")[2]).get("id", [""])[0])
            self.send_json(t if t else {"fehler": "nicht gefunden"}, 200 if t else 404)
        elif path == "/api/outbox": self.send_json(outbox_get())
        elif path in ("/touren", "/touren.html"): self.send_page("touren.html")
        elif path == "/api/rides": self.send_json({"fahrten": ride_list()})
        elif path == "/api/ride":
            d = ride_detail(urllib.parse.parse_qs(self.path.partition("?")[2]).get("id", [""])[0])
            self.send_json(d if d else {"fehler": "nicht gefunden"}, 200 if d else 404)
        elif path == "/api/ride/preview":
            pv = ride_preview(urllib.parse.parse_qs(self.path.partition("?")[2]).get("id", [""])[0])
            self.send_json({"p": pv} if pv else {"fehler": "nicht gefunden"}, 200 if pv else 404)
        elif path in ("/api/ride/gpx", "/api/ride/csv"):
            rid = urllib.parse.parse_qs(self.path.partition("?")[2]).get("id", [""])[0]
            txt = ride_gpx(rid) if path.endswith("gpx") else ride_csv(rid)
            if txt is None: return self.send_json({"fehler": "nicht gefunden"}, 404)
            b = txt.encode("utf-8"); self.send_response(200)
            self.send_header("Content-Type", "application/gpx+xml" if path.endswith("gpx") else "text/csv; charset=utf-8")
            self.send_header("Content-Disposition", f'attachment; filename="fahrt_{rid}.{path[-3:]}"'); self.send_header("Content-Length", str(len(b))); self.end_headers()
            if self.command != "HEAD": self.wfile.write(b)
        elif path == "/api/status":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            self.send_json(status())
        elif path == "/api/need":
            q = urllib.parse.parse_qs(self.path.partition("?")[2]); ids = [x for v in q.get("ids", []) for x in v.split(",") if x in BYID]
            if self.command != "HEAD" and ids:
                key = ",".join(sorted(ids))
                if time.time() - NEEDLOG.get(key, 0) > 300:
                    NEEDLOG[key] = time.time(); log("Handy meldet: " + ", ".join(BYID[i]["name"] for i in ids), cat="handy")
                request_build(ids)
            self.send_json({"regionen": {i: region_state(i) for i in ids}, "baut": S["state"] == "running", "detail": S["detail"]})
        else: self.serve_file(path)
    def serve_file(self, path):
        base = os.path.realpath(PUB); full = os.path.realpath(os.path.join(base, path.lstrip("/")))
        if not full.startswith(base + os.sep) or not os.path.isfile(full) or full.endswith(".part") or full.endswith(".new"):
            self.send_error(404); return
        size = os.path.getsize(full); a, b = 0, size - 1; code = 200
        rng = self.headers.get("Range", "")
        if rng.startswith("bytes="):
            try:
                x, _, y = rng[6:].split(",")[0].partition("-")
                if x == "": a = max(0, size - int(y))
                else: a = int(x); b = int(y) if y else size - 1
                b = min(b, size - 1)
                if a > b or a >= size: self.send_response(416); self.send_header("Content-Range", f"bytes */{size}"); self.end_headers(); return
                code = 206
            except ValueError: a, b, code = 0, size - 1, 200
        self.send_response(code)
        self.send_header("Content-Type", "application/json" if full.endswith(".json") else "application/octet-stream")
        self.send_header("Accept-Ranges", "bytes"); self.send_header("Content-Length", str(b - a + 1))
        self.send_header("X-Accel-Buffering", "no")   # der Proxy (NPM) soll nicht zwischenlagern, sonst sieht man hier nur den schnellen Teil bis zum Proxy
        self.send_header("Last-Modified", self.date_time_string(os.path.getmtime(full))); self.send_header("Cache-Control", "no-cache")
        if code == 206: self.send_header("Content-Range", f"bytes {a}-{b}/{size}")
        self.end_headers()
        if self.command == "HEAD": return
        tx = xfer_start(self, path, a, size)
        try:
            with open(full, "rb") as f:
                f.seek(a); left = b - a + 1
                while left > 0:
                    d = f.read(min(262144, left))
                    if not d: break
                    self.wfile.write(d); left -= len(d); xfer_add(tx, len(d))
        except (BrokenPipeError, ConnectionResetError): pass
        finally: xfer_end(tx)
    def authed(self):
        if not ADMIN_PW: return False
        if hmac.compare_digest(self.headers.get("X-Admin-Password", ""), ADMIN_PW): return True
        for part in self.headers.get("Cookie", "").split(";"):
            k, _, v = part.strip().partition("=")
            if k == "hudsess" and hmac.compare_digest(v, SESS_TOKEN): return True
        return False
    def send_page(self, name, code=200):
        b = open(os.path.join(HERE, "static", name), "rb").read()
        if b"<!--AUTH-->" in b: b = b.replace(b"<!--AUTH-->", open(os.path.join(HERE, "static", "auth.html"), "rb").read())
        if self.extern(): b = re.sub(rb"<!--(ADMIN|NEWPROF)-->.*?<!--/\1-->", b"", b, flags=re.S)   # von außen kein Verwaltungs-Link und kein "Neu anlegen"
        else: b = re.sub(rb"<!--EXT-->.*?<!--/EXT-->", b"", b, flags=re.S)   # nur von außen sichtbarer Text (zuhause steht dort etwas anderes)
        if b"<!--NAV-->" in b: b = b.replace(b"<!--NAV-->", open(os.path.join(HERE, "static", "nav.html"), "rb").read())
        self.send_response(code); self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(b))); self.send_header("Cache-Control", "no-store"); self.end_headers()
        if self.command != "HEAD": self.wfile.write(b)
    def do_POST(self):
        try:
            ln = int(self.headers.get("Content-Length", "0"))
            if ln > 40_000_000: return self.send_json({"fehler": "Zu groß."}, 413)
            body = json.loads(self.rfile.read(ln) or b"{}")
        except Exception: return self.send_json({"fehler": "Ungültige Eingabe."}, 400)
        p = self.path.split("?")[0]
        if self.extern() and p not in PROF_POST:   # von außen nur Profil-Funktionen; neue Profile nur im Heimnetz
            if p == "/api/profile/create": return self.send_json({"fehler": "Neue Profile gibt es nur im Heimnetz. Den Zugang legt der Admin an."}, 403)
            return self.deny(p)
        if p == "/api/login":
            if not ADMIN_PW: return self.send_json({"fehler": "ADMIN_PASSWORD ist im Container nicht gesetzt."}, 403)
            ak = "a:" + self.real_ip()
            if blocked(ak): return self.send_json({"fehler": "Zu viele Versuche. Bitte in 15 Minuten nochmal."}, 429)
            if not hmac.compare_digest(str(body.get("pw", "")), ADMIN_PW):
                fail(ak); log(f"Admin-Anmeldung fehlgeschlagen ({self.real_ip()})", cat="anmeldung"); return self.send_json({"fehler": "Falsches Passwort."}, 401)
            log(f"Admin angemeldet ({self.real_ip()})", cat="anmeldung")
            b = json.dumps({"ok": True}).encode()
            self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(b)))
            self.send_header("Set-Cookie", f"hudsess={SESS_TOKEN}; Path=/; Max-Age=2592000; HttpOnly; SameSite=Strict"); self.end_headers(); self.wfile.write(b); return
        if p == "/api/logout":
            b = b"{}"; self.send_response(200); self.send_header("Content-Length", "2")
            self.send_header("Set-Cookie", "hudsess=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict"); self.end_headers(); self.wfile.write(b); return
        if p == "/api/profile/create":
            ip = self.ipkey()
            if blocked(ip): return self.send_json({"fehler": "Zu viele Versuche. Bitte in 15 Minuten nochmal."}, 429)
            try: return self.send_json({"ok": True, "name": str(body.get("name", "")).strip(), "code": profile_create(body.get("name", ""), body.get("invite", ""))})
            except PermissionError as ex: fail(ip); return self.send_json({"fehler": str(ex)}, 403)
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 400)
        if p in PROF_POST and not self.need_profile(): return
        if p in PROF_POST and p != "/api/live": log(f"{CUR.name}: {p[5:]}", cat="anmeldung")
        if p == "/api/live":
            try: FRI.update(CUR.name, body.get("lat"), body.get("lon"), body.get("spd", 0), body.get("hdg", 0))
            except (ValueError, TypeError) as ex: return self.send_json({"fehler": str(ex)}, 400)
            return self.send_json({"ok": True, "sicht": FRI.viewers(CUR.name)})
        if p == "/api/live/stop": FRI.stop(CUR.name); return self.send_json({"ok": True})
        if p.startswith("/api/friends/"):
            who = str(body.get("name", "")).strip(); k, v = find_profile(who)
            if not v or v.get("disabled"): return self.send_json({"fehler": "Dieses Profil gibt es nicht."}, 404)
            try:
                if p == "/api/friends/request":
                    res = FRI.request(CUR.name, k); log(f"Freunde: {CUR.name} → {k}: {res}", cat="freunde"); return self.send_json({"ok": True, "status": res})
                if p == "/api/friends/respond":
                    acc = bool(body.get("accept")); FRI.respond(CUR.name, k, acc); log(f"Freunde: {CUR.name} {'nimmt an' if acc else 'lehnt ab'}: {k}", cat="freunde"); return self.send_json({"ok": True})
                if p == "/api/friends/remove":
                    FRI.remove(CUR.name, k); log(f"Freunde: {CUR.name} entfernt {k}", cat="freunde"); return self.send_json({"ok": True})
                if p == "/api/friends/permit":
                    FRI.permit(CUR.name, k, body.get("allow")); log(f"Freunde: {CUR.name} {'erlaubt' if body.get('allow') else 'verbietet'} {k} die Ansicht", cat="freunde"); return self.send_json({"ok": True})
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 400)
        if p not in PROF_POST:   # alles andere nur mit Admin-Passwort
            if not ADMIN_PW: return self.send_json({"fehler": "ADMIN_PASSWORD ist nicht gesetzt - Änderungen sind abgeschaltet."}, 403)
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
        if p == "/api/admin/invite":
            v = str(body.get("invite", "")).strip().upper()
            if not re.match(r"^[A-Z0-9]{4,20}$", v): return self.send_json({"fehler": "Einladungscode: 4 bis 20 Buchstaben oder Zahlen."}, 400)
            with PLOCK: PST["invite"] = v; save_profiles()
            return self.send_json({"ok": True})
        if p == "/api/admin/profile":
            act = str(body.get("action", "")); name = str(body.get("name", "")); k, v = find_profile(name)
            try:
                if act == "create": return self.send_json({"ok": True, "code": _profile_add(name.strip()) if NAME_RE.match(name.strip()) else (_ for _ in ()).throw(ValueError("Name: 2 bis 24 Zeichen."))})
                if not v: return self.send_json({"fehler": "Profil nicht gefunden."}, 404)
                if act == "resetcode":
                    with PLOCK: v["code"] = new_code(); save_profiles()
                    return self.send_json({"ok": True, "code": v["code"]})
                if act in ("disable", "enable"):
                    with PLOCK: v["disabled"] = act == "disable"; save_profiles()
                    return self.send_json({"ok": True})
                if act == "delete":
                    if str(body.get("confirm", "")).strip().lower() != k.lower(): return self.send_json({"fehler": "Zum Löschen den Namen bestätigen."}, 400)
                    with PLOCK: PST["profiles"].pop(k, None); save_profiles()
                    shutil.rmtree(profile_dir(v["slug"]), ignore_errors=True); FRI.remove_profile(k); NOTES.remove_profile(k); log("Profil gelöscht:", k)
                    return self.send_json({"ok": True})
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 400)
            return self.send_json({"fehler": "unbekannt"}, 400)
        if p == "/api/admin/scdb":   # {"name": "datei.gpx", "b64": "..."}  (GPX oder ZIP mit GPX); danach wird die Liste neu berechnet
            try:
                import base64
                raw = base64.b64decode(str(body.get("b64", "")), validate=False)
                n = scdb_store(str(body.get("name", "scdb.gpx")), raw)
                log(f"SCDB-Upload: {str(body.get('name', ''))[:60]} ({fmt_size(len(raw))}, {n} GPX-Datei(en))", cat="blitzer")
                if n == 0: return self.send_json({"fehler": "Keine GPX-Datei erkannt (GPX oder ZIP mit GPX-Dateien erwartet)."}, 400)
                if body.get("rebuild", True) is False: return self.send_json({"ok": True, "dateien": n})
                return self.send_json({"ok": True, "dateien": n, "anzahl": scdb_rebuild(), "scdb": scdb_info()})
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 400)
            except Exception as ex:
                log("FEHLER SCDB:", ex); return self.send_json({"fehler": str(ex)[:300]}, 500)
        if p == "/api/admin/regionen-holen":
            try: n, missing = regions_fetch(); return self.send_json({"ok": True, "anzahl": n, "fehlend": missing, "regionsliste": regions_info()})
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 409)
            except Exception as ex: log("FEHLER Regionsliste:", ex); return self.send_json({"fehler": str(ex)[:300]}, 502)
        if p == "/api/admin/region/loeschen":
            try: return self.send_json({"ok": True, "frei": region_delete(str(body.get("id", "")))})
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 409)
        if p == "/api/admin/friends/entfernen":
            if not FRI.remove_link(str(body.get("a", "")), str(body.get("b", ""))): return self.send_json({"fehler": "Diese Verbindung gibt es nicht."}, 404)
            log(f"Freunde: Admin entfernt {body.get('a')} – {body.get('b')}", cat="freunde"); return self.send_json({"ok": True})
        if p == "/api/admin/handy/entfernen":
            if not dev_remove(str(body.get("id", "")).strip().lower()): return self.send_json({"fehler": "Dieses Handy gibt es nicht (mehr)."}, 404)
            return self.send_json({"ok": True})
        if p == "/api/admin/scdb/rebuild":
            try: return self.send_json({"ok": True, "anzahl": scdb_rebuild(), "scdb": scdb_info()})
            except ValueError as ex: return self.send_json({"fehler": str(ex)}, 400)
            except Exception as ex: return self.send_json({"fehler": str(ex)[:300]}, 500)
        if p == "/api/admin/scdb/clear":
            shutil.rmtree(SCDB_DIR, ignore_errors=True); scdb_rebuild(); log("SCDB-Liste geleert", cat="blitzer"); return self.send_json({"ok": True, "scdb": scdb_info()})
        if p == "/api/run":
            ids = [i for i in body.get("ids", CFG["regions"]) if i in BYID]
            if not ids: return self.send_json({"fehler": "Keine Region gewählt."}, 400)
            if not start_run(ids, "poi" if body.get("only") == "poi" else None): return self.send_json({"fehler": "Es läuft schon ein Bau."}, 409)
            return self.send_json({"ok": True})
        if p == "/api/stop":
            if S["state"] == "running" and not S["stop"]:
                S["stop"] = True; log("Abbrechen gedrückt"); set_detail("Abbruch läuft …")
                with PROCS_LOCK: ps = list(PROCS)
                for pr in ps:
                    try: pr.terminate()
                    except Exception: pass
            return self.send_json({"ok": True})
        if p == "/api/admin/logcfg":
            lv = str(body.get("level", ""))
            with CFG_LOCK:
                if lv in LOG_PRESETS: CFG["log_level"] = lv; CFG["log_cats"] = list(LOG_PRESETS[lv])
                if isinstance(body.get("cats"), list):
                    CFG["log_cats"] = [k for k, _ in LOG_CATS if k in body["cats"]]
                    CFG["log_level"] = next((k for k, v in LOG_PRESETS.items() if sorted(v) == sorted(CFG["log_cats"])), "eigene")
                save_cfg()
            return self.send_json({"ok": True, "level": CFG["log_level"], "cats": CFG["log_cats"]})
        if p == "/api/admin/baustellen/abrufen":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            if body.get("stop"): BSS["stop"] = True; return self.send_json({"ok": True})
            return self.send_json({"ok": bs_run(force=True)})
        if p == "/api/admin/notify":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            if "profil" in body:
                k, _ = find_profile(str(body.get("profil", "")))
                if not k: return self.send_json({"fehler": "Profil nicht gefunden."}, 404)
                NOTES.set_recip(k, bool(body.get("an"))); log("Admin-Meldungen für %s: %s" % (k, "an" if body.get("an") else "aus"))
            elif "typ" in body:
                if not NOTES.set_type(str(body.get("typ")), bool(body.get("an"))): return self.send_json({"fehler": "Unbekannte Art."}, 400)
            elif "info" in body:
                if not NOTES.set_info(str(body.get("info")), bool(body.get("an"))): return self.send_json({"fehler": "Unbekannte Info."}, 400)
            elif body.get("test"): NOTES.add("test", "Testmeldung aus der Verwaltung.")
            elif body.get("leeren"): NOTES.clear()
            return self.send_json({"ok": True, **NOTES.admin()})
        if p == "/api/admin/routing/leistung":
            if not self.authed(): return self.send_json({"fehler": "Bitte anmelden."}, 401)
            try:
                with CFG_LOCK:
                    CFG["br_mem_mb"] = max(512, min(16384, int(body.get("mem_mb", CFG["br_mem_mb"]))))
                    CFG["br_threads"] = max(1, min(32, int(body.get("threads", CFG["br_threads"]))))
                    save_cfg()
            except (ValueError, TypeError): return self.send_json({"fehler": "Ungültiger Wert."}, 400)
            log(f"Routing-Leistung: {CFG['br_mem_mb']} MB, {CFG['br_threads']} Berechnungen, BRouter startet neu", cat="karte")
            def _restart():
                try: stop_brouter(); start_brouter()
                except Exception as ex: log("BRouter-Neustart:", ex)
            threading.Thread(target=_restart, daemon=True).start()
            return self.send_json({"ok": True})
        if p == "/api/admin/logclear":
            log_clear(); log("Protokoll geleert"); return self.send_json({"ok": True})
        if p == "/api/config":
            try:
                with CFG_LOCK:
                    for k in ("limit_in_mbit", "limit_out_mbit"):
                        if k in body: CFG[k] = max(0.0, float(body[k]))
                    for k in ("win_start", "win_end"):
                        if k in body: CFG[k] = int(body[k]) % 24
                    if "every_days" in body: CFG["every_days"] = max(1, int(body["every_days"]))
                    if "br_mem_mb" in body: CFG["br_mem_mb"] = max(512, min(16384, int(body["br_mem_mb"])))
                    if "br_threads" in body: CFG["br_threads"] = max(1, min(32, int(body["br_threads"])))
                    if "extern_karten" in body:
                        CFG["extern_karten"] = bool(body["extern_karten"]); log("Karten extern ladbar: " + ("AN" if CFG["extern_karten"] else "aus"))
                    if "regions" in body: CFG["regions"] = [i for i in body["regions"] if i in BYID]
                    if "nur_gebaute" in body: CFG["nur_gebaute"] = bool(body["nur_gebaute"])
                    if "versteckt" in body: CFG["versteckt"] = sorted({i for i in body["versteckt"] if i in BYID})
                    save_cfg()
                if "nur_gebaute" in body or "versteckt" in body:
                    write_manifest(); log("App-Sichtbarkeit geändert: " + ("nur gebaute Regionen" if CFG["nur_gebaute"] else "alle Regionen") + f", {len(CFG['versteckt'])} ausgeblendet", cat="karte")
            except (ValueError, TypeError): return self.send_json({"fehler": "Ungültiger Wert."}, 400)
            return self.send_json({"ok": True})
        if p == "/api/tour/save":
            try: return self.send_json({"ok": True, "id": tour_save(body)})
            except (ValueError, TypeError, KeyError, IndexError) as ex: return self.send_json({"fehler": f"Ungültige Tour: {ex}"}, 400)
        if p == "/api/tour/delete":
            tid = str(body.get("id", ""))
            if tid.isalnum() and os.path.exists(_tour_path(tid)): os.remove(_tour_path(tid))
            return self.send_json({"ok": True})
        if p == "/api/tour/rename": return self.send_json({"ok": tour_rename(str(body.get("id", "")), body.get("name", ""))})
        if p == "/api/ride/upload":
            try: ride_upload(body); return self.send_json({"ok": True})
            except (ValueError, TypeError) as ex: return self.send_json({"fehler": f"Ungültige Fahrt: {ex}"}, 400)
        if p == "/api/ride/demo":
            if body.get("weg"): demo_delete()
            else: demo_create()
            return self.send_json({"ok": True})
        if p == "/api/ride/rename": return self.send_json({"ok": ride_rename(str(body.get("id", "")), body.get("name", ""))})
        if p == "/api/ride/delete": ride_delete(str(body.get("id", ""))); return self.send_json({"ok": True})
        if p == "/api/favs/save":
            if not isinstance(body.get("favs"), list) or len(body["favs"]) > 1000: return self.send_json({"fehler": "Ungültig"}, 400)
            return self.send_json({"ok": True, "favs": favs_merge(body["favs"])})
        if p == "/api/settings/save":
            try: settings_save(body); return self.send_json({"ok": True})
            except (ValueError, TypeError) as ex: return self.send_json({"fehler": f"Ungültig: {ex}"}, 400)
        if p == "/api/phone":
            try: outbox_set(body); return self.send_json({"ok": True})
            except (ValueError, TypeError, KeyError, IndexError) as ex: return self.send_json({"fehler": f"Ungültige Tour: {ex}"}, 400)
        self.send_json({"fehler": "unbekannt"}, 404)

def main():
    os.makedirs(PUB, exist_ok=True); load_cfg(); log_load(); save_cfg(); load_profiles(); dev_load(); dev_cleanup()
    try: migrate_old()
    except Exception as e: log(f"Profil-Umzug: {e}")
    try: write_manifest()   # Regionsliste (auch neue Regionen) gleich beim Start erneuern
    except Exception as ex: log("Manifest-Fehler:", ex)
    ps = ProxyServer(("127.0.0.1", PROXY_PORT), ProxyH); threading.Thread(target=ps.serve_forever, daemon=True).start()
    try: NOTES.add("start", "Der Server wurde gestartet (Version %s)." % VERSION); NOTES.check_version(VERSION)
    except Exception as ex: log("Start-Meldung:", ex)
    if len(sys.argv) > 1 and sys.argv[1] == "now": start_run(scheduled_ids())
    threading.Thread(target=scheduler, daemon=True).start()
    def br_boot():
        try: prepare_brouter_profiles(); start_brouter()
        except Exception as ex: log("BRouter-Start-Fehler:", ex)
    threading.Thread(target=br_boot, daemon=True).start()
    log(f"Statusseite auf Port {PORT}", "(Änderungen aus)" if not ADMIN_PW else "")
    class Srv(ThreadingHTTPServer): daemon_threads = True
    Srv(("0.0.0.0", PORT), H).serve_forever()
if __name__ == "__main__": main()
