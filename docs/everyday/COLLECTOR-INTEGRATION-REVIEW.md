# Collector integration — bounded correctness and recovery review

Status: Independent bounded review; CI01–CI04, including the CI03 failure-history residual, are addressed in source with matching focused host evidence: 28 tests passed. Rendered/device and full integration acceptance remain pending. No overall UX score, 10/10 or issue-63 completion is claimed.

Reviewer: `/root/ux_ui_review`, 1 November 2026. Scope: collector HTTP/coordinator/preview/weather/JSON/local/catalogue owners, Android bootstrap/worker, Room collector persistence/codecs, database migration 32–33, DI/application wiring and `CollectorIntegrationTest`. The reviewer performed no builds, test execution, phone operations or production/test edits. Full source screens, briefing, child presentation, tools, Library and widget integration are planned subsequent work and are not counted as missing implementation in this bounded review.

The inspected implementation has meaningful safeguards: external adapters require selected/enabled sources and revocable global authority before transfer; internal adapters use existing local owners; source versions and reservation tokens fence late publication; failures retain the prior body; HTTP 304 advances verification rather than retrieval time; request reservations persist across process death; HTML is converted to inert text; public URL/DNS/redirect boundaries reuse the governed transport. The starter catalogue stores external sources unselected. These are source observations, not evidence that the currently running host batch has passed.

All code paths below are repository-relative; line numbers refer to this reviewed batch.

## CI01 — P1: an accepted calendar snapshot can make all collector states unreadable

`app/src/main/java/com/halo/app/everyday/collectors/PublicHttpCollector.kt:79–81` limits only the imported calendar's record count. `app/src/main/java/com/halo/app/data/everyday/CollectorCodec.kt:48–53` then encodes all exceptions and issues, whereas its decoder at `:68–71` rejects more than 2,000 exceptions or issues. These are not equivalent admission bounds.

A small calendar can contain one valid repeating event and more than 2,000 distinct excluded occurrences, spread across multiple `EXDATE` properties within the existing property/body limits. `OrganizerIcs.kt:159–164` builds all those overrides. The HTTP adapter accepts its one-record preview and the codec writes it, but its next read rejects it. Similarly, multiple diagnostics per event can exceed the codec's issue bound independently of the accepted record count.

`RoomCollectorRepository.kt:13–18` decodes every row while constructing `state`, `states` and the observed source list. It does not isolate an unreadable snapshot. Therefore this is more than one failed calendar: the newly persisted row can terminate the entire list/worker projection, and the coordinator's final `repository.state` also fails. The existing cache can be replaced by a body that this same version cannot read.

**Correction:** validate all payload/codec bounds before committing a successful snapshot, including calendar exceptions and issues. Enforce the same invariant in the persistence owner so alternate callers cannot bypass it. Preserve the last readable snapshot and a typed failure on rejection. Provide a bounded recovery path for an already unreadable snapshot so unrelated sources and source removal/settings remain accessible.

**Acceptance:** serve a valid one-event recurring calendar with 2,001 distinct exclusions split across bounded properties; rejection must preserve the previous snapshot, leave the source list readable and allow another source to refresh. Exercise the diagnostic count independently, a restart after rejection, and an intentionally unreadable stored snapshot. No oversized input may commit a CURRENT receipt that cannot be reopened.

## CI02 — P2: manual refresh silently discards its cooldown explanation

`RoomCollectorRepository.kt:68–75` returns a `CollectorAdmission.Deferred` carrying BACKOFF and a purposeful reason for cadence/server delay or the one-minute manual cooldown. Unlike quota deferral, these branches do not persist that receipt. `CollectorCoordinator.kt:40–42` ignores the deferred result and returns from the transfer; `:96` then returns the unchanged stored state.

After a successful refresh, a second explicit refresh within one minute therefore returns the old CURRENT state and no explanation, although no request ran. After a Local attempt replaced the state with LOCAL_ONLY while retaining a future server delay, switching to Power and refreshing early can still return the old Local-only reason instead of the actual backoff reason. The existing backoff test verifies call suppression, not the caller's recovery explanation.

**Correction:** make the deferred outcome observable to the command caller and source-state projection, either through a typed refresh outcome or an appropriate durable attempt update. Preserve the prior successful retrieval/verification times, failure count and authoritative next-allowed time. Do not label a skipped attempt as a new successful check.

