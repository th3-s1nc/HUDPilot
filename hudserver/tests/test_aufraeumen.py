"""Tests: Handys vergessen/entfernen und Regionen vom Server löschen."""
import json, os, sys, tempfile, time, unittest

TMPD = tempfile.mkdtemp()
os.environ["DATA_DIR"] = TMPD
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "updater"))
import update as U


def touch(p, n=10):
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb") as f: f.write(b"x" * n)


class HandyTest(unittest.TestCase):
    def setUp(self):
        U.DEVS.clear()
        now = time.time()
        U.DEVS["a" * 16] = {"name": "Neu", "profile": "p1", "first": now - 99, "seen": now - 60}
        U.DEVS["b" * 16] = {"name": "Mittel", "profile": "p1", "first": now - 999, "seen": now - 5 * 86400}
        U.DEVS["c" * 16] = {"name": "Alt", "profile": "p1", "first": now - 999999, "seen": now - 100 * 86400}
        U.DEVS["d" * 16] = {"name": "Fremd", "profile": "p2", "first": now, "seen": now}

    def test_cleanup_vergisst_nur_alte(self):
        self.assertEqual(U.dev_cleanup(), 1)
        self.assertNotIn("c" * 16, U.DEVS)
        self.assertEqual(len(U.DEVS), 3)

    def test_liste_neueste_zuerst(self):
        self.assertEqual([g["name"] for g in U.dev_list("p1")], ["Neu", "Mittel", "Alt"])

    def test_entfernen(self):
        self.assertTrue(U.dev_remove("b" * 16))
        self.assertFalse(U.dev_remove("b" * 16))
        self.assertEqual(len(U.dev_list("p1")), 2)
        with open(U.DEV_FILE) as f: self.assertNotIn("b" * 16, json.load(f))


class RegionLoeschenTest(unittest.TestCase):
    def setUp(self):
        self.r = [r for r in U.REGIONS][:2]
        self.a, self.b = self.r
        U.S["state"] = "idle"; U.PENDING.clear() if hasattr(U.PENDING, "clear") else None
        for r in self.r:
            touch(f"{U.PUB}/maps/{r['id']}.pmtiles", 100)
            touch(f"{U.PUB}/pois/{r['id']}.tsv.gz", 20)
            touch(f"{U.PUB}/limits/{r['id']}.zip", 30)
        for r in self.r:
            for t in U.tiles_for(r["bbox"]): touch(f"{U.PUB}/rd5/{t}.rd5", 7)
        self._cfg = list(U.CFG["regions"])
        U.CFG["regions"] = [self.a["id"], self.b["id"]]

    def tearDown(self):
        U.CFG["regions"] = self._cfg

    def test_loescht_dateien_und_haken(self):
        freed = U.region_delete(self.a["id"])
        self.assertGreaterEqual(freed, 150)
        for sub, ext in (("maps", "pmtiles"), ("pois", "tsv.gz"), ("limits", "zip")):
            self.assertFalse(os.path.exists(f"{U.PUB}/{sub}/{self.a['id']}.{ext}"))
            self.assertTrue(os.path.exists(f"{U.PUB}/{sub}/{self.b['id']}.{ext}"))
        self.assertNotIn(self.a["id"], U.CFG["regions"])
        self.assertIn(self.b["id"], U.CFG["regions"])

    def test_geteilte_kacheln_bleiben(self):
        shared = set(U.tiles_for(self.a["bbox"])) & set(U.tiles_for(self.b["bbox"]))
        U.region_delete(self.a["id"])
        for t in shared: self.assertTrue(os.path.exists(f"{U.PUB}/rd5/{t}.rd5"), t)
        for t in set(U.tiles_for(self.a["bbox"])) - set(U.tiles_for(self.b["bbox"])):
            self.assertFalse(os.path.exists(f"{U.PUB}/rd5/{t}.rd5"), t)

    def test_waehrend_bau_verweigert(self):
        U.S["state"] = "running"
        try:
            with self.assertRaises(ValueError): U.region_delete(self.a["id"])
        finally: U.S["state"] = "idle"
        self.assertTrue(os.path.exists(f"{U.PUB}/maps/{self.a['id']}.pmtiles"))

    def test_unbekannt(self):
        with self.assertRaises(ValueError): U.region_delete("gibtsnicht")


if __name__ == "__main__": unittest.main()
