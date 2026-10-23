# Host UI localization — #36 / #39 / #43

Base `bca90bd9ae69e7ced5f98bcad8b2b309e25c2710`. Completed all production and regression sources before validation. Read-only review found no remaining actionable findings.

Startup recovery, native picker instructions, audio/attention notification labels and structured-result host accessibility/status copy now use the existing selected-language resource owners. Shared lookup preserves no-argument literal percentages. Source/model content, callbacks, IDs, permission boundaries and runtime lifecycle remain unchanged. Measured text/tool UI and content-free reports explicitly identify English fixtures; other languages require separate qualification.

Added17 EN/RU/LV/PL catalogue entries. Coverage checker now guards nativeUi keys and raw host Text labels. Existing four-language values are unchanged. Full4795 Polish-resource coverage, generator freshness,15 Python regressions and exact old-value parity PASS before the Android gate.

First focused gate PASS in7m49s:12 desktop cases across AppLanguageTest(5), AppLanguageUiTest(4), StudyProgressLocalizationTest(3), zero failures/errors/skips. Both matching APKs assembled. [Raw gate/XML/parity](artifacts/HALO-HOST-UI-036-2026-10-24). Phone reconnected; installation and isolated native checks are running, results pending. This does not establish real speech, TalkBack, live notification behavior, per-language model quality, or full original issue acceptance.