**Acceptance:** a second tap within 60 seconds yields zero transfers and the cooldown reason/time; a server-backoff → Local → Power sequence reports the remaining server wait. Reopening the source must still show the retained snapshot with its original timestamps.

## CI03 — P2: endpoint retry authority resets across preview, metadata changes and real re-add

The durable budget and retry policy are attached to UI source identity rather than consistently to the actual requested endpoint:

- `CollectorPreview.kt:16–18` budgets a preview under `preview:<URL hash>`, while `:36–38` saves it with a fresh `custom:<UUID>`. `RoomCollectorRepository.kt:86,98–103` counts saved requests by that source ID. Removing a custom source and adding the same endpoint through the real preview/add path therefore starts another per-source budget; preview and saved refresh also consume separate per-source budgets. The global 500-request ceiling remains intact, but the advertised 96-request per-source ceiling does not follow the endpoint. `sourceQuotaSurvivesDeleteReAddAndPreviewHasCooldown` recreates the same ID directly and does not cover the actual UUID-generating path.
- `CollectorPreview.kt:26–32` retains no failed-attempt or `Retry-After` receipt. `reservePreview` at `RoomCollectorRepository.kt:91–96` permits another request after 60 seconds even when the last preview received a four-hour server delay.
- `RoomCollectorRepository.kt:38–44` uses the same `sameContent` decision for snapshot invalidation and retry-history preservation. Changing only a publisher or language for the same endpoint resets the attempt to NEVER, so an outstanding server delay is discarded even though the destination has not changed.

These paths can repeatedly hit a rate-limited provider while presenting ordinary source configuration/recovery to the user, eventually exhausting the shared daily quota and blocking unrelated sources.

**Correction:** keep durable endpoint request-budget/backoff identity separate from source/card identity and parsed-content invalidation. Apply it to previews and saved sources, preserve it through remove/re-add and metadata-only changes, and retain server retry times after preview failures. Keep the global cap and no-request Local admission. If duplicate source configurations are supported, they should share that endpoint authority; otherwise reject/resolve duplicates explicitly.

**Acceptance:** use `inspect → add → refresh → remove → inspect → add`, with distinct generated source IDs but the same endpoint, and confirm the per-endpoint quota survives. A preview HTTP 429 with four-hour `Retry-After` must prevent another transfer after 61 seconds, including after restart or subsequent add. Editing publisher/language during saved-source backoff must not reset the server wait. A genuinely different endpoint retains its independent quota.

## CI04 — P2: policy-denied sources can permanently starve an allowed due feed

`CollectorCoordinator.kt:104–107` takes the first bounded candidates before checking admission. A policy denial at `:80–83` copies the old attempt without advancing `at` or a separate scheduling cursor. `CollectorRefreshWorker.kt:21` limits the external pass to five candidates.

For a concrete existing-policy case, let a selected news feed have a prior snapshot that is now due. Select five never-fetched weather locations while Power permits connected services/account metadata but disables LOCATION. The weather sources have null `at`, sort before the previously fetched news feed, and fail authority before reservation. Every subsequent periodic pass selects the same five unchanged candidates. The permitted news feed is never reached and its cached information ages indefinitely. This is consistent with the current per-category check in `PrivacyModeStore.kt:220`; it does not require a speculative future policy.

**Correction:** make the bounded pass fair across denied/deferred candidates. For example, distinguish scan budget from transfer budget, continue past denied candidates, or maintain a durable scheduling cursor/denial timestamp without overwriting successful retrieval time or bypassing authority. Avoid a tight retry loop and retain the exact per-request policy boundary.

**Acceptance:** five LOCATION-denied, never-fetched weather sources plus one permitted previously fetched/due feed must refresh the feed in a bounded pass while producing zero weather requests. Repeat across worker/process restart and then permit LOCATION; weather should become eligible without re-adding sources.

## Integration boundaries retained for the next workstreams

These are required downstream checks, not additional defects attributed to absent screens:

