# Issue 64 Organizer implementation plan

> For agentic workers: use superpowers:executing-plans inline. The user's whole-batch/no-build instruction overrides per-step execution.

Goal: implement the complete issue-64 responsibility, delegation, planning and research scope using existing canonical owners.
Architecture: extend OrganizerRecord/Codec/UseCases; bind real routines and results; extend collectors/briefs and registered tools; expose all through current Organizer/Orb.
Tech stack: Kotlin, Compose, Room JSON records, WorkManager, Runtime ToolRegistry/automation, governed OkHttp.
Spec: docs/superpowers/specs/2026-11-03-issue-64-organizer-design.md and docs/everyday/ISSUE-64-SOURCE.md.
Base: b381c6834. Branch codex/issue-64-organizer. Preserve gradlew executable change.

## Global constraints

- Full production/regression source batch first; no Gradle/tests/build while power is being repaired.
- Reuse Runtime, ToolRegistry/CognitiveSession, OrganizerRepository, RoutineExecutor, CollectorRepository and existing notifications.
- Exactly Local/Power; current Kids, scoped disclosure, permissions, exact version and uncertain-effect no-replay boundaries remain enforced.
- Actual scheduling/execution/result receipts determine UI state. No silent OEM ownership or paid service dependency.
- Preserve legacy JSON, routes, calendar semantics, archive/restore and source selection behavior.

## Review focus

- Ownership changes while a worker is suspended must prevent later effects and preserve uncertain earlier effects.
- Process loss after execution claim must never replay a possibly committed action.
- Selected source or mode revocation during research must prevent publication/disclosure.
- Old Organizer records/archive/briefs must retain meaning with new optional fields.
- WATCH polling without changed evidence must remain waiting and produce no fabricated completion/notification.

## Tasks

- [ ] 1. Canonical commitment model and transitions: OrganizerCommitment.kt, OrganizerModels.kt, OrganizerCodec.kt, OrganizerUseCases.kt; tests for defaults, CAS, owner transitions, terminal/unknown states, stopwatch.
- [ ] 2. Real capability execution/binding: OrganizerOperations.kt and registered operation adapter, existing RoutineManagementActions/RoutineExecutor integration and lifecycle reconciliation; tests exact plan/version/cancellation, real receipts and no replay.
- [ ] 3. Research sources: provider contracts/parser, CollectorModels/Codec/PublicHttpCollector/Registry, bounded retained-evidence query and source management; tests provider parsing, zero Local requests, SSRF/robots/size/quotas/dedup/cache/revocation.
- [ ] 4. Composable briefs: selected sections, midday/weekly periods, responsibility/work/unfinished/research facts; extend persistence/settings/schema/presentation and tests.
- [ ] 5. Organizer surfaces: Today/Planner/Time/Routines/Sources/Briefs/Inbox/Research, ownership forms/actions, exact Workspace links and contextual Orb; reviewed EN/RU/LV/PL copy and UI regression sources.
- [ ] 6. AI/voice parity: strict argument/result contracts, production registration, manifest/discovery/policy/provenance, actual native intent boundary; registered workflow regression sources.
- [ ] 7. Independent whole-batch source review, correction sources, acceptance mapping/docs/commits, source freeze. Only after source completion and stable power run focused gate then APK build.
