# #35 exact-artifact synthetic native embedding diagnostic

Source base: main `767abe6ca105b4195e9c00d00129915a10e74861`, fetched before editing. This batch adds a test-only diagnostic and host-runner mode, with no production source changes. #35 remains open.

## Implementation

`LocalEmbeddingPhoneTest#qualifyInstalledEmbedding` reuses `LiteRtEmbeddingProvider`, the canonical inventory verification/pinned artifact lease, native resource admission and teardown. It requires the supported `embeddinggemma2-740m` catalogue fingerprint and a matching fresh host SHA/length. No independent SDK handle, index write, preference setter, download, provider request or capability/recommendation promotion.

Fixed `synthetic-embedding-cpu-v1` criteria were written before native execution:

- Three English documents (cat care, bread, astronomy), four cat-care queries in EN/LV/RU/PL. Each expected-document cosine margin must exceed 0.01. This measures cross-language query retrieval against this small English corpus, not full language qualification.
- Two generated 256px PNGs (red circle / blue square), two English descriptions and one duplicate image. Both text-to-image margins and duplicate-versus-other image margin must exceed 0.01; duplicate cosine must be at least 0.999.
- The production `EmbeddingBatch` validates finite, normalized 768D vectors. Logs retain scalar scores/timings and identities, not vectors or personal files.

Per-stage statuses distinguish semantic `QUALITY_FAILED`, native/admission `UNAVAILABLE` and cancellation `INTERRUPTED`. The final test cannot pass if either stage is unavailable or incomplete. The host preserves generic crash/timeout/interruption truth and only refines a completed assertion with this exact run/SHA marker. Mixed quality failure and unavailability retain both in the CSV.

The stage timeout is cooperative (180 seconds); the host process timeout bounds blocked synchronous JNI. Memory is sampled PSS/native heap at 200ms, not exact peak RSS/GPU/energy qualification. Generated shapes do not establish representative media accuracy, source permissions or consumer-flow acceptance.

## Source review and desktop checks

Read-only source review found and resolved the initial runner conflation of ranking failure with native runtime failure. Follow-up review: no actionable findings. No reviewer builds/edits.

`Test-LiteRtProbeStatus.ps1`: 11 existing protocol cases +11 exact-run embedding cases passed. They cover quality/native/mixed outcomes, crash, timeout, interrupted process, incomplete instrumentation, stale run, wrong SHA and unknown status. Nine invalid mode combinations rejected before inventory/output creation (metadata, roles, GPU, capture, resume, context, cycles, residency, provider verification). PowerShell syntax and source whitespace checks passed.

## APK gate and physical result

Single requested gate `:app:assembleDebugAndroidTest`: **PASS in 5m9s**, 464 tasks (12 executed /452 up-to-date). Its dependency graph compiled production/test sources; no production APK assembly or broad desktop test rerun was requested.

Installed APK fingerprints were independently read back and matched:

| Package | SHA-256 | Bytes |
|---|---|---:|
| Production (unchanged) | `37d9ffe5598051af3a6033f7c0955d32a30a4e6cf014b92313979aa1a797c146` | 287548931 |
| Android test | `0151e5e1de4a3a9c0384da7df9ed04125f834bfdc2dc53e91396fa70d3ba1402` | 1963640 |

Physical run `41edc9893ed6435296f3f04da4a08740`: **PASS**, one Android case in94.443s, on `f01fe673` /23124RA7EO /API35 /LiteRT0.18.0. Model SHA `e7a8a2204b91e0f96e92960e84a09a89212e1633dcb7575a9bf3378b4df77f4c`, 484622336 bytes. The fresh host snapshot, test verification and provider pinned lease agreed. This executed real native CPU embedding engines, including the image encoder, rather than metadata inspection alone.

| Fixed check | Measured cosine margin | Criterion | Result |
|---|---:|---:|---|
| EN query → English cat-care document | 0.261467 | >0.01 | PASS |
| LV query → same document | 0.123865 | >0.01 | PASS |
| RU query → same document | 0.254321 | >0.01 | PASS |
| PL query → same document | 0.247178 | >0.01 | PASS |
| Red-circle text → correct image | 0.146558 | >0.01 | PASS |
| Blue-square text → correct image | 0.168192 | >0.01 | PASS |
| Duplicate image versus other image | 0.193640 | >0.01 | PASS |
| Duplicate-image cosine | 1.000000619 | >=0.999 | PASS |

Text batch: 7 vectors, initialization1677ms, computation5384ms, complete provider call8829ms. Image/text batch: 5 vectors, initialization1465ms, computation28830ms, complete call31477ms. Timings are these single cold batches, not repeated/per-query latency or a representative corpus benchmark. A normalized float-vector dot product can slightly exceed1 due to rounding; raw scores are preserved.

Sampled process peak PSS1154571KiB, native heap538294128bytes (200ms samples). Admission estimates753057792/1021493248bytes are policy budgets, not measured memory. No GPU run, audio/video embedding, model switch, low-RAM stress, consumer index/source-permission flow or human speech was exercised. No saved selection/capability promotion. MainActivity resumed after the run.

Raw build, exact-source hashes, package readback identities, immutable selected-artifact snapshot, native output/scalar rows, filtered footprints, CSV and launch evidence: [artifacts](artifacts/HALO-EMBEDDING-NATIVE-035-2026-10-24/).

## Remaining acceptance

Keep #35 open for representative text/image/audio/video similarity and consumer workflows, low-RAM/cancellation/model-switch/offline/permissions behavior, broad latency/memory characterization and human language review. This diagnostic neither enables catalogue media modalities nor marks a model suitable for conversational roles. No CI/Actions.
