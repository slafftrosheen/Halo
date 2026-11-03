# Issue #63 — guarded conditional feed provenance (2026-11-03)

**Status:** code-only GitHub push; new production behavior, a hermetic integration regression and this record are committed to `main`. JUnit/Gradle, Android app/worker, real RSS endpoints, network trace, mode-transition observation and human UI tests **NOT_RUN**.

## Defect

A conditional feed refresh reused cached news on any HTTP 304 as long as source ID and version matched, regardless of the *final* response URL after redirects. The 304 might originate from a different resource that had just replaced a redirect target; an ETag collision or misbehaving origin could make an unrelated old body appear freshly verified. Source version is necessary but does not prove content-resource identity.

## Code change

`PublicHttpCollector` now checks the normalized (fragment-free) final request URL returned by OkHttp against the prior successful `CollectorSnapshot.effectiveUrl` (or the source endpoint for legacy snapshots) before accepting `NotModified`. No DNS or fetch is performed by this check. On mismatch it returns an explicit unavailable result and requests validator reset.

`CollectorCoordinator` commits that failure under the existing source-version/reservation-token CAS, retains the cached body and **its original `retrievedAt` and `verifiedAt`**, but clears only `etag` and `lastModified`. The next normal, policy-admitted/budgeted refresh therefore fetches an unconditional body from the current final URL. It does not make an unbudgeted hidden retry, bypass Local/Power, or silently discard user data.

## Regression source — NOT_RUN

One `CollectorIntegrationTest` case uses an in-process OkHttp fixture returning 304 with a different final resource URL. It verifies refusal, unchanged old content and provenance, cleared validators and a later unconditional successful refresh. Existing same-URL 304 and conditional header tests remain unchanged.

Local command:

```bash
./gradlew :app:testDebugUnitTest --tests 'com.halo.app.data.everyday.CollectorIntegrationTest'
```

The real-device full #63 collector/worker, Local zero-network, RSS reachability, content provenance and UI/launcher-widget acceptance remain separate. Do not close issue #63 on this source-only checkpoint.
