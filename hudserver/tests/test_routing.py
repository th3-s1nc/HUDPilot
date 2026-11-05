"""Tests: Routing-Leistung (Messung, Einstellung) und Status-Infos der Admin-Meldungen."""
import json, os, sys, tempfile, threading, unittest, urllib.request

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import notify as NT
import update as U


class RoutingTest(unittest.TestCase):
    def setUp(self):
        U.RT_LOG.clear(); U.RT_STAT.update(tag="", n=0, ms=0, max=0, langsam=0, aktiv=0, fehler=0)

    def test_messung_zaehlt_dauer_km_und_fehler(self):
        body = b'{"features":[{"properties":{"creator":"x","track-length":"123456","total-time":"900"}}]}'
        U.rt_start(); U.rt_done("motorrad_kurvig__a0b0", 3, 200, 1200, body)
        U.rt_start(); U.rt_done("auto", 2, 422, 7000, b"")
        j = U.routing_info()
        self.assertEqual(j["aktiv"], 0); self.assertEqual(j["heute"]["n"], 2); self.assertEqual(j["heute"]["avg_ms"], 4100)
        self.assertEqual(j["heute"]["max_ms"], 7000); self.assertEqual(j["heute"]["langsam"], 1); self.assertEqual(j["heute"]["fehler"], 1)
        self.assertEqual(j["letzte"][0]["prof"], "auto"); self.assertEqual(j["letzte"][1]["prof"], "motorrad_kurvig"); self.assertEqual(j["letzte"][1]["km"], 123.5)

    def test_route_proxy_misst_auch_bei_fehler(self):
        old = U.route_proxy_raw
        try:
            U.route_proxy_raw = lambda q: (503, b"{}")
            self.assertEqual(U.route_proxy("lonlats=1,2|3,4&profile=auto")[0], 503)
            def boom(q): raise RuntimeError("x")
            U.route_proxy_raw = boom
            with self.assertRaises(RuntimeError): U.route_proxy("lonlats=1,2|3,4")
        finally: U.route_proxy_raw = old
        j = U.routing_info(); self.assertEqual(j["aktiv"], 0); self.assertEqual(j["heute"]["n"], 2); self.assertEqual(j["heute"]["fehler"], 2)

    def test_liste_hoechstens_zwanzig(self):
        for i in range(30): U.rt_start(); U.rt_done("auto", 2, 200, 100 + i, b"")
        j = U.routing_info(); self.assertEqual(len(j["letzte"]), 20); self.assertEqual(j["letzte"][0]["ms"], 129)

    def test_standard_leistung(self):
        self.assertEqual((U.CFG["br_mem_mb"], U.CFG["br_threads"]), (4096, 8))


class StatusTest(unittest.TestCase):
    def test_kacheln(self):
        items = U.build_status(); keys = [x["key"] for x in items]
        self.assertIn("version", keys); self.assertIn("speicher", keys); self.assertIn("handys", keys)
        for x in items:
            self.assertIn(x["stufe"], ("ok", "warn", "bad", "run")); self.assertTrue(x["wert"])
        self.assertTrue(all(x["key"] in NT.INFO_KEYS for x in items))

    def test_auswahl_filtert(self):
        n = NT.Notes(os.path.join(tempfile.mkdtemp(), "n.json")); n.status_fn = U.build_status
        keys = [x["key"] for x in n.status()]
        self.assertNotIn("live", keys); self.assertIn("version", keys)       # live ist standardmäßig aus
        n.set_info("version", False); n.set_info("live", True)
        keys = [x["key"] for x in n.status()]; self.assertNotIn("version", keys); self.assertIn("live", keys)
        self.assertFalse(n.set_info("gibtsnicht", True))

    def test_fehler_in_status_fn_bricht_nichts(self):
        n = NT.Notes(os.path.join(tempfile.mkdtemp(), "n.json")); n.status_fn = lambda: 1 / 0
        self.assertEqual(n.status(), [])


class HttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import http.server
        U.PST["profiles"].clear(); U.ADMIN_PW = "pw"
        cls.ca = U._profile_add("Anna"); cls.ct = U._profile_add("Tom")
        U.NOTES = NT.Notes(os.path.join(tempfile.mkdtemp(), "n.json")); U.NOTES.status_fn = U.build_status
        cls.srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), U.H)
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()
        cls.base = "http://127.0.0.1:%d" % cls.srv.server_address[1]

    @classmethod
    def tearDownClass(cls): cls.srv.shutdown()

    def call(self, who, path, body=None):
        if who == "admin": h = {"X-Admin-Password": "pw", "Content-Type": "application/json"}
        else: h = {"X-Profile": who, "X-Code": self.ca if who == "Anna" else self.ct, "Content-Type": "application/json"}
        req = urllib.request.Request(self.base + path, data=None if body is None else json.dumps(body).encode(), headers=h)
        try:
            with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

    def test_status_nur_fuer_empfaenger(self):
        self.call("admin", "/api/admin/notify", {"profil": "anna", "an": True})
        c, j = self.call("Anna", "/api/notify?since=0"); self.assertTrue(j["erlaubt"]); self.assertTrue(len(j["status"]) >= 5)
        self.assertEqual(self.call("Tom", "/api/notify?since=0")[1]["status"], [])
        c, a = self.call("admin", "/api/admin/notify"); self.assertEqual(len(a["infos"]), len(NT.INFOS))
        self.call("admin", "/api/admin/notify", {"info": "version", "an": False})
        self.assertNotIn("version", [x["key"] for x in self.call("Anna", "/api/notify?since=0")[1]["status"]])
        self.assertEqual(self.call("admin", "/api/admin/notify", {"info": "x", "an": True})[0], 400)

    def test_routing_admin_und_leistung(self):
        c, j = self.call("admin", "/api/admin/routing"); self.assertEqual(c, 200); self.assertIn("heute", j); self.assertEqual(j["cfg"]["threads"], 8)
        c, j = self.call("admin", "/api/admin/routing/leistung", {"mem_mb": 99999, "threads": 0}); self.assertEqual(c, 200)
        self.assertEqual((U.CFG["br_mem_mb"], U.CFG["br_threads"]), (16384, 1))
        self.call("admin", "/api/admin/routing/leistung", {"mem_mb": 4096, "threads": 8})
        self.assertEqual(self.call("Anna", "/api/admin/routing")[0], 401)

    def test_ohne_anmeldung(self):
        req = urllib.request.Request(self.base + "/api/admin/routing")
        with self.assertRaises(urllib.error.HTTPError) as e: urllib.request.urlopen(req)
        self.assertEqual(e.exception.code, 401)


if __name__ == "__main__":
    unittest.main()
