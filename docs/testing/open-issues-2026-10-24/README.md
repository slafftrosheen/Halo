# Open-issue verification pack

This pack covers the **11 issues still open on 2026-10-24**: #28, #29, #30,
#33, #35, #36, #38, #39, #42, #43 and #54. It retains their complete fetched
bodies in `source-issues.json`, separates 163 original requirement rows from
38 detailed acceptance protocols, and provides a runner for 35 native fixture
cases. Every acceptance row starts **NOT_TESTED**. Earlier source/device evidence
is historical evidence to review, not a pass on the new binary.

The current human instruction authorizes finishing #30 source, compiling, and
preparing this pack. It supersedes older code-only/no-build sequencing prose.
The separate instruction for #30 is **implement source now; keep activation
gated**. No test command enables the production feature or creates a release.

## What to run

### Latest completed run

On 2026-10-24 both debug APKs compiled, installed and launched on `f01fe673`
(`23124RA7EO`, API35); installed base APKs were pulled and matched the sealed
SHA256 values. All **35 native fixtures passed** (ink12, workspace7, notes/Study4,
display9, setup/privacy3). The focused source regressions had **82 passes**.
The first phone run found a protected RSA TLS signing-authorization defect; its
failure is preserved alongside the corrected passing run. See
[the implementation report](../HALO-CROSS-DEVICE-INK-030-VERIFICATION-2026-10-24.md)
and [current fixture results](../artifacts/CROSS-DEVICE-INK-030-2026-10-24/phone2-corrected-pass/automated-results.json).

The complete human protocols and original requirement rows remain NOT_TESTED;
these automated results do not prove them. `closure-audit.json` retains all 11
issues as KEEP_OPEN pending their requirement-by-requirement evidence review.

A subsequent [desktop input lifetime correction](../HALO-DESKTOP-INK-LIFETIME-030-2026-10-24.md)
passed all 21 host tests and rebuilt both APKs. The current `build-manifest.json`
seals this newer pair, source stamp
`2383adbd3aabda84bf07a389bced41e8e770236f3bac8422b0e7a1a160cb11b7`.
The phone disconnected; these newer APKs are **NOT_INSTALLED / NOT_PHONE_TESTED**.
The 35-case phone pass above applies to the preceding pair retained in its own
artifact manifest. Install/read back the current pair before a new phone run.

### Repeat against the sealed APKs

Use PowerShell 7 from the repository root. The compiled APKs must first be sealed
against the successful build log and the exact pre-build source witness:

```powershell
pwsh -File scripts/verification/run_open_issue_tests.ps1 -Phase Seal `
  -BuildLog docs/testing/artifacts/DESKTOP-INK-LIFETIME-030-2026-10-24/gradle.log `
  -SourceWitness docs/testing/artifacts/DESKTOP-INK-LIFETIME-030-2026-10-24/source-identities.json
```

Then install/read back the exact app and test packages and run the fixture groups:

```powershell
pwsh -File scripts/verification/run_open_issue_tests.ps1 -Phase Install -Serial f01fe673
pwsh -File scripts/verification/run_open_issue_tests.ps1 -Phase Core -Serial f01fe673
```

Choose the actual current serial if it changes. The runner preserves app data,
refuses a different signed/installed binary, checks source hashes, counts actual
JUnit cases, rejects skips/crashes/timeouts, and records separate per-suite
results. It does not uninstall, download models, activate a microphone, send a
provider request, purchase anything or close an issue. Tests use isolated fixtures;
some language/display fixtures temporarily exercise preferences and restore them.
Keep personal content out of any manually captured screenshots or reports.

Optional groups can be selected with `-Suites ink,workspace,notes-study,display,setup-privacy`.
The complete default Core phase includes all five. Native fixture scope is clearly
labelled; synthetic pen input is not a physical pressure-pen result, and typed
semantic-owner calls are not real speech or permitted-provider acceptance.

## Installed models: deliberate campaigns only

Read-only inventory has its own phase, without native inference or downloads:

```powershell
pwsh -File scripts/verification/run_open_issue_tests.ps1 -Phase Inventory -Serial f01fe673
```

Use the exact freshly verified installed model ID, SHA and byte length for an
explicit `Intent`, `Embedding` or `Roles` phase. Required parameters are
`-ModelId`, `-ExpectedSha256`, `-ExpectedBytes`; role runs also accept
`-Backend CPU|GPU` and `-Language EN|RU|LV|PL`. Each run carries a new run ID
and the sealed source stamp. The embedding diagnostic currently targets exact
`embeddinggemma2-740m` CPU and tests text/image only; it does **not** qualify
audio/video consumer retrieval. Intent's prepared-final-text matrix does **not**
exercise actual STT. The role fixture does not replace human language-quality or
audible acceptance. Inspect semantic journal outcomes, not only JUnit success.

Use `voice-measurements.csv` for the controlled #33 serial/Auto comparison. Fill
actual end-of-speech and first-audible timestamps, fixed configuration/fixture/model
identities, correctness, audible confirmation and raw evidence path. Then run
`summarize_voice_benchmark.py INPUT.csv --output REPORT.json`. It requires at least
30 warm and five cold samples for each profile, reports median/nearest-rank p95,
and keeps timing unqualified if measurements or audible/correctness proof are
missing. Thermal/battery/actual backend traces still require separate review.

