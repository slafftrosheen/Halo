# Everyday implementation ledger — issue 63

Status: implementation in progress. No issue-63 feature or acceptance is complete merely because it appears in this plan. The complete source requirements are [ISSUE-63-SOURCE.md](ISSUE-63-SOURCE.md); the UI integration decisions are [ISSUE-63-UX-INTEGRATION-AUDIT.md](ISSUE-63-UX-INTEGRATION-AUDIT.md).

## Audited ownership

| Concern | Existing owner | Extension |
|---|---|---|
| Global mode and in-flight cancellation | `ai/PrivacyModeStore`, SDK `RemoteDispatchPolicy`, `ManualDependencyGraph` | Keep the two persisted enum IDs for compatibility; display Local/Power. Add governed public collectors/model downloads, close nullable-policy bypasses, inventory every transport. Preserve parental capability limits without creating a third privacy policy. |
| Local persistence | `data/AppDatabase` v33; Workspace/Notes/relation owners | Organizer records, recurrence exceptions, delivery receipts, scheduling outbox and collector state implemented in the same Room database. Notes stay in their existing tables. Migration host checks passed; backup integration remains pending. |
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
| A organizer (calendar, tasks/subtasks, reminders, alarms, timers, world time, links, undo, ICS) | Foundation and Android delivery implemented with bounded host verification; full UI/integration pending | Foundation 50-case run and delivery/persistence 30-case correction pass. Alarm lanes, notification precision, snooze/stop, locked boot handoff and legacy migration have host coverage. Real OS acceptance, backup, tools, links and full organizer UI remain pending. |
| B typed collectors, sources, safety, caching, quotas, cancellation, workers | Registry, real HTTP/local adapters, persistent cache/endpoint budgets, preview and bounded worker implemented; source UI/onboarding integration pending | Collector integration 28-case host pass covers 20 integration, 5 parser and 3 migration cases; earlier shared-policy 165-case run and dated starter-feed validation remain separate. Full consumer/global-mode/device acceptance pending. |
| C deterministic briefs, changes, quiet hours, spoken/child presentation | Pending | Offline facts/provenance/baseline/action tests and integrated UI |
| D complete UI 5 screens and states, languages, assets | Pending | Matching source/build/renders and independent review |
| E persistent configurable Screen instances | Pending | Instance/CAS/migration/route/ambient tests and renders |
| F launcher variants, configuration, privacy and updates | Pending | Host binding/instance tests/build/assets; launcher and OS acceptance later |
| G typed canonical AI/voice operations | Pending | Registry/descriptor/capability/result and execution tests |
| H policy matrix, documentation, compatibility, commits | In progress: audit and plan recorded | Full transport inventory, threat model/retention/Android constraints, checks and final checklist |

An intermediate commit is a reviewable implementation batch, not a declaration that unfinished workstreams are complete. Hardware-only criteria and participant criteria remain separately identified; neither may be inferred from a host test or screenshot.

## Foundation evidence

`docs/testing/artifacts/HALO-EVERYDAY-63-2026-11-01/organizer-collector-foundation-results.json` records 50 tests in five suites, zero failures/errors/skips, BUILD SUCCESSFUL in 13m53s. The adjacent source manifest and archived JUnit ZIP bind the evidence to its production snapshot. Subsequent network-authority changes require their own run. Independent organizer review source findings OF01–08 and IC01–06 were addressed; this does not certify complete organizer UX or issue 63.

`network-policy-final-results.json` records the subsequent focused batch: 165 tests in 19 suites, zero failures/errors/skips, BUILD SUCCESSFUL in 16m54s. All 64 tracked source inputs matched the final manifest; JUnit and matching source files are archived alongside it. The bounded policy review's NP01–04 findings are source-addressed. Remaining organizer delivery, collectors, briefing, UI, Screen instances, launcher widgets and full integration are not complete.

`organizer-delivery-cas-results.json` records 30 tests in two suites, zero failures/errors/skips, BUILD SUCCESSFUL in 8m2s. All 31 source inputs matched the manifest and source archive. The preceding recovery run compiled the app and passed the 17 initial delivery cases but exposed an older-progress persistence regression (87 combined tests, one failure); explicit receipt comparison fixed it, and the focused final run adds a clock-rollback/stale-comparison case. Earlier failures remain archived. This verifies the bounded delivery implementation on host, not device behavior or complete organizer UX.

`collector-final-results.json` records 28 tests in three suites, zero failures/errors/skips, BUILD SUCCESSFUL in 6m57s. All 29 source inputs matched the manifest, including schema 33. All 20 annotated integration methods ran, plus five parser and three migration cases. Synthetic HTTP fixtures cover validators/304/size/backoff; existing SDK tests separately cover actual transport cancellation/DNS safeguards. Earlier compile and test-initialization failures remain archived. Collector source review CI01–04 is source-addressed; complete source screens, briefing/cards/widget/tool consumers, full policy matrix and native acceptance remain unfinished.
