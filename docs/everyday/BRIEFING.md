# Your day in Halo — implementation and evidence

Status: bounded core integration verified on host: 51 tests in eight suites passed against 78 matching frozen source files. Full pages, tool exposure, Screen/native widgets and device acceptance are not established by this document.

## Existing owners

`EverydayBriefEngine` projects typed organizer records and collector snapshots into facts. It performs no network request, inference, speech or OS scheduling. `EverydayBriefService` reads the existing organizer and collector repositories and publishes through `RoomBriefRepository`. Existing Daily Assistant approvals, recovery and Study prompts remain owned by `DailyAssistantController`; a brief can include its actively observed base projection without feeding a prior brief back into itself or starting the runtime from a worker.

`DailyAssistantProjector.withBrief` adds the entry to the existing Today projection. The briefing page, history/settings and world-clock page are currently staged outside the app source and are not yet accepted UI integrations. Instrument routing, Library and typed voice actions remain work in progress.

## Facts, freshness and comparison

Local organizer facts are read directly from their owner. External data is consumed only from selected, enabled, unmuted collector snapshots with matching source versions. Building or reopening a brief never refreshes them. Source stamps retain retrieval, verification and expiry separately; Local presentation labels external information cached. Expired warnings are filtered again when a stored brief is displayed.

The first morning baseline is inserted atomically with its document and remains immutable for that date, time zone and audience. A missing baseline is an unavailable comparison, not “nothing changed.” Newly observed sources without morning data and missing/muted sources are not treated as verified unchanged. A current successful local owner may establish removal; an external feed item rolling out of its bounded cache does not establish deletion.

Fact IDs and content fingerprints are deterministic. Conditional HTTP verification alone does not invent a headline change. Calendar fingerprints include the displayed title, details, occurrence times and effective source URL. Display selection is bounded to 40 facts, or 12 in Kids, with a maximum of three Kids headlines; change history is bounded to 200 entries before audience filtering.

## Family and speech authority

Source approval does not share private organizer, note, activity or notification items with Kids. Organizer sharing uses explicit record IDs; child note membership reuses the existing Kids project library and checks active Workspace records. Stored briefs revalidate current source selection/version, item existence and family scope before display or reading. Cross-audience reopening discards inherited comparisons and marks a changes brief as lacking a matching baseline; only a morning baseline for that same audience can establish comparison. This correction and the 12-item Kids limit passed host regressions.

Explicit reading uses `HaloReadAloudController` and the existing speech output coordinator. Its captured remote provenance includes only the categories represented by spoken facts and changes. Global mode/category/provider policy remains authoritative. A per-start session token protects a newer reader from stale cancellation; the source is checked again before each sentence and changes are monitored while that reader owns playback. There is no second provider pipeline.

## Proactive delivery

Proactive notices default off. An enabled 15-minute WorkManager pass may build a morning or evening brief during its four-hour eligibility window. It respects user quiet hours, current schedule/version, local date/time zone and the canonical attention policy. It uses no exact alarm, wake lock, network refresh or automatic reading.

The OS notice contains a generic prompt to open Halo, not private brief facts. Canonical attention delivery revalidates opt-in and quiet time after suspended content checks, including a queued release. Its real result is retained: dispatched, queued, suppressed, failed and unknown remain distinct.

The first document identity is retained per period. A preparation claim with no canonical delivery evidence can resume after its five-minute lease; recovery uses the same request/document ID and original one-hour expiry. Canonical persisted attention receipts and pending rows are queried directly. Unknown dispatch is never automatically reannounced. Deleted history is not silently substituted. Receipt updates cannot regress confirmed dispatch to a late cancellation/retry state.

## Persistence, city data and clocks

Database migration 33→34 adds brief documents, audience baselines, settings, delivery receipts and world clocks. Settings and clock edits use expected versions; reorder validates the entire clock/version set, and removal has versioned Undo. Zone IDs use platform time-zone rules, including daylight-saving transitions.

The offline city asset is a transformed GeoNames `cities15000` extract with 34,195 entries. Search requires no geolocation permission or network request. The checked-in provenance records its source, retrieval date, transformations, hashes and CC BY 4.0 attribution. `tools/everyday/import-cities.py` is a host-side rebuild tool, not an application transport. App UI must show attribution. Older platform databases skip an unrecognized zone rather than guess a fixed offset.

Publishing trims history to 60 documents and prunes documents, baselines and delivery records older than 30 days. Clock mutations prune older deletion tombstones. Full backup/import/data-clear/retention integration remains part of workstream H; these mutation-time bounds do not claim a completed global erasure policy.

## Evidence

- `briefing-core-results.json`: production Kotlin and Room schema generation completed; test compilation failed at an inferred fixture type. JUnit: NOT_RUN. All 72 frozen source hashes matched; generated schema34 is recorded separately.
- `briefing-recovery-results.json`: 51 tests ran, with one failure caused by Android expanding a `.gz` asset filename. All 78 source hashes matched. The compressed payload was renamed `.bin`, with its encoding documented and its content hash unchanged.
- `briefing-final-results.json`: BUILD SUCCESSFUL in 5m1s; 51 tests in eight suites, zero failures/errors/skips. All 78 frozen source hashes matched, including the actual Android city asset lookup and cross-audience comparison correction. Source and JUnit archives are adjacent.
- `BRIEFING-INTEGRATION-REVIEW.md`: independent bounded source review, including unresolved/previous findings and their dispositions. No overall UX rating or device claim.

Artifacts are under `docs/testing/artifacts/HALO-EVERYDAY-63-2026-11-01/`. Phone availability is acknowledged; code-first ordering remains in effect. Participant testing remains deferred by the user.
