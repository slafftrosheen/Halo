# Briefing and world-clock core — bounded integration review

Status: Independent bounded review; BI01–BI06 and the city asset lookup correction have matching focused host evidence: 51 tests passed. Full UI, tools, widgets, backup and rendered/device acceptance remain pending. No overall UX score, 10/10 or issue-63 completion is claimed.

Reviewer: `/root/ux_ui_review`, 1 November 2026. Baseline checkpoint: `b0c7c1e78`. Scope: `everyday/briefing`, BriefWorker, Brief and WorldClock Room owners/codecs/entities, OfflineCityCatalogue and asset provenance, database 34, DI/application wiring, read-aloud validity/provenance changes, Daily Assistant core projection and AndroidAttentionHost brief handling. The parent froze the core snapshot for a host run during this review and has not yet provided a passing result. The reviewer performed no builds, test execution, phone operations or implementation edits.

Full organizer/source/brief screens, native entry completion, Library/tools, Screen cards and launcher widgets are subsequent workstreams. Their absence is not counted as a regression in this bounded review. All code paths below are repository-relative; line numbers describe the inspected snapshot.

## Existing safeguards observed

The deterministic engine consumes typed local snapshots and organizer owners without fetching or starting a model. Source selection, mute/disable, source version, child sharing and local-object existence are rechecked before reopening a saved brief. Expired warnings are filtered at projection/read time. Stored source stamps retain retrieval and verification times. Morning baseline insertion uses a transaction and conflict-ignore key per date/zone/audience; settings use version comparison. Brief history is capped at 60 documents on publication. Speech uses the existing sentence/audio owner with captured remote categories and policy revision. Proactive requests carry generic notification text rather than private briefing bodies, use the existing attention owner and do not choose a speech channel.

World-clock edits, removal, Undo and exact-list reorder use record versions in Room transactions. Display time uses zone rules rather than stored numeric offsets. Offline city lookup reads a bundled asset and requests neither network nor location permission. The asset SHA-256 independently matches its provenance JSON: `f9bb8422bebb3e59162ddc266fe1d08c72319c28e7ef68b09d1d0c3d58dec2fa`. That metadata records 34,195 rows, GeoNames source/attribution/license URLs and the extraction timestamp; this review did not refetch the upstream dataset or perform a new license audit.

These are source observations and a local artifact hash check, not execution or device acceptance.

## BI01 — P1: a pre-dispatch claim can permanently consume a scheduled brief

`BriefProactiveCoordinator.kt:22–32` claims the period before invoking delivery. `RoomBriefRepository.kt:36–43` exposes every delivery-row key as done, regardless of CLAIMED, FAILED or CANCELLED state, and claims use conflict-ignore insertion. A process stop after the brief claim but before attention submission leaves CLAIMED indefinitely. A known failure/cancellation before sink entry also permanently excludes that period from `BriefSchedulePolicy.due`, even when the user is still within its valid window.

The existing attention delivery owner already distinguishes queued, confirmed and unknown dispatch, and deduplicates stable request IDs. The new brief owner currently does not reconcile its pre-dispatch claim with that evidence. Blanket retry would introduce duplicates; blanket consumption silently loses known-undispatched work.

**Correction:** persist/reuse the same document/request identity and reconcile CLAIMED with the existing attention receipt/queue owner. Separate confirmed/unknown dispatch from a known pre-dispatch interruption. Retry only where evidence permits it and only while current opt-in, quiet hours and schedule still allow it. Retain unknown delivery truth if a sink might have received the request.

**Acceptance:** stop after brief claim but before attention submission, then reopen within the schedule window; reconcile and deliver once. Separately stop after attention records unknown/dispatch evidence and verify no duplicate. Exercise a known pre-dispatch cancellation and a queued request that later expires. The current `cancelledClaimIsDurableAndNotRetriedAsDuplicateNotification` test covers blanket consumption; it needs these distinct outcomes rather than treating every cancellation as evidence of dispatch.

The parent proposed retaining the first document per period, resuming a brief-level pre-submit claim only when canonical attention evidence is absent, and preserving QUEUED/unknown/confirmed outcomes and explicit sink failure. That design addresses this finding in principle; source correction remains pending. Recovery should query persisted receipt/pending rows rather than cold-start flow defaults, preserve the original request identity/expiry, handle a pruned or explicitly removed document as unavailable, and reconcile later queued outcomes without reannouncing them.

## BI02 — P2: reopening an adult brief in Kids bypasses the child projection bounds

