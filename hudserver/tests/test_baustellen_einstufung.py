"""Einstufung der Baustellen-Meldungen, Straßensuche mit Straßennummer, brauchbare Linien. Texte stammen aus der echten Liste."""
import os, sys, unittest
import xml.etree.ElementTree as ET
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import baustellen as B

E = ET.Element("x")
AUSW = "St2089 Kufstein Richtung Bad Aibling | zwischen A8, Bad Aibling und Pullach | Beschränkungen, von 18.12.2026 00:00 Uhr bis 20.12.2026 23:59 Uhr | Gesperrt für Ausweichverkehr bei Stau auf der Autobahn. Gültig an Freitagen, Samstagen, Sonntagen und Feiertagen."


class KindTest(unittest.TestCase):
    def test_ausweichverkehr_ist_nur_eingeschraenkt(self): self.assertEqual(B._kind(E, AUSW), "r")
    def test_gesperrte_spur_ist_nur_eingeschraenkt(self):
        self.assertEqual(B._kind(E, "B15 Saalhaupt Richtung Landshut | zwischen Ausfahrt A93 und Neufahrn | linker Fahrstreifen gesperrt, Fahrbahn auf 1 Fahrstreifen verengt"), "r")
        self.assertEqual(B._kind(E, "B85 Amberg, Kurfürstenring | rechter Fahrstreifen gesperrt, Baustelle"), "r")
    def test_echte_sperrung_bleibt(self):
        self.assertEqual(B._kind(E, "St2129 zwischen Eppenschlag und Beiwald | gesperrt, Fahrbahnerneuerung, bis 13.11.2026 15:00 Uhr, empfohlene Umleitung"), "c")
        self.assertEqual(B._kind(E, "B19 Einfahrt Sonthofen-Süd | Verbindungsfahrbahn gesperrt, eine örtliche Umleitung ist eingerichtet"), "c")
    def test_alle_spuren_oder_vollsperrung_bleibt_gesperrt(self):
        self.assertEqual(B._kind(E, "A8 | beide Fahrstreifen gesperrt, Baustelle"), "c")
        self.assertEqual(B._kind(E, "A8 | Vollsperrung, linker Fahrstreifen und rechter Fahrstreifen"), "c")
    def test_zaehler_fuers_protokoll(self):
        B.STAT["spur"] = B.STAT["ausweich"] = 0
        B._kind(E, AUSW); B._kind(E, "x | linker Fahrstreifen gesperrt"); B._kind(E, "x | gesperrt")
        self.assertEqual((B.STAT["spur"], B.STAT["ausweich"]), (1, 1))


class UsableTest(unittest.TestCase):
    A = [48.0, 12.0]
    def test_mit_strasse_immer(self): self.assertTrue(B.usable({"a": self.A, "b": [48.1, 12.1], "g": [[48.0, 12.0], [48.1, 12.1]]}))
    def test_kurze_luftlinie_ja(self): self.assertTrue(B.usable({"a": self.A, "b": [48.001, 12.0]}))    # ca. 110 m
    def test_lange_luftlinie_nein(self): self.assertFalse(B.usable({"a": self.A, "b": [48.03, 12.03]}))   # ca. 4 km
    def test_closed_now_laesst_lange_luftlinie_weg(self):
        lang = {"k": "c", "e": 0, "s": 0, "a": self.A, "b": [48.03, 12.03]}
        gut = {"k": "c", "e": 0, "s": 0, "a": self.A, "b": [48.03, 12.03], "g": [self.A, [48.03, 12.03]]}
        self.assertEqual(B.closed_now([lang, gut], 100), [gut])


