"""Anmelde-Sperre: Aufheben, Ausschalten für 1 Stunde, Liste, Admin-Endpunkte."""
import json, sys, os, threading, time, unittest, urllib.request, urllib.error
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "updater"))
import update as U


class SperreLogik(unittest.TestCase):
    def setUp(self):
        self._save = U.save_cfg; U.save_cfg = lambda: None
        U.CFG["sperre_aus_bis"] = 0; U.FAILS.clear()
    def tearDown(self): U.save_cfg = self._save; U.CFG["sperre_aus_bis"] = 0; U.FAILS.clear()

    def test_sperre_greift_von_aussen_nach_5(self):
        for _ in range(4): U.fail("x:1.2.3.4")
        self.assertFalse(U.blocked("x:1.2.3.4"))
        U.fail("x:1.2.3.4"); self.assertTrue(U.blocked("x:1.2.3.4"))

    def test_aufheben_gibt_frei_und_zaehlt(self):
        for _ in range(5): U.fail("x:1.2.3.4")
        for _ in range(10): U.fail("192.168.1.5")
        U.fail("x:9.9.9.9")   # nur 1 Versuch, nicht gesperrt
        self.assertEqual(len(U.sperre_info()["gesperrt"]), 2)
        self.assertEqual(U.sperre_aufheben(), 2)
        self.assertFalse(U.blocked("x:1.2.3.4")); self.assertFalse(U.blocked("192.168.1.5"))
        self.assertEqual(U.sperre_info()["gesperrt"], [])

    def test_ausschalten_sperrt_nie_und_zaehlt_nicht(self):
        for _ in range(5): U.fail("x:1.2.3.4")
        U.sperre_schalten(True)
        self.assertFalse(U.blocked("x:1.2.3.4"))          # bestehende Sperre sofort weg
        for _ in range(50): U.fail("x:1.2.3.4")
        self.assertFalse(U.blocked("x:1.2.3.4"))
        self.assertGreater(U.sperre_info()["aus_rest"], 3500)
        self.assertEqual(U.FAILS, {})                      # in der Zeit wird nichts gezählt

    def test_schaltet_sich_nach_einer_stunde_von_selbst_ein(self):
        U.sperre_schalten(True); U.CFG["sperre_aus_bis"] = int(time.time()) - 1
        self.assertEqual(U.sperre_info()["aus_rest"], 0)
        for _ in range(5): U.fail("x:1.2.3.4")
        self.assertTrue(U.blocked("x:1.2.3.4"))

    def test_wieder_einschalten_beginnt_neu(self):
        U.sperre_schalten(True); U.sperre_schalten(False)
        self.assertEqual(U.sperre_info()["aus_rest"], 0)
        for _ in range(4): U.fail("x:1.2.3.4")
        self.assertFalse(U.blocked("x:1.2.3.4"))

    def test_liste_zeigt_art_adresse_und_restzeit(self):
        for _ in range(5): U.fail("x:1.2.3.4")
        for _ in range(10): U.fail("a:10.0.0.7")
        rows = {r["wer"]: r for r in U.sperre_info()["gesperrt"]}
        self.assertEqual(rows["1.2.3.4"]["art"], "extern"); self.assertEqual(rows["10.0.0.7"]["art"], "Admin")
        self.assertTrue(0 < rows["1.2.3.4"]["rest"] <= 900)


class SperreHttp(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls._save = U.save_cfg; U.save_cfg = lambda: None
        U.ADMIN_PW = "pw"; U.FAILS.clear(); U.CFG["sperre_aus_bis"] = 0
        cls.srv = U.ThreadingHTTPServer(("127.0.0.1", 0), U.H); cls.port = cls.srv.server_address[1]
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.srv.shutdown(); cls.srv.server_close(); U.save_cfg = cls._save; U.CFG["sperre_aus_bis"] = 0; U.FAILS.clear()

    def call(self, path, body=None, pw="pw"):
        req = urllib.request.Request(f"http://127.0.0.1:{self.port}{path}", data=None if body is None else json.dumps(body).encode(),
                                     headers={"X-Admin-Password": pw, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

    def test_ohne_passwort_nichts(self):
        self.assertEqual(self.call("/api/admin/sperre", pw="falsch")[0], 401)
        self.assertEqual(self.call("/api/admin/sperre", {"aktion": "aus"}, pw="falsch")[0], 401)
        self.assertEqual(U.sperre_aus_rest(), 0)

    def test_ablauf(self):
        c, j = self.call("/api/admin/sperre"); self.assertEqual(c, 200); self.assertEqual(j["aus_rest"], 0)
        c, j = self.call("/api/admin/sperre", {"aktion": "aus"}); self.assertTrue(c == 200 and j["aus_rest"] > 3500)
        c, j = self.call("/api/admin/sperre", {"aktion": "an"}); self.assertTrue(c == 200 and j["aus_rest"] == 0)
        for _ in range(10): U.fail("x:5.5.5.5")
        c, j = self.call("/api/admin/sperre"); self.assertEqual([r["wer"] for r in j["gesperrt"]], ["5.5.5.5"])
        c, j = self.call("/api/admin/sperre", {"aktion": "aufheben"}); self.assertTrue(c == 200 and j["gesperrt"] == [])
        self.assertEqual(self.call("/api/admin/sperre", {"aktion": "quatsch"})[0], 400)


if __name__ == "__main__":
    unittest.main()
