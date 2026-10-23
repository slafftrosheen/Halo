# Intent admission correction #36

Base `d3f8b29d80cee2fd17f40fb812fe81bf3e9cf9b3`, freshly fetched with zero divergence before the source freeze. Scope: existing local classifier admission and reply validity; no new intent engine, planner, permission, action executor or provider fallback.

## Source behavior and canonical owners

LocalIntentModelRouter's separate advisory mutex and fast busy/residency snapshots did not reserve LiteRT's canonical load mutex. A competing chat/model change could enter between the snapshot and ordinary `generate`, leaving the classifier queued and able to load its earlier candidate after a different chat model became resident.

`LiteRtLocalProvider.tryGenerateAdvisory` now attempts the existing load mutex without waiting. Inside that mutex it rechecks actual model-session identity and compute activity; another resident model or active work returns no advice. Ordinary `generate` continues to await the same mutex. Both use the same extracted `generateLocked` implementation, including physical validation, native resource admission, generation, diagnostics and cleanup. Cancellation releases the advisory lock in `finally`.

The advisory API accepts only bounded model/text/system-instruction inputs and constructs a fresh text-only request with 12 output tokens, no tools, disabled reasoning, suppressed streaming and temperature zero. It passes only that system instruction and final text; no memory, screen, files, device state or chat history. Caller-owned 2s timeout remains unchanged. It does not select a role, download, qualify a model or introduce a cloud path.

Router preferences are rechecked before requesting advice and after generation. The reply must report the actual local provider and exact resolved model, contain no tool calls, and parse as a standalone ACTION/ANSWER/UNCERTAIN label. Changed/disabled settings and mismatched replies return no advice. A label remains advisory and does not authorize side effects; existing final-input, deterministic native-command, runtime tool permission/approval/replay boundaries are unchanged.

## Source review and regression sources

All production and regression sources were finished before the first focused gate. Read-only review found no actionable source or signature blocker.

Five new tests use the actual provider with a fake native runtime: a held conversational generation prevents advisory queueing; a different resident chat model and preferences remain untouched; admitted advice uses canonical bounded configuration and actual response identity; cancellation releases the mutex for a later request; invalid input cannot load/generate. Existing provider stream/generation tests exercise both paths after the extraction. Two router cases reject changed/disabled settings, foreign/missing resolved identity, tool calls and non-label text.

## Execution and acceptance

Focused gates passed on their first attempts: provider production/test compilation plus 20 cases in 1m13s; app production/test compilation plus four router cases in 5m21s. Zero failures/errors/skips across 24 cases. No APK assembly/install while ADB is absent. No model download, native inference, cloud call or CI/Actions.

#36 remains OPEN: physical EN/LV/RU/PL classification/action-loop matrix, real Qwen label conformity and measured latency/residency, actual markup UI/TTS/history behavior, restart/model removal and complete Polish UI remain unverified. Fake runtime contention is proof of the admission code path, not physical inference or multilingual quality.


Compiled production trees: app `f76b9e792bc1d295b674030b395c185faf6924fa`, LiteRT `418839e45707a0e354448c2efdf345e5f6cb6f1a`. No production changes followed the gates. Raw logs and result XML are in `artifacts/HALO-INTENT-ADMISSION-036-2026-10-23/`. ADB remains empty; the last built APK is the final #39 package and the last installed APK is the first #39 package. Neither contains these #36 or later #38 changes.
