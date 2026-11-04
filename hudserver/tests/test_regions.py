"""Tests für die Europa-Regionsliste (ohne Netz, mit nachgebauter Geofabrik-Übersicht).
Aufruf: python3 -m unittest discover -s hudserver/tests"""
import json, os, sys, tempfile, unittest

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


def box(w, s, e, n):
    return [[w, s], [e, s], [e, n], [w, n], [w, s]]


def feat(fid, parent, name, polys, pbf=True):
    urls = {"pbf": f"https://download.geofabrik.de/{fid}-latest.osm.pbf"} if pbf is True else ({"pbf": pbf} if pbf else {})
    geom = {"type": "Polygon", "coordinates": [polys[0]]} if len(polys) == 1 else {"type": "MultiPolygon", "coordinates": [[p] for p in polys]}
    return {"type": "Feature", "properties": {"id": fid.split("/")[-1], "parent": parent, "name": name, "urls": urls}, "geometry": geom}


def index():
    return {"type": "FeatureCollection", "features": [
        feat("europe", None, "Europe", [box(-30, 30, 50, 80)], pbf=False),
        feat("europe/france", "europe", "France", [box(-5, 41, 10, 51)]),
        feat("europe/france/bretagne", "france", "Bretagne", [box(-5.2, 47.2, -1, 49)]),
        feat("europe/france/alsace", "france", "Alsace", [box(6.8, 47.4, 8.3, 49.1)]),
        feat("europe/denmark", "europe", "Denmark", [box(8, 54.5, 15.2, 57.8)]),
        feat("europe/norway", "europe", "Norway", [box(4.5, 57.9, 31.2, 71.3), box(10, 76.4, 33, 80.9)]),
        feat("europe/great-britain", "europe", "Great Britain", [box(-8, 49.8, 2, 60.9)]),
        feat("europe/germany", "europe", "Germany", [box(5.8, 47.2, 15.1, 55.1)]),
        feat("europe/germany/bayern", "germany", "Bayern", [box(8.9, 47.2, 13.9, 50.6)]),
    ]}


class RegionListTest(unittest.TestCase):
    def setUp(self):
        self.out, self.missing = U.regions_from_index(index())
        self.by = {r["id"]: r for r in self.out}

    def test_land_mit_unterregionen(self):
        self.assertIn("fr-bretagne", self.by); self.assertIn("fr-alsace", self.by)
        self.assertNotIn("fr", self.by)
        b = self.by["fr-bretagne"]
        self.assertEqual(b["name"], "Bretagne"); self.assertEqual(b["land"], "FR"); self.assertEqual(b["pbf"], "europe/france/bretagne")
        self.assertEqual(b["bbox"], [-5.2, 47.2, -1.0, 49.0])

    def test_land_ohne_unterregionen_bleibt_ganz(self):
        d = self.by["dk"]
        self.assertEqual(d["name"], "Dänemark"); self.assertEqual(d["pbf"], "europe/denmark"); self.assertEqual(d["land"], "DK")

    def test_basisland_wird_uebergangen(self):
        self.assertFalse([r for r in self.out if r["land"] == "DE"])

    def test_fehlende_laender_werden_gemeldet(self):
        self.assertIn("Belgien", self.missing); self.assertNotIn("Dänemark", self.missing)

    def test_ferne_kleine_insel_zaehlt_nicht_fuers_rechteck(self):
        n = self.by["no"]["bbox"]
        self.assertLess(n[3], 72)   # Spitzbergen (bis 80.9) bleibt draußen

    def test_nahe_insel_zaehlt_mit(self):
        idx = index()
        for f in idx["features"]:
            if f["properties"]["id"] == "denmark":   # Bornholm liegt nahe genug (unter 3 Grad)
                f["geometry"] = {"type": "MultiPolygon", "coordinates": [[box(8, 54.5, 12.7, 57.8)], [box(14.7, 55.0, 15.2, 55.3)]]}
        out, _ = U.regions_from_index(idx)
        self.assertEqual({r["id"]: r for r in out}["dk"]["bbox"][2], 15.2)

    def test_ausweichkennung_grossbritannien(self):
        g = self.by["gb"]
        self.assertEqual(g["pbf"], "europe/great-britain"); self.assertEqual(g["name"], "Großbritannien")

    def test_ids_sind_eindeutig_und_gueltig(self):
        ids = [r["id"] for r in self.out]
        self.assertEqual(len(ids), len(set(ids)))
        self.assertFalse(set(ids) & set(U.BASE_IDS))
        import re
        for i in ids: self.assertRegex(i, r"^[a-z0-9-]+$")

    def test_region_ohne_pbf_wird_nicht_angeboten(self):
        idx = index(); idx["features"][2]["properties"]["urls"] = {}
        out, _ = U.regions_from_index(idx)
        ids = {r["id"] for r in out}
        self.assertNotIn("fr-bretagne", ids); self.assertIn("fr-alsace", ids)