The prior unchanged Qwen warm-intent run failed its 2-second bound. Do not repeat
that same campaign hoping for a pass; record its existing failure, change the
actual implementation/configuration if needed, and qualify the changed inputs.
Do not rerun unchanged long campaigns or assume GPU initialization proves GPU
graph placement. The #33 baseline/Auto comparison needs actual audible timing,
identical artifacts/settings and real stage/delegate traces.

## Physical and human sessions

| Issue | Protocol | Additional input required |
|---|---|---|
| #28 | [Local ink](issue-28.md) | Actual compatible pressure pen/tablet, finger/mouse, palm, accessibility and long-session measurements |
| #29 | [Ink context](issue-29.md) | Real selected regions, spoken STT, Study and explicitly permitted provider cases |
| #30 | [Cross-device ink](issue-30.md) | Explicit qualification host configuration, real Android tablet + second host, actual platform adapters and measured latency/loss/battery |
| #33 | [Runtime performance](issue-33.md) | Controlled serial/Auto benchmarks, actual backend placement and audible timing, audio routes and thermal/memory measurements |
| #35 | [Embedding retrieval](issue-35.md) | Exact native supported artifacts, labelled multimodal sources and consumer flows |
| #36 | [Intent/action loop](issue-36.md) | All 48 fixture IDs by keyboard and actual speech, action/approval/readback and stream/history inspection |
| #38 | [Continuous voice](issue-38.md) | At least ten real turns per claimed language, audible barge-in and route/lifecycle cases |
| #39 | [Model setup/catalog](issue-39.md) | Isolated first-run profile, acquisition/recovery and real role/language/device evidence |
| #42 | [Paid Play release](issue-42.md) | Actual released paid listing, purchasing-account install/reinstall/refund/support and release-owner copy review |
| #43 | [Alpha journeys](issue-43.md) | Three central physical journeys, Kids/adult Study, device/accessibility/privacy matrix |
| #54 | [Assistant operability](issue-54.md) | Three meaningful workflows by typed and spoken request, on-device and permitted Power, with exact saved-object readback |

Use the actual `intent-classification-v1.json` fixture from the test APK source.
Use known test documents, pictures, recordings and boards; retain their source
IDs/versions and content hashes. Record actual recognized fixture IDs/outcomes
without retaining private speech or provider credentials. Do not silently replace
a failed/unsupported configuration with another one in a report.

For #30, activation remains closed in the production app. The fixture-only
`RemoteInkPhoneTest` uses separate temporary AndroidKeyStore aliases and loopback
TLS; it does not enable production pairing or claim a second device. Full physical
qualification must exercise an explicitly qualified host configuration before a
release activation decision. Windows/Linux native source and a JNI bridge are in
`host-ink/native`; their actual toolchain/hardware and each Huion/OpenTabletDriver/
Pi combination need their own compatibility record. This pack never promises
their compatibility from source or Android compilation.

For #42, sideloading this debug APK proves no Play purchase entitlement. The human
release owner must review the paid listing and use the actual purchasing account.
No base-app unlock/backend or unapproved feature pack belongs in this test plan.
Official references remain the issue's [paid-app pricing guidance](https://support.google.com/googleplay/android-developer/answer/6334373)
and [refund/order guidance](https://support.google.com/googleplay/android-developer/answer/2741495).

## Evidence and closure review

1. Keep automated logs, exact APK readbacks, source stamp, run ID, device/API/RAM,
   model SHA/runtime/backend/context, language/provider/mode and timestamps.
2. Run each issue's protocols. Capture observed outcome, actual measurements,
   durable source/result IDs and restart readback. Denial/unsupported/interrupted
   is not a quality pass. Preserve a failure before a corrective rerun.
3. Fill `requirements.json` and `protocols.json` without changing their original
   requirement/step text. A PASS needs reviewer plus evidence entries containing
   `path`, `sha256`, `scope`, `sourceStamp`, `recordedAt`. Use an explicit evidence
   root and relative paths. Redact account/order/payment/credential and personal
   prompt/audio data. Record failed/unavailable rows as FAIL/BLOCKED/NOT_TESTED.
4. Review each original requirement against the actual evidence; the protocols
   organize execution but do not shrink the original scope.
5. Run the closure audit, for example:

```powershell
python scripts/verification/review_acceptance.py --evidence-root .artifacts `
  --output .artifacts/open-issue-closure-review.json
```

The audit checks unchanged specification text and evidence-file hashes; a fixture
cannot satisfy a full physical protocol. It only identifies candidates for a
human evidence review and never edits GitHub. Close an issue only after that
requirement-by-requirement review proves its actual acceptance. Until then it
remains open, even if every source regression is green.

## Regenerating

Fetch a fresh public/authenticated GitHub issue snapshot through the available
connector or API, then run `prepare_open_issue_pack.py --snapshot PATH`. It refuses
a changed open-issue set until protocols are reviewed. Regeneration resets results
to NOT_TESTED: preserve completed evidence/result files before doing so.
