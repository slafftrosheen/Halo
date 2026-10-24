"""Summarize real recorded voice timing. Does not generate measurements or qualify an issue."""
import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path

def summarize(path):
    groups = defaultdict(lambda: defaultdict(list))
    for row in csv.DictReader(path.open(encoding="utf-8-sig", newline="")):
        if not row.get("runId"):
            continue
        for field in ("configurationKey", "sourceStamp", "appSha256", "modelSha256", "fixtureSha256", "device", "language", "evidencePath"):
            if not row.get(field):
                raise ValueError(f"Missing {field} on {row['runId']}")
        if row["profile"] not in ("SERIAL", "AUTO") or row["temperature"] not in ("COLD", "WARM"):
            raise ValueError("Unknown profile/temperature")
        # Grouping the exact fixed configuration avoids comparing different model/fixture/language bytes.
        key = tuple(row[field] for field in ("configurationKey", "modelSha256", "fixtureSha256", "device", "language"))
        eos, audible = float(row["endOfSpeechMs"]), float(row["firstAudibleMs"])
        if not math.isfinite(eos) or not math.isfinite(audible) or eos < 0 or audible < eos:
            raise ValueError("Invalid real timing")
        groups[key][row["profile"]].append({"latencyMs": audible - eos, "warm": row["temperature"] == "WARM",
            "correct": row.get("correct") == "true", "humanAudibleConfirmed": row.get("humanAudibleConfirmed") == "true",
            "runId": row["runId"]})
    output = []
    for key, profiles in groups.items():
        item = {"configurationKey": key[0], "modelSha256": key[1], "fixtureSha256": key[2], "device": key[3], "language": key[4]}
        eligible = True
        for profile in ("SERIAL", "AUTO"):
            rows = profiles.get(profile, [])
            if len({row["runId"] for row in rows}) != len(rows):
                raise ValueError("Repeated run ID cannot increase the sample size")
            warm = sorted(row["latencyMs"] for row in rows if row["warm"])
            cold = [row for row in rows if not row["warm"]]
            valid = len(warm) >= 30 and len(cold) >= 5 and all(row["correct"] and row["humanAudibleConfirmed"] for row in rows)
            eligible &= valid
            item[profile] = {"warmCount": len(warm), "coldCount": len(cold), "correctAndAudible": valid,
                "warmMedianMs": statistics.median(warm) if warm else None,
                "warmP95Ms": warm[math.ceil(len(warm) * .95) - 1] if warm else None}
        item["timingCriterion"] = "NOT_QUALIFIED"
        if eligible:
            item["timingCriterion"] = "PASS" if all(item["AUTO"][field] < item["SERIAL"][field]
                for field in ("warmMedianMs", "warmP95Ms")) else "FAIL_REGRESSION_OR_NO_IMPROVEMENT"
        item["remaining"] = "Review raw traces, actual delegate placement, correctness, RSS/thermal/battery and all original issue requirements"
        output.append(item)
    return output

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = summarize(args.csv)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Summarized {len(result)} real configuration groups; no issue is automatically qualified")
