# Everyday collectors

Implementation is in progress for issue 63. This describes the current collector owner; the briefing, source screens, cards, widgets and tool entry points have separate integration gates in the implementation ledger.

## Ownership and useful local state

`CollectorRegistry` has five passive local adapters and four public adapters: RSS/Atom, weather, warnings and public ICS. Local readers use the existing organizer and Notes tables, bounded Activity roots, the existing process Timeline, this package's active Android notifications, battery broadcasts and already observed local runtime/model health. They do not start models, ask for location or notification-listener permissions, or copy another app's notifications. An empty result means no observations from that owner; it is not sample content.

`RoomCollectorRepository` owns source versions, snapshots, attempts, request reservations and endpoint retry state in the existing database (migration 32–33). Notes and organizer records keep their canonical owners. The initial catalogue seeds local sources selected and external sources unselected. The seed marker is durable: deleting a starter does not cause it to return after restart. Weather requires an explicit manual place; the catalogue's Riga constant is an available example, not a claim about the user's location.

## Authority and transmitted data

Every external read requires the existing global Power authority and permitted connected services. Weather also requires the location data category because its selected city coordinates leave the phone. The request sends only a city rounded to two decimal coordinates plus forecast fields/time zone, or the selected public feed URL, validators and a Halo user-agent. It does not attach Notes, activity, organizer bodies, notification content, account cookies or authorization headers. Scoped provenance from a tool call still travels through the shared SDK policy; an outer collector lease is not permission to strip that provenance.

Local mode rejects before transport and before an external request reservation. Cached bodies remain readable with source/timestamps and an explicitly stale external-cache presentation. Switching mode or changing source version/selection cancels the transfer; the final source-version and reservation-token comparison prevents publication by an old request. Parent coroutine cancellation completes the durable attempt before propagating cancellation.

The transport accepts public HTTPS endpoints, validates actual DNS answers, refuses local/special-address destinations, uses no proxy or cookies, strips credentials, and checks every redirect. The SDK permits at most five redirects. Each response body is bounded to 2 MiB and the default HTTP call has a 30-second deadline. XML entity declarations, unsafe links, overly deep structures and oversized payload collections are rejected. Reader content is sanitized plain text; rendering it does not fetch images or trackers. A malformed provider body produces a recoverable result, not a successful empty snapshot.

## Freshness and failure

Snapshots separate `retrievedAt` (when this body arrived), `verifiedAt` (when a successful response confirmed it), and `expiresAt`. HTTP 304 updates verification/expiry without rewriting retrieval time. Failure, rejection and cancellation retain the prior body and its timestamps. Source metadata changes preserve cache only when endpoint, place, language and publisher retain their content identity. Source version changes cancel ongoing work.

Normal state readers isolate an unreadable cached payload, remove that derived cache and expose a recovery attempt for that source; it cannot make all other sources unreadable. Payload limits are checked before publication and again during restore. Public calendars permit at most 100 records, 2,000 exceptions and 2,000 reported import issues. The public reader never silently imports these records into the personal organizer.

Manual refresh returns a structured receipt including whether a request was admitted and any cadence, cooldown, quota or retry reason. Consumers must display that receipt; a deferred operation is not a fresh success. Warning expiry must be checked when presenting cached/304 results, not only when parsing HTTP 200. Empty warning feeds do not certify safe conditions.

## Scheduling, quotas and retention

WorkManager performs a periodic pass no more frequently than its 15-minute interval; Android may delay it. A pass admits at most five local and five external operations. Its scan is bounded to the 100-source limit and continues past policy-denied/deferred entries, so denied weather cannot indefinitely starve permitted news. No widget binding, clock tick or scroll triggers polling. Exact alarms, wake locks and foreground services are not used for collectors.

External reservations permit at most 500 logical attempts across a rolling 24 hours and 96 for a normalized endpoint. Preview and polling share endpoint identity, a one-minute cooldown, quotas and server retry delays, including after removing/re-adding or duplicating a source. A logical attempt can contain the original request and up to five validated redirect hops; these limits are not a claim of 500 total HTTP hops. Failed and cancelled admitted attempts still consume budget. Clock rollback does not reset a rolling budget. Request records expire after the rolling day; inactive endpoint retry entries are pruned after seven days. Failure lineage belongs to the endpoint: cancellation preserves it, and only an actual success or 304 resets it.

Sources persist until removed. Each source holds at most one bounded typed snapshot and one latest attempt; deleting a source cascades those derived records. Disabled/muted sources retain their cache but are excluded from refresh/briefing according to their separate settings. Request budgets and endpoint retry state deliberately survive source deletion. Backup, global data-clear integration, UI-controlled cache removal and complete retention presentation remain part of the unfinished integration work.

## Evidence and remaining acceptance

The first integrated build failed before tests on a dependency-graph type-inference error. The next run passed parser/migration checks but the integration test class did not initialize because one test returned Boolean. Both failures are archived. The final batch passed 28 tests: all 20 integration, 5 parser and 3 migration cases, zero failures/errors/skips, with 29 matching source hashes. The bounded UX review is `COLLECTOR-INTEGRATION-REVIEW.md`; it separates source corrections, host fixtures and downstream presentation obligations. These collectors have no claimed device scheduling, provider uptime, complete Local/Power matrix, visual or overall issue-63 acceptance yet.