`EverydayBriefService.kt:45–69` changes a stored adult document's audience to KIDS and filters content authority, but retains the adult-selected fact/change lists and comparison metadata. It does not reapply `EverydayBriefEngine.kt:160–174`, where child headlines are limited to three and the full child fact list to twelve. A previously generated adult brief with many now-approved records/headlines can therefore remain adult-sized after conversion. Filtering unapproved content alone does not preserve the engine's child presentation contract or give an adult morning baseline child-specific meaning.

**Correction:** require/build a separate Kids document when reopening across that boundary, or reuse the shared audience projection and current child comparison baseline after authority filtering. Apply the same bounded behavior to changes, not only the main facts. Do not silently treat the adult baseline as the child's morning brief.

**Acceptance:** generate an adult brief with more than twelve approved facts and more than three approved headlines, switch to Kids and reopen. The result must follow the child limits or explicitly route to a fresh Kids brief, with no unapproved local content. Cover CHANGES and switching audience during the suspended read.

## BI03 — P2: unchanged live attention facts expire after sixty seconds

`DailyAssistantController.kt:45–60` sets `baseUpdatedAt` after `distinctUntilChanged`, while `recentBase()` returns an empty snapshot when that timestamp is older than sixty seconds. An unchanged approval, due Study item or other attention fact therefore disappears from briefing input after one minute even while Home remains subscribed and the underlying owner still reports it. Calling `refresh()` does not renew the timestamp if the projected value is unchanged because equality suppression occurs first.

DI uses this same `recentBase()` for `BriefLocalAuthority` attention IDs. The expiry can therefore also invalidate an existing saved fact/read-aloud session as though content had changed, while the actual item remains present.

**Correction:** distinguish owner readiness/subscription freshness from content equality. Renew observation validity on a real owner observation/refresh, or retain explicitly live owner state until that owner is no longer current. Preserve the deliberate rule that a background brief must not start the runtime merely to obtain optional attention facts.

**Acceptance:** keep an unchanged pending approval and Study item actively observed for more than sixty seconds; fresh brief input and still-exists checks must retain them. Explicit refresh with an identical projection must not falsely return empty. Then stop the owner/change or remove the item and confirm the intended unavailable/removal behavior without starting the runtime.

## BI04 — P2: public-calendar corrections are missed and occurrence links use the master URL

`EverydayBriefEngine.kt:93–97` displays the effective occurrence's title/details, but hashes only title/start/end for its comparison revision. A publisher correction to event details changes the actual displayed fact while “Since morning” reports no update. That branch also copies `record.sourceUrl` from the master rather than `occurrence.record.sourceUrl`, even though the recurrence owner projects an occurrence-specific URL override.

**Correction:** fingerprint the represented effective occurrence fields consistently, including displayed details and its action/source URL, and use the effective occurrence URL. Keep the stable occurrence identity so ordinary content corrections produce UPDATED rather than unrelated added facts.

**Acceptance:** save a morning baseline, then refresh the same event identity/time with corrected description and an occurrence-specific URL override. The comparison must contain UPDATED and the fact must link to the override. An unchanged conditional verification must still produce no invented change.

## BI05 — P2: final delivery can use quiet-hour/opt-in authority read before a suspension

`AndroidAttentionHost.kt:132–139`, `briefStillAllowed`, reads settings and evaluates quiet hours before suspending in `briefService.visible`. If the user disables proactive briefs, edits quiet hours, or the clock enters quiet time during that read, the method can return the old true result. The subsequent notification/silent surface then runs using authority that has changed. This final sink check also handles queued attention released later, so the proactive coordinator's earlier checks do not cover it.

**Correction:** after the suspended document validation, recheck current settings/version and current local quiet time immediately before sink dispatch. Revalidate any relevant document/audience identity at that final boundary. Preserve the existing attention queue's expiry and completion semantics.

**Acceptance:** hold document validation behind a barrier, turn proactive off or enter quiet time, then release it. Neither Android notification nor silent surface may dispatch. Repeat for an attention request released from its queue, not only a new worker request.

## BI06 — P2: stale brief invalidation can stop a newer shared reader

`BriefReadAloud.kt:39–41` checks `playing.id == readingId` in a flow emission and then calls unscoped `reader.stop()`. `HaloReadAloudController.kt:167` schedules the actual stop on Main. A Notes/answer read can replace the brief between the flow snapshot and that queued Main action; the old brief monitor then cancels the newer read. Cancelling the monitor does not revoke a stop already scheduled on the reader's scope.

**Correction:** add a scoped stop whose expected session/token check and cancellation happen together on the reader owner. Use a per-start generation if replaying the same document ID is allowed. The monitor's earlier flow identity check is useful for subscription lifetime but is not cancellation authority for a later Main action.

