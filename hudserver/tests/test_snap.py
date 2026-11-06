"""Tests: Zwischenspeicher für Strecken, Einrasten auf die Straße (/api/snap), BRouter-Wächter, Selbsttest."""
import json, os, sys, tempfile, threading, time, unittest, urllib.request

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


def geo(lon, lat):
    return json.dumps({"features": [{"geometry": {"coordinates": [[lon, lat, 400], [lon + 0.0003, lat]]}, "properties": {"track-length": "20", "total-time": "3"}}]}).encode()


class CacheTest(unittest.TestCase):
    def setUp(self):
        U.rc_clear(); U.RC_STAT.update(treffer=0, fehlt=0)

    def test_ablauf_nach_zehn_minuten(self):
        U.rc_put("a", b"x", now=1000)
        self.assertEqual(U.rc_get("a", now=1100), b"x")
        self.assertIsNone(U.rc_get("a", now=1000 + U.RC_TTL + 1))

    def test_hoechstens_so_viele_eintraege_aeltester_faellt_raus(self):
        for i in range(U.RC_MAX + 5): U.rc_put(f"k{i}", b"x")
        self.assertEqual(len(U.ROUTE_CACHE), U.RC_MAX)
        self.assertIsNone(U.rc_get("k0"))
        self.assertIsNotNone(U.rc_get(f"k{U.RC_MAX + 4}"))

    def test_gleiche_strecke_wird_nur_einmal_gerechnet(self):
        calls = []
        old = U.route_proxy_raw
        try:
            def fake(q): calls.append(q); return 200, b'{"features":[]}'
            U.route_proxy_raw = fake
            q = "lonlats=12.0,48.0|12.1,48.1&profile=auto"
            self.assertEqual(U.route_proxy(q)[0], 200); self.assertEqual(U.route_proxy(q)[0], 200)
            self.assertEqual(len(calls), 1)
            U.route_proxy(q + "&avoid=a"); self.assertEqual(len(calls), 2)
        finally: U.route_proxy_raw = old

    def test_fehler_werden_nicht_gespeichert(self):
        calls = []
        old = U.route_proxy_raw
        try:
            U.route_proxy_raw = lambda q: (calls.append(q) or (422, b"{}"))
            U.route_proxy("lonlats=1,2|3,4&profile=auto"); U.route_proxy("lonlats=1,2|3,4&profile=auto")
            self.assertEqual(len(calls), 2)
        finally: U.route_proxy_raw = old

    def test_manifest_leert_den_speicher(self):
        U.rc_put("a", b"x"); os.makedirs(U.PUB, exist_ok=True); U.write_manifest()
        self.assertIsNone(U.rc_get("a"))


class SnapTest(unittest.TestCase):
    def test_rastet_auf_den_anfang_der_kleinen_strecke_ein(self):
        old = U.route_proxy_raw
        try:
            U.route_proxy_raw = lambda q: (200, geo(12.0010, 48.0002))
            r = U.snap_point(12.0, 48.0, "motorrad_kurvig")
            self.assertTrue(r["ok"]); self.assertEqual((r["lon"], r["lat"]), (12.001, 48.0002)); self.assertTrue(50 < r["m"] < 100)
        finally: U.route_proxy_raw = old

    def test_zweiter_versuch_nach_der_anderen_seite(self):
        seen = []
        old = U.route_proxy_raw
        try:
            def fake(q):
                seen.append(q)
                return (422, b"{}") if len(seen) == 1 else (200, geo(12.0, 48.0))
            U.route_proxy_raw = fake
            self.assertTrue(U.snap_point(12.0, 48.0)["ok"]); self.assertEqual(len(seen), 2)
        finally: U.route_proxy_raw = old

    def test_keine_strasse_in_der_naehe(self):
        old = U.route_proxy_raw
        try:
            U.route_proxy_raw = lambda q: (422, b"{}")
            self.assertEqual(U.snap_point(12.0, 48.0), {"ok": False})
            U.route_proxy_raw = lambda q: (503, b"{}")
            self.assertFalse(U.snap_point(12.0, 48.0)["ok"])
        finally: U.route_proxy_raw = old

    def test_unbekanntes_profil_nimmt_standard(self):
        got = []
        old = U.route_proxy_raw
        try:
            U.route_proxy_raw = lambda q: (got.append(q) or (200, geo(12.0, 48.0)))
            U.snap_point(12.0, 48.0, "gibtsnicht"); self.assertIn(U.PROFILES[0], got[0])
        finally: U.route_proxy_raw = old


