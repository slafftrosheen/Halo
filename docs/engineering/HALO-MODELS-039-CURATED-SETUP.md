# HALO-MODELS-039 — consumer catalogue and local setup boundaries

Date: 2026-10-21. Base: `14b54b1d301ef4cf49b20c371c37e6ecf2d0cb9d`.
Issue: #39. **SOURCE IMPLEMENTED; NOT COMPILED OR TEST-RUN; DEVICE ACCEPTANCE PENDING.**
This batch does not apply the unpublished #40 search/media/cost package.

## Intent and scope

Keep normal setup small and understandable without hiding a user's existing files,
selected models or interrupted downloads. Reuse the current model inventory,
downloader, role qualification owner and Compose surfaces. Do not invent a
"Halo Tested" label from a download checksum, catalog maturity or a smoke answer.
The patch implements the consumer-discovery slice of #39, not the complete issue.

## Source defects addressed

- `OnDeviceModelsScreen` hid some advanced controls but still exposed family
  discovery buttons that invoked Hugging Face synchronization on the normal page.
- `LocalAnswerSetup` offered the first three open-download generators after Qwen,
  making catalogue order an implicit recommendation; it lacked explicit paused
  download recovery and checked installed status without comparing current pins.
- `LocalModelDeviceFit` skipped the storage check for zero space and threw for
  invalid/large catalogue sizes, allowing misleading guidance or a UI exception.

## Changes and ownership

`CuratedLocalCatalog` is a read-only presentation policy. Its two candidate
entries reference the existing canonical Qwen3 0.6B and EmbeddingGemma 2 definitions;
no duplicate URLs, invented SHA values, licenses or model artifacts are added.
Complete catalogue data equality includes source revision, bytes, checksum, task,
access, declared capabilities and runtime profile. Missing or conflicting rows
are not silently admitted. This is candidate identity, NOT native compatibility
or device/language qualification. No model is promoted to Tested/Recommended.

The normal model screen offers those candidates plus existing installed,
selected, resident and non-empty download-state entries. Imported/other models
remain manageable with explicit uncurated warnings. Conflicting duplicate IDs
are collapsed to one management row without a curated badge. No existing files,
preferences, selected targets, role assignments or downloads are deleted.

Full catalogue discovery, arbitrary import, licensed acquisition and technical
controls now require both the technical route and enabled Developer tools.
Consumer filters are limited to all/text/search; hidden developer purpose filters
do not leak into the normal page. Existing permission, Kids and runtime gates
remain unchanged; this presentation boundary is not a security authorization.

Local onboarding uses the same exact curated projection. It waits for inventory
readiness, requires current verified SHA and byte count before enabling selection,
and exposes cancellation, resume, discard-partial and retry through the existing
model manager. Local search still requires its separate source/privacy setup.
Speech/STT/VAD/TTS installers are unchanged and remain their existing explicit
steps; no artificial LiteRT entries are created for speech packs.

`CuratedLocalModelGuidance` presents purpose, qualification caveats, LFM2.5
English-first guidance for known family/source metadata, and resource estimates.
Memory/disk reads move off composition to an IO snapshot refreshed at transfer
stage/inventory/pressure changes or explicit Refresh, not every progress byte.
Unknown measurements remain unknown. Zero space means insufficient space;
invalid sizes cannot overflow or crash the guidance path. These are estimates,
not reservations or a guarantee; acquisition keeps its authoritative preflight.

New copy is in `halo_curated_models.xml` with sixteen matching EN/RU/LV keys.
Existing brand styles and navigation are retained. No dependency, permission,
network endpoint, database schema, provider behavior or CI workflow is added.

## Verification record

Authored **24 new test cases as source only**: 16 catalogue/identity/discovery
cases and 8 capacity-boundary cases. The existing device-fit test is retained.
Coverage includes exact pins, changed metadata, duplicate/conflicting IDs,
retained models, no false VERIFIED promotion, discovery opt-in, installed-byte
identity, English-first warnings, zero/unknown storage and inconsistent memory.

Performed: exact base-file Git blob hash checks; self-review of changed call
sites and fallback/recovery paths; XML parsing, key parity and resource-reference
checks; patch whitespace and file-scope checks. Uploaded content hashes and
remote commit/ref are to be verified at publication. No independent reviewer
agent was available.

