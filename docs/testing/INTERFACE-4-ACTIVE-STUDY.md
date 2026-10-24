# Interface 4.0 active Study action bridge

Integrated test execution and exact device/artifact results: [verification ledger](INTERFACE-4-VERIFICATION.md). Sub-batch source notes below retain their original evidence boundaries.

`ActiveStudyToolsOwner` attaches only the exact RESUMED native `StudyViewModel`
provided by `StudyScreen(activeTools=...)`. Multiple views, missing views, loading
state and changed session/UI/source tokens reject. No attachment is restored and
no command is replayed on navigation, configuration change or process recreation.

`halo_study_current` reads the attached session, stage, busy/draft state, review
intensity, current source identities and up to30 readable source candidates with
their native Study versions. It also projects bounded real attempt/turn/practice
IDs, the latest actual tutor turn, and the latest native export/restart IDs. These
are personal Workspace data. The bridge never grants source access or remote
disclosure; central exposure and result provenance must register both tool IDs.

`halo_study_session_action` invokes existing native methods for review intensity,
source selection/refresh/rebase/restart, notes export, guided progression/help,
the learner's explicit solution request, literal attempt plus feedback, asking
the tutor, generating reviewable practice drafts, and stopping this lesson's
tutor/reader. It accepts a closed operation schema. Pending learner text is never
overwritten. Existing native session/tutor/source/execution-choice, Kids, privacy,
provider and spending policies stay authoritative; no generic Chat substitute,
second tutor, automatic grading, card acceptance or cloud approval is introduced.

Admission reads canonical session/source/candidate state on IO and rechecks the
same UI identity on Main before invoking a callback. Every dispatch consumes its
epoch. First persisted progression, attempt and source-selection/rebase/restart
writes compare the exact observed saved session inside `StudyRepository`'s Room
transaction; source operations also recheck exact native source versions there.
Native async tutor actions retain their existing current-request/session/source
checks. Notes export captures the reviewed native UI snapshot. Existing UI calls
retain default behavior when no semantic expectation was supplied.

An action receipt is **DISPATCHED** only. It never claims that a tutor responded,
an attempt was graded, practice drafts were committed, a note was created or a
lesson restarted. Read the actual active/stored session after dispatch. Busy,
notice, real result identities and existing stored Study operations provide
evidence; absent, failed or interrupted work must not be replayed. Export/restart
IDs are the latest actual native commits, not independent provider receipts.

The scoped bridge intentionally requires the active lesson. Existing
`WorkspaceStudyActions` remain available for safely versioned stored-session,
literal attempt, card, preference and completion operations outside that view.
Open an exact observed session through its existing navigation owner when needed.
No missing session/source IDs are invented.

Regression sources authored: `ActiveStudyToolsOwnerTest` covers missing/ambiguous
attachment, stale UI, pending learner draft preservation, exact saved-session
transaction rejection and actual native tutor dispatch/readback. `StudySessionToolsTest`
covers closed arguments, interaction/admission, literal learner content and
DISPATCHED-only truth. Source compilation/test execution belongs to the root
integrated gate: this sub-batch ran zero Gradle/build/install/provider requests.
Native typed/STT-final, permitted remote provider, parent/permission, background,
TalkBack and physical output acceptance remain NOT_RUN here.
