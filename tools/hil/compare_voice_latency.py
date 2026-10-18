#!/usr/bin/env python3
"""Offline comparison of real Halo first-audio logcat markers (issue #33).

Example:
  python tools/hil/compare_voice_latency.py baseline.log candidate.log

Inputs must be captured with the same device, model bytes, prompt, context,
audio route, thermal state, and cold/warm policy. This script does not
establish whether those conditions were matched and never runs ADB itself.
It reads only HALO_VOICE_LATENCY lines, not transcripts or responses.
"""
from __future__ import annotations

import argparse
import math
from pathlib import Path
import re
from statistics import median

MARKER = "HALO_VOICE_LATENCY"
LATENCY = re.compile(r"\beosToAudioMs=(null|[0-9]+)\b")
TRACE = re.compile(r"\binteraction=([^\s]+)")
MIN_SAMPLES = 30


def read_samples(path: Path) -> tuple[list[int], int, int]:
    """Samples, first-audio records, duplicate trace records.

    The logging owner emits first-audio only, so this cannot measure failed,
    cancelled or unstarted interactions. The denominator is NOT all attempts.
    """
    values: dict[str, int | None] = {}
    duplicates = 0
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if MARKER not in line:
            continue
        match = LATENCY.search(line)
        if not match:
            continue
        identity = TRACE.search(line)
        # Keep distinct lines without a trace key rather than collapsing them.
        key = identity.group(1) if identity else "line-" + str(len(values))
        if key in values:
            duplicates += 1
            continue
        value = None if match.group(1) == "null" else int(match.group(1))
        # Reject corrupt/wildly inflated samples rather than letting them dominate P95.
        if value is not None and value > 600_000:
            value = None
        values[key] = value
    samples = sorted(v for v in values.values() if v is not None)
    return samples, len(values), duplicates


def percentile(sorted_values: list[int], q: float) -> int:
    assert sorted_values and 0 < q <= 1
    return sorted_values[max(0, math.ceil(len(sorted_values) * q) - 1)]


def summary(path: Path, minimum: int) -> dict[str, int | float | None]:
    values, first_audio, duplicates = read_samples(path)
    eligible = len(values) >= minimum
    return {
        "first_audio_records": first_audio,
        "valid_buffered_endpoint_records": len(values),
        "missing_endpoint_records": first_audio - len(values),
        "duplicate_records_ignored": duplicates,
        "coverage_pct": round(100 * len(values) / first_audio, 1) if first_audio else None,
        "median_ms": median(values) if eligible else None,
        "p95_ms": percentile(values, 0.95) if eligible else None,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("baseline", type=Path, help="Serial/baseline logcat text")
    parser.add_argument("candidate", type=Path, help="Parallel/candidate logcat text")
    parser.add_argument("--min-samples", type=int, default=MIN_SAMPLES,
                        help="Minimum real endpoint-to-audio measurements per run (default: 30)")
    args = parser.parse_args()
    if args.min_samples < 30:
        parser.error("At least 30 real measurements are required for P95 reporting")

    outputs = {}
    for label, path in (("baseline", args.baseline), ("candidate", args.candidate)):
        try:
            outputs[label] = summary(path, args.min_samples)
        except OSError as exc:
            parser.error(f"Cannot read {label} file: {exc}")
        print(f"{label}: {path.name}")
        for key, value in outputs[label].items():
            print(f"  {key}: {value if value is not None else 'NOT_ESTABLISHED'}")

    baseline = outputs["baseline"]
    candidate = outputs["candidate"]
    if baseline["p95_ms"] is not None and candidate["p95_ms"] is not None:
        print("Descriptive deltas (candidate minus baseline):")
        for name in ("median_ms", "p95_ms"):
            difference = candidate[name] - baseline[name]
            print(f"  {name}: {difference:+.1f} ms")
    else:
        print("Insufficient real samples for a descriptive median/P95 comparison.")

    print("LIMITS: first-audio records are not all attempts; failure rate cannot be determined.")
    print("LIMITS: speech endpoint unavailable for live STT; missing values are never zero.")
    print("LIMITS: unpaired logcat cannot prove identical prompts, thermal state, model/backend,")
    print("        completion quality, GPU delegate placement or statistical significance.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