**Acceptance:** begin a brief read, queue its invalidation, replace it with a different reader before the stop runs, and confirm the replacement continues. Repeat with the same brief ID started again. An invalidation that still owns the active session must stop that session promptly.

## Scope and acceptance limits

The world-clock CAS paths, database-34 declarations/migration and city source pipeline showed no additional concrete defect in this bounded inspection. This does not establish migration execution, city search on supported Android zone databases, rendered forms or actual timezone-change behavior.

Retention currently means cleanup during writes: brief publication prunes documents/baselines/delivery history older than thirty days, and world-clock save/remove prunes old tombstones. It is not an independently verified hard deletion deadline during inactivity. Future retention copy and data-clear/backup integration must describe that policy accurately; those unfinished integrations are not new findings in this pass.

The source test fixtures cover deterministic projection, absent versus unchanged baseline, some child/source filtering, warning expiry, quiet-hour schedules, immutable baseline, settings CAS, concurrent delivery claims, history cap, clock reorder/Undo/DST and bundled-city lookup. They were inspected only. The current parent-owned run and later fixes require their own matching results; no earlier collector or organizer archive validates this new core snapshot.

No overall score, visual acceptance, participant/assistive result, live-provider behavior, phone scheduling/speech result or issue-63 completion is inferred.

## Corrective source review

The parent requested a bounded recheck after applying BI01–BI06 corrections. This section supersedes the original open dispositions for the mechanisms identified below, while retaining the original failure descriptions. No implementation, tests or device operations were performed by the reviewer.

| Finding | Source disposition | Inspected correction and limit |
|---|---|---|
| BI01 | Addressed in source | `BriefProactiveCoordinator.kt:15–60` reconciles persisted canonical attention state, lets an unsubmitted CLAIMED row become RETRYABLE after its preparation lease, and reuses the first document/request identity and original one-hour expiry. Known dispatch, queue, unknown and failure remain consumed. `RoomBriefRepository.kt:36–49` and `BriefEntities.kt:33–38` provide reclaim comparison and terminal-state protections; DI queries the actual attention receipt/pending rows. Source cases cover pre-submit cancellation, expired preparation recovery, unknown completion and deleted history. Matching execution is still pending. |
| BI02 | Partially addressed | `EverydayBriefService.kt:63–69` now reapplies the shared engine selector to reopened facts and Kids comparison facts, retaining the twelve-fact/three-headline bounds and filtering removed changes from Kids. The stored comparison baseline/audience is still reused across the adult→Kids conversion; see the residual below. |
| BI03 | Addressed in source | `DailyAssistantController` tracks active base observation, clears readiness when observation ends, observes a subscriber-only thirty-second clock and stamps observation before equality suppression. `recentBase` no longer treats unchanged content as expired. The new source test retains an unchanged active projection after sixty seconds and an explicit refresh without adding the BRIEF item back into its own attention input. |
| BI04 | Addressed in source | `EverydayBriefEngine.kt:95–98` fingerprints the displayed effective occurrence details and source URL in addition to title/time, and uses the effective occurrence URL for the fact. The new source test checks description/URL corrections produce UPDATED. |
| BI05 | Addressed in source | `BriefNoticeAuthority.kt:9–15` rereads settings/version after suspended availability validation and then checks current proactive opt-in, quiet time and expiry. AndroidAttentionHost uses that helper for delivery and silent surfacing. The new barrier-test source exercises opt-out and crossing quiet time/expiry during validation. |
| BI06 | Addressed in source | `HaloReadAloudController` returns a per-start session token and checks that token on Main in `stopSession` before cancelling. `BriefReadAloud` monitors/cancels only its own token, including monitor errors. The source regression queues replacement readers and same-document replay before an old token's stop; it checks that only the current token can stop the replacement. |

### BI02 residual — an adult baseline is relabeled as a Kids comparison

`EverydayBriefService.kt:71–74` still returns `stored.copy(audience = actualAudience, ...)` without changing `baselineAt`, `baselineMissing` or the origin of the retained changes. If an adult morning brief and adult CHANGES document exist but there is no Kids morning baseline, switching to Kids and reopening that CHANGES document produces `audience = KIDS`, a non-null adult `baselineAt` and `baselineMissing = false`. The new child limit test itself constructs this sequence, but currently checks only the resulting list lengths.

The engine explicitly treats date, zone **and audience** as baseline identity. Authority filtering and list bounding do not convert the adult comparison into a valid Kids comparison. The returned metadata can cause the shared presentation to describe adult-derived changes as the child's “Since morning,” despite the missing child baseline.

**Remaining correction:** on cross-audience reopening, either rebuild/recompare with the target audience's baseline or mark the comparison unavailable/missing and suppress inherited comparison changes. Preserve the now-correct bounded and authority-filtered facts. Do not silently overwrite the separately stored morning baseline.

