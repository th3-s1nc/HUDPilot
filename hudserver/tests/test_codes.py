"""Tests: Zugangscodes nur als Hash, Umwandlung alter Profile, Code selbst ändern."""
import json, os, sys, tempfile, threading, unittest, urllib.request, urllib.error

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


class HashTest(unittest.TestCase):
    def test_hash_und_pruefung(self):
        h = U.code_hash("ABCD2345", True)
        self.assertTrue(h.startswith("pbkdf2$")); self.assertNotIn("ABCD2345", h)
        self.assertTrue(U.code_check("abcd2345", True, h))      # vom Server vergeben: Schreibweise egal
        self.assertFalse(U.code_check("ABCD2346", True, h))
        h2 = U.code_hash("MeinGeheim!", False)   # genaue Schreibweise (nur falls mal gebraucht)
        self.assertTrue(U.code_check("MeinGeheim!", False, h2)); self.assertFalse(U.code_check("meingeheim!", False, h2))
        self.assertNotEqual(U.code_hash("ABCD2345", True), h)   # jedes Mal anderes Salz

    def test_kaputter_hash(self):
        self.assertFalse(U.code_check("x", True, "muell"))


class ProfileTest(unittest.TestCase):
    def setUp(self):
        U.PST["profiles"].clear(); U.VERIFIED.clear(); U.FAILS.clear()

    def test_neues_profil_ohne_klartext(self):
        code = U._profile_add("Anna"); v = U.PST["profiles"]["Anna"]
        self.assertNotIn("code", v); self.assertIn("hash", v)
        self.assertNotIn(code, json.dumps(U.PST))
        self.assertIsNotNone(U.profile_login("anna", code.lower()))
        self.assertIsNone(U.profile_login("Anna", "FALSCH123"))
        self.assertNotIn("code", U.profile_info()[0])

    def test_umwandlung_alter_profile(self):
        U.PST["profiles"]["Tom"] = {"slug": "tom", "code": "TOMCODE2", "created": 1, "seen": 1, "disabled": False}
        json.dump(U.PST, open(U.PFILE, "w")); U.migrate_codes()
        v = U.PST["profiles"]["Tom"]; self.assertNotIn("code", v)
        self.assertIsNotNone(U.profile_login("Tom", "tomcode2"))
        self.assertTrue(os.path.exists(U.PFILE + ".vor-hash"))
        self.assertNotIn("TOMCODE2", open(U.PFILE).read())
        U.migrate_codes()   # zweites Mal: nichts mehr zu tun

    def test_resetcode_macht_alten_ungueltig(self):
        old = U._profile_add("Anna"); k, v = U.find_profile("Anna")
        self.assertIsNotNone(U.profile_login("Anna", old))
        new = U.new_code(); U.code_set(v, new, True)
        self.assertIsNone(U.profile_login("Anna", old)); self.assertIsNotNone(U.profile_login("Anna", new))

    def test_code_aendern(self):
        old = U._profile_add("Anna")
        U.profile_set_code("Anna", old, "Neuer Code 2026")
        self.assertIsNone(U.profile_login("Anna", old))
        self.assertIsNotNone(U.profile_login("Anna", "Neuer Code 2026"))
        self.assertIsNone(U.profile_login("Anna", "NEUER CODE 2026"))   # neue Passwörter unterscheiden Groß- und Kleinschreibung
        self.assertTrue(U.profile_info()[0]["eigener_code"])

    def test_alte_passwoerter_bleiben_unempfindlich(self):
        old = U._profile_add("Anna")
        self.assertIsNotNone(U.profile_login("Anna", old.lower()))   # vom Server erzeugt: Schreibweise egal (wie bisher)

    def test_aendern_pruefungen(self):
        old = U._profile_add("Anna")
        with self.assertRaises(PermissionError): U.profile_set_code("Anna", "FALSCH", "Neuer Code 2026")
        for bad in ("kurz", "x" * 65, "mit|strich1234", "mit;semikolon1", "Umlaut-Müller1", old):
            with self.assertRaises(ValueError): U.profile_set_code("Anna", old, bad)
        self.assertIsNotNone(U.profile_login("Anna", old))   # nichts verändert

    def test_gesperrtes_profil_kann_nicht_aendern(self):
        old = U._profile_add("Anna"); U.PST["profiles"]["Anna"]["disabled"] = True
        with self.assertRaises(PermissionError): U.profile_set_code("Anna", old, "Neuer Code 2026")


class HttpTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        U.PST["profiles"].clear(); U.ADMIN_PW = "pw"; U.FAILS.clear()
        cls.code = U._profile_add("Anna")
        cls.srv = U.ThreadingHTTPServer(("127.0.0.1", 0), U.H); cls.port = cls.srv.server_address[1]
        threading.Thread(target=cls.srv.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls): cls.srv.shutdown(); cls.srv.server_close()

    def post(self, code, body):
        req = urllib.request.Request(f"http://127.0.0.1:{self.port}/api/profile/code", data=json.dumps(body).encode(),
                                     headers={"X-Profile": "Anna", "X-Code": code, "Content-Type": "application/json"})
        try:
            with urllib.request.urlopen(req) as r: return r.status, json.loads(r.read())
        except urllib.error.HTTPError as e: return e.code, json.loads(e.read())

    def test_ablauf(self):
        c, j = self.post("FALSCH123", {"alt": "x", "neu": "Neuer Code 2026"}); self.assertEqual(c, 401)
        c, j = self.post(self.code, {"alt": "FALSCH", "neu": "Neuer Code 2026"}); self.assertEqual(c, 403)
        c, j = self.post(self.code, {"alt": self.code, "neu": "kurz"}); self.assertEqual(c, 400)
        c, j = self.post(self.code, {"alt": self.code, "neu": "Neuer Code 2026"}); self.assertEqual((c, j.get("ok")), (200, True))
        c, j = self.post(self.code, {"alt": self.code, "neu": "Noch ein Code 1"}); self.assertEqual(c, 401)   # alter Code gilt nicht mehr
        c, j = self.post("Neuer Code 2026", {"alt": "Neuer Code 2026", "neu": "Noch ein Code 1"}); self.assertEqual(c, 200)


if __name__ == "__main__":
    unittest.main()
