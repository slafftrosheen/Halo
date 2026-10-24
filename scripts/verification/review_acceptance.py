"""Verify evidence bindings and report closure candidates; never modifies GitHub or grants approval."""
import argparse
import hashlib
import json
from pathlib import Path

PACK = Path(__file__).resolve().parents[2] / "docs/testing/open-issues-2026-10-24"
STATUSES = {"PASS", "FAIL", "BLOCKED", "NOT_TESTED"}

def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))

def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            result.update(block)
    return result.hexdigest()

def review(pack, evidence_root):
    metadata = read(pack / "pack-metadata.json")
    requirements = read(pack / "requirements.json")
    protocols = read(pack / "protocols.json")
    for rows, key, fields in ((requirements, "requirements", ("id", "issue", "sourceLine", "section", "requirement")),
                              (protocols, "protocols", ("id", "issue", "title", "steps", "expected"))):
        if len(rows) != metadata[key] or len({row["id"] for row in rows}) != len(rows):
            raise ValueError(f"Missing/duplicate {key}; do not narrow the acceptance scope")
        spec = [{field: row[field] for field in fields} for row in rows]
        actual = hashlib.sha256(json.dumps(spec, sort_keys=True, ensure_ascii=False).encode()).hexdigest()
        if actual != metadata[key + "SpecSha256"]:
            raise ValueError(f"Acceptance {key} changed; review the original issue scope and regenerate deliberately")
    summary = []
    for issue in metadata["openIssues"]:
        rows = [row for row in requirements + protocols if row["issue"] == issue]
        pending = []
        for row in rows:
            if row["status"] not in STATUSES:
                raise ValueError(f"Invalid status on {row['id']}")
            reason = row["status"]
            if row["status"] == "PASS":
                if not row.get("reviewer") or not row.get("evidence"):
                    reason = "PASS_WITHOUT_REVIEWED_EVIDENCE"
                for item in row.get("evidence", []):
                    if not isinstance(item, dict):
                        reason = "EVIDENCE_NEEDS_PATH_HASH_SCOPE_AND_SOURCE_BINDING"; break
                    if not all(item.get(key) for key in ("path", "sha256", "scope", "sourceStamp", "recordedAt")):
                        reason = "INCOMPLETE_EVIDENCE_BINDING"; break
                    path = (evidence_root / item["path"]).resolve()
                    if not path.is_relative_to(evidence_root) or not path.is_file() or digest(path) != item["sha256"]:
                        reason = "MISSING_OR_CHANGED_EVIDENCE"; break
                    if row in protocols and item["scope"] == "NATIVE_FIXTURE_ONLY":
                        reason = "FIXTURE_CANNOT_PROVE_FULL_PHYSICAL_PROTOCOL"; break
            if reason != "PASS":
                pending.append({"id": row["id"], "reason": reason})
        summary.append({"issue": issue, "decision": "KEEP_OPEN" if pending else "CANDIDATE_FOR_HUMAN_EVIDENCE_REVIEW",
                        "requirementsAndProtocols": len(rows), "pending": pending})
    return summary

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--pack", type=Path, default=PACK)
    parser.add_argument("--evidence-root", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = review(args.pack.resolve(), args.evidence_root.resolve())
    if args.output:
        args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    for row in result:
        print(f"#{row['issue']}: {row['decision']} ({len(row['pending'])} pending evidence reviews)")
    raise SystemExit(1 if any(row["pending"] for row in result) else 0)
