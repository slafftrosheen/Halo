# Issue 63 — complete the ten audit corrections

Status: coding batch in progress. No build or test execution until all production and regression sources are ready.

Spec: [requirements audit](/home/pi/Projects/Halo/docs/testing/ISSUE-63-REQUIREMENTS-AUDIT-2026-11-03.md), [issue body](/home/pi/Projects/Halo/docs/everyday/ISSUE-63-SOURCE.md), repository directives 18–19.
Base: `24e226b10`. Existing checkout `codex/issue-63-completion`; preserve the unstaged executable bit on `gradlew`.

## Global constraints

- Keep Runtime/host boundaries, existing ToolRegistry/CognitiveSession and canonical domain owners. No alternate agent, store or privacy mode.
- Preserve Local/Power, family/disclosure/source-version authority, exact occurrence identities and uncertain-write no-replay behavior.
- User ruling: author all fixes and regression sources first; do not compile, build or execute tests per edit/task. Review the whole coding batch before the focused gate and one app build.
- Native device, real STT/provider, launcher, accessibility and battery acceptance stay distinct from source/host checks.
- Final Android document selection/grants remain human-owned; import already-authorized calendar data and prepare managed exports functionally through the assistant.

## Tasks

1. Delivery and recognized intents (AUD63-01/02): inject the delivery coordinator into Everyday execution; route default Halo alarm/timer native commands through registered canonical operations while retaining explicit Android Clock commands. Add old-tag cancellation and native mapping/owner regressions.
2. Briefing (AUD63-03/04): carry explicit attention observation availability/time; include bounded receipt-aware outstanding reminders across dates. Add unavailable-vs-empty and midnight/disposition/grant regressions.
3. Organizer/setup UI (AUD63-05/06/10): localize parser reason codes/parameters, search eligible link owners before result bounds, make weather setup removal/replacement update its selected source. Add locale/large-list/repeated-save regressions.
4. Screen persistence (AUD63-07): common checked Apply outcome off the UI thread, preserve drafts and distinguish conflict/unknown persistence; cover failure/restart/revision cases.
5. Calendar assistant parity (AUD63-09): reviewed bounded import and managed ICS export via exact canonical source/object versions, central authority/disclosure/descriptors/manifest. Cover stale/untrusted/uncertain cases and legitimate SAF boundary.
6. Result contracts (AUD63-08): per-operation bounded schemas, accurate operations and durable identity fields matching actual payloads; validate production registry and representative real results, update identities/provenance fixtures for new commands.
7. Whole-batch source review, correction pass, documentation/commits, then matching focused tests and build. Record exact results and remaining genuine external acceptance; do not equate unexecuted source with verified completion.

## Interface/preflight decisions

| Producer → consumer | Decision |
|---|---|
| Native intent mapping → app host executor → organizer | Runtime emits typed domain arguments, Android time resolution and persistence stay in app host. Existing native execution/policy owner is retained. |
| Attention owner → brief input → baseline | Successful empty and unavailable need distinct metadata; old callers/fixtures default conservatively and explicit observed fixtures remain expressible. |
| Organizer receipts → brief outstanding occurrence | Only actual unresolved occurrences enter; dismissed/completed/cancelled ones stay absent and Kids grants still filter. |
| Calendar operations → result schemas/discovery | Add operations before freezing schemas/IDs and update manifest/policy tests together. |
| Screen touch → canonical store | Persistence confirmation must share the revision lock; acknowledgement failure is unknown, not permission to replay. |

## Progress

- Planning and source investigation started; all ten IDs remain open until production and regression sources are reviewed.
- Tests/build: NOT_RUN. User batching instruction overrides per-task RED/GREEN execution in the skill workflow.
