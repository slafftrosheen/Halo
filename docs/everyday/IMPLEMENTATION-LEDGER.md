# Everyday implementation ledger — issue 63

Status: source implementation and final host checks are complete; APK installation is the current handoff step. No issue-63 acceptance is complete merely because it appears in this plan. The complete source requirements are [ISSUE-63-SOURCE.md](ISSUE-63-SOURCE.md); the UI integration decisions are [ISSUE-63-UX-INTEGRATION-AUDIT.md](ISSUE-63-UX-INTEGRATION-AUDIT.md).

## Audited ownership

| Concern | Existing owner | Extension |
|---|---|---|
| Global mode and in-flight cancellation | `ai/PrivacyModeStore`, SDK `RemoteDispatchPolicy`, `ManualDependencyGraph` | Keep the two persisted enum IDs for compatibility; display Local/Power. Add governed public collectors/model downloads, close nullable-policy bypasses, inventory every transport. Preserve parental capability limits without creating a third privacy policy. |
| Local persistence | `data/AppDatabase` v34; Workspace/Notes/relation owners | Organizer records, recurrence exceptions, delivery receipts, scheduling outbox and collector state implemented in the same Room database. Notes stay in their existing tables. Migration through v34 passed the bounded host suite. Portable archive version 5 integrates these domains; see EVERYDAY-INTEGRATION.md. |
| Time and delivery | `host-android/reminder/ReminderRecordStore`, `ExactAlarmAccess`, Android alarm actions | Retain legacy receipt/import support, implement owned recurring schedules, occurrence identity, snooze/stop, reconciliation after crash/reboot/time change, notification/exact/full-screen permission receipts. |
| Tool execution | `ToolExecutor`, descriptor catalogue, Runtime/ReasoningEngine/CommandExecutor | Typed organizer/source/brief/widget commands with version checks and structured outcome truth. No second agent path. |
| Screen and UI | `HaloScreen`, `LocalScreenStore`, `ShellPageOwner`, UI 5 contracts | Organizer routes are Screen subroutes, model-independent. Stable card instance IDs throughout layout/config/actions. Reuse Apply/Cancel/CAS/Undo, semantic assets and measured Zone. |
| Your Day | `daily/DailyAssistantProjector` and controller | Share structured deterministic briefs and local attention signals. Persist morning baseline; compare stable source identities and revisions. |
| Library | Workspace records, relations and canonical detail router | Typed organizer/brief projections and revalidated links; never route them as generic files. |
| Native widgets | `HaloNativeWidget` | Per-appWidgetId settings and click identity, actual data variants, configuration, redaction, bounded updates and previews. |
| Setup | `SetupProgressStore` | Resumable optional source/city/card selection, no network in Local and no required model/account. |

## Coherent coding batches and verification gates

1. **Organizer foundation:** pure time/recurrence and record contracts; transactional Room persistence; versioned edits, occurrence exceptions, subtasks, completion, conflicts, search, delete/undo; scheduling outbox. Verify DST/recurrence/series edits/instance identity, process restoration and migration. No UI completion claim yet.
2. **Organizer delivery and tooling:** AlarmManager/notification/WorkManager adapters, native permission recovery, timer monotonic clock and reboot behavior, legacy reminder migration, canonical commands/traces, Activity/Timeline/Library links, bounded ICS import/export. Verify persisted-before-scheduling, stale callback fencing, cancellation/snooze/reboot and dispatch semantics.
3. **Global policy and collectors:** exhaustive transport audit, revocable network leases, registry, safe HTTP/DNS/redirect/size checks, RSS/Atom/ICS/weather schemas, conditional cache, backoff/quotas, worker cancellation and observability; validate starter endpoints on host and record date/result/attribution. Verify zero Local requests, transition cancellation, parser safety and cache freshness.
4. **Briefing and configuration:** deterministic morning/on-demand/evening, retained baselines, priority and provenance, quiet hours and optional read aloud, age-appropriate source filtering, onboarding selections, source management and reader. Verify offline operation and missing-versus-unchanged baseline, source opt-out and muted behavior.
5. **UI 5 and Screen:** complete agenda/tasks/reminders/alarms/timers/world clock/weather/news/brief views and forms; all state/recovery paths, per-instance cards, canonical routes, Library integration, semantic assets and EN/RU/LV/PL. Verify owner leases, duplicate instances, restore/resize/IME/large text/child authority and localized renders.
6. **Launcher widgets:** eight variants plus configurable dashboard, configuration/reconfiguration, isolated instances, click routing, privacy, locale/theme/resize/deletion/process/reboot/data updates and assets. Verify RemoteViews binding, stale/missing-record recovery and bounded refresh scheduling.
7. **Final integration:** all tools discoverable and model-independent manual paths; Local/Power matrix across Home/Screen/Workspace/Kids/Study/voice/widgets/workers, migrations, accessibility and docs. Run meaningful broad host/build checks, independent UX/UI review and local commits. Only after all code is implemented ask for new phone tests. Real participant work remains deferred.

