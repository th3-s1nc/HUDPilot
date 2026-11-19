"""Tests: Schräglage wird wie in der App geglättet (Ausreißer zählen nicht), Kennzahlen und Diagramm folgen der Glättung."""
import os, sys, tempfile, unittest

os.environ["DATA_DIR"] = tempfile.mkdtemp()
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


class SchraeglageTest(unittest.TestCase):
    def test_einzelner_ruck_verschwindet(self):
        a = [1, 4, 39, 65, 15, 2, 2, 3, 1]   # Werte wie bei der Autofahrt: 65° auf gerader Strecke
        s = U.smooth_lean(a)
        self.assertLessEqual(max(s), 15)
        self.assertEqual(round(max(s)), 15)

    def test_echte_kurve_bleibt_und_links_bleibt_negativ(self):
        self.assertEqual(round(max(U.smooth_lean([2, 10, 22, 24, 25, 24, 22, 8, 1]))), 24)
        self.assertLess(U.smooth_lean([0, -20, -22, -21, 0])[2], 0)

    def test_luecken_bleiben_luecken(self):
        s = U.smooth_lean([None, 5, 6, 5])
        self.assertIsNone(s[0])
        self.assertEqual(U.smooth_lean([None, None]), [None, None])
        self.assertEqual(U.smooth_lean([]), [])

    def test_kennzahlen_und_diagramm_nutzen_die_glaettung(self):
        lean = [1, 4, 39, 65, 15, 2, 2, 3, 1, -3, -2]
        rows = [[1000 * i, 48.0 + i * 0.0003, 12.0, None, 60.0, 0, v] for i, v in enumerate(lean)]
        st, _, _ = U.analyse(rows)
        self.assertTrue(st["has_lean"])
        self.assertLessEqual(st["lean_left"], 15)
        self.assertLessEqual(st["lean_right"], 3)
        U.CUR.slug = None


if __name__ == "__main__":
    unittest.main()