class WatchTest(unittest.TestCase):
    class P:
        def __init__(self, alive): self.alive = alive
        def poll(self): return None if self.alive else 1

    def setUp(self):
        self.old = (U.BR_PROC.get("p"), U.BR_PROC.get("since"), U.start_brouter, U.stop_brouter, U.BR_JAR)
        U.BR_JAR = os.path.abspath(__file__); self.calls = []
        U.start_brouter = lambda: self.calls.append("start"); U.stop_brouter = lambda: self.calls.append("stop")
        U.BR_WATCH.update(fails=0, neustarts=0)

    def tearDown(self):
        U.BR_PROC["p"], U.BR_PROC["since"], U.start_brouter, U.stop_brouter, U.BR_JAR = self.old

    def test_nie_gestartet_nichts_tun(self):
        U.BR_PROC["p"] = None; U.BR_PROC["since"] = 0
        self.assertFalse(U.brouter_watch(now=10_000)); self.assertEqual(self.calls, [])

    def test_beendet_wird_sofort_neu_gestartet(self):
        U.BR_PROC["p"] = self.P(False); U.BR_PROC["since"] = 1000
        self.assertTrue(U.brouter_watch(now=5000)); self.assertEqual(self.calls, ["stop", "start"]); self.assertEqual(U.BR_WATCH["neustarts"], 1)

    def test_antwortet_nicht_erst_nach_zweimal(self):
        U.BR_PROC["p"] = self.P(True); U.BR_PROC["since"] = 1000
        self.assertFalse(U.brouter_watch(now=5000, alive_fn=lambda: False)); self.assertEqual(self.calls, [])
        self.assertTrue(U.brouter_watch(now=5060, alive_fn=lambda: False)); self.assertEqual(self.calls, ["stop", "start"])

    def test_kurzer_aussetzer_zaehlt_nicht(self):
        U.BR_PROC["p"] = self.P(True); U.BR_PROC["since"] = 1000
        U.brouter_watch(now=5000, alive_fn=lambda: False)
        self.assertFalse(U.brouter_watch(now=5060, alive_fn=lambda: True)); self.assertEqual(U.BR_WATCH["fails"], 0)
        self.assertFalse(U.brouter_watch(now=5120, alive_fn=lambda: False)); self.assertEqual(self.calls, [])

    def test_gerade_erst_gestartet_wird_in_ruhe_gelassen(self):
        U.BR_PROC["p"] = self.P(False); U.BR_PROC["since"] = 5000
        self.assertFalse(U.brouter_watch(now=5030)); self.assertEqual(self.calls, [])


class SelftestTest(unittest.TestCase):
    def test_ohne_brouter_und_karte_meldet_karte(self):
        old, oldR = U.BR_JAR, U.REGIONS
        try:
            U.BR_JAR = "/gibt/es/nicht.jar"; U.REGIONS = []
            self.assertIn("Keine fertige Karte vorhanden", U.selftest())
        finally: U.BR_JAR, U.REGIONS = old, oldR


class HttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        U.ADMIN_PW = "pw"
        cls.srv = U.ThreadingHTTPServer(("127.0.0.1", 0), U.H); cls.port = cls.srv.server_address[1]
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls): cls.srv.shutdown(); cls.srv.server_close()

    def get(self, path):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{self.port}{path}") as r: return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

    def test_snap_endpunkt(self):
        old = U.route_proxy_raw
        try:
            U.route_proxy_raw = lambda q: (200, geo(12.0002, 48.0001))
            c, j = self.get("/api/snap?lon=12.0&lat=48.0&profile=auto"); self.assertEqual(c, 200); self.assertTrue(j["ok"])
            self.assertEqual(self.get("/api/snap?lon=x&lat=1")[0], 400)
            self.assertEqual(self.get("/api/snap?lon=200&lat=1")[0], 400)
        finally: U.route_proxy_raw = old


if __name__ == "__main__":
    unittest.main()
