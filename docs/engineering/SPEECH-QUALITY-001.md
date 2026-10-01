# SPEECH-QUALITY-001 — local recognition quality

Source baseline: main 32d20ca1cd8f7e447983eec9740f20be6aa92ebc; sherpa-onnx v1.13.8.
Implemented 2026-10-01. Physical-device recognition quality has **not** been validated.

| Model | Local | Languages | Tier | Status | Download MB | CPU threads |
|---|---|---|---|---|---:|---:|
| Whisper Tiny int8 | yes | multilingual, including lv | Fast | supported | 104 | 2 |
| Whisper Base int8 | yes | multilingual, including lv | Balanced | recommended general purpose | 161 | 3 |
| Whisper Small int8 | yes | multilingual, including lv | Quality | supported, quality-test candidate | 375 | 4 |
| Omnilingual ASR 300M int8 | yes | broad multilingual | Experimental | comparison engine; Latvian output unvalidated | 365 | 4 |
| Qwen3-ASR 0.6B | yes | documented limited set | Future | not wired | — | — |

Model IDs, provider IDs, file roles, exact byte counts, SHA-256 checksums, pinned upstream
commit revisions and execution settings live in `SherpaSpeechModelCatalog`. Artifacts:
[official Tiny](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-tiny),
[official Base](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-base),
[official Small](https://huggingface.co/csukuangfj/sherpa-onnx-whisper-small),
[official Omnilingual](https://huggingface.co/csukuangfj/sherpa-onnx-omnilingual-asr-1600-languages-300M-ctc-int8-2025-11-12).
No English-only artifacts or Qwen implementation were added.

## Setup and selection

Settings → Voice → Speech recognition contains the language picker, model cards,
explicit downloads, deletion and Silero installation. No models download automatically.
**Latviešu = lv**: multilingual Whisper receives `language="lv", task="transcribe"`.
Automatic language passes `language=""`. The runtime consumes the same DataStore-backed
language repository as the picker and waits for migration before resolving a recognizer.

Explicit model selection is persisted using the existing SpeechProviderSelector.
Missing explicit models report unavailable and never fall back. Automatic chooses installed
Base, then Tiny; it never chooses Small or experimental Omnilingual. Tiny keeps its directory
`sherpa-whisper-tiny` and provider ID `speech.sherpa_onnx.whisper_tiny`. Existing complete Tiny
files remain valid without downloading again; their sizes are checked, while new installations
also verify checksums. Installed, Selected and Resident are separate facts. Select another model
before deleting the selected or currently resolved automatic model.

Downloads use descriptor totals, IO storage preflight and a 64 MB reserve. The full artifact set
is conservatively reserved even when some files exist. Each file downloads to `.partial`, checks
exact size and SHA-256, then atomically replaces its destination in the same directory. There is
no second full-size copy. Verification sidecars bind new model readiness to the verified file's
checksum, length and modification time. Failed verification preserves the previous destination;
partial sets are unavailable. Revisions are pinned for all recognition models; the Silero release
artifact is protected by exact size and SHA-256.

## Capture, ownership and observability

The one production SpeechCaptureProfile uses threshold 0.50, minimum speech 200 ms,
trailing silence 350 ms, maximum speech 8 seconds and pre-roll 400 ms. Both Silero adapters
consume this profile. The capture detector notices installation on the next capture.
Whole-capture quality analysis runs on Default for phone and external buffered recognition,
reuses its analyzer and aggregates finalized Silero segments (including the last segment after
flush). Energy fallback explicitly reports SIMPLE_ENERGY; only actual Silero analysis reports SILERO.

STT remains HEAVY_ASR under NativeResourceCoordinator. Threads are clamped to at most
available processors minus one, with a floor of one. Repeated utterances reuse the recognizer.
Model/language changes close the old delegate before constructing a replacement. Native stream
cleanup ownership and resource coordination remain in place. STT_RECOGNIZER_INVALIDATED records
MODEL_CHANGED, LANGUAGE_CHANGED or CONFIG_CHANGED. Backend/config is fixed CPU/int8/greedy for this batch.

HALO_STT_BEGIN includes capture, provider, model, family, language, audio duration, peak,
residency and effective threads. HALO_STT_DONE includes load/decode/total time, character count
and RTF = decodeMs / audioMs. No transcript or PCM is logged. RecognitionQuality carries model
and timing provenance into accepted observations and rejected speech events. Activity shows
compact model/language/decode/RTF provenance and expandable quality details, including Energy fallback.

## Isolated self-test and next physical acceptance

Test speech recognition requests microphone permission if needed, records until Finish capture
(or 15 seconds), then recognizes using the selected provider. It refuses to start during a runtime
speech turn. It shows transcript, model, language, audio duration, load/decode/total time, RTF,
VAD voiced ratio/estimator, peak and the same transcript gate result. It has no EventBus,
cognition, network or persistence dependency; capture stops before decode and PCM arrays are
cleared at completion/cancellation. Leaving the settings screen cancels the probe.

Install Base, Small and Silero explicitly. Speak the same English references with Tiny/Base/Small,
then select Latviešu and repeat the Latvian references with Base/Small. Omnilingual is an optional
comparison and is not recommended for Latvian until actual device output has been reviewed.
Record results and Activity diagnostics; only physical comparisons can establish quality/latency
improvements or a future default.

Latvian references:
- Kāda šodien ir temperatūra?
- Atver Spotify un atskaņo mūziku.
- Uzliec modinātāju uz septiņiem no rīta.
- Atgādini man rīt piezvanīt Jānim.
- Cik daudz baterijas ir palicis brillēm?
- Izslēdz skaņu telefonā.

English references:
- What's the weather today?
- Open Spotify and play music.
- Set an alarm for seven tomorrow morning.
- Remind me tomorrow to call John.
- How much battery is left in my glasses?
- Mute my phone.

## Verification

Required checks: :providers:sherpa-stt:test, :engines:speech:test,
:app:testDebugUnitTest and :app:assembleDebug. The final :app:assembleDebug run succeeded. Provider, engine and observer suites passed; the focused app speech/provider-factory/Activity run passed.
Unit tests cover catalog uniqueness/pins/languages, exact-size/checksum installation, partial
sets and failed replacement, selection policy, old Tiny compatibility, Whisper lv/auto config,
model/language replacement ordering, both buffered boundaries, visible energy fallback,
language persistence and isolated probe cleanup. Native decoding quality and JNI teardown
remain physical-device acceptance items.

### Full app suite limitations (2026-10-01)

The required unfiltered `:app:testDebugUnitTest` run executed 136 tests: 130 passed,
6 failed in unchanged code outside this batch:

- CognitionMigrationTest: three schema/migration failures (including expecting schema 20 while AppDatabase is 22).
- RoomNotebookRepositoryTest: migration from schema 1 to 22 lacks the registered path in its test fixture.
- WorkspaceMigrationTest: migration from schema 4 to 22 lacks the registered path in its test fixture.
- HostToolDescriptorDispatchTest: accessibility ownership list omits halo_ui_search, halo_ui_set_checked and halo_ui_wait.

`git show HEAD` confirms the schema-version mismatch already exists; the affected database,
migration tests, accessibility descriptors and ownership tests have no local diff. These failures
were left unchanged because this batch excludes unrelated runtime/cognition work. Consequently
the full app-suite acceptance criterion remains blocked by those existing failures.

Final passed test counts: provider: 16 tests / 0 failures, engine: 13 tests / 0 failures, observer: 11 tests / 0 failures, focused app: 20 tests / 0 failures.


### Subsequent resolution in SPEECH-QUALITY-002

All six failures above were repaired in SPEECH-QUALITY-002 using the current database version,
the shared complete migration registry, and the existing accessibility tool inventory. The final
unfiltered app suite now passes all 143 tests, and debug APK assembly succeeds. See
[SPEECH-QUALITY-002 validation](SPEECH-QUALITY-002.md). The limitations above describe the earlier
001 run; they are no longer open test failures. Physical-device recognition quality remains unvalidated.
