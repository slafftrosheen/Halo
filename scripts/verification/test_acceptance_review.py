import importlib.util
import json
import shutil
import tempfile
import unittest
from pathlib import Path

MODULE_PATH = Path(__file__).with_name("review_acceptance.py")
SPEC = importlib.util.spec_from_file_location("acceptance_review", MODULE_PATH)
REVIEW = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(REVIEW)

class AcceptanceReviewTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.pack = self.root / "pack"
        self.pack.mkdir()
        for name in ("requirements.json", "protocols.json", "pack-metadata.json"):
            shutil.copyfile(REVIEW.PACK / name, self.pack / name)
    def tearDown(self):
        self.temporary.cleanup()
    def write(self, name, value):
        (self.pack / name).write_text(json.dumps(value), encoding="utf-8")
    def test_unexecuted_pack_keeps_every_original_open_issue_open(self):
        result = REVIEW.review(self.pack, self.root)
        self.assertEqual(11, len(result))
        self.assertTrue(all(row["decision"] == "KEEP_OPEN" and row["pending"] for row in result))
    def test_deleting_or_rewording_a_requirement_cannot_create_a_pass(self):
        rows = REVIEW.read(self.pack / "requirements.json")
        rows[0]["requirement"] = "An easier requirement"
        self.write("requirements.json", rows)
        with self.assertRaisesRegex(ValueError, "changed"):
            REVIEW.review(self.pack, self.root)
    def test_pass_without_hashed_reviewed_evidence_stays_open(self):
        rows = REVIEW.read(self.pack / "requirements.json")
        rows[0]["status"] = "PASS"
        self.write("requirements.json", rows)
        result = REVIEW.review(self.pack, self.root)
        pending = next(row for row in result if row["issue"] == rows[0]["issue"])["pending"]
        self.assertEqual("PASS_WITHOUT_REVIEWED_EVIDENCE", next(row for row in pending if row["id"] == rows[0]["id"])["reason"])
    def test_native_fixture_cannot_qualify_a_full_physical_protocol(self):
        evidence = self.root / "fixture.txt"
        evidence.write_text("Synthetic fixture pass", encoding="utf-8")
        rows = REVIEW.read(self.pack / "protocols.json")
        rows[0].update(status="PASS", reviewer="Fixture reviewer", evidence=[{
            "path": "fixture.txt", "sha256": REVIEW.digest(evidence), "scope": "NATIVE_FIXTURE_ONLY",
            "sourceStamp": "fixture-source", "recordedAt": "2026-10-24T00:00:00Z"}])
        self.write("protocols.json", rows)
        result = REVIEW.review(self.pack, self.root)
        pending = next(row for row in result if row["issue"] == rows[0]["issue"])["pending"]
        self.assertEqual("FIXTURE_CANNOT_PROVE_FULL_PHYSICAL_PROTOCOL", next(row for row in pending if row["id"] == rows[0]["id"])["reason"])

if __name__ == "__main__":
    unittest.main()
