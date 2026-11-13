"""Tests: TXT-Export der Protokolle."""
import os, sys, tempfile, unittest

os.environ["DATA_DIR"] = tempfile.mkdtemp()
os.environ["ADMIN_PASSWORD"] = "GeheimesAdminWort123"
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


class ExportTest(unittest.TestCase):
    def test_export_enthaelt_abschnitte_und_keine_passwoerter(self):
        U.RT_LOG.clear()
        U.RT_STAT.update(tag="", n=0, ms=0, max=0, langsam=0, aktiv=0, fehler=0)
        U.rt_start(); U.rt_done("motorrad_landstrasse__a0", 13, 200, 12000, b'{"track-length":"226100"}', {"sperr": True, "nogos": False, "avoid": "autobahn"})
        U.log("Testzeile für den Export")
        t = U.export_text()
        for k in ("ZUSTAND", "EINSTELLUNGEN", "ROUTING-LEISTUNG", "REGIONEN", "BAUSTELLEN", "PROTOKOLL", U.VERSION, "Testzeile für den Export"):
            self.assertIn(k, t)
        self.assertIn("| motorrad_landstrasse | 13 | 226.1 | 12000 | 200 | ja | autobahn", t)
        self.assertNotIn("GeheimesAdminWort123", t)

    def test_rt_done_ohne_extra_bleibt_kompatibel(self):
        U.RT_LOG.clear(); U.rt_start(); U.rt_done("auto", 2, 200, 100, b"")
        self.assertEqual(U.RT_LOG[-1]["punkte"], 2)
        self.assertNotIn("sperr", U.RT_LOG[-1])


if __name__ == "__main__":
    unittest.main()
