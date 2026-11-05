import io, os, sys, unittest
sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "updater"))
import baustellen as B

A = [48.93119, 12.20879]; Z = [48.92699, 12.20907]           # Köfering und Gewerbegebiet, Luftlinie ca. 500 m
DIRECT = [A, [48.9290, 12.2089], Z]                            # fast gerade (Bahnhofstraße)
DETOUR = [[48.93119, 12.20879], [48.93183, 12.20598], [48.92735, 12.20645], [48.92459, 12.20816], [48.92673, 12.20953]]  # über die Bundesstraße, über 1 km

def rec(rid, a, b, text="Zwischen A und B gesperrt"):
    pt = lambda p: f"<pointCoordinates><latitude>{p[0]}</latitude><longitude>{p[1]}</longitude></pointCoordinates>"
    return f"""<situation><situationRecord xsi:type="MaintenanceWorks" id="{rid}"><generalPublicComment><comment><values><value>{text}</value></values></comment>
<commentType>description</commentType></generalPublicComment><groupOfLocations><from>{pt(a)}</from><to>{pt(b)}</to></groupOfLocations></situationRecord></situation>"""

def xml(*recs):
    return ('<?xml version="1.0"?><d2LogicalModel xmlns="http://datex2.eu/schema/2/2_0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">'
            '<payloadPublication><publicationTime>2026-11-05T10:00:00Z</publicationTime>' + "".join(recs) + "</payloadPublication></d2LogicalModel>").encode()


class GeomTest(unittest.TestCase):
    def test_plausible(self):
        self.assertTrue(B.plausible(DIRECT, A, Z))
        self.assertFalse(B.plausible(DETOUR, A, Z))
        self.assertFalse(B.plausible(None, A, Z))

    def test_detour_wird_gerade_linie(self):
        items = [{"a": A, "b": Z}]
        ok, fail = B.add_geometry(items, lambda a, b: DETOUR, {})
        self.assertEqual((ok, fail), (0, 1)); self.assertNotIn("g", items[0])

    def test_direkter_weg_wird_genommen(self):
        items = [{"a": A, "b": Z}]
        ok, fail = B.add_geometry(items, lambda a, b: DIRECT, {})
        self.assertEqual((ok, fail), (1, 0)); self.assertIn("g", items[0])

    def test_alter_zwischenspeicher_wird_ignoriert(self):
        old = {f"{A[0]},{A[1]}|{Z[0]},{Z[1]}": DETOUR}   # Schlüssel der alten Version
        items = [{"a": A, "b": Z}]; calls = []
        B.add_geometry(items, lambda a, b: calls.append(1) or DIRECT, old)
        self.assertEqual(len(calls), 1); self.assertIn("g", items[0])

    def test_gespeicherter_schlechter_weg_wird_nochmal_geprueft(self):
        cache = {B.GEOM_V + f"{A[0]},{A[1]}|{Z[0]},{Z[1]}": DETOUR}
        items = [{"a": A, "b": Z}]
        B.add_geometry(items, lambda a, b: None, cache)
        self.assertNotIn("g", items[0])

    def test_kurze_strecke_ohne_suche(self):
        items = [{"a": A, "b": [A[0] + 0.0001, A[1]]}]; calls = []
        B.add_geometry(items, lambda a, b: calls.append(1), {})
        self.assertEqual(calls, [])


class ParseTest(unittest.TestCase):
    def test_beide_richtungen_zaehlen_einmal(self):
        data = xml(rec("x1", A, Z, "Zwischen Köfering und Alteglofsheim gesperrt"), rec("x2", Z, A, "Zwischen Alteglofsheim und Köfering gesperrt"))
        items, pub = B.parse(io.BytesIO(data))
        self.assertEqual(len(items), 1); self.assertEqual(items[0]["k"], "c")

    def test_verschiedene_stellen_bleiben(self):
        data = xml(rec("x1", A, Z), rec("x2", A, [48.9, 12.2]))
        items, _ = B.parse(io.BytesIO(data))
        self.assertEqual(len(items), 2)


if __name__ == "__main__":
    unittest.main()