class ReloadTest(unittest.TestCase):
    def tearDown(self):
        if os.path.exists(U.EXTRA_FILE): os.remove(U.EXTRA_FILE)
        U.reload_regions()

    def test_ohne_datei_nur_basis(self):
        U.reload_regions()
        self.assertEqual([r["id"] for r in U.REGIONS], U.BASE_IDS)

    def test_extras_werden_angehaengt_und_unbrauchbare_uebergangen(self):
        good = {"id": "dk", "name": "Dänemark", "land": "DK", "bbox": [8, 54.5, 15.2, 57.8], "pbf": "europe/denmark"}
        dup = dict(good, id=U.BASE_IDS[0])
        bad_id = dict(good, id="Böse/ID")
        bad_bbox = dict(good, id="xx", bbox=[1, 2])
        json.dump([good, dup, bad_id, bad_bbox, "kaputt"], open(U.EXTRA_FILE, "w", encoding="utf-8"))
        U.reload_regions()
        ids = [r["id"] for r in U.REGIONS]
        self.assertEqual(ids, U.BASE_IDS + ["dk"])
        self.assertIn("dk", U.BYID)
        self.assertEqual(U.LAND_NAMES["DK"], "Dänemark")

    def test_neue_regionen_sind_nicht_angehakt(self):
        json.dump([{"id": "dk", "name": "Dänemark", "land": "DK", "bbox": [8, 54.5, 15.2, 57.8], "pbf": "europe/denmark"}], open(U.EXTRA_FILE, "w", encoding="utf-8"))
        U.reload_regions()
        self.assertNotIn("dk", U.CFG["regions"])
        self.assertEqual(U.CFG["regions"], U.BASE_IDS)

    def test_kaputte_datei_schadet_nicht(self):
        open(U.EXTRA_FILE, "w").write("{nicht json")
        U.reload_regions()
        self.assertEqual([r["id"] for r in U.REGIONS], U.BASE_IDS)


if __name__ == "__main__":
    unittest.main()


class FetchTest(unittest.TestCase):
    """regions_fetch gegen einen kleinen lokalen Server, der die nachgebaute Übersicht ausliefert."""
    def test_holen_speichern_laden(self):
        import http.server, threading, functools, shutil
        d = tempfile.mkdtemp()
        json.dump(index(), open(os.path.join(d, "index-v1.json"), "w", encoding="utf-8"))
        h = functools.partial(http.server.SimpleHTTPRequestHandler, directory=d)
        h.log_message = lambda *a, **k: None
        srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), h)
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        old = U.GEOFABRIK
        try:
            U.GEOFABRIK = f"http://127.0.0.1:{srv.server_address[1]}"
            os.makedirs(U.PUB, exist_ok=True)
            n, missing = U.regions_fetch()
            self.assertGreater(n, 3); self.assertIn("Belgien", missing)
            self.assertIn("fr-bretagne", U.BYID)
            mf = json.load(open(f"{U.PUB}/manifest.json", encoding="utf-8"))
            names = {a["id"]: a["landname"] for a in mf["alle"]}
            self.assertEqual(names["fr-bretagne"], "Frankreich"); self.assertEqual(names[U.BASE_IDS[0]], "Deutschland")
            self.assertEqual(U.regions_info()["extra"], n)
            self.assertEqual(U.CFG["regions"], U.BASE_IDS)
        finally:
            U.GEOFABRIK = old; srv.shutdown()
            if os.path.exists(U.EXTRA_FILE): os.remove(U.EXTRA_FILE)
            U.reload_regions()

    def test_fehler_wenn_nicht_erreichbar(self):
        old = U.GEOFABRIK
        try:
            U.GEOFABRIK = "http://127.0.0.1:9"
            with self.assertRaises(RuntimeError): U.regions_fetch()
        finally: U.GEOFABRIK = old
