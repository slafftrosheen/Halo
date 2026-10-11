# ONBOARDING-LITERT-DOGFOOD-002 — 2026-10-11

## Live tester symptoms

The tester reports that the guided setup wizard opens repeatedly while using Halo and LiteRT struggles both to load and to answer. No device logs, exact model, file hash, or runtime error payload were supplied this turn. Changes below address reproducible source-level causes, not an asserted successful physical-device repair.

## Onboarding

- The previous AppShell startup gate considered every saved INCOMPLETE wizard to be first-run-required, even after a user pressed Explore Halo. It did not persist dismissal.
- SetupProgressStore now persists a separate auto-dismissal flag without fabricating LIMITED or VERIFIED readiness.
- AppShell shows the first-run wizard only on the initial un-dismissed app launch. Presenting it acknowledges the initial offer; Explore Halo likewise persists the dismissal. The wizard's step remains saved and can be resumed intentionally via Settings.
- Two pure store tests cover persistent dismissal, stage retention, and completed-outcome gating.
- **Acceptance:** fresh install opens setup once, Explore Halo allows normal navigation after rotation/process death, Settings can reopen the guide, and no fake verified status is recorded.

## LiteRT

- LocalModelSession now matches resident artifact identity using model ID, file path, physical SHA when available, and **original requested** RuntimeLoadConfig. The effective context after memory downshift is kept separately, so repeat turns reuse the valid downshift without retriggering cold loading.
- A failed native unload retains ownership and returns failure. The next model is not allocated until the former runtime explicitly confirms successful teardown.
- LiteRtLocalProvider applies the persisted developer backend/context policy to reuse checks, and status no longer calls a stale resident configuration READY.
- LiteRtModelManager coalesces in-flight inventory scan lookups; ordinary cold startup may reuse an already verified inventory descriptor when the file length and modified timestamp still match. Explicit physical verification still rehashes and checks integrity.
- The streaming provider now sends Failed on cold-load contention and truly empty generation instead of an uncaught cold-load exception or blank Completed.
- Four fake-runtime session tests cover same-config reuse, changes of path/backend/SHA, blocked replacement after unload failure, and unload/reload. One provider test checks empty streaming. These tests are **authored, not executed**.

## Safety, limitations and next physical acceptance

No GitHub Actions used. No third-party model API introduced. No automatic GPU enabling. No claim that LiteRT executes all downloaded artifact families.

The cached verified descriptor is bounded by file length and last-modified time, not an independent fresh hash. Malicious or unusual in-place modifications preserving both cannot be detected without explicit Verify model.

Run local Gradle debug unit tests and APK build, then on device:
1. Cold launch after selecting a verified installed LiteRT artifact: observe inventory completion, engine initialization, first token and steady reuse.
2. Change developer backend CPU/GPU or context settings: ensure actual load profile is updated; ensure repeat identical requests do not reload.
3. Try invalid/oversized/corrupt models and missing storage: check diagnostics and no false Ready/Completed.
4. Interrupt model load and switch models: verify native engine teardown succeeds before second allocation and no memory leak occurs.
5. Check Android logs for HALO_LITERT_LOAD_START, HALO_LITERT_LOAD_READY, HALO_LITERT_LOAD_FAILED and runtime native stage; share sanitized diagnostics for any remaining native failures.

Relevant tests: SetupProgressStoreTest, LocalModelSessionResidencyTest, LiteRtLocalProviderStreamTest, existing LiteRtModelManager inventory suite.