class RefTest(unittest.TestCase):
    HEAD = ["Longitude", "Latitude", "Elevation", "Distance", "CostPerKm", "ElevCost", "TurnCost", "NodeCost", "InitialCost", "WayTags", "NodeTags"]
    def msgs(self, *rows): return [self.HEAD] + [["0", "0", "0", str(d), "0", "0", "0", "0", "0", t, ""] for d, t in rows]

    def test_klassen(self):
        self.assertIn("primary", B.ref_classes("B15")); self.assertIn("secondary", B.ref_classes("St2129"))
        self.assertIn("motorway", B.ref_classes("A93")); self.assertIn("tertiary", B.ref_classes("R35")); self.assertIsNone(B.ref_classes(""))

    def test_anteil_auf_der_richtigen_strassenklasse(self):
        m = self.msgs((800, "highway=secondary surface=asphalt"), (100, "highway=track"), (100, "highway=secondary_link"))
        self.assertAlmostEqual(B.class_share(m, {"secondary"}), 0.9)
        self.assertEqual(B.class_share(m, {"primary"}), 0.0); self.assertEqual(B.class_share([], {"primary"}), 0.0)

    def test_zweiter_versuch_mit_strassennummer(self):
        a = [48.9, 12.8]; b = [48.93, 12.83]   # etwa 4 km Luftlinie
        bogen = [a, [48.95, 12.80], b]   # viel länger als 1,3-fach: erster Versuch scheitert
        items = [{"a": a, "b": b, "r": "St2129", "k": "c", "s": 0, "e": 0}]
        ok, fail = B.add_geometry(items, lambda x, y: bogen, {}, ref_fn=lambda x, y, r: bogen if r == "St2129" else None)
        self.assertEqual((ok, fail), (1, 0)); self.assertTrue(items[0]["g"]); self.assertEqual(B.add_geometry.ref_ok, 1)

    def test_ohne_nummer_bleibt_luftlinie(self):
        a = [48.9, 12.8]; b = [48.93, 12.83]; bogen = [a, [48.95, 12.80], b]
        items = [{"a": a, "b": b, "r": "", "k": "c", "s": 0, "e": 0}]
        calls = []
        ok, fail = B.add_geometry(items, lambda x, y: bogen, {}, ref_fn=lambda *z: calls.append(1))
        self.assertEqual((ok, fail), (0, 1)); self.assertNotIn("g", items[0]); self.assertEqual(calls, [])

    def test_zu_langer_umweg_wird_auch_mit_nummer_verworfen(self):
        a = [48.9, 12.8]; b = [48.93, 12.83]; weit = [a, [49.5, 12.8], [49.5, 13.5], b]
        items = [{"a": a, "b": b, "r": "B15", "k": "c", "s": 0, "e": 0}]
        ok, fail = B.add_geometry(items, lambda x, y: weit, {}, ref_fn=lambda *z: weit)
        self.assertEqual((ok, fail), (0, 1))


class NachbessernTest(unittest.TestCase):
    def test_reklass_nur_nach_text(self):
        x = {"k": "c", "t": AUSW}; self.assertTrue(B.reklass(x)); self.assertEqual(x["k"], "r")
        y = {"k": "c", "t": "B15 | linker Fahrstreifen gesperrt"}; self.assertTrue(B.reklass(y)); self.assertEqual(y["k"], "r")
        z = {"k": "c", "t": "St2129 zwischen Eppenschlag und Beiwald | gesperrt, Fahrbahnerneuerung"}; self.assertFalse(B.reklass(z)); self.assertEqual(z["k"], "c")
        self.assertFalse(B.reklass({"k": "r", "t": AUSW}))

    def test_dritter_versuch_ueber_alle_wege(self):
        a = [48.93119, 12.20879]; b = [48.92699, 12.20907]; weg = [a, [48.9290, 12.2089], b]
        items = [{"a": a, "b": b, "r": "", "k": "c", "s": 0, "e": 0}]
        ok, fail = B.add_geometry(items, lambda x, y: None, {}, ref_fn=lambda *z: None, any_fn=lambda x, y, r=None: weg)
        self.assertEqual((ok, fail), (1, 0)); self.assertTrue(items[0]["g"]); self.assertEqual(B.add_geometry.any_ok, 1)

    def test_alle_wege_umweg_wird_verworfen(self):
        a = [48.93119, 12.20879]; b = [48.92699, 12.20907]; umweg = [a, [48.93183, 12.20598], [48.92459, 12.20816], b]
        items = [{"a": a, "b": b, "r": "", "k": "c", "s": 0, "e": 0}]
        ok, fail = B.add_geometry(items, lambda x, y: None, {}, any_fn=lambda x, y, r=None: umweg)
        self.assertEqual((ok, fail), (0, 1))


