import os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "updater"))
import update as U


ALLE = [{"id": "a", "bereit": True}, {"id": "b", "bereit": False}, {"id": "c", "bereit": True}]
REG = [{"id": "a"}, {"id": "c"}]


class Sichtbar(unittest.TestCase):
    def test_nur_gebaute_blendet_ungebaute_aus(self):
        reg, al = U.app_sichtbar(REG, ALLE, [], True)
        self.assertEqual([x["id"] for x in al], ["a", "c"])
        self.assertEqual([x["id"] for x in reg], ["a", "c"])

    def test_alle_zeigt_auch_ungebaute(self):
        reg, al = U.app_sichtbar(REG, ALLE, [], False)
        self.assertEqual([x["id"] for x in al], ["a", "b", "c"])

    def test_versteckte_fallen_ueberall_weg(self):
        for nur in (True, False):
            reg, al = U.app_sichtbar(REG, ALLE, ["a", "b"], nur)
            self.assertNotIn("a", [x["id"] for x in reg])
            self.assertNotIn("a", [x["id"] for x in al])
            self.assertNotIn("b", [x["id"] for x in al])

    def test_standard_ist_nur_gebaute(self):
        self.assertTrue(U.CFG["nur_gebaute"])
        self.assertEqual(U.CFG["versteckt"], [])

    def test_eingaben_veraendern_nichts(self):
        U.app_sichtbar(REG, ALLE, ["a"], True)
        self.assertEqual(len(ALLE), 3); self.assertEqual(len(REG), 2)


if __name__ == "__main__":
    unittest.main()
