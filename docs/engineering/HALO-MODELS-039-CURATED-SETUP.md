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

- A transactional, resumable one-click multi-role pack installer spanning
  LLM/intent/STT/VAD/TTS, with aggregate storage budget, rollback and process-death
  recovery. This batch only reuses individual-model download controls.
- A complete, audited per-artifact provenance/license/backend manifest and
  device/runtime/role/language qualification matrix, including invalidation on
  upgrades. Existing evidence owners remain; no new evidence is fabricated.
- A measured multilingual conversation recommendation. Qwen is still an intent
  candidate and LFM2.5 is not promoted for non-English conversation/Study.
- Device acceptance: fresh install without technical discovery; technical mode
  opt-in; existing custom/gated models; pause/restart/resume; failed inventory;
  low/unknown space; stale SHA/size; 200% text and small screens; model changes
  and source/privacy setup. Compile and execute the new cases only when authorized.

Polish voice support does not imply Polish app UI; this patch adds EN/RU/LV only.