**Acceptance:** extend the adult→Kids reopening case to assert truthful missing/unavailable comparison when no Kids baseline exists. Then create a distinct Kids morning baseline and verify that a Kids comparison uses that baseline's time/fingerprints, not the adult one. Include read-aloud's comparison notice as well as the returned data model.

## Intermediate execution evidence

The inspected [first core result](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/briefing-core-results.json) records **BUILD FAILED in 7m11s**, with production Kotlin and Room generation completed and **JUnit NOT_RUN**. Test compilation failed on an inferred recursive fixture type; the artifact records 72 matched source files and the generated database-34 schema hash. This is compilation evidence only. The parent reports explicit fixture types have since corrected that compile problem; the corrective source and regression cases above still require their own matching run. No previous collector/organizer passing archive is substituted for it.

## BI02 residual and city asset source correction

The subsequent bounded recheck confirms BI02's comparison residual is **addressed in source**. `EverydayBriefService.kt:66–78` now detects a different audience, suppresses inherited changes, clears `baselineAt`, marks a CHANGES document's baseline missing and marks its allowed sources unavailable for comparison. It retains the shared audience bounds on visible facts. `BriefPresentation.kt:38–42` explains the absence of a matching morning brief for this saved view rather than claiming there was no morning brief anywhere.

The extended `adultStoredBriefReappliesKidsLimitsToFactsAndComparisonChanges` source case checks an adult CHANGES document reopened in Kids has no inherited changes, `baselineMissing = true` and no baseline time. It then creates an actual Kids morning baseline, changes the shared records and checks the resulting Kids comparison has twelve bounded changes and a valid baseline. These updated assertions have not yet been reported as executed in the final corrected snapshot. No further concrete residual was found in this narrow comparison correction.

The city lookup failure also has a coherent source correction: DI, the asset test, importer and provenance now name `everyday/everyday-cities.bin`; the catalogue still reads the payload with `GZIPInputStream`. The obsolete `.tsv.gz` app/test/importer reference is gone. The reviewer independently checked the renamed file remains **3,524,577 bytes** with SHA-256 `f9bb8422bebb3e59162ddc266fe1d08c72319c28e7ef68b09d1d0c3d58dec2fa`, matching the provenance and prior data. The metadata now explicitly records the asset filename and gzip TSV encoding. This confirms unchanged data and matching source references; packaged lookup/search still requires the rerun.

## Recovery execution evidence

The inspected [recovery result](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/briefing-recovery-results.json) records **BUILD FAILED in 7m57s**: **51 tests in eight suites, one failure, zero errors/skips**. Fifty cases passed, including the scoped reader stop, active Daily observation, migration, briefing persistence and quiet-hour barrier cases. The artifact records 78 matched source files. The archived city-test failure is specifically `FileNotFoundException: everyday/everyday-cities.tsv.gz` in `bundledCitySearchSupportsDiacriticsAndRussianWithoutNetwork`.

The parent traced that failure to Android asset merging expanding the `.gz` asset name and changed the gzip payload's filename to `.bin`. The renamed asset and BI02 comparison correction postdate this run. The run therefore remains failed; its fifty passing cases are evidence only for that archived snapshot. The final rerun is pending, and no complete core, packaged-city, rendered UI or device acceptance is inferred from these results.

## Matching final core host evidence

The inspected [final result](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/briefing-final-results.json) records **BUILD SUCCESSFUL in 5m1s**, with **51 tests in eight suites, zero failures/errors/skips**. The batch covers the scoped speech-session regression, Daily projection/observation, migration, brief persistence and comparison, notice authority, deterministic briefing, world clocks and the packaged offline-city lookup. The BI01–BI06 corrections, BI02 cross-audience residual and `.bin` city asset are included in this snapshot.

Matching [JUnit](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/briefing-final-junit.zip), [source archive](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/briefing-final-source.zip), source manifest and build log are retained. The reviewer independently checked **all 78 current source hashes** against the manifest captured at 21:11:19 UTC: zero mismatches.

This supersedes the pending-final-host statements for this bounded core. All known BI01–BI06 findings are source-addressed with matching focused host evidence, and the city lookup rerun passed. The earlier no-JUnit compilation failure and subsequent 51-case/one-failure recovery remain recorded as failed runs; neither is relabeled green.

This is not full issue-63 or overall UX acceptance. Full screens/callers, tools, Screen/launcher widgets, backup/data-clear integration, the complete policy matrix, rendered accessibility and Android device scheduling/speech remain separate gates. No overall score, participant result or device result is inferred. The reviewer performed no builds, production edits or phone operations.
