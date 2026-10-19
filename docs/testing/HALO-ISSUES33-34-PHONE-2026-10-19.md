# Issues 33/34 — connected-phone verification, 2026-10-19

## Source and authority

- Pulled `main` with `git pull --ff-only`; base `e500b2aff470b3791b6d4b1e88b1b6aeae1a2d6d`, equal to `origin/main` at the start of verification.
- Design authority: `halo-devkit-v1.4/IMPLEMENTATION.md`, Brand Book 2.3, Round 2, plus the learning/voice contracts. This is a refinement of the supplied system.
- Device: Redmi `23124RA7EO` / `sapphiren`, serial `f01fe673`; package `com.aistudio.braintool.xkqzps`.
- The owner explicitly requested installation, testing and fixes. This authorizes local verification after the older coding-only issue batches. No hosted CI was invoked.
- Pre-existing untracked runtime observer sources, runtime specification and `docs/superpowers/` were preserved. The APK is a dirty-workspace artifact; it is not represented as a clean release from the base commit.

## Reproduced defects and fixes

1. Shared `HaloIcon` had no intrinsic/default size. Icons in unconstrained voice/read controls collapsed to zero pixels, despite their touch targets remaining present. Default to the supplied 24dp icon grid while preserving explicit dimensions, including the 32dp Kids microphone. A Compose regression test first failed against the original code.
2. Study reader published sentence ownership after invoking narration. A narrator reporting playback start synchronously could have its valid callback rejected, or have callback state overwritten by `preparing`. Publish the new generation before narration and preserve callback results. The recovery regression now emits an immediate real-contract `Started` acknowledgement; it failed before the fix. It also checks pause/resume, stale completion/ranges and sentence advancement.
3. RU/LV Kids draft instructions referred to a Save button while the actual action is Keep (`Оставить` / `Paturēt`). Correct the instructions and regenerate the catalogue/resources.
4. Canvas Link had no RU/LV catalogue entry. Add `Связать` / `Saistīt`.
5. Correct four layout-test host failures by using the existing hostless theme; retain production speech initialization. Update two stale expectations: lowercase halo and persisted journey phase rather than inferring practice from any explanation.

## Baseline evidence

- Latest base APK built successfully and installed with `adb install -r`; launch succeeded without clearing app data.
- Initial targeted app batch: 158 tests, 151 passed, 7 failed. Four failures were uninitialized layout-test speech hosts; two were stale assertions; the reader assertion led to the immediate-callback regression above.
- Physical RU Kids Home: Explore/Create labels fit, existing dock glyphs render, and existing saved Math session remains available.
- Kids Explore: Math starter → prior-knowledge discovery → Not yet → next-step preparation. Stop/back returned control. No completed tutor answer or assessed understanding is claimed.
- Kids Create manual path: entered `Halo test story. A fox found a star.`, Keep → saved project → Canvas; actual body text appears on the board alongside its source reference. This creates a clearly identifiable test project/note and board; existing user content was retained.
- Saved-story reader opened actual saved text and local reading emitted `HALO_VOICE_LATENCY` on actual audio start. This is playback-event evidence, not an audible-quality verdict or microphone-to-audio measurement.
- Grown-up space correctly opened Android's device-credential prompt. The owner authenticated on the phone, and the protected options sheet opened. Cloud remained off. Full halo exited Kids and restored the original disabled preference; no credential bypass was used.

