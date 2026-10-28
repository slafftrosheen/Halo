# Issue 61 coding checkpoint — 2026-10-26

Issue: https://github.com/slafftrosheen/Halo/issues/61

Source changes only, as requested. Build, unit tests, device tests, spoken journeys, provider calls,
TalkBack, font/layout checks and restart qualification are **NOT_RUN**. This document does not close
issue 61 or establish its prerequisite issue 55 acceptance.

## Existing owners and entry points retained

- Adult: `StudyHomeScreen` / `StudyHomeViewModel` starts or resumes `StudyScreen` /
  `StudyViewModel`. `StudyLearningSetup` owns learner level/support/source policy choices;
  `StudyExplanationOptions` owns explicit detail choice. `LearningJourneyPanel` owns the optional
  starting description, topic exploration and reflection; practice remains in the same Study screen.
- Kids: `KidsModeScreen` presents Learn and Create. Learn uses the same Study home/session owners
  with PRIMARY/EARLY_PRIMARY levels and larger controls. `KidsLearningSources` uses the existing
  camera/import/Workspace owners and the grown-up gate; captures are received with
  `publishForInference = false`. Create retains `KidsProjectViewModel` and existing Canvas handoff.
- Persistence: canonical Study Room sessions, attempts, feedback, cards and schedules remain in
  `StudyRepository` / `StudyUseCases`. `StudyLessonStore` remains the bounded local journey,
  reply-history and interrupted-draft owner. No second session database or scheduler was added.
- Speech and tools: existing narration adapter and STT/CognitiveSession tool dispatch remain the
  owners. `ActiveStudyToolsOwner` is still attached only to the actual native lesson;
  `StudySessionTools` forwards approved, version-guarded operations to that same ViewModel.

## Source work by requirement

| Package | Existing implementation and this batch | Remaining boundary |
| --- | --- | --- |
| A — inventory/simplify | Entry points and owners mapped above. No duplicate lesson/quiz/editor introduced. | No claim of a complete consumer UX simplification or hidden-controls audit across every entry point. |
| B — probe/depth | Existing optional probe, Skip and persisted detail/language choices retained. The saved learner baseline now travels with subsequent tutor turns after restore. ASK does not repeat the initial probe when an explicit baseline exists. A successfully answered baseline clears only that exact draft, preserving any newer input. Reflection text hydrates after asynchronous restore. | Model interpretation of a baseline is tentative, not a grade. No adaptive mastery or language capability is inferred. |
| C — grounded sources | Re-resolve exact source identities/access both before inference and before accepting tutor replies or practice cards. Missing, unavailable, deleted, excluded or changed sources reject. Check returned generation ID. Save exact source-version maps on replies; omit old-version or unknown legacy history from new prompts even after explicitly continuing with updated material. Summary includes source links, exact opaque versions, passage locators and each turn's own citations/uncertainty. | Resolution checks surround asynchronous work; they are not a distributed transaction with external providers. Exact image/Ink-region intake still depends on existing Context Bridge and source resolver capabilities. No new OCR or region resolver is invented. |
| D — voice continuity | Existing speech ownership retained. Add approved typed Study actions `knowledge_probe`, `explore_topic`, `reflect`, `reflect_feedback`, `suggest_topics` through the same existing scoped tool and CognitiveSession path. Current snapshot exposes journey stage, accepted hint count and baseline/reflection presence without returning their literal contents. Operability metadata retains the supported version 1 envelope with additive result fields and explicit action limitations. Existing stop/read-along/draft recovery retained. | Real spoken/STT, interruption, background/restart, audio route and provider-language qualification remains NOT_RUN. No new speech engine or parallel foreground voice session. |
| E — practice/agency | Replace the fabricated ordinal help history with actual accepted replies. Repeated hints explicitly advance one small step; Kids full-solution generation requires two accepted hints for the current exercise/material in the ViewModel and production gateway. Failed requests or stage navigation do not unlock solutions. Existing explicit solution request, reviewed draft cards, answer reveal, self-assessment and scheduling retained. | Generative compliance with small-step instructions requires real negative-case qualification; prompting is not proof that every provider obeys. Long bounded history may require asking for hints again. |
| F — artifacts | Persist one summary-note ID and acknowledged revision in the existing journey and a stable `study-summary:<sessionId>` ObjectProvenance receipt. Note creation/update and receipt write share the existing Room transaction. Retry and owner recreation recover the same artifact even if local journey-link persistence failed; initialization repairs that projection. Changed summaries use the canonical Notebook command owner with the exact saved revision. Changed/moved/deleted notes and existing recovery drafts refuse overwrite. Summary retains baseline, reflection, explored branches, accepted practice, citations and source links. | Crash/transaction qualification remains NOT_RUN. A user-edited summary requires manual Notebook reconciliation instead of automatic overwrite. No new export engine. |
| G — child/privacy | Reuse existing child remote-sharing, Workspace exclusions, Private/remote routing, verified vision, permissions, category grants and approved tool execution. New actions do not grant providers, permissions, external media or phone actions. History checks prevent silently resending an old source revision. | Provider/privacy-switch, refusal, network and child-media device scenarios remain NOT_RUN. |
| H — consumer UI | Keep existing adult detail controls, Kids Learn/Create tracks, larger targets, reduced-motion/learning feedback and localized UI. Fix restored reflection input hydration. Dedicated `StudyJourneyCopy` supplies new notices and exported headings/preferences in EN/RU/LV/PL; `resolveUiText` uses it for uncatalogued app-authored labels. Export language follows the explicit explanation-language preference, then the app locale; learner/source/model text remains untouched. Raw tutor-stage/knowledge-policy/depth enums are removed from summary prose. | No new visual redesign. 360/412dp, 200% fonts, TalkBack and linguistic/device acceptance remain NOT_RUN. |

## Follow-up qualification

Final source review also adds a canonical `StudyUseCases.permitsSource` callback, wired by the parent
to the existing `WorkspaceMediaAiPolicy.permitsObject(id, true)`. `sourcesStillCurrent` checks current
object access before and after source resolution. Late tutor/card acceptance, summary export and
Study reference-version checks therefore inherit current exclusion policy rather than relying on a
permission check made earlier inside the provider gateway. These changes remain unexecuted source.

Run the issue's adult, child, mixed-media and negative scenarios only when testing resumes. Include
exact app SHA, real source versions/regions, actual provider/model/language, reopened progress,
sanitized receipts and separate typed/spoken/device evidence. Specifically exercise source deletion,
permission exclusion and revision changes during a slow response; old-version history after Continue;
two graduated hints before Kids solution; mismatched generation IDs; repeated summary export;
Notebook edits/recovery conflicts; and failure to persist the summary link after a successful note save.

Deferred regression sources: `ContextBridgeSourceRegressionTest` covers anchored source readback,
stale-source non-broadening, summary owner recreation/idempotence and protection of user edits, plus
explicit per-object privacy precedence. `ContextSourceReceiptsTest` adds durable selected-range receipt
readback. These sources were written but **not executed**.

No build, test, ADB, download, commit, push or issue mutation was performed for this batch.
