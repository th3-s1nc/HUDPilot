"""Tests: Auto-Profil (Vermeiden-Schalter funktionieren wie bei den Motorrad-Profilen)."""
import os, re, shutil, sys, tempfile, unittest

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater", "brouter-profiles")


class AutoProfilTest(unittest.TestCase):
    def setUp(self):
        self.old = U.BR_PROF; U.BR_PROF = tempfile.mkdtemp()
        for f in os.listdir(SRC): shutil.copy(os.path.join(SRC, f), U.BR_PROF)

    def tearDown(self): U.BR_PROF = self.old

    def test_in_liste(self): self.assertIn("auto", U.PROFILES)

    def test_alle_profile_haben_die_schalter(self):
        for p in U.PROFILES:
            txt = open(os.path.join(SRC, p + ".brf"), encoding="utf-8").read()
            for k in U.AVOID_KEYS: self.assertRegex(txt, r"(?m)^assign avoid_%s\s*=\s*0" % k, (p, k))

    def test_variante_setzt_schalter(self):
        name = U.variant_profile("auto", ["motorway", "toll"])
        self.assertTrue(name.startswith("auto__"))
        txt = open(os.path.join(U.BR_PROF, name + ".brf"), encoding="utf-8").read()
        self.assertRegex(txt, r"(?m)^assign avoid_motorway = 1"); self.assertRegex(txt, r"(?m)^assign avoid_toll = 1")
        self.assertRegex(txt, r"(?m)^assign avoid_ferry = 0")

    def test_auto_nur_fuer_autos(self):
        txt = open(os.path.join(SRC, "auto.brf"), encoding="utf-8").read()
        self.assertIn("switch caraccess", txt); self.assertNotIn("or caraccess motorcycleaccess", txt)
        self.assertRegex(txt, r"(?m)^assign motorwaycost\s*=\s*1\.0")


if __name__ == "__main__": unittest.main()


class NogosTest(unittest.TestCase):
    def test_gueltige_kreise(self):
        self.assertEqual(U.clean_nogos("12.1,49.0,300|12.2,49.1,350"), "12.100000,49.000000,300|12.200000,49.100000,350")

    def test_ungueltiges_wird_verworfen(self):
        self.assertEqual(U.clean_nogos("x,1,2|12,49,10|12,49,99999|200,49,300|12,49"), "")
        self.assertEqual(U.clean_nogos("12,49,300|abc|13,50,400"), "12.000000,49.000000,300|13.000000,50.000000,400")

    def test_begrenzt_und_leer(self):
        many = "|".join(f"12.{i:03d},49.0,300" for i in range(200))
        self.assertEqual(len(U.clean_nogos(many).split("|")), 80)
        self.assertEqual(U.clean_nogos(""), ""); self.assertEqual(U.clean_nogos(None), "")


class LaenderTest(unittest.TestCase):
    def test_nur_gebaute_regionen(self):
        old = U.REGIONS
        U.REGIONS = [{"id": "zzx-nicht-gebaut", "land": "DE", "bbox": [1, 2, 3, 4]}]
        try: self.assertEqual(U.laender_boxen(), [])
        finally: U.REGIONS = old


class TourPunkteTest(unittest.TestCase):
    def test_viele_punkte_erlaubt(self):
        pts = [[49 + i * 0.001, 12.0] for i in range(300)]
        self.assertEqual(len(U.clean_points(pts)), 300)

    def test_zu_viele_oder_zu_wenige(self):
        with self.assertRaises(ValueError): U.clean_points([[49 + i * 0.001, 12.0] for i in range(301)])
        with self.assertRaises(ValueError): U.clean_points([[49, 12]])
