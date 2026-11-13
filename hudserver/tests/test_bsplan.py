"""Tests: eigener Zeitplan für den Baustellen-Abruf."""
import json, os, sys, tempfile, unittest

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U

H = 3600


class PlanTest(unittest.TestCase):
    def setUp(self):
        self.old = dict(U.CFG)
        U.CFG.update(win_start=1, win_end=6, bs_aktiv=True, bs_win_start=3, bs_win_end=4, bs_every_h=24)

    def tearDown(self):
        U.CFG.clear(); U.CFG.update(self.old)

    def test_eigenes_fenster_zaehlt_nicht_das_der_karten(self):
        m = {"geprueft": 0}
        self.assertTrue(U.bs_due(now=100 * H, hour=3, meta=m))
        self.assertFalse(U.bs_due(now=100 * H, hour=2, meta=m))   # im Fenster der Karten, aber nicht im eigenen
        self.assertFalse(U.bs_due(now=100 * H, hour=4, meta=m))

    def test_abstand_in_stunden(self):
        m = {"geprueft": 100 * H}
        self.assertFalse(U.bs_due(now=100 * H + 23 * H, hour=3, meta=m))
        self.assertTrue(U.bs_due(now=100 * H + 24 * H, hour=3, meta=m))
        U.CFG["bs_every_h"] = 6
        self.assertTrue(U.bs_due(now=100 * H + 6 * H, hour=3, meta=m))

    def test_ausgeschaltet(self):
        U.CFG["bs_aktiv"] = False
        self.assertFalse(U.bs_due(now=1000 * H, hour=3, meta={"geprueft": 0}))

    def test_gleiche_zahl_heisst_immer(self):
        U.CFG.update(bs_win_start=0, bs_win_end=0)
        self.assertTrue(U.bs_due(now=1000 * H, hour=14, meta={"geprueft": 0}))

    def test_fenster_ueber_mitternacht(self):
        U.CFG.update(bs_win_start=22, bs_win_end=2)
        self.assertTrue(U.bs_due(now=1000 * H, hour=23, meta={"geprueft": 0}))
        self.assertTrue(U.bs_due(now=1000 * H, hour=1, meta={"geprueft": 0}))
        self.assertFalse(U.bs_due(now=1000 * H, hour=12, meta={"geprueft": 0}))

    def test_ohne_eigenes_fenster_gilt_das_der_karten(self):
        U.CFG.update(bs_win_start=None, bs_win_end=None)
        self.assertTrue(U.bs_due(now=1000 * H, hour=2, meta={"geprueft": 0}))
        self.assertFalse(U.bs_due(now=1000 * H, hour=8, meta={"geprueft": 0}))

    def test_load_cfg_uebernimmt_altes_fenster(self):
        json.dump({"win_start": 2, "win_end": 5}, open(f"{U.DATA}/config.json", "w"))
        U.CFG.update(bs_win_start=None, bs_win_end=None)
        U.load_cfg()
        self.assertEqual((U.CFG["bs_win_start"], U.CFG["bs_win_end"]), (2, 5))
        json.dump({"win_start": 2, "win_end": 5, "bs_win_start": 22, "bs_win_end": 23}, open(f"{U.DATA}/config.json", "w"))
        U.load_cfg(); self.assertEqual((U.CFG["bs_win_start"], U.CFG["bs_win_end"]), (22, 23))


if __name__ == "__main__":
    unittest.main()
