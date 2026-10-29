# Unattended verification continuation — 2026-10-29

Status: **IN_PROGRESS / KEEP_OPEN**. The owner requested every check requiring no human participation. Voice remains deferred. This extends [the earlier non-voice evidence](ISSUE-CLOSURE-NONVOICE-2026-10-29.md), retaining its exact source/artifact boundaries.

Started on clean main `602fbeb5331aecad8800c56156499ffb19378348`, matching fetched origin. Device: authorized Redmi 23124RA7EO, Android 15. No speech recording, credentials, purchases, model downloads, parental-policy changes or unsafe production-journal tests. No hosted CI.

## Host and static gates

| Gate | Actual result and boundary |
| --- | --- |
| Fresh full baseline | **3,602 PASS**, 747 classes / 25 modules, no failures/errors/skips; final exit 0, 22m 6s. `haloVerify`, host-ink and ElevenLabs unit tasks executed freshly. Source 602fbeb, before subsequent UI changes. [Counts](issue62/2026-10-29/unattended/full-host-gate.json). |
| Localized UI delta v2 | **232 PASS / 58 classes**, final success 5m 38s. Predates later compact/performance changes. [Result](issue62/2026-10-29/unattended/grouped-ui-recovery-v2-result.json). |
| Python | **31 PASS** across four discovered test files, including 12 asset-audit mutation regressions. 4,178 centralized UI entries and 5,081 Polish strings checked. Static scanning is not complete runtime visual acceptance. |
| Integrated v4 | App compilation failed on unsupported ChecksPermission annotation; no app tests/new APK qualification. Removed only the annotation, retaining actual checks/revocation recovery. |
| Integrated v5 | **3,615 PASS / 2 FAIL**, 3,617 tests, 747 classes / 25 modules, no errors/skips; final exit 1, 26m 36s. **haloStatic PASS**. [Result](issue62/2026-10-29/unattended/integrated-v5-result.json). |
| Recovery v6 | **41 PASS / 1 FAIL**, 42 tests; final exit 1, 10m 38s. Motion frame tests, all OpenRouter tests and OpenRouter lint pass; Library SET/readback race remains red. [Result](issue62/2026-10-29/unattended/recovery-v6-result.json). |
| Recovery v7 | **38 PASS / 1 FAIL**, 39 affected tests; final exit 1, 9m 57s. Both APKs assembled. All nine Library SearchOrder tests pass; existing mixed-trash preparation fixture fails before a write. [Result](issue62/2026-10-29/unattended/recovery-v7-result.json). |
| Recovery v8 | DIAGNOSTIC ONLY: 38/39 affected tests passed; second CONTEXT preparation raced the asynchronous Working Set projection. App lint still running. A review source edit overlapped execution, so the initial source witness is not a coherent final gate; rerun required after edits freeze. Saved v7 APKs are immutable and unaffected. |

Baseline assembled app/test hashes: `0fa2c918534e982d129d36bb7466bb713b68219e370c3ea83e11eccf17e981ab` / `4372925434286f1a653c1990e1e1b451850a263cc92c3a4d1ebc205aacd94131`. These were not installed for the early native runs. Each later gate retains frozen source/build/config hashes; repeated test executions are not distinct coverage totals.

The asset audit formerly rejected 140 icons by XML byte comparison. Its strict structural vector signature now shares the existing SVG converter; all 140 outputs matched the old importer exactly. Mutation tests retain rejection of geometry, paint, alpha, unknown attributes, bitmap and Lottie changes. No artwork was regenerated; this is source fidelity, not aesthetic approval.

The original static run had 40 errors (host 3/app 37). [Triage](issue62/2026-10-29/unattended/haloStatic-lint-triage.json) distinguishes 16 actual issues, 22 analyzer limitations and two owning-library permission declarations already present in the app. Corrections cover supported audio APIs, observable Compose state, locale/resources, consent translations and actual permission/API guards. No dispatch authority, parental consent or new app-level permission was added. Narrow compatibility suppressions remain documented.

## Native fixtures and actual Activity checks

| Installed pair / run | Observed result |
| --- | --- |
| app 1858152f / test 6931becf, functional source 2b1652182 (602fbeb docs only) | Background setup/privacy **3/3 PASS**; background contracts/import **22/23 PASS, widget FAIL**; foreground **23/28 PASS, five FAIL**. Isolated owners/loopback TLS/import smoke are not real assistant Private/cloud E2E or pack qualification. |
| v2 app 49acb231 / test 36dc83f5 | **10/12 PASS**, Mini Send offscreen and 240×96 widget clipping FAIL. Actual Mini Back/full-Activity transfer passed. [Result](issue62/2026-10-29/unattended/recovery-v2-native-result.json). |
| v3 app 3798101f / test 758df338 | **12/12 native PASS**. Host **29/30 PASS**, Russian Talk wrapping still red at 240×96. [Native result](issue62/2026-10-29/unattended/recovery-v3-native-result.json). |

The corrected widget fixture uses effective ancestor visibility, mandatory Orb/action bounds and text clipping; it exposed a genuine production defect. Current source fixes narrow widget content allocation and RemoteViews.reapply restoration. Mini compact layout pins Stop/Review/composer/Orb, and its native fixture uses the actual launcher across embedded language/font/short-height combinations. Production callback, approval and capture ownership remain unchanged. Current final native rerun is pending.

Saved immutable v6 app/test SHA-256: `a57d695b3cfad5fc67aa7650fcf9482ab9b84c268326628722fdd897cc1d16bd` / `193a07cd99781ffdb324b5a5f8014b3179fa56d9a9a489c761a66ecfd46a34e2`.