**Not performed:** Kotlin/Android compilation, Gradle tasks, unit/instrumentation
execution, CI/Actions, native model loading, downloads, emulator or physical
phone validation. Source checks are not a passing test or runtime result.

## Remaining #39 scope — keep the issue open

- **Updated since the base batch:** a resumable starter bundle for intent/STT/VAD/TTS
  now has a durable journal, aggregate storage admission, owner-controlled transfers,
  resume, and rollback. This is not a complete local conversation LLM pack and does
  not auto-select or qualify any model. See the source follow-up below.
- A complete, audited per-artifact provenance/license/backend manifest and
  device/runtime/role/language qualification matrix, including invalidation on
  upgrades. Existing evidence owners remain; no new evidence is fabricated.
- The current pinned starter-bundle source inventory and explicit evidence gaps
  are recorded in [the qualification matrix](../testing/HALO-MODEL-QUALIFICATION-MATRIX-2026-10-22.md).
  It is not a qualification pass; upstream license review and reproducible runs
  remain outstanding.
- A measured multilingual conversation recommendation. Qwen is still an intent
  candidate and LFM2.5 is not promoted for non-English conversation/Study.
- Device acceptance: fresh install without technical discovery; technical mode
  opt-in; existing custom/gated models; pause/restart/resume; failed inventory;
  low/unknown space; stale SHA/size; 200% text and small screens; model changes
  and source/privacy setup. Compile and execute the new cases only when authorized.

Polish voice support does not imply Polish app UI; this patch adds EN/RU/LV only.
Subsequent Polish UI source slices are recorded in their own commits and issue
comments; the catalog remains partial.

## Source follow-up — Silero removal owner and VAD rollback

Added a consumer-visible remove action for optional Silero VAD. Removal now
releases and suspends the lazily owned native detector before deleting only the
VAD model, checksum proof, and interrupted partial file. If removal fails, the
runtime is allowed to load a still-valid model again; a verified reinstall also
re-enables loading. Detector access and teardown are synchronized so removal
cannot close a native detector while a frame is being processed. The speech
inventory regression cases cover VAD cleanup, preserving unrelated files,
runtime release, refreshed installation state, and rejection of unknown IDs.

This is a rollback prerequisite, not the transactional LLM/intent/STT/VAD/TTS
pack installer. No build or test was run per #43; source and localization checks
are the only evidence for this follow-up.

## Source follow-up — resumable starter bundle

Added a process-lifetime coordinator and durable, content-free transaction
journal for the pinned Qwen3 0.6B intent candidate, Whisper Small, Silero VAD,
and Supertonic local speech output. The journal records the exact stage and
which supported artifacts were already present before the transaction. On
process recovery, an interrupted install becomes resumable; owner inventories
are reconciled before the next stage so a completed transfer is not needlessly
repeated. Qwen remains a candidate and Supertonic is installed without voice
selection or audible qualification.

The setup step performs aggregate free-space admission before acquisition,
including the existing LiteRT/Sherpa and Supertonic safety reserves. Each
download remains owned by its existing manager. Pause preserves each owner's
resume data. Rollback cancels transfers and removes only newly acquired model
assets; active or newly selected assets produce a retryable rollback failure.
Pre-existing interrupted artifacts are left untouched and must be resolved
through their current owner before starting the bundle.

Source regressions were added for journal round-trip, malformed journal
rejection, and resume/rollback state. They were NOT executed per #43. The bundle
does not auto-select a model or prove a functional quality-qualified profile.
The complete provenance/backend/device/language qualification matrix,
qualification invalidation, executable owner verification and physical
acceptance remain open. In particular, acquisition success is not evidence
that this bundle is recommended for any device or language.

The source follow-up is present on `main` in commits `72edc447425d5b4ead16ada00261ef9690f1ab73`
(Silero removal owner), `7c2941d686e92b9050af5bbdc575cfe9ee781824` (resumable starter
bundle), `a707f7f9879699d200ab07cbaad8b27e1a8cb988` (storage preflight), and
`ea05440b145d00d06cc74f70ce04f8ac25235ba7` (aggregate requirements on shared filesystems).
These are source revisions only; the new regression cases remain unexecuted and
owner/device acceptance remains outstanding.
