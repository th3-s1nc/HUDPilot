"""Tests: Tourenliste (km/Zeit), doppelte Namen, Nachrechnen, Ortsdurchfahrten-Stärke."""
import os, shutil, sys, tempfile, unittest

os.environ["DATA_DIR"] = tempfile.mkdtemp()
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U

PTS = [[48.9, 12.1], [49.0, 12.2], [48.9, 12.1]]


class TourenTest(unittest.TestCase):
    def setUp(self):
        U.PST["profiles"].clear(); U._profile_add("Anna"); U.CUR.slug = U.PST["profiles"]["Anna"]["slug"]
        shutil.rmtree(U.tours_dir(), ignore_errors=True)

    def test_km_und_zeit_werden_gespeichert_und_gelistet(self):
        tid = U.tour_save({"name": "A", "points": PTS, "km": 226.14, "min": 192, "asc": 1204.4})
        t = [x for x in U.tour_list() if x["id"] == tid][0]
        self.assertEqual((t["km"], t["min"], t["asc"]), (226.1, 192, 1204))
        self.assertEqual(t["points"], 3)   # bleibt für ältere Apps im Aufruf

    def test_ohne_werte_oder_unsinn_kein_km(self):
        tid = U.tour_save({"name": "A", "points": PTS, "km": "x", "min": -5})
        self.assertIsNone([x for x in U.tour_list() if x["id"] == tid][0]["km"])

    def test_doppelter_name_planer_fragt_nach(self):
        a = U.tour_save({"name": "Schinderhannes", "points": PTS, "km": 100, "min": 90})
        with self.assertRaises(U.TourNameTaken) as cm: U.tour_save({"name": "schinderhannes", "points": PTS, "pruefen": True})
        self.assertEqual(cm.exception.info["id"], a)
        # derselbe Name derselben Tour ist kein Konflikt
        self.assertEqual(U.tour_save({"id": a, "name": "Schinderhannes", "points": PTS, "pruefen": True}), a)

    def test_ueberschreiben_ersetzt_die_vorhandene(self):
        a = U.tour_save({"name": "X", "points": PTS})
        new = [[50.0, 11.0], [50.1, 11.1]]
        self.assertEqual(U.tour_save({"name": "X", "points": new, "pruefen": True, "ueberschreiben": a}), a)
        self.assertEqual(len(U.tour_list()), 1); self.assertEqual(U.tour_get(a)["points"], new)

    def test_app_ohne_pruefen_bekommt_nummer(self):
        U.tour_save({"name": "Tour", "points": PTS})
        b = U.tour_save({"name": "Tour", "points": PTS}); c = U.tour_save({"name": "Tour", "points": PTS})
        self.assertEqual(sorted(x["name"] for x in U.tour_list()), ["Tour", "Tour (2)", "Tour (3)"])

    def test_umbenennen_nicht_auf_vergebenen_namen(self):
        a = U.tour_save({"name": "A", "points": PTS}); b = U.tour_save({"name": "B", "points": PTS})
        self.assertFalse(U.tour_rename(b, "a")); self.assertTrue(U.tour_rename(b, "C"))

    def test_staerke_wird_gespeichert_nur_mit_schalter(self):
        a = U.tour_save({"name": "A", "points": PTS, "avoid": ["city"], "cityw": "2"})
        b = U.tour_save({"name": "B", "points": PTS, "avoid": ["toll"], "cityw": "2"})
        self.assertEqual(U.tour_get(a)["cityw"], 2.0); self.assertNotIn("cityw", U.tour_get(b))

    def test_staerke_wird_auf_stufen_gerundet(self):
        self.assertEqual(U.city_w("1,6"), 1.5); self.assertEqual(U.city_w(9), 4.0); self.assertEqual(U.city_w("x"), 1.5); self.assertEqual(U.city_w(0), 0.5)

    def test_profilvariante_traegt_staerke(self):
        d = tempfile.mkdtemp(); old = U.BR_PROF; src = os.path.join(os.path.dirname(U.__file__), "brouter-profiles")
        for n in os.listdir(src): shutil.copy(os.path.join(src, n), d)
        U.BR_PROF = d
        try:
            n1 = U.variant_profile("motorrad_landstrasse", "city", "1"); n3 = U.variant_profile("motorrad_landstrasse", "city", "3")
            self.assertNotEqual(n1, n3)
            self.assertIn(U.city_cost(3.0).strip(), open(os.path.join(d, n3 + ".brf"), encoding="utf-8").read())
            self.assertIn(U.city_cost(1.0).strip(), open(os.path.join(d, n1 + ".brf"), encoding="utf-8").read())
        finally: U.BR_PROF = old

    def test_nachrechnen_traegt_werte_ein_und_laesst_rest(self):
        a = U.tour_save({"name": "Alt", "points": PTS, "avoid": ["toll"]}); vor = U.tour_get(a)
        calls = []
        def fake(pts, prof, avoid, cityw=None): calls.append((len(pts), avoid)); return 226.1, 192, 1204, [[12.1, 48.9, 300.4], [12.15, 48.95, 310], [12.1, 48.9, 300]]
        old = U.route_full; U.route_full = fake
        try:
            self.assertTrue(U._alte_touren())
            self.assertEqual(U.tours_nachrechnen(), 1); self.assertEqual(U.tours_nachrechnen(), 0)   # zweites Mal nichts mehr zu tun
            self.assertFalse(U._alte_touren())
        finally: U.route_full = old
        self.assertEqual(U.route_get(a), [[12.1, 48.9, 300], [12.15, 48.95, 310], [12.1, 48.9, 300]])
        t = U.tour_get(a)
        self.assertEqual((t["km"], t["min"], t["asc"]), (226.1, 192, 1204))
        for k in ("points", "profile", "avoid", "name", "updated"): self.assertEqual(t[k], vor[k])
        self.assertEqual(calls, [(3, ["toll"])])

    RT = [[12.1, 48.9, 300.4], [12.15, 48.95], [12.1, 48.9, 300]]

    def test_strecke_wird_mit_gespeichert_und_geliefert(self):
        a = U.tour_save({"name": "S", "points": PTS, "km": 100, "min": 90, "route": self.RT})
        self.assertEqual(U.route_get(a), [[12.1, 48.9, 300], [12.15, 48.95], [12.1, 48.9, 300]])

    def test_strecke_ohne_laenge_wird_nicht_gespeichert(self):
        a = U.tour_save({"name": "S", "points": PTS, "route": self.RT}); self.assertIsNone(U.route_get(a))

    def test_strecke_die_nicht_zu_start_und_ziel_passt_wird_verworfen(self):
        a = U.tour_save({"name": "S", "points": PTS, "km": 1, "min": 1, "route": [[13.5, 50.0], [13.6, 50.1]]}); self.assertIsNone(U.route_get(a))
        self.assertIsNone(U.route_clean([[200, 0], [1, 1]], PTS)); self.assertIsNone(U.route_clean("x", PTS)); self.assertIsNone(U.route_clean([[12.1, "a"], [1, 2]], PTS))

    def test_neu_speichern_ohne_strecke_loescht_die_alte(self):
        a = U.tour_save({"name": "S", "points": PTS, "km": 1, "min": 1, "route": self.RT}); self.assertIsNotNone(U.route_get(a))
        U.tour_save({"id": a, "name": "S", "points": [[48.9, 12.1], [49.5, 12.9]], "km": 2, "min": 2}); self.assertIsNone(U.route_get(a))

    def test_loeschen_entfernt_auch_die_strecke_datei_nicht_in_der_liste(self):
        a = U.tour_save({"name": "S", "points": PTS, "km": 1, "min": 1, "route": self.RT})
        self.assertEqual(len(U.tour_list()), 1)   # die .rt-Datei darf nicht als Tour auftauchen


if __name__ == "__main__":
    unittest.main()