## Required completion evidence

| Scope | Status | Required evidence |
|---|---|---|
| A organizer (calendar, tasks/subtasks, reminders, alarms, timers, world time, links, undo, ICS) | Implemented | Canonical Room owners, UI5 instruments, delivery/recurrence/ICS, Library and Activity links; current host evidence below. OS delivery/permission acceptance remains phone-dependent. |
| B typed collectors, sources, safety, caching, quotas, cancellation, workers | Implemented | Passive local readers, real bounded public adapters, source catalogue/reader/onboarding/tools and guarded workers. Dated public endpoint validation remains a reachability snapshot, not uptime. |
| C deterministic briefs, changes, quiet hours, spoken/child presentation | Implemented | Structured engine, baseline comparison, source/family revalidation, optional speech and quiet-hour owner. Audible completion and actual scheduled notification delivery remain phone-dependent. |
| D UI5 screens/states, languages and assets | Implemented; matching rendering/review checks complete | First-party instruments, retained editable drafts, loading/error/stale/permission recovery and EN/RU/LV/PL source catalogue generation. |
| E persistent configurable Screen instances | Implemented | Stable independent IDs, arrangement/size/visibility, per-card configuration, revisioned Apply/Cancel/Undo and portable state. |
| F launcher variants, configuration, privacy and updates | Implemented; matching render/live-clock checks complete | Eight providers, native/themed responsive layouts, independent atomic settings, live TextClock, privacy filtering and bounded update owner. Real launcher behavior remains phone-dependent. |
| G typed canonical AI/voice operations | Implemented | Twenty-two strict Everyday commands plus Screen query/configure; central capabilities/descriptors/disclosure and canonical executor checks. Provider wording/recognition remains real-session acceptance. |
| H policy, docs, compatibility, tests and delivery | Code, documentation and host verification complete; APK handoff in progress | Explicit LOCAL-POWER-MATRIX.md, transport audit, archive/retention/recovery/component map and frozen host evidence. Build/install and review must finish before handoff. |

An intermediate commit is a reviewable implementation batch, not a declaration that unfinished workstreams are complete. Hardware-only criteria and participant criteria remain separately identified; neither may be inferred from a host test or screenshot.

## Foundation evidence

`docs/testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-collector-foundation-results.json` records 50 tests in five suites, zero failures/errors/skips, BUILD SUCCESSFUL in 13m53s. The adjacent source manifest and archived JUnit ZIP bind the evidence to its production snapshot. Subsequent network-authority changes require their own run. Independent organizer review source findings OF01–08 and IC01–06 were addressed; this does not certify complete organizer UX or issue 63.

`network-policy-final-results.json` records the subsequent focused batch: 165 tests in 19 suites, zero failures/errors/skips, BUILD SUCCESSFUL in 16m54s. All 64 tracked source inputs matched the final manifest; JUnit and matching source files are archived alongside it. The bounded policy review's NP01–04 findings are source-addressed. Remaining organizer delivery, collectors, briefing, UI, Screen instances, launcher widgets and full integration are not complete.

`organizer-delivery-cas-results.json` records 30 tests in two suites, zero failures/errors/skips, BUILD SUCCESSFUL in 8m2s. All 31 source inputs matched the manifest and source archive. The preceding recovery run compiled the app and passed the 17 initial delivery cases but exposed an older-progress persistence regression (87 combined tests, one failure); explicit receipt comparison fixed it, and the focused final run adds a clock-rollback/stale-comparison case. Earlier failures remain archived. This verifies the bounded delivery implementation on host, not device behavior or complete organizer UX.

`collector-final-results.json` records 28 tests in three suites, zero failures/errors/skips, BUILD SUCCESSFUL in 6m57s. All 29 source inputs matched the manifest, including schema 33. All 20 annotated integration methods ran, plus five parser and three migration cases. Synthetic HTTP fixtures cover validators/304/size/backoff; existing SDK tests separately cover actual transport cancellation/DNS safeguards. Earlier compile and test-initialization failures remain archived. Collector source review CI01–04 is source-addressed; complete source screens, briefing/cards/widget/tool consumers, full policy matrix and native acceptance remain unfinished.

