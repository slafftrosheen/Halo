# #54 Study learner controls

Base `12fb212a737471d0c2292c43a6f4d38f67a2f616`, fetched main0/0 and clean before edits. **Source, desktop gate and isolated native owner fixture verified. Keep #54 open.** No CI/Actions.

## Feature and canonical owners

Study UI already saves learner attempts, preferences, completion, practice cards and recall reviews through StudyUseCases/StudyRepository. Assistant integration previously only started/described sessions. New WorkspaceStudyActions exposes13 operations through the existing WorkspaceToolExecutor and ToolRegistry, using the same graph-owned StudyUseCases/StudyRepository/Room database. No new tutor, provider request, learning store, execution, policy or approval owner.

| Tool | Meaningful saved operation |
|---|---|
| halo_study_sessions | Bounded saved-session listing |
| halo_study_state | Exact session identity, recent attempts, paged card references |
| halo_study_select_exercise | Existing exercise-selection use case |
| halo_study_submit_attempt | Literal learner attempt and durable receipt; not graded |
| halo_study_set_preferences | Explicit learner languages, goal, level/support/knowledge preferences |
| halo_study_complete | Learner-chosen completion; no mastery claim |
| halo_study_delete | Approved deletion of session/dependent learning data; sources retained |
| halo_study_card_get | Exact saved card/content version and provenance |
| halo_study_card_create | Literal learner question/answer draft, no invented grounding |
| halo_study_card_edit | Existing edit owner, retaining provenance, acceptance and review history |
| halo_study_card_accept | Explicit inclusion/exclusion from review |
| halo_study_card_review | Learner's explicit AGAIN/ALMOST/KNOWN rating, existing scheduler/event owner |
| halo_study_card_delete | Approved removal of exact card/review history |

Actual descriptors carry ToolOperabilityv1, closed typed arguments, bounded JSON results/receipts, source-version fields and workspace.read/write capabilities. Runtime and bundled Study skill tool sets/per-tool risks, operational guidance, Home Study catalogue and Workspace result provenance are wired. New presentation uses existing Study/Save/Delete labels; no resource/catalogue translation changed. Saved text is untrusted content. Metadata neither authorizes a write nor proves actual voice recognition.

## Typed operation contract

Session/card identities hash canonical structured saved fields, including provenance, current exercise, lifecycle and schedule. Exact identities and card-to-session membership are checked inside the same Room transaction as the canonical use case. Bounded SQL pages return20 sessions/cards with lookahead; state returns at most5 recent attempts, explicitly truncating previews. A full card read returns the exact content identity plus bounded question/answer and a truncation flag. Results are bounded to24000characters.

Review found a stale deletion race before the first gate: canonical attempts/feedback/cards/reviews did not change the parent session row. All canonical child writes now atomically advance its existing update marker; setup/progress/preferences preserve monotonicity with MAX(now,previous+1), including a fixed/backward clock. No new column, schema migration, epoch store or replacement owner. Thus a pending deletion cannot remove new learner data after a canonical UI edit without re-reading the session. Write failure rolls the child insert and invalidation back together. Card receipts return the refreshed parent identity.

Exact duplicate attempt request identity/payload reads the persisted receipt without another write, even after later session edits. Changed payload under that identity is refused. A new attempt requires the current active exercise. Other writes are NEVER_REPLAY; uncertain keyless card creation is read back before retry. Exact card/schedule versions prevent a stale review from adding a second review. Literal learner cards are marked edited so regeneration cannot silently replace their content. No inferred rating, verified-grade claim, automatic mastery, solution generation, source replacement or cloud opt-in.

Deletion remains DESTRUCTIVE/ALWAYS/NEVER_REPLAY through the existing authorizer, including an always-allow routine preference. Other writes preserve USER_POLICY and reads remain safely replayable. Existing capability, Kids, Private/Power and provider/permission boundaries remain. Cancellation before local deletion admission propagates. Once the local-history mutex is acquired, a bounded noncancellable local deletion/commit outcome handoff prevents caller cancellation from restoring history after SQLite commits. Room owns database rollback; local cleanup failures compensate already removed local keys. This is not cross-store OS-process-death atomicity. No arbitrary pen, hardware, source access or provider automation is invented.

