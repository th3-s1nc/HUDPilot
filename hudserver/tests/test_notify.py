"""Tests: Admin-Meldungen (Logik und HTTP-Durchlauf)."""
import json, os, sys, tempfile, threading, unittest, urllib.request

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import notify as NT
import update as U


def neu(): return NT.Notes(os.path.join(tempfile.mkdtemp(), "n.json"))


class LogikTest(unittest.TestCase):
    def test_standardwerte(self):
        n = neu()
        self.assertTrue(n.enabled("scdb")); self.assertTrue(n.enabled("zertifikat")); self.assertFalse(n.enabled("profil")); self.assertFalse(n.enabled("version"))

    def test_ausgeschaltet_wird_nicht_gemeldet(self):
        n = neu(); n.set_type("scdb", False)
        self.assertIsNone(n.add("scdb", "x")); self.assertEqual(n.admin()["events"], [])
        n.set_type("scdb", True); self.assertIsNotNone(n.add("scdb", "x"))

    def test_unbekannte_art(self):
        n = neu(); self.assertIsNone(n.add("quatsch", "x")); self.assertFalse(n.set_type("quatsch", True))

    def test_pause_gegen_wiederholung(self):
        n = neu(); self.assertIsNotNone(n.add("platte", "a", cool=3600)); self.assertIsNone(n.add("platte", "b", cool=3600))
        self.assertIsNotNone(n.add("platte", "c"))   # ohne Pause immer

    def test_nur_empfaenger_bekommen_etwas(self):
        n = neu(); n.add("start", "hallo")
        self.assertEqual(n.since("Anna", 0), {"erlaubt": False, "events": [], "letzte": 0, "status": []})
        n.set_recip("Anna", True); j = n.since("Anna", 0)
        self.assertTrue(j["erlaubt"]); self.assertEqual(len(j["events"]), 1); self.assertEqual(j["letzte"], 1)
        self.assertEqual(n.since("Anna", 1)["events"], [])   # nichts Neues
        self.assertFalse(n.since("Tom", 0)["erlaubt"])
        n.set_recip("Anna", False); self.assertFalse(n.since("Anna", 0)["erlaubt"])

    def test_begrenzung_und_nummern(self):
        n = neu()
        for i in range(250): n.add("test", "m%d" % i)
        self.assertEqual(len(n.d["events"]), NT.MAX_EVENTS); n.set_recip("A", True)
        j = n.since("A", 0); self.assertEqual(len(j["events"]), 50); self.assertEqual(j["letzte"], 250); self.assertEqual(j["events"][-1]["id"], 250)

    def test_speichern_und_laden(self):
        n = neu(); n.set_recip("Anna", True); n.set_type("scdb", False); n.add("start", "x")
        m = NT.Notes(n.path); self.assertTrue(m.is_recip("Anna")); self.assertFalse(m.enabled("scdb")); self.assertEqual(len(m.d["events"]), 1)

    def test_version_nur_bei_wechsel(self):
        n = neu(); n.set_type("version", True)
        n.check_version("1.0"); self.assertEqual(n.d["events"], [])         # erster Start: keine Meldung
        n.check_version("1.0"); self.assertEqual(n.d["events"], [])
        n.check_version("1.1"); self.assertEqual(len(n.d["events"]), 1)

    def test_profil_loeschen(self):
        n = neu(); n.set_recip("Anna", True); n.remove_profile("Anna"); self.assertFalse(n.is_recip("Anna"))

    def test_zertifikat_tage_ohne_quelle(self):
        self.assertIsNone(U.cert_days_left())


class HttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        import http.server
        U.PST["profiles"].clear(); U.ADMIN_PW = "pw"
        cls.ca = U._profile_add("Anna"); cls.ct = U._profile_add("Tom")
        U.NOTES = neu()
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

    def test_ablauf(self):
        self.assertFalse(self.call("Anna", "/api/notify")[1]["erlaubt"])
        c, j = self.call("admin", "/api/admin/notify", {"profil": "anna", "an": True}); self.assertEqual((c, j["recip"]), (200, ["Anna"]))
        self.call("admin", "/api/admin/notify", {"test": True})
        c, j = self.call("Anna", "/api/notify?since=0"); self.assertTrue(j["erlaubt"]); self.assertEqual(j["events"][-1]["typ"], "test")
        last = j["letzte"]; self.assertEqual(self.call("Anna", "/api/notify?since=%d" % last)[1]["events"], [])
        self.assertFalse(self.call("Tom", "/api/notify?since=0")[1]["erlaubt"])
        self.assertEqual(self.call("Tom", "/api/notify?since=0")[1]["events"], [])
        c, j = self.call("admin", "/api/admin/notify", {"typ": "scdb", "an": False}); self.assertFalse([t for t in j["typen"] if t["key"] == "scdb"][0]["on"])
        self.assertEqual(self.call("admin", "/api/admin/notify", {"profil": "Nobody", "an": True})[0], 404)
        self.assertEqual(self.call("admin", "/api/admin/notify", {"typ": "x", "an": True})[0], 400)
        c, j = self.call("admin", "/api/admin/profiles"); self.assertTrue([p for p in j["profile"] if p["name"] == "Anna"][0]["meldungen"])

    def test_admin_ohne_anmeldung(self):
        for path, data in (("/api/admin/notify", None), ("/api/admin/notify", b"{}")):
            req = urllib.request.Request(self.base + path, data=data, headers={"Content-Type": "application/json"})
            try: urllib.request.urlopen(req); code = 200
            except urllib.error.HTTPError as e: code = e.code
            self.assertEqual(code, 401)

    def test_ohne_profil_gesperrt(self):
        try: urllib.request.urlopen(self.base + "/api/notify"); code = 200
        except urllib.error.HTTPError as e: code = e.code
        self.assertEqual(code, 401); self.assertIn("/api/notify", U.PROF_GET)


if __name__ == "__main__": unittest.main()