## Final verification

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: PASS. Final Gradle invocation succeeded in 7m 33s.
- App targeted suite: 159 tests, zero failures. Includes the new icon sizing test, immediate narration callback recovery, voice trace/latency, Kids, Study and brand tests.
- Runtime SDK: 60 tests; execution engine: 12; speech engine: 22; cognition: 316. All passed in this session. The final invocation correctly reused those unchanged passing results. Total: 569 tests, zero failures.
- EN/RU/LV generation and `--check`: 2939 strings, PASS. `git diff --check`: PASS. The requested mechanical detector returned no findings for the changed native UI files; this is not visual acceptance.
- Both final app and instrumentation APKs installed successfully with `adb install -r`; app launch succeeded. No app-data clear or uninstall.
- App APK SHA-256: `70905a53e8c928443f061ade457b7eb76822e9aa0a0ddbabd8239de5aac4a697`.
- Instrumentation APK SHA-256: `f68c04024cd40585950bf0f8d86d0741c8c56ee887a0c808e374e50b644fab6f`.
- Physical post-fix Home screenshot confirms the dictation microphone now renders. Shared button dimensions remain intact.
- Post-fix Settings read-aloud glyphs also render. Advanced → System check → Technical tools opens the localized voice-latency panel, which correctly reports no measured speech turns after process restart. It does not fabricate percentiles from the standalone model probes.
- Adult Notebook → Study → saved Math session resumes; Listen to this lesson exposes the actual saved response. Its Russian response is incoherent and is not accepted as correct tutoring. This is a provider/model-quality limitation, not a successful learning outcome.
- Adult Read chapter aloud correctly asks for passage-specific consent because ElevenLabs is selected. Chose Do not send text. No adult passage was approved for cloud transmission during this test. Kids reading earlier used its enforced local route.
- Installed-inventory instrumentation: PASS (1 test). All 17 artifacts were fingerprinted; exact installed APK hashes match the built APKs. Unverified imported tiny/random artifact remains unverified.
- Physical Qwen 3 0.6B Dynamic INT4 CPU: PASS, three fresh-engine strict control probes at 1024 context plus Halo provider verification. Five-turn resident-engine correctness/isolation probe: PASS, five of five.
- Physical explicit GPU: PASS, one strict control probe at 1024 context plus Halo provider verification. Requested/effective and provider load/generation backend reported GPU with no fallback. This is not proof of every operation's delegate placement.
- All probes used the already installed 344,671,744-byte artifact, SHA-256 `03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856`, LiteRT runtime 0.18.0, the same installed APK pair and device. No new model was downloaded; no role/backend selection was changed.

| Controlled raw probe measurement | CPU, three fresh-engine cycles | GPU, one fresh-engine cycle |
| --- | --- | --- |
| Engine initialization | 2208–2288 ms | 8889 ms |
| First token after conversation start | 5512–5984 ms | 3887 ms |
| Total control probe | 8345–8847 ms | 13235 ms |

These are small controlled diagnostic samples, not a serial/concurrent voice benchmark, throughput qualification or thermal/energy study. GPU loaded more slowly cold, but its post-initialization first token was faster here. Sampled peak PSS was 1,147,440 KiB for CPU and 1,387,652 KiB for GPU; GPU device-memory counters were unavailable. This does not justify overriding adaptive/manual backend policy.

## Acceptance limits

This pass does not establish all-theme/all-language pixel-perfect parity, Polish app-language support, camera/OCR worksheet correctness, complete provider-backed journeys A–D, microphone recognition accuracy, audible speech quality, Bluetooth/wearable behavior, or voice p50/p95 improvement. Some existing technical diagnostics and Notebook labels still fall back to English in the Russian interface; this is not presented as complete localization. Issue 33 needs a matched baseline and at least 30 genuine buffered endpoint-to-audio samples before percentile claims. Standalone reading/typed turns do not supply those endpoints. Issue 34 remains closed as previously directed; closure does not replace these acceptance checks.

Evidence folder: `artifacts/HALO-ISSUES33-34-2026-10-19/`.

## Delivery state

Final fetch confirmed `origin/main...HEAD` = `0 0` at base `e500b2af`. Fixes and evidence remain local, uncommitted and unpushed; the installed APK includes them. The existing untracked files were not adopted into a commit. The clearly labelled test story and its board remain available for inspection. Kids mode was restored to its original disabled state; no cloud consent, model download or role/backend preference was changed.
