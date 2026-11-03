# Issue 63 implementation audit and completion batch

Status: implementation and source review finished; builds and test execution deliberately deferred by the user. No runtime pass is claimed.

**Goal:** Close concrete integrity gaps in the existing A–H implementation of issue #63 without duplicating its domain owners.

**Architecture:** Keep the current collector, organizer, daily briefing, widget and canonical runtime owners. Change shared domain boundaries so touch, voice, Screen, widgets and workers receive the same corrections.

**Tech Stack:** Kotlin, java.time, Room, OkHttp, existing JUnit/Robolectric suites.

**Spec:** [issue snapshot](../../everyday/ISSUE-63-SOURCE.md), [existing implementation handoff](../../everyday/ISSUE-63-CODE-HANDOFF.md).

## Constraints and review focus

- Preserve exactly Local/Power and all child/disclosure/version fences.
- No builds, Gradle tasks or test runs during this coding phase. Author regressions first, then implementations; execution and APK verification are a later phase at the user's direction.
- Keep current checkout/caches on `codex/issue-63-completion`; preserve the pre-existing executable-mode change on `gradlew`.
- Do not infer device, provider, launcher, accessibility or spoken acceptance from source changes.
- Review unsolicited/stripped validators, preserved cache timestamps, empty-to-nonempty briefing comparisons, DST gaps/overlaps, unsupported calendar overrides and explicit provenance for every Everyday command.

## Tasks

- [x] Collector: reject 304 unless the final request carried a usable validator; preserve cached payload and freshness on failure. Add HTTP and Room/coordinator regressions, including subsequent recovery and zero Local transport.
- [x] Briefing: mark the directly read attention owner as observed even when empty, so additions/removals compare truthfully. Add empty/nonempty and child filtering regressions.
- [x] Calendar interchange: parse bounded nominal day/week durations before elapsed hours/minutes/seconds; reject unsupported override recurrence and duplicate-master ambiguity. Add DST, invalid-duration and whole-series-withholding cases in `OrganizerIcsTest`.
- [x] Runtime policy tests: replace obsolete aggregate-count assumptions with explicit Everyday membership and explicit per-tool provenance expectations; retain denied/local/allowed gates.
- [x] Source review and handoff: inspect the complete diff, update the audit/evidence document, commit only this batch. List the exact deferred test/build commands and unresolved device acceptance.

## Later validation commands (not executed during implementation)

```bash
./gradlew :runtime:cognition:test --tests '*PersonalConfigurationPolicyTest' --console=plain
./gradlew :app:testDebugUnitTest --tests '*CollectorIntegrationTest' --tests '*EverydayBriefTest' --tests '*BriefPersistenceTest' --tests '*OrganizerIcsTest' --tests '*OrganizerDeliveryTimingTest' --console=plain
./gradlew haloAppBuild --console=plain
```

Run the existing broader policy, widget, typed-tool and migration suites after focused checks succeed. The previous local core run reported 744/746 passing with two stale policy-test assertions; the previous APK build passed. Neither result verifies this new batch.

## Source-review record

Independent read-only review found two important duration representation gaps: floating nominal day/week durations cannot survive device-zone changes, and later-overlap endpoint instants cannot survive storage as local time. Both now produce explicit manual-review issues, with source regressions for representable alternatives. Test execution is NOT_RUN by the user instruction; the checked task boxes record authored implementation and source review, not passing test/build acceptance. No deferred minor finding or policy relaxation was introduced.
