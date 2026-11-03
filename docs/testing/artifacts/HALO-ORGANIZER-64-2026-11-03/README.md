# Issue 64 coding-batch freeze

Production and regression source: `74f2e62d1374f1392f84e5d0a31f35c8e8b7dc27`.
Initial batch: `c894b1961`; all seven source-review corrections: `74f2e62d1`.
[source-freeze.json](source-freeze.json) records the exact Git tree and 4,429 selected build-text input hashes. The Git tree also binds tracked binary/assets content. The preserved pre-existing executable mode change on `gradlew` is outside the source commits.

Source-only checks: clean diff; generated resource catalog matched 4,922 keys in each of EN/RU/LV/PL. No compiler, tests, build, APK, device, speech or provider acceptance ran for this batch. The source review/correction evidence is [ISSUE-64-SOURCE-REVIEW.md](../../ISSUE-64-SOURCE-REVIEW.md). Review of the initial batch was independent; the correction pass was authored and inspected by the implementer.

## Later gate, after stable power

Use one worker, no parallel execution and the installed local JVM/toolchain configuration. Complete one focused batch before an APK build; preserve logs and terminal exit status. A source hash or interrupted log is not a passing result.

- Language module: `NativeCommandParserTest`, including existing #63 clock/reminder cases and explicit Organizer vs memory routing.
- Runtime cognition: `OrganizerRoutineStartTest`, `PersonalConfigurationPolicyTest` and affected existing native/disclosure policy regressions.
- App: `OrganizerCommitmentPersistenceTest`, `OrganizerOperationsTest`, `OrganizerResearchTest`, `OrganizerBriefCompositionTest`, `OrganizerOperationsUiTest`, `EverydayArchiveTest`, `ConfigurationSkillManifestParityTest`, `EverydayToolExecutorTest` and affected existing Organizer/collector/brief/permission regressions from #63.
- Then build the APK once, record its source and SHA-256, and perform native scheduling/notification/render checks.

Actual typed device, STT-final voice and permitted model/provider workflows remain distinct required evidence in [HALO-ORGANIZER-64-OPERABILITY.md](../../HALO-ORGANIZER-64-OPERABILITY.md). The inherited #63 gate also remains unverified after power-interrupted runs. No issue has been closed and no branch has been pushed or merged.