- **Child presentation:** `StarterSourceCatalogue.local()` marks all local source kinds `childApproved = true`, but the adapters read unscoped organizer titles/details, note titles, activity requests and Halo-owned notification text. That source flag does not establish per-item child/profile eligibility. No current consumer or demonstrated live child leak was found in this batch. Child briefs/cards/readers must apply existing profile/content authority before exposing those facts; weather/source approval must not become approval for arbitrary private local content.
- **Cache truth:** consumers must show source/publisher and the distinction between retrieved, verified, failed-attempt and expiry times. External cache remains stale in Local. Muted, unselected and disabled sources remain distinguishable; retained bodies are not automatic permission to include them in a brief.
- **Warnings:** expiry filtering currently occurs on HTTP 200 parsing. A retained or HTTP-304-confirmed warning still carries its original `expires`; downstream current-warning projections must evaluate it at presentation time and distinguish expired/archive data, absent warnings and an unavailable source. A valid empty warning feed is not proof of safety.
- **Passive local state:** current local adapters avoid requesting notification-listener or location permissions and avoid starting the runtime. Their local observations, empty collections and raw state codes still need the planned localized, accessible presentation and canonical route validation.
- **Malformed-source recovery:** integration should exercise malformed XML/JSON through the preview form as well as through the coordinator, retaining purposeful error recovery and cancellation rather than assuming every parser failure returns an Updated/Unavailable value.

## Evidence limits

`CollectorIntegrationTest` provides source fixtures for Local/no-budget admission, internal facts, 304 timestamps, failure cache retention, source-version cancellation, parent cancellation, interruption recovery, metadata preservation, quotas, bootstrap selection, sanitized HTTP, body limits and weather units. Those fixtures were inspected only. The current parent-owned host run has not been reported as complete in this review, and the older foundation/network/delivery archives do not establish this newer collector batch's success.

Migration 32–33, source/attempt/snapshot invalidation joins, DI cancellation hooks and application/worker bootstrap were inspected. This document does not claim migration execution, rendered source UX, Android scheduling behavior, real provider correctness, full Local/Power matrix acceptance, performance/battery qualification or issue-63 completion.

## Corrective source review

The parent requested a bounded recheck after applying the four corrections. The recovery host run was still active; its first compile interruption and subsequent explicit DI type fix are parent-reported context, not passing evidence. This disposition supersedes the original open status above without rewriting the original failure record.

| Finding | Source disposition | Inspected correction and limit |
|---|---|---|
| CI01 | Addressed in source | `CollectorPayloadLimits.kt:29–32` bounds calendar records, exceptions and issues; HTTP publication and `CollectorCodec.encode` both invoke the shared validator. `RoomCollectorRepository.kt:16–28` isolates an unreadable snapshot, removes only that snapshot and returns a FAILED recovery reason while retaining the source. The new test covers oversized codec admission and an unreadable stored snapshot with another readable source. Matching execution and the complete HTTP/restart acceptance sequence remain unclaimed. |
| CI02 | Addressed in source | `CollectorCoordinator.kt:13,26–27,45,100` returns a `CollectorRefreshReceipt` with admission truth and the deferred attempt/reason; the compatibility `refresh` API also returns the deferred state. Successful snapshot timestamps remain untouched. The new test asserts no second request and the returned one-minute BACKOFF reason. Planned callers must consume that result; no rendered feedback claim is made. |
| CI03 | Partially addressed; residual below | Full normalized-URL fingerprints now key both previews and saved requests. `collector_endpoint_state` is included in the version-33 entities/migration. `reserve`/`reservePreview` enforce shared cooldown, quota and blocked-until state; `finishPreview` persists Retry-After and fences older attempted-at values. Removing/re-adding or changing publisher/language no longer discards an outstanding endpoint wait. The failure counter used by saved transfers still has source-local lineage. |
| CI04 | Addressed in source | `CollectorCoordinator.kt:85,105–116` advances denial attempt time without changing retrieval time, scans at most the source-count bound and counts only admitted transfers against the transfer budget. The new test places five denied weather sources ahead of an allowed due news feed and checks zero weather calls plus one news verification. No worker/device execution is inferred. |

### CI03 residual — shared endpoint failure count is overwritten by source-local history

`CollectorCoordinator.kt:71,94` still computes a failed saved refresh from `current.attempt.consecutiveFailures`. A newly added duplicate/replacement source, or a publisher/language edit that resets its content state, starts at zero even when the endpoint has several consecutive failures. `RoomCollectorRepository.kt:105` preserves that shared count during admission, but does not expose it in the reservation; `:152–153` then overwrites it with the new source's count of one when the transfer fails.

Consequently the existing server wait is now correctly honored, but after that wait expires the next failed request through another source identity starts exponential recovery over. This remains possible through ordinary remove/re-add or metadata editing, rather than requiring an alternate transport. The cancellation branch at `CollectorCoordinator.kt:89–91` likewise creates an attempt with default failure count zero, and `finish` clears the shared count despite no successful response.