Deletion also guards an exact local-history identity in addition to the Room session identity. The shared existing StudyLessonStore serializes lesson, journey and recovery writers with deletion; changed local data rejects an old approval. Cleanup failure restores existing local values and rolls back the database transaction. A persisted deletion marker rejects late callbacks, including a recreated store; late pending-turn cleanup is harmless. Original source material and unrelated sessions remain. Reader position/global preferences are not claimed erased.

Completion/deletion now stop only their own session's narration through the existing SpeechOutputStudyNarration controller. A stale session operation cannot cancel a newer lesson's publishing/playback. Explicit global Stop remains available through its existing path.

## Source review and regression scope

Production, desktop regressions and the native fixture were authored before the first gate. The initial read-only review found the stale-deletion race above; it was corrected with canonical mutations and regressions before building. A subsequent parity audit found the separate UI lesson/recovery cleanup; it now belongs to the shared use case. Follow-up review found and resolved a commit-boundary cancellation race before the final gate. Final read-only review has no remaining actionable findings.

Twenty-two new app cases cover actual registration/contracts/risks, bounded pagination, exact/idempotent literal attempts, exercise changes, omitted preferences, fixed-clock identity, atomic failure rollback, all seven canonical child-write categories invalidating old deletion, protected learner cards, provenance/history preservation, foreign sessions/cards, concurrent reviews, invalid/self-reported ratings, deletion/cascades/source retention, completion, malformed arguments and untrusted literal content. Existing real playback-owner regression now checks another session cannot stop current narration. Runtime tests cover one Study owner and Workspace provenance/risk per operation; bundled/runtime metadata parity and registered-feature operability are asserted.

## Three functional workflows

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable real-user readback |
|---|---|---|---|---|---|
| Choose exact exercise → save learner attempt → inspect | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Create/edit/accept card → record learner recall → inspect schedule | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Complete session / approve deletion while preserving original material | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

The isolated native fixture uses synthetic data, a UUID-named Room database and the registered policy/semantic owners; it is not an actual keyboard/STT-final/Power journey. Database close/reopen is not OS process death. Tutor generation, source-changing/re-grounding, read-aloud journeys and full cross-tool/voice/provider acceptance remain separate. Do not close #54 on offline controls or synthetic receipt evidence.

## Verification results

Initial focused gate stopped after 9m4s on one fixture-source Int/Long mismatch; production compilation and9 runtime cases passed. Fixed the fixture type, completed the separate local-cleanup parity refinement and its regressions, then ran the final cohesive gate. Final gate PASS12m34s,513 tasks/30 executed:77 app cases across7 classes and9 runtime cases across3 classes, zero failures/errors/skips; both APKs assembled. All22 captured compiled source identities match. Native run7171407b05d447108ddc71a7d9e1ec5c PASS1/1 in0.890s on connected f01fe673/23124RA7EO/API35. The actual registered13-tool owners saved one literal attempt and one learner recall event, completed the session, refused a stale review, then reopened the same real Room database and read back the exact saved IDs/card/lifecycle. Separate local recovery remained intact. UUID fixture database/preferences were cleaned; no global learner data, mic or provider request. MainActivity resumed. This proves isolated semantic owners, not actual keyboard/STT-final/Power or OS process death.



Installed app SHA256 e534b753b4699c7e15ef0cdac8c38a3f858ea1d86dbf44979092c4d4857545e9 (288516147bytes), test SHA256 fb24f5390d60d5917441975e3efd6834349c0fd9c8988a06896cd5393e45ec8e (2122155bytes); both installed readbacks match. [Bounded raw evidence and source identities](artifacts/STUDY-LEARNER-CONTROLS-054-2026-10-24). No screenshot/render review is claimed for unchanged presentation labels. Actual three-workflow typed/spoken and permitted Power acceptance remains NOT_RUN; #54 remains OPEN.