class SucheTest(unittest.TestCase):
    A = [48.84252, 12.11165]; Z = [48.80455, 12.13631]   # B15 bei Schierling, etwa 4,6 km
    def strasse(self, schleife=0.0):   # Linie von A nach Z mit etwas Bogen
        m = [(self.A[0] + self.Z[0]) / 2, (self.A[1] + self.Z[1]) / 2 + schleife]
        return [self.A, m, self.Z]
    def item(self, r="B15"): return {"a": self.A, "b": self.Z, "r": r, "k": "c", "s": 0, "e": 0}

    def test_alle_wege_kommt_vor_der_strassennummer(self):
        gegen = [self.A, [48.9, 12.3], [48.8, 12.3], self.Z]   # Umweg über die Anschlussstelle (Einbahn): länger, aber "passende Klasse"
        it = [self.item()]
        ok, fail = B.add_geometry(it, lambda a, b: gegen, {}, any_fn=lambda a, b, r=None: self.strasse(0.005), ref_fn=lambda a, b, r: gegen)
        self.assertEqual((ok, fail), (1, 0)); self.assertEqual(len(it[0]["g"]), 3)   # die direkte Linie, nicht der Umweg
        self.assertEqual(B.add_geometry.any_ok, 1); self.assertEqual(B.add_geometry.ref_ok, 0)

    def test_linie_die_nicht_bei_den_punkten_beginnt_wird_verworfen(self):
        daneben = [[self.A[0] + 0.01, self.A[1]], [self.Z[0] + 0.005, self.Z[1]], [self.Z[0] + 0.01, self.Z[1]]]   # ca. 1 km neben Anfang und Ende
        it = [self.item()]
        ok, fail = B.add_geometry(it, lambda a, b: None, {}, any_fn=lambda a, b, r=None: daneben, ref_fn=lambda a, b, r: daneben)
        self.assertEqual((ok, fail), (0, 1)); self.assertNotIn("g", it[0])

    def test_kein_abbruch_bei_vielen_fehlschlaegen_wenn_max_misses_gross(self):
        its = [dict(self.item(), a=[48.0 + i * 0.01, 12.0], b=[48.0 + i * 0.01 + 0.03, 12.0]) for i in range(40)]
        calls = []
        B.add_geometry(its, lambda a, b: calls.append(1) or None, {}, max_misses=10 ** 9)
        self.assertEqual(len(calls), 40)
        calls.clear(); B.add_geometry([dict(i) for i in its], lambda a, b: calls.append(1) or None, {})
        self.assertEqual(len(calls), 25)   # Standard: nach 25 Fehlschlägen in Folge Schluss

    def test_fehlschlaege_werden_gemerkt_aber_nur_wenn_etwas_gefunden_wurde(self):
        gut = dict(self.item(r=""), a=[48.0, 12.0], b=[48.0, 12.01])
        schlecht = dict(self.item(r=""), a=[49.0, 12.0], b=[49.03, 12.0])
        direkt = lambda a, b: [a, [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], b] if a[0] == 48.0 else None
        cache = {}; B.add_geometry([gut, schlecht], direkt, cache, now=1000)
        self.assertTrue(any(k.startswith("x|") for k in cache))
        calls = []; s2 = dict(schlecht)
        B.add_geometry([s2], lambda a, b: calls.append(1) or None, cache, now=1000 + 3600); self.assertEqual(calls, [])         # vor Kurzem vergeblich
        B.add_geometry([s2], lambda a, b: calls.append(1) or None, cache, now=1000 + 8 * 86400); self.assertEqual(len(calls), 1)   # nach 7 Tagen wieder
        leer = {}; B.add_geometry([dict(schlecht)], lambda a, b: None, leer, now=1000)   # nichts gefunden: vielleicht ist BRouter nur nicht bereit
        self.assertFalse(any(k.startswith("x|") for k in leer))


