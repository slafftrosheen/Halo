# #29: [Roadmap][Halo Ink 2/3] Ink as cognitive context — region selection, voice + drawing and Study annotations

Original: https://github.com/slafftrosheen/Halo/issues/29 · updated 2026-10-24T02:09:18Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I29-P01: Exact source

Select two visibly distinct drawn regions and one annotated PDF page. Ask about each, save a bookmark, reopen/rotate/zoom, move the source, then edit/delete/relink it and retry the old selection.

**Pass evidence:** Every answer/bookmark references the exact object/page/strokes/revision; stale or deleted sources require reselection; a different selection cannot be substituted.

Status: NOT_TESTED. Evidence: pending.

### I29-P02: Real mixed voice

Select a region physically, speak an instruction through the actual STT-final route, then change selection during a delayed request and repeat with an ambiguous referent.

**Pass evidence:** One authorized interaction binds speech and exact selected region; changed or ambiguous references clarify; no action or unrelated source is inferred from a pen gesture.

Status: NOT_TESTED. Evidence: pending.

### I29-P03: Privacy/failure

Use Private/offline and unavailable OCR/vision, then deny provider consent; repeat with a permitted direct user-owned provider and cancel while cropping/transcribing.

**Pass evidence:** Local markup/selection still works; no unapproved content transfer; crop is bounded and source-scoped; failures/cancellation release resources and explain the actual limitation.

Status: NOT_TESTED. Evidence: pending.

### I29-P04: Study/localization

Run question → learner attempt → hint → feedback → revision → save/resume from ink, image/PDF and a voice memo. Inspect source-linked transcription and EN/LV/RU/PL limitations at phone/tablet sizes.

**Pass evidence:** Guidance remains distinct from giving the answer; context/provenance and privacy decision remain inspectable; unsupported language/recognizer is not advertised as supported. Record latency/memory.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I29-R01** (original line 9, Core user journeys): 1. Draw a diagram in Canvas, circle one part and ask: “Halo, explain this part.”
- [ ] **I29-R02** (original line 10, Core user journeys): 2. Annotate imported PDF pages or study worksheets, ask a question tied to the selected strokes/region, and get an answer anchored to the correct source/page.
- [ ] **I29-R03** (original line 11, Core user journeys): 3. Write a calculation or mark a student's answer; request **guided** Study feedback rather than automatically replacing the student's work.
- [ ] **I29-R04** (original line 12, Core user journeys): 4. Capture/annotate material on an Android tablet while a headset or glasses supplies the spoken instruction.
- [ ] **I29-R05** (original line 13, Core user journeys): 5. Keep strokes, source references and attached questions editable and retrievable in Notebook; no hidden one-off screenshot.
- [ ] **I29-R06** (original line 16, Scope): - [ ] Implement a portable **InkSelectionContext** (stable workspace object / page identity; bounding region in document coordinates; selected stroke IDs; revision/version; origin and timestamps).
- [ ] **I29-R07** (original line 17, Scope): - [ ] Provide box/lasso or other accessible region selection, clear hover/selection affordance and an explicit **Ask about selection / Add to working set / Study this** UI entry point.
- [ ] **I29-R08** (original line 18, Scope): - [ ] Bind ink context to existing Canvas/Notebook source objects, PDF page geometry and annotations; preserve selection fidelity under zoom, rotation, edits and page changes.
- [ ] **I29-R09** (original line 19, Scope): - [ ] Extend the cognitive context/observation contract to include referenced region metadata and an optional privacy-controlled cropped render, with deterministic fallback when vision/OCR is unavailable.
- [ ] **I29-R10** (original line 20, Scope): - [ ] Support mixed voice + pen turns: strokes/selection establish referent; the speech transcript describes the instruction. Handle ambiguous/outdated selections with clarification, not inferred actions.
- [ ] **I29-R11** (original line 21, Scope): - [ ] Provide intent-specific flows in Study (question → attempt → hint → feedback → revision), keeping educational guidance separate from generating the answer outright.
- [ ] **I29-R12** (original line 22, Scope): - [ ] Make selection provenance, local/remote processing decision, model availability, action approvals and receipts inspectable; do not send handwriting/images to cloud providers without policy approval.
- [ ] **I29-R13** (original line 23, Scope): - [ ] Provide EN/LV/RU UI copy and appropriate handwriting/OCR limitations; no assumption that every language/script is supported by one recognizer.
- [ ] **I29-R14** (original line 24, Scope): - [ ] Ensure deterministic selection, attachments, context bookmarks and local markup work **without** AI.
- [ ] **I29-R15** (original line 27, Acceptance criteria): - User can select a drawn region and ask about **that exact selection**; the answer is linked to source object/page/ink revision rather than an untracked screenshot.
- [ ] **I29-R16** (original line 28, Acceptance criteria): - Selected strokes/page anchors remain valid through save/reopen, orientation changes and basic edits; stale/deleted references are explicitly rejected or repaired with user involvement.
- [ ] **I29-R17** (original line 29, Acceptance criteria): - With an unavailable provider or denied cloud consent, strokes and selections remain locally usable, and AI-only operations report a truthful limitation.
- [ ] **I29-R18** (original line 30, Acceptance criteria): - Mixed voice-and-ink referents are resolved from the same interaction session; conflicting or ambiguous referents require clarification.
- [ ] **I29-R19** (original line 31, Acceptance criteria): - Tests cover selection geometry, stable IDs, source revisions, region cropping privacy, Study guided flow, transcription attachment and provider failure.
- [ ] **I29-R20** (original line 32, Acceptance criteria): - Performance and memory stay bounded on a phone; tablet layout is practical.
