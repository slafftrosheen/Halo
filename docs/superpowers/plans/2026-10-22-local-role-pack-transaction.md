# Local Starter Pack Transaction Implementation Plan

> **For agentic workers:** Use the native execution approach in this task; coding agents must not run builds, tests, CI, or Actions per #43.

**Goal:** Add a one-action, resumable acquisition flow for the pinned Qwen intent candidate, Whisper Small, Silero VAD, and official Supertonic pack, with aggregate capacity preflight, exact baseline protection, rollback, and process-death recovery.

**Architecture:** A process-lifetime coordinator journals the transaction using the existing `Storage` owner and delegates each artifact to its current installer. It verifies the LiteRT artifact and speech inventory before advancing, treats a newly installed TTS fingerprint as acquired without selecting or audibly qualifying it, and deletes only assets absent from the transaction baseline. The guided setup surface observes coordinator state and exposes start/resume, pause, and rollback.

**Tech Stack:** Kotlin, coroutines/StateFlow, existing LiteRT/WorkManager, Sherpa speech inventory, Supertonic pack store, Compose, EN/RU/LV `ui.tsv` generation.

**Spec:** GitHub issue #39, with #43's no-build/no-test/no-CI rule.

## Global Constraints

- Do not promote Qwen, Whisper, VAD, or Supertonic to Tested/Recommended without owner/device evidence.
- Preserve exact installed baselines and never auto-select a voice or candidate model.
- Respect pinned checksums and existing owner storage gates; reject unknown or import-only LiteRT entries.
- Coding agents do not run builds, tests, CI, or Actions; physical qualification belongs to the owner.

## Review Focus

- Process death after an installer commits bytes but before the journal advances: resume must reconcile owner inventories before downloading again.
- Rollback with pre-existing artifacts: no baseline artifact or user selection may be removed.
- Download cancellation and low storage: partial transfers remain resumable and aggregate preflight occurs before new acquisition.
- Runtime-held or newly selected assets: rollback must report rather than force-delete active models.
- Candidate qualification: acquisition success must not imply selection, role qualification, or recommendation.

## Files

- Create `app/src/main/java/com/halo/app/ai/LocalRolePackInstaller.kt` for the journal, aggregate preflight, staged owner calls, recovery, pause, and rollback.
- Expose the existing speech/TTS storage roots through their owners only as needed for aggregate free-space checks.
- Wire one process-lifetime coordinator through `ManualDependencyGraph` and expose start/resume/pause/rollback in guided setup.
- Add EN/RU/LV strings through `app/src/main/localization/ui.tsv`; regenerate catalog files.
- Add source regression cases for journal/recovery/preflight/rollback invariants, but do not execute them under #43.
- Extend `docs/engineering/HALO-MODELS-039-CURATED-SETUP.md` with exact source progress and remaining owner qualification.