`briefing-core-results.json` records BUILD FAILED in 7m11s: production Kotlin and Room generation completed, then a recursive inferred fixture type prevented test compilation. JUnit NOT_RUN. All72 frozen source files matched; generated schema34 is separately hashed. The subsequent `briefing-recovery` run ran 51 tests with one failure: Android asset merging expanded the `.gz` filename, breaking the real asset lookup. All 78 hashes matched. The final `briefing-final` run passed all 51 tests in eight suites, with all 78 hashes matched, after correcting the packaged filename and cross-audience baseline handling. Earlier failed evidence is retained; full pages, tools, Screen/launcher widgets and hardware remain pending.

## Integrated source and first final run — 2 November 2026

EVERYDAY-INTEGRATION.md documents actual owners, UI5/native component mapping, disclosure, retention, restore and partial-failure behavior. LOCAL-POWER-MATRIX.md maps each requested surface to the real canonical boundary and its evidence scope. Setup, Library, typed commands and portable restore are no longer pending source placeholders.

`integration-source.json`/ZIP freeze 3,741 inputs. The first integrated run compiled production and test sources and ran 299 tests: 281 passed, 18 failed, zero errors/skips; all 3,741 hashes matched. Native geometry and render checks produced all eight variants in EN light, RU 200% dark, LV high density and minimum RU 200% sizes. The failures were retained rather than reported as final success: narrow date input did not settle; clock broadcast fixtures started unrelated manifest workers; Screen query assertions used escaped JSON and the old seven-tool count; the older online admission fixture omitted the real mode-profile owner.

The corrective source freeze `integration-final-source.json` covers 3,743 inputs, including the final paired-device transport boundary. Its result is pending. Corrections include full-width large-text date entry, actual TextClock receiver testing, typed Screen JSON assertions, real profile-backed online fixtures, restored brief retention, and zero-DNS/public-IP rejection for local BuddyBot transport. Independent source review EUI01–05 and earlier W/WT findings are source-addressed. No human/device score is inferred.


`integration-final` was a compile-only failure (explicit OkHttp DNS interface), with JUnit NOT_RUN and all 3,743 hashes matching. The corrected `integration-verified` run passed 302/302 tests with zero failures/errors/skips in 8m58s, all 3,743 hashes matching. This includes real mode-backed online fixtures and the paired-device URL boundary.

The independent visual pass then found two compact-widget/localization issues and a same-profile disclosure race. `handoff` addressed those and passed 305/305 tests in 9m17s, zero failures/errors/skips, all 3,744 hashes matching. Its 32 native frames and six form captures include aligned synthetic clock time, distinguishable minimum-size identities, the real dark Surface and large-text date entry. Held organizer/source/brief reads were revoked without changing Kids experience. `release` adds the coherent canonical stamp and a real Room WAL transaction-start regression; verification is running before APK handoff.


`release` compiled the coherent disclosure correction and ran 306 cases: 296 passed and ten failed in the new file-backed tool fixture because Robolectric's method-name temp directory plus the UUID database filename exceeded Windows MAX_PATH. All 3,745 source hashes matched. The failure was isolated to the test path; source and JUnit evidence are retained. `release-final` keeps production identical and uses a short database filename while retaining actual WAL and the new transaction-start regression.


## Final source acceptance and installation handoff

`release-final-results.json` records **306 tests in 45 suites, zero failures/errors/skips, BUILD SUCCESSFUL in 5m51s**. All **3,745** frozen source hashes match. JUnit, source and 38 rendered fixtures are archived beside the result. This includes the metadata-only coherent disclosure stamp, three held same-profile grant regressions and the file-backed Room WAL writer-start regression; source review found no residual in that ordering. The short test filename fixes the evidenced Windows path limit without changing production or disabling WAL.

The independent final widget review closes WV01/WV02 and finds no remaining concrete widget defect. Original-rubric ratings are expert usability/implementation **8.0/10** and visual clarity **9.2/10**, with physical/assistive/comprehension evidence explicitly separate. The score is not inflated to 10/10. Issue closure, participant acceptance and actual Android delivery/launcher behavior are not claimed. See ISSUE-63-CODE-HANDOFF.md for the source checklist.
