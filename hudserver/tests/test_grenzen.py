"""Tests: echte Grenzlinien (.poly) für die Länder-Prüfung im Planer."""
import json, os, sys, tempfile, unittest

os.environ["DATA_DIR"] = tempfile.mkdtemp()
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U

POLY = """bayern
1
    1.0E+01  5.0E+01
    1.2E+01  5.0E+01
    1.2E+01  4.8E+01
    1.0E+01  4.8E+01
    1.0E+01  5.0E+01
END
!2
    1.05E+01  4.95E+01
    1.1E+01  4.95E+01
    1.1E+01  4.9E+01
END
END
"""


class GrenzenTest(unittest.TestCase):
    def test_parse_ignoriert_loecher_und_kopfzeile(self):
        r = U.poly_parse(POLY)
        self.assertEqual(len(r), 1)
        self.assertEqual(r[0][0], [10.0, 50.0]); self.assertEqual(len(r[0]), 5)

    def test_vereinfachen_behaelt_ecken(self):
        ring = [[10.0, 48.0], [10.5, 48.0001], [11.0, 48.0], [11.0, 49.0], [10.0, 49.0], [10.0, 48.0]]
        v = U.poly_simplify(ring)
        self.assertLess(len(v), len(ring)); self.assertIn([11.0, 49.0], v)

    def test_laden_und_ausgabe_mit_poly(self):
        rid = U.REGIONS[0]["id"]; os.makedirs(f"{U.PUB}/maps", exist_ok=True)
        open(f"{U.PUB}/maps/{rid}.pmtiles", "wb").write(b"x")
        U.GRENZ_TRY.clear()
        self.assertGreaterEqual(U.grenzen_laden(fetch=lambda url: POLY), 1)
        es = U.laender_boxen()
        self.assertTrue(all("poly" in e for e in es)); self.assertIn(U.REGIONS[0]["land"], [e["land"] for e in es])
        U.GRENZ_TRY.clear()
        self.assertEqual(U.grenzen_laden(fetch=lambda url: 1 / 0), 0)   # schon vorhanden: nicht noch einmal holen


if __name__ == "__main__":
    unittest.main()