**Remaining correction:** carry the admitted endpoint failure history into the reservation/finish owner and increment that authoritative history on a new failure. Preserve it on cancellation or another no-response outcome; reset it after an actual successful response/304. Keep source attempt presentation separate from endpoint transfer history and preserve the existing newer-attempt fence.

**Focused acceptance:** fail endpoint A repeatedly, wait until its last permitted retry time, then refresh the same endpoint through a duplicate, a real remove/re-add, and a publisher/language edit. Its next failure must continue the prior exponential sequence, not become failure one. Cancel an admitted retry before a result, reopen, then fail again; cancellation must not reset that sequence. A real success/304 should reset it. The currently added shared-endpoint test verifies a retained four-hour wait but ends with success, so it does not cover this residual.

The four new source tests were inspected only. There is still no passing claim for the active correction batch, no phone operation, and no expansion of this review into unfinished UI/brief/tool workstreams.

## CI03 final source disposition

The subsequent bounded recheck found the reported failure-history residual addressed in source. This section supersedes the partial disposition in the preceding historical review; no additional concrete defect was found in these corrected mechanisms.

- `CollectorRepository.kt:7–8` carries the admitted `consecutiveFailures` in the reservation. `RoomCollectorRepository.kt:85–111` obtains that value from the canonical endpoint row for external sources, writes it into FETCHING and returns it with the reservation, independently of the alias's presentation history.
- `CollectorCoordinator.kt:71,96` increments the admitted failure count on unavailable/exception outcomes. Its blocked and cancellation paths at `:85–93` retain that count instead of defaulting to zero.
- `RoomCollectorRepository.kt:151–160` keeps the newer-attempt fence, resets shared history only for CURRENT/NOT_MODIFIED, updates it for FAILED/OFFLINE/BACKOFF, and preserves it for other terminal paths. Manual admission uses endpoint backoff/cooldown/quota rather than an alias's stale failure delay; ordinary background cadence remains separate.
- `endpointFailureLineageSurvivesNewSourceAndCancellationUntilAnActualSuccess` at `CollectorIntegrationTest.kt:284–308` exercises failure one, a duplicate source continuing to failure two, actual coroutine cancellation preserving two, another failure becoming three, successful publication resetting zero, and a subsequent failure becoming one. The test source was inspected, not executed by this reviewer.

The four inspected files (reservation contract, coordinator, Room repository and integration test) independently match their SHA-256 entries in `collector-final-source.json`, captured at 20:29:03 UTC. This binds the narrow source disposition to the intended final rerun; it is not a passing-test claim.

The preceding [recovery result](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/collector-recovery-results.json) is preserved as **BUILD FAILED in 8m10s**: 9 reported test entries, one failure, zero errors/skips. Three migration cases and five parser cases passed; the integration class failed initialization, so its test methods did not execute. The artifact records 28 matched source files. The earlier estimate of 21 integration methods was an overcount of `this@TestPolicy` references; it is not an execution total. The new final run was still active at that source review; its subsequent result follows below.

## Matching final host evidence

The [final result](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/collector-final-results.json) records **BUILD SUCCESSFUL in 6m57s**, with **28 tests in three suites, zero failures/errors/skips**: 20 collector integration cases, five parser cases and three migration cases. Matching [JUnit](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/collector-final-junit.zip) and [source](../testing/artifacts/HALO-EVERYDAY-63-2026-11-01/collector-final-source.zip) archives are retained alongside the manifest and build log.

The reviewer independently compared all **29 manifest source hashes** with the current files: zero mismatches. The 20 actual `@Test fun` method names in `CollectorIntegrationTest` exactly match the 20 archived JUnit testcase names, with no missing or extra methods. The earlier expectation of 22 integration tests counted `this@TestPolicy` references and was incorrect; the final 20-case integration count is complete for this source snapshot.

This supersedes the pending-host statements for the final bounded collector batch. CI01–CI04 and the CI03 failure-history correction now have matching source and focused host evidence. The earlier initialization failure remains recorded as a failed run; it is not relabeled green.

HTTP cases use synthetic interceptors. Existing live-source validation and SDK DNS/socket evidence remain separate; this result does not establish real-provider or Android worker behavior. Full UI/caller integration, briefing, child presentation, tools, widgets, the complete Local/Power matrix, rendered accessibility and device acceptance remain outside this pass. No overall score or issue-63 completion is inferred. No builds or phone operations were performed by the reviewer.
