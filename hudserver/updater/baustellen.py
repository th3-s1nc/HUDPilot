"""Baustellen und Sperrungen (Mobilithek, DATEX II): große XML-Datei -> kleine Liste fürs Handy.

Nur Standardbibliothek. Zum Testen mit Dateien gibt es parse(); der Abruf mit Zertifikat steht in fetch_live().
"""
import datetime, json, math, os, re, subprocess, time
import xml.etree.ElementTree as ET

D2 = "{http://datex2.eu/schema/2/2_0}"
XSI = "{http://www.w3.org/2001/XMLSchema-instance}type"
KEEP = {"MaintenanceWorks", "ConstructionWorks", "RoadOrCarriagewayOrLaneManagement", "GeneralObstruction"}
RESTRICT = {"laneClosures", "narrowLanes", "singleAlternateLineTraffic", "intermittentShortTermClosures", "contraflow",
            "useOfSpecifiedLanesOrCarriagewaysAllowed", "doNotUseSpecifiedLanesOrCarriageways", "temporaryTrafficLightsInOperation"}
ROAD_RE = re.compile(r"^\s*((?:A|B|St|S|K|M|NEA|WÜ|AS|DLG|RH|PAN|TS|R)\s?\d+)", re.I)


def _iso(s):
    try: return int(datetime.datetime.strptime(s.strip(), "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc).timestamp())
    except Exception:
        try: return int(datetime.datetime.fromisoformat(s.strip().replace("Z", "+00:00")).timestamp())
        except Exception: return 0


def _txt(e, tag):
    x = e.find(".//" + D2 + tag)
    return x.text.strip() if x is not None and x.text else ""


def _comment(rec):
    best = {}
    for c in rec.findall(D2 + "generalPublicComment"):
        typ = _txt(c, "commentType")
        v = c.find(".//" + D2 + "value")
        if v is not None and v.text and v.text.strip(): best.setdefault(typ, v.text.strip())
    return best.get("description") or best.get("locationDescriptor") or next(iter(best.values()), "")


def _point(e):
    pc = e.find(".//" + D2 + "pointCoordinates") if e is not None else None
    if pc is None: return None
    try: return [round(float(pc.find(D2 + "latitude").text), 5), round(float(pc.find(D2 + "longitude").text), 5)]
    except Exception: return None


def _kind(rec, text):
    if re.search(r"vollsperrung|gesperrt|sperrung", text, re.I) and not re.search(r"keine sperrung|aufgehoben", text, re.I): return "c"
    for t in rec.iter(D2 + "roadOrCarriagewayOrLaneManagementType"):
        if t.text == "roadClosed": return "c"
        if t.text in RESTRICT: return "r"
    for t in rec.iter(D2 + "roadMaintenanceType"):
        if t.text in ("laneClosures", "narrowLanes", "singleAlternateLineTraffic"): return "r"
    if re.search(r"ampel|einen? fahrstreifen|engstelle|fahrbahnverengung|wechselseitig", text, re.I): return "r"
    return "w"


def parse(src, progress=None):
    """XML lesen. Gibt (liste, publikationszeit) zurück; liste = alle Datensätze mit Ort, noch ungefiltert."""
    size = os.path.getsize(src) if isinstance(src, str) else 0
    out = []; pub = 0; seen = set(); n = 0
    f = open(src, "rb") if isinstance(src, str) else src
    try:
        for ev, el in ET.iterparse(f, events=("end",)):
            tag = el.tag
            if tag == D2 + "publicationTime" and not pub and el.text: pub = _iso(el.text)
            if tag != D2 + "situation": continue
            n += 1
            for rec in el.findall(D2 + "situationRecord"):
                if rec.get(XSI) not in KEEP: continue
                loc = rec.find(D2 + "groupOfLocations")
                if loc is None: continue
                a = _point(loc.find(".//" + D2 + "from")); b = _point(loc.find(".//" + D2 + "to"))
                disp = _point(loc.find(D2 + "locationForDisplay"))
                a = a or disp or b; b = b or a
                if not a: continue
                text = _comment(rec)
                v = rec.find(D2 + "validity")
                s = _iso(_txt(v, "overallStartTime")) if v is not None and _txt(v, "overallStartTime") else 0
                e = _iso(_txt(v, "overallEndTime")) if v is not None and _txt(v, "overallEndTime") else 0
                k = _kind(rec, text)
                key = (tuple(a), tuple(b), k, s, e)
                if key in seen: continue
                seen.add(key)
                m = ROAD_RE.match(text)
                out.append({"i": rec.get("id", "")[-40:], "r": re.sub(r"\s", "", m.group(1)) if m else "", "t": text[:400], "k": k, "a": a, "b": b, "s": s, "e": e})
            el.clear()
            if progress and size and n % 25 == 0:
                try: progress(min(0.99, f.tell() / size))
                except Exception: pass
    finally:
        if isinstance(src, str): f.close()
    return out, pub


def active(items, ref, ahead_days=90):
    """Nur, was zum Zeitpunkt ref gilt oder in den nächsten Tagen beginnt (ohne Ende = gilt weiter)."""
    lim = ref + ahead_days * 86400
    return [x for x in items if (x["e"] == 0 or x["e"] >= ref) and (x["s"] == 0 or x["s"] <= lim)]


def hav(a, b):
    p = math.pi / 180
    h = 0.5 - math.cos((b[0] - a[0]) * p) / 2 + math.cos(a[0] * p) * math.cos(b[0] * p) * (1 - math.cos((b[1] - a[1]) * p)) / 2
    return 12742000 * math.asin(math.sqrt(max(0, h)))


def _perp(p, a, b):
    """Abstand Punkt p von Strecke ab in Metern (flache Näherung, reicht für kurze Strecken)."""
    k = 111320 * math.cos(math.radians(a[0]))
    ax, ay, bx, by, px, py = a[1] * k, a[0] * 110540, b[1] * k, b[0] * 110540, p[1] * k, p[0] * 110540
    dx, dy = bx - ax, by - ay
    if dx == dy == 0: return math.hypot(px - ax, py - ay)
    t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
    return math.hypot(px - ax - t * dx, py - ay - t * dy)


def simplify(pts, tol=6.0):
    """Douglas-Peucker (iterativ)."""
    if len(pts) < 3: return pts
    keep = [False] * len(pts); keep[0] = keep[-1] = True; stack = [(0, len(pts) - 1)]
    while stack:
        i, j = stack.pop(); dmax, idx = 0, -1
        for k in range(i + 1, j):
            d = _perp(pts[k], pts[i], pts[j])
            if d > dmax: dmax, idx = d, k
        if dmax > tol and idx > 0: keep[idx] = True; stack += [(i, idx), (idx, j)]
    return [p for p, k in zip(pts, keep) if k]


def add_geometry(items, route_fn, cache, progress=None, stop=lambda: False):
    """Zu jedem Eintrag die echte Straße zwischen Anfang und Ende holen (route_fn(a, b) -> [[lat, lon], ...] oder None).
    Ist der Weg unplausibel länger als die Luftlinie, bleibt es bei der geraden Linie."""
    ok = fail = misses = 0
    for n, x in enumerate(items):
        if stop(): break
        key = f"{x['a'][0]},{x['a'][1]}|{x['b'][0]},{x['b'][1]}"
        d = hav(x["a"], x["b"])
        if d >= 25:
            g = cache.get(key)
            if not g and misses < 25:
                try: g = route_fn(x["a"], x["b"])
                except Exception: g = None
                if g: cache[key] = g; misses = 0   # nur Erfolge merken, Misserfolge werden beim nächsten Mal wieder versucht
                else: misses += 1                  # 25 Fehlschläge hintereinander: keine Routendaten da, der Rest bleibt gerade
            if g:
                length = sum(hav(g[i], g[i + 1]) for i in range(len(g) - 1))
                if len(g) >= 3 and length <= 3 * d + 300:
                    x["g"] = [[round(p[0], 5), round(p[1], 5)] for p in simplify(g)]; ok += 1
                else: fail += 1
            else: fail += 1
        if progress and n % 10 == 0: progress((n + 1) / max(1, len(items)))
    return ok, fail


def fetch_live(cfg, out_path, last_modified=""):
    """Abruf per HTTPS-GET mit Maschinenzertifikat (curl). Gibt (status, last_modified) zurück; status: 'neu', 'unveraendert' oder Fehlertext."""
    import gzip, shutil
    hdr = out_path + ".hdr"; raw = out_path + ".raw"
    url = f"https://mobilithek.info:8443/mobilithek/api/v1.0/subscription/{cfg['abo']}/clientPullService?subscriptionID={cfg['abo']}"
    cmd = ["curl", "-sS", "-m", "300", "-o", raw, "-D", hdr, "-w", "%{http_code}", "--cert", f"{cfg['p12']}:{cfg['pass']}", "--cert-type", "P12",
           "-H", "Accept-Encoding: gzip", url]
    if last_modified: cmd[1:1] = ["-H", "If-Modified-Since: " + last_modified]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=330)
    code = (r.stdout or "").strip()[-3:]
    if r.returncode != 0: return ("Abruf gescheitert: " + (r.stderr or "").strip()[:200], last_modified)
    if code == "304": return ("unveraendert", last_modified)
    if code != "200":
        why = {"403": "keine Berechtigung (Abo noch nicht freigegeben?)", "404": "Abo ungültig oder Zugriffsgrenze erreicht", "204": "kein Datenpaket vorhanden",
               "400": "Anfrage fehlerhaft", "406": "gzip fehlt"}.get(code, "")
        return (f"Mobilithek antwortet mit HTTP {code} {why}".strip(), last_modified)
    lm = last_modified
    try:
        for line in open(hdr, encoding="latin-1"):
            if line.lower().startswith("last-modified:"): lm = line.split(":", 1)[1].strip()
    except Exception: pass
    with open(raw, "rb") as f: magic = f.read(2)
    if magic == b"\x1f\x8b":
        with gzip.open(raw, "rb") as a, open(out_path, "wb") as b: shutil.copyfileobj(a, b)
    else: shutil.copyfile(raw, out_path)
    for p in (raw, hdr):
        try: os.remove(p)
        except Exception: pass
    return ("neu", lm)


# ---------- Sperrungen auf einer Route erkennen und umfahren (Webplaner) ----------

def closed_now(items, ref):
    """Gesperrte Meldungen, die zum Zeitpunkt ref gelten."""
    return [x for x in items if x.get("k") == "c" and (x["e"] == 0 or x["e"] >= ref) and (x["s"] == 0 or x["s"] <= ref)]


def _line(x):
    g = x.get("g")
    return g if g and len(g) >= 2 else [x["a"], x["b"]]


def _cross(a, b, c, d):
    def side(p, q, r): return (q[1] - p[1]) * (r[0] - p[0]) - (q[0] - p[0]) * (r[1] - p[1])
    d1, d2, d3, d4 = side(a, b, c), side(a, b, d), side(c, d, a), side(c, d, b)
    return ((d1 > 0 > d2) or (d1 < 0 < d2)) and ((d3 > 0 > d4) or (d3 < 0 < d4))


def hits(route, sites, radius=35.0):
    """Welche Meldungen liegen auf der Route? route = [[lat, lon], ...]. Rückgabe: Liste der getroffenen Meldungen."""
    if len(route) < 2: return []
    padlat = radius / 110540.0; out = []
    for x in sites:
        ln = _line(x)
        la0 = min(p[0] for p in ln) - padlat; la1 = max(p[0] for p in ln) + padlat
        padlon = radius / (111320.0 * max(0.2, math.cos(math.radians(ln[0][0]))))
        lo0 = min(p[1] for p in ln) - padlon; lo1 = max(p[1] for p in ln) + padlon
        idx = [i for i, p in enumerate(route) if la0 <= p[0] <= la1 and lo0 <= p[1] <= lo1]
        if not idx: continue
        segs = set()
        for i in idx:
            if i > 0: segs.add(i - 1)
            if i < len(route) - 1: segs.add(i)
        hit = False
        for i in segs:
            a, b = route[i], route[i + 1]
            for p in ln:
                if _perp(p, a, b) <= radius: hit = True; break
            if hit: break
            for j in range(len(ln) - 1):
                if _perp(a, ln[j], ln[j + 1]) <= radius or _perp(b, ln[j], ln[j + 1]) <= radius or _cross(a, b, ln[j], ln[j + 1]): hit = True; break
            if hit: break
        if hit: out.append(x)
    return out


def nogo_param(sites, route, pad=0.25, max_vertices=250, max_pts=40):
    """BRouter-Parameter polylines (lon,lat,...|...) für gesperrte Meldungen nahe der Route; die nächsten zuerst, gedeckelt (die Adresse darf nicht zu lang werden)."""
    if not route: return ""
    la0 = min(p[0] for p in route) - pad; la1 = max(p[0] for p in route) + pad
    lo0 = min(p[1] for p in route) - pad; lo1 = max(p[1] for p in route) + pad
    samp = route[::max(1, len(route) // 200)]
    cand = []
    for x in sites:
        ln = _line(x)
        if max(p[0] for p in ln) < la0 or min(p[0] for p in ln) > la1 or max(p[1] for p in ln) < lo0 or min(p[1] for p in ln) > lo1: continue
        d = min(hav(ln[0], p) for p in samp)
        cand.append((d, ln))
    cand.sort(key=lambda t: t[0])
    parts, used = [], 0
    for _, ln in cand:
        if len(ln) > max_pts: ln = [ln[i * (len(ln) - 1) // (max_pts - 1)] for i in range(max_pts)]
        if used + len(ln) > max_vertices: break
        used += len(ln)
        parts.append(",".join("%.6f,%.6f" % (p[1], p[0]) for p in ln))
    return "|".join(parts)
