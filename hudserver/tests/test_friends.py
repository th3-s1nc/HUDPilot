"""Tests: Freunde und Live-Standort (Logik und HTTP-Durchlauf)."""
import json, os, sys, tempfile, threading, unittest, urllib.request

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import friends as FR
import update as U


class Uhr:
    def __init__(self): self.t = 1_800_000_000.0
    def __call__(self): return self.t


def neu():
    u = Uhr(); d = tempfile.mkdtemp()
    return FR.Friends(os.path.join(d, "f.json"), now=u), u, d


class FreundeLogikTest(unittest.TestCase):
    def test_anfrage_annehmen(self):
        f, u, _ = neu()
        self.assertEqual(f.request("Anna", "Tom"), "angefragt")
        self.assertEqual([r["status"] for r in f.listing("Tom")], ["incoming"])
        self.assertEqual([r["status"] for r in f.listing("Anna")], ["outgoing"])
        self.assertEqual(f.pending_in("Tom"), 1); self.assertEqual(f.pending_in("Anna"), 0)
        f.respond("Tom", "Anna", True)
        self.assertEqual([r["status"] for r in f.listing("Anna")], ["friend"])

    def test_nur_der_angefragte_darf_antworten(self):
        f, u, _ = neu(); f.request("Anna", "Tom")
        with self.assertRaises(ValueError): f.respond("Anna", "Tom", True)
        with self.assertRaises(ValueError): f.respond("Tom", "Mike", True)

    def test_ablehnen_loescht(self):
        f, u, _ = neu(); f.request("Anna", "Tom"); f.respond("Tom", "Anna", False)
        self.assertEqual(f.listing("Anna"), [])

    def test_gegenseitige_anfrage_wird_freundschaft(self):
        f, u, _ = neu(); f.request("Anna", "Tom")
        self.assertEqual(f.request("Tom", "Anna"), "befreundet")
        self.assertEqual(f.listing("Tom")[0]["status"], "friend")

    def test_fehler(self):
        f, u, _ = neu()
        with self.assertRaises(ValueError): f.request("Anna", "anna")
        f.request("Anna", "Tom")
        with self.assertRaises(ValueError): f.request("Anna", "Tom")
        f.respond("Tom", "Anna", True)
        with self.assertRaises(ValueError): f.request("Anna", "Tom")

    def test_anfragen_pro_stunde_begrenzt(self):
        f, u, _ = neu()
        for i in range(FR.REQ_PER_HOUR):
            f.request("Anna", "P%d" % i)
            f.remove("Anna", "P%d" % i)
        with self.assertRaises(ValueError): f.request("Anna", "Neu")
        u.t += 3601
        self.assertEqual(f.request("Anna", "Neu"), "angefragt")

    def test_gespeichert_und_geladen(self):
        f, u, d = neu(); f.request("Anna", "Tom"); f.respond("Tom", "Anna", True); f.permit("Anna", "Tom", True)
        g = FR.Friends(os.path.join(d, "f.json"), now=u)
        r = g.listing("Tom")[0]
        self.assertTrue(r["they_allow"]); self.assertFalse(r["i_allow"])


