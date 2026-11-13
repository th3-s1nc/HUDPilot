"""Tests: Schalter "Ortsdurchfahrten meiden" (Profilvariante)."""
import os, sys, tempfile, shutil, unittest

os.environ["DATA_DIR"] = tempfile.mkdtemp()
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


class CityTest(unittest.TestCase):
    def setUp(self):
        self.d = tempfile.mkdtemp(); self.old = U.BR_PROF
        for n in os.listdir(os.path.join(os.path.dirname(U.__file__), "brouter-profiles")):
            shutil.copy(os.path.join(os.path.dirname(U.__file__), "brouter-profiles", n), self.d)
        U.BR_PROF = self.d

    def tearDown(self): U.BR_PROF = self.old

    def test_city_ist_ein_gueltiger_schalter(self):
        self.assertIn("city", U.AVOID_KEYS)
        self.assertEqual(U.avoid_clean("city,quatsch"), ["city"])

    def test_variante_enthaelt_aufschlag_nur_wenn_gewaehlt(self):
        n = U.variant_profile("motorrad_landstrasse", "city")
        txt = open(os.path.join(self.d, n + ".brf"), encoding="utf-8").read()
        self.assertIn(U.CITY_COST.strip(), txt)
        n2 = U.variant_profile("motorrad_landstrasse", "motorway")
        self.assertNotIn(U.CITY_COST.strip(), open(os.path.join(self.d, n2 + ".brf"), encoding="utf-8").read())
        self.assertNotIn(U.CITY_COST.strip(), open(os.path.join(self.d, "motorrad_landstrasse.brf"), encoding="utf-8").read())

    def test_ohne_schalter_bleibt_das_grundprofil(self):
        self.assertEqual(U.variant_profile("motorrad_landstrasse", ""), "motorrad_landstrasse")


if __name__ == "__main__":
    unittest.main()