class BootTest(unittest.TestCase):
    def test_nachbessern_stuft_sofort_um_und_sucht_strasse(self):
        import json, tempfile
        sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
        os.environ["DATA_DIR"] = tempfile.mkdtemp()
        import update as U
        os.makedirs(U.BSD, exist_ok=True)
        a = [48.93119, 12.20879]; b = [48.92699, 12.20907]
        items = [{"i": "1", "r": "", "t": AUSW, "k": "c", "a": a, "b": a, "s": 0, "e": 0},
                 {"i": "2", "r": "", "t": "Zwischen Köfering und Alteglofsheim | gesperrt, Baustelle", "k": "c", "a": a, "b": b, "s": 0, "e": 0}]
        json.dump({"stand": 1, "ref": 1, "n": 2, "items": items}, open(U.BS_LIST, "w", encoding="utf-8")); json.dump({"stand": 1}, open(U.BS_META, "w"))
        old = (U.bs_route, U.bs_route_ref, U.bs_route_any)
        U.bs_route = lambda x, y: None; U.bs_route_ref = lambda *z: None; U.bs_route_any = lambda x, y, r=None: [a, [48.9290, 12.2089], b]
        try: U.bs_nachbessern()
        finally: U.bs_route, U.bs_route_ref, U.bs_route_any = old
        res = json.load(open(U.BS_LIST, encoding="utf-8"))["items"]
        self.assertEqual(res[0]["k"], "r"); self.assertEqual(res[1]["k"], "c"); self.assertTrue(res[1]["g"])
        m = json.load(open(U.BS_META)); self.assertEqual((m["v"], m["gv"]), (U.BS_VER, U.BS_VER))
        U.bs_nachbessern()   # zweites Mal: nichts mehr zu tun, kein Fehler

    def test_nachsuche_autobahn_zuerst(self):
        import json, tempfile
        sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__))))
        os.environ["DATA_DIR"] = tempfile.mkdtemp()
        import update as U
        os.makedirs(U.BSD, exist_ok=True)
        a = [48.93119, 12.20879]; b = [48.92699, 12.20907]; c = [48.66174, 12.5207]; d = [48.63551, 12.36342]
        items = [{"i": "1", "r": "", "t": "Zwischen X und Y | gesperrt", "k": "c", "a": a, "b": b, "s": 0, "e": 0},
                 {"i": "2", "r": "A92", "t": "A92 | Arbeiten", "k": "w", "a": c, "b": d, "s": 0, "e": 0}]
        json.dump({"stand": 1, "ref": 1, "n": 2, "items": items}, open(U.BS_LIST, "w", encoding="utf-8")); json.dump({"stand": 1}, open(U.BS_META, "w"))
        reihe = []
        old = (U.bs_route, U.bs_route_ref, U.bs_route_any)
        U.bs_route = lambda x, y: None; U.bs_route_ref = lambda *z: None
        U.bs_route_any = lambda x, y, r=None: reihe.append(r or "-") or None
        try: U.bs_nachbessern()
        finally: U.bs_route, U.bs_route_ref, U.bs_route_any = old
        self.assertEqual(reihe[0], "A92")


class AutobahnTest(unittest.TestCase):
    A = [48.66174, 12.5207]; Z = [48.63551, 12.36342]   # A92 bei Dingolfing, ca. 12 km
    def test_autobahn_meldung_nimmt_zuerst_alle_wege_und_nicht_die_parallelstrasse(self):
        mitte = [(self.A[0] + self.Z[0]) / 2, (self.A[1] + self.Z[1]) / 2]
        autobahn = [self.A, mitte, self.Z]
        ueber_land = [self.A, [self.A[0] + 0.03, mitte[1]], self.Z]   # Umweg über Landstraßen, aber noch unter 1,3-fach
        it = [{"a": self.A, "b": self.Z, "r": "A92", "k": "w", "s": 0, "e": 0}]
        calls = []
        ok, fail = B.add_geometry(it, lambda a, b: calls.append("fahrzeug") or ueber_land, {}, any_fn=lambda a, b, r=None: autobahn)
        self.assertEqual((ok, fail), (1, 0)); self.assertEqual(it[0]["g"], [[round(self.A[0], 5), round(self.A[1], 5)], [round(self.Z[0], 5), round(self.Z[1], 5)]]); self.assertEqual(calls, [])   # gerade Autobahn; Fahrzeugprofil gar nicht gefragt

    def test_verdaechtige_linien_werden_neu_gesucht(self):
        a = self.A; b = self.Z; mitte = [a[0] + 0.05, (a[1] + b[1]) / 2]
        self.assertTrue(B.suspicious({"a": a, "b": b, "r": "", "g": [a, mitte, b]}))                       # Umweg über 12 % länger
        self.assertTrue(B.suspicious({"a": a, "b": b, "r": "A92", "g": [a, [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], b]}))   # Autobahn immer
        self.assertFalse(B.suspicious({"a": a, "b": b, "r": "B15", "g": [a, [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2], b]}))
        self.assertFalse(B.suspicious({"a": a, "b": b, "r": "A92"}))   # ohne Linie: kein Verdacht, sondern ohnehin offen


if __name__ == "__main__":
    unittest.main()