class LiveTest(unittest.TestCase):
    def freunde(self):
        f, u, d = neu(); f.request("Anna", "Tom"); f.respond("Tom", "Anna", True); return f, u

    def test_ohne_erlaubnis_unsichtbar(self):
        f, u = self.freunde(); f.update("Anna", 49.0, 12.0, 80, 90)
        self.assertEqual(f.positions_for("Tom"), [])
        self.assertFalse(f.listing("Tom")[0]["sharing"])

    def test_mit_erlaubnis_sichtbar(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Anna", 49.0, 12.0, 87.4, 90)
        p = f.positions_for("Tom")
        self.assertEqual(len(p), 1); self.assertEqual(p[0]["name"], "Anna"); self.assertEqual(p[0]["spd"], 87.4)
        self.assertTrue(f.listing("Tom")[0]["sharing"])
        self.assertEqual(f.positions_for("Anna"), [])   # Tom hat nichts erlaubt, und teilt nicht

    def test_richtung_ist_getrennt(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Tom", 49.0, 12.0, 10, 0)
        self.assertEqual(f.positions_for("Anna"), [])   # Tom teilt, hat Anna aber nichts erlaubt

    def test_veraltet_nach_zwei_minuten(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Anna", 49.0, 12.0)
        u.t += FR.LIVE_TTL + 1
        self.assertEqual(f.positions_for("Tom"), [])

    def test_stop_entfernt(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Anna", 49.0, 12.0); f.stop("Anna")
        self.assertEqual(f.positions_for("Tom"), [])

    def test_freund_entfernen_beendet_sicht(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Anna", 49.0, 12.0); f.remove("Tom", "Anna")
        self.assertEqual(f.positions_for("Tom"), []); self.assertEqual(f.listing("Anna"), [])

    def test_ungueltige_position(self):
        f, u = self.freunde()
        with self.assertRaises(ValueError): f.update("Anna", 99, 12)
        with self.assertRaises(ValueError): f.update("Anna", 49, 200)
        with self.assertRaises(ValueError): f.update("Anna", "x", 12)

    def test_tempo_wird_begrenzt(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Anna", 49.0, 12.0, 9999, 400)
        p = f.positions_for("Tom")[0]; self.assertEqual(p["spd"], 400.0); self.assertEqual(p["hdg"], 40)

    def test_sichtbar_fuer_anzahl(self):
        f, u = self.freunde(); self.assertEqual(f.viewers("Anna"), 0); f.permit("Anna", "Tom", True); self.assertEqual(f.viewers("Anna"), 1)

    def test_statistik(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True)
        f.update("Anna", 49.0, 12.0); u.t += 5; f.update("Anna", 49.0, 12.0); u.t += 5; f.update("Anna", 49.0, 12.0)
        f.positions_for("Tom")
        a = f.admin(); t = a["tage"][-1]
        self.assertEqual(a["freundschaften"], 1); self.assertEqual(a["teilen_jetzt"], ["Anna"])
        self.assertEqual(a["sieht_gerade"], [{"von": "Tom", "ziel": "Anna"}])
        self.assertEqual((t["meldungen"], t["sekunden"], t["spitze"], t["nutzer"]), (3, 10, 1, 1))
        self.assertEqual((t["anfragen"], t["angenommen"]), (1, 1))
        self.assertNotIn("lat", json.dumps(a))   # die Verwaltung bekommt keine Positionen

    def test_profil_loeschen(self):
        f, u = self.freunde(); f.permit("Anna", "Tom", True); f.update("Anna", 49.0, 12.0); f.remove_profile("Anna")
        self.assertEqual(f.listing("Tom"), []); self.assertEqual(f.positions_for("Tom"), [])


class HttpTest(unittest.TestCase):
    """Echter Durchlauf durch den Server-Handler."""
    @classmethod
    def setUpClass(cls):
        import http.server
        U.PST["profiles"].clear()
        cls.ca = U._profile_add("Anna"); cls.ct = U._profile_add("Tom")
        U.FRI = FR.Friends(os.path.join(tempfile.mkdtemp(), "f.json"))
        cls.srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), U.H)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.base = "http://127.0.0.1:%d" % cls.srv.server_address[1]

    @classmethod
    def tearDownClass(cls): cls.srv.shutdown()

    def call(self, who, path, body=None):
        code = self.ca if who == "Anna" else self.ct
        h = {"X-Profile": who, "X-Code": code, "Content-Type": "application/json"}
        req = urllib.request.Request(self.base + path, data=None if body is None else json.dumps(body).encode(), headers=h)
        try:
            with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

    def test_ablauf(self):
        self.assertEqual(self.call("Anna", "/api/friends/request", {"name": "tom"})[1]["status"], "angefragt")
        c, j = self.call("Tom", "/api/friends"); self.assertEqual(j["offen"], 1); self.assertEqual(j["friends"][0]["status"], "incoming")
        self.assertEqual(self.call("Tom", "/api/friends/respond", {"name": "Anna", "accept": True})[0], 200)
        self.call("Anna", "/api/friends/permit", {"name": "Tom", "allow": True})
        c, j = self.call("Anna", "/api/live", {"lat": 49.1, "lon": 12.2, "spd": 55, "hdg": 10}); self.assertEqual((c, j["sicht"]), (200, 1))
        c, j = self.call("Tom", "/api/friends/live"); self.assertEqual(j["positions"][0]["name"], "Anna"); self.assertEqual(j["positions"][0]["spd"], 55)
        self.assertEqual(self.call("Anna", "/api/live/stop", {})[0], 200)
        self.assertEqual(self.call("Tom", "/api/friends/live")[1]["positions"], [])
        self.assertEqual(self.call("Anna", "/api/friends/request", {"name": "Niemand"})[0], 404)

    def test_ohne_anmeldung_gesperrt(self):
        try: urllib.request.urlopen(self.base + "/api/friends"); code = 200
        except urllib.error.HTTPError as e: code = e.code
        self.assertEqual(code, 401)
        req = urllib.request.Request(self.base + "/api/live", data=b"{}", headers={"Content-Type": "application/json"})
        try: urllib.request.urlopen(req); code = 200
        except urllib.error.HTTPError as e: code = e.code
        self.assertEqual(code, 401)

    def test_pfade_auch_von_aussen(self):
        for p in ("/api/friends", "/api/friends/live"): self.assertIn(p, U.PROF_GET)
        for p in ("/api/live", "/api/live/stop", "/api/friends/request", "/api/friends/respond", "/api/friends/remove", "/api/friends/permit"): self.assertIn(p, U.PROF_POST)


if __name__ == "__main__": unittest.main()
