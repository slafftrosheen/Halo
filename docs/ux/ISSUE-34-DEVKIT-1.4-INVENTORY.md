# Issue 34 / DevKit 1.4 implementation inventory

Source inspection at main `97c82273d1fe274a05f824d3b256361b4da0b506`, 2026-10-18. Existing local Settings/Mini/widget edits are preserved. Current authority: `halo-devkit-v1.4/IMPLEMENTATION.md`, `learning/LEARNING_HANDOFF.md`, `learning/spec/halo_learning_contract.json`, `voice/VOICE_READ_HANDOFF.md`, `voice/spec/halo_voice_read_contract.json`. Screen IDs below identify target contracts, not already implemented screens.

| Target | Actual source and data flow before this batch | Gaps |
|---|---|---|
| K00 | `KidsModeScreen.kt` HOME → `KidsHomeChoices.kt`: Ask, Learn, Create. Header uses real Home session signal, Stop calls Home cancel + Study cancel/read stop. | Three entries rather than Explore/Create; no durable project resume. KidsPreferences is presentation-only; no parent PIN security exists here. |
| K10 / K11 | LEARN → `KidsLearningStart`: typed/local voice topic, Maths/Reading chips; `StudyHomeViewModel.start` → StudyUseCases; two child-level active sessions resume. | No prior-knowledge question; no homework/file/Notebook picker in Kids. |
| K12 / H22 | `KidsLesson` uses the same `StudyViewModel` and StudyUseCases as adults: latest tutor turn, real reader controls, draft, submitAttempt, askQuestion, advance, finish. Attempts persisted before generation; request identity drops cancelled replies. | Active task and actions live in one scrolling card; no pinned learning dock, branch map or hint ladder. |
| K13 | No topic-map screen or stored concept graph found. | Must persist explicit branch state; no inference of mastery from chat. |
| K14 / H23 | Completed session label; bloom follows lifecycle completion. | Completion is not evidence of independent reasoning; no child reflection/checkpoint screen. |
| H20 / H21 | Adult `StudyHomeScreen` exposes `ExtractionImportButton`; `ExtractionReviewController` imports files/photos, runs local extraction, allows ordered text edits and Save/Ask/Study. `InboxCaptureDialog` has real camera reception without automatic inference. | No Kids camera-to-exercise route, exercise boxes or per-exercise confidence. Existing extraction explicitly warns Cyrillic OCR unsupported. Never invent worksheet content or page lock. |
| C30 / C32 | `KidsCreationPanel`: idea + Story/Character chips; `home.submitText` canonical assistant routing; renders changed response after busy ends, rejection/error visible. | One-shot result; no Keep/Change/Discard, durable project, Canvas handoff, revision history. Image generation must not be advertised without capability and parent consent. |
| C31 / C33 / C34 | No dedicated shaping/review/project screen in existing Kids source. NotebookUseCases provides real create/save/get note/draft operations. Canvas has independent draft semantics. | Reuse these owners; do not treat displayed generated text as saved child work. |
| S40 / S41 | `StudyHomeScreen`: continue sessions, due cards, Goal→Material→Style→Options wizard; Notebook/files, extraction review; `StudyHomeViewModel` persists via StudyUseCases. | Guided exploration and diagnostic phase missing; voice coverage partial. |
| S42 | `StudyScreen` + `StudyViewModel`: tutor turns, question/attempt, staged help, solution confirmation, citations, visuals/PDF pages, reader, practice generation, notes save. `StudyTutorGateway` uses AiProviderRouter capabilities and source policies. | Four presentation phases infer from transcript, no explicit seven-stage shared state. Topic-only remote allowance bypasses sharing toggle before this batch. |
| S43 | Existing details tabs: material, languages, setup, schedule, progress; edits use canonical session methods. | Preserve settings and provider-independent persistence. |
| S44 | Real card check/reveal/rate; review events drive measured mastery. Notes saving uses NotebookUseCases. | Do not conflate generic tutor response with assessment; checked reasoning is distinct from lifecycle completion. |
| K15–17 / S45–47 | Kids read-along and Study reader exist; local dictation previews in HaloVoiceTextActions. | Story pagination, pre-reader two-tap mode, aligned reading practice, explain-back gap assessment and global media transport are not implemented. |
| V01–08 | `HaloVoiceTextActions` owns local selected STT capture, preview, canonical TtsRequested reading, per-passage cloud consent, lifecycle cancellation. | Shared typed HaloTextField absent; capture limited to 15 seconds; read transport currently component scoped. No fabricated word timing. |

## Asset inventory

Existing presence: `orb/lottie`, `kids/lottie`, static variants; shared `HaloFx.kt` supplies trace, ripple, bloom, draft border and staged tool FX. v1.4 adds six learning effects in `learning/lottie` and nine voice effects in `voice/lottie`, with static SVG fallbacks. Reference Compose files describe contracts and must be adapted, not copied as an independent animation system. Only real runtime events can trigger listening/thinking/speaking, page recognition or success. Reduce-motion uses existing shared policy.

## Delivery constraints

Issue 34 asks for coding only: no tests, builds, CI or deployments during its implementation. Earlier UI validation predates adopting that instruction; its Russian enlarged Appearance assertion failed and remains unresolved. The phone disconnected before latest UI refinements were installed; original Kids mode was on and still needs restoring. This inventory is source evidence, not end-to-end provider/device acceptance. Issue stays open until all acceptance journeys and explicit limitations are accounted for.