Saved immutable v7 app/test SHA-256: `c0701068e442953e5029b456dea70f04a30761ea73a81c3bde744a2f8ca6474d` / `193a07cd99781ffdb324b5a5f8014b3179fa56d9a9a489c761a66ecfd46a34e2`. v7 changes the Library receipt guard; v8 changes only host test readiness.

## Production configuration matrix

Actual MainActivity test: eight routes × 360/412dp × 100/200% font × EN/RU/LV/PL × light/dark = **256 subcases in one JUnit test**. Scope: real route projection, native footer targets, one Orb, bounds/nonoverlap and no capture. It is not every page action or TalkBack speech.

First attempt stopped with zero completed cases; OS restoration verified. v2 completed **32 PASS / 224 FAIL**, settings restored. [Result](issue62/2026-10-29/unattended/matrix-v2-result.json). Subsequent private native XML confirmed selected root enabled/selected at 48dp but lacking ACTION_CLICK under Role.Tab. Current source uses Button semantics, retains selection and adds a callback regression. Final physical matrix remains pending.

The original draft guard read an obsolete absent preference and provides **no preservation evidence**. The current runner reads canonical encrypted draft storage after owner readiness. Independently, actual New conversation → History navigation reopened the original user conversation with its exact draft; no user draft was cleared or sent. Screenshots/XML remain private until reviewed.

## Library receipt and fixture corrections

v6 diagnosed real SET behavior: the requested query was applied, then its own asynchronous render update invalidated a strict final snapshot read. v7 uses a lease bound to the applied owner revision for SET acknowledgement, while retaining strict initial admission and READ snapshots, permission checks, resumed-entry identity, competing-edit rejection and no replay. Pending readback stays explicitly unsettled and does not claim old rows are fresh results. All nine SearchOrder regressions passed in v7.

The unmodified mixed-trash test then failed during preparation. Its ID-only readiness accepted an active chat row before append updated its metadata. The host fixture now waits within ten seconds for rendered requested rows to equal their committed canonical WorkspaceObjects. No preparation/apply/write retries or production guard changes were added. v8 must confirm the correction.

## Actual provider journeys — positive results remain unverified

A separate ADULT/GUIDED/SOURCE_ONLY cloud Study session `20fbda3b-60c9-40a1-bf69-8e2e12951554` references only own QA note `1e543729-b284-4d79-8226-1f20f4ccc618`, revision 1. Persisted source identity, explicit cloud choice and draft were verified. Two explicit attempts on free `nvidia/nemotron-3.5-lightning:free` returned localized empty-response failure. No teaching, graded practice, source-grounded follow-ups or saved progress success is inferred.

An actual typed request in a separate QA chat failed NETWORK_ERROR before tool admission or note creation; draft retained. Effective AGENTIC target was free `nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free`. Loose legacy role preferences are not authoritative because the active mode profile shadows them. USER observations' legacy AndroidAssist provenance label does not prove OS screen capture, and offered model capabilities do not prove attached images.

New tested provider-boundary diagnostics record normalized finish/model identifiers, numeric token counts, failure type, bounded HTTP status and exception class; never prompts, source content, hidden reasoning, credentials or exception bodies. No automatic retries, paid fallback, reasoning-mode, token-budget, sharing or routing policy changes. Actual retries on the diagnostic build are pending. Reasoning exhausting a shared completion budget is only a hypothesis until measured.

## Sustained idle observation and remaining resource evidence

v3 reproduced foreground idle CPU failure: approximately six minutes / 12 samples averaged **109.12% of one core**. Thermal status 0; background CPU fell to 0%. An attempted method trace was empty and establishes no attribution.

Current source stops the full-screen idle guide aura's permanent frame clock while retaining static appearance and active feedback. The initial draw-counter fixture was invalid before its first draw. Replacement rendered-pixel tests passed in v6: idle frames stay identical while active feedback changes. This does not itself prove CPU reduction.

The first v6 physical attempt stopped with its host session after three samples and is incomplete. The fresh v6 observation captured **29 samples over 870.343 seconds (14.5 minutes)**, then an external call took foreground and the guard stopped without UI interaction. Including startup, weighted CPU averaged **5.90% of one core**; intervals after the first two samples averaged **1.39%**. Thermal status stayed 0; PSS went from 309,711 to 274,231 KiB. [Summary](issue62/2026-10-29/unattended/idle-v6-summary.json).

Scope: debug foreground Home, landscape, guide light on, current dark STILL background, charging. This is **not a completed 15-minute run**, animated-background, release battery-energy or leak qualification. Layout/initial state were not controlled for a causal A/B percentage claim. Final gfx counters include interruption/background transition and show 33/142 missed deadlines; these are not a smoothness PASS or clean sustained-animation trace. Animated backgrounds, reduced-motion/power/lifecycle and focused interactive frame captures remain separate requirements.

## Remaining unattended acceptance

Pending: final compact-widget/Mini/native union and reviewed production matrix; nested screen/IME/reduced-motion/theme-preview checks; actual typed multi-step workflows; grounded adult Study generation/practice/save/resume; memory review/correction/forget; four real Screen card types including routine and exact Study/Workspace continuation; correlated Private/no-outbound real journeys; active and animated-background performance. Fixture/import coverage does not establish those journeys.

A reproduced Russian Home shows a fixed English trusted-native DISPATCHED receipt. Host localization must use exact native run/interaction/typed outcome identity, preserving arbitrary provider/user content and special workflow/settings/screen-control messages. Its correction is a separate source batch; no complete native-response localization claim.

Speech/listening, physical stylus/wearable, owner-dependent parental and Play purchase gates retain their prerequisites. No issue is closed by this ledger, compilation, unit counts or an interrupted observation. Temporary keep-awake must be restored to the recorded original value before finishing device work.
