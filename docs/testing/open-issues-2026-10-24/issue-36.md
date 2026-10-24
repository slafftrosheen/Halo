# #36: HALO-INTENT-150: phone-qualify small local intent routing and end-to-end tool calls

Original: https://github.com/slafftrosheen/Halo/issues/36 · updated 2026-10-24T03:02:59Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I36-P01: Real intent matrix

Use intent-classification-v1.json: all 48 fixture IDs across EN/RU/LV/PL by keyboard and actual spoken STT-final input. Keep negations, quotes, indirect requests, questions and composite actions. Capture fixture ID, recognized-route correctness, expected/actual label, model SHA, warm/cold and p95.

**Pass evidence:** Labels conform without thinking leakage; final text alone cannot authorize an action. Record the known prior warm Qwen failure as historical FAIL until a changed implementation/configuration produces new evidence. Admission skips do not count as classifier success.

Status: NOT_TESTED. Evidence: pending.

### I36-P02: Action loop/markup

Execute native and registered cross-tool requests with approvals denied/allowed, duplicated proposals and ambiguous/UNCERTAIN output. Inspect streamed chat, TTS and stored reply for malformed/partial HALO_PROMPT envelopes.

**Pass evidence:** No wrong/duplicate side effect, partial ASR action or hidden marker leakage; normal text still streams; uncertainty clarifies and missing planner setup is honest.

Status: NOT_TESTED. Evidence: pending.

### I36-P03: Selection/residency

Enable a qualified intent model, restart, change/remove the package, compete with STT/TTS/chat and switch low-memory residency; deny setup/permissions and retry.

**Pass evidence:** Opt-in state is accurate; bounded intent admission never replaces a competing resident; stale/failed/unknown evidence cannot resurrect a positive recommendation; no cloud fallback.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I36-R01** (original line 4, Implemented): - A small on-device LiteRT model (default candidate Qwen3 0.6B if present/verified) chooses one of ACTION / ANSWER / UNCERTAIN for *final* text/voice, with 2s bound and local-only model option in On-device Models. No cloud fallback, no tool execution or memory/screen input to classifier.
- [ ] **I36-R02** (original line 5, Implemented): - Native exact commands stay deterministic, assistant action routing preserves screen/workspace hints, model UNCERTAIN cannot authorize an action.
- [ ] **I36-R03** (original line 6, Implemented): - HALO_PROMPT tool-envelope tokens are withheld from streamed chat/TTS until parser finishes. Malformed or unexposed markup is not displayed as raw speech/text. Parser rejects trailing garbage and oversized envelopes.
- [ ] **I36-R04** (original line 7, Implemented): - Source regression cases for ambiguous intents, native priority, partial-ASR bypass, strict model outputs, tool JSON validation.
- [ ] **I36-R05** (original line 8, Implemented): - EN/RU/LV product copy. Engineering details: `docs/engineering/HALO-INTENT-149.md`.
- [ ] **I36-R06** (original line 11, Outstanding acceptance — do not close on source completion): - [ ] Physical phone install / Android compile and regression tests by owner/alpha tester.
- [ ] **I36-R07** (original line 12, Outstanding acceptance — do not close on source completion): - [ ] Voice and keyboard classification matrix EN/RU/LV/PL with 40+ contrasting act-vs-ask examples, negation, quoting, indirect commands and composite app workflows.
- [ ] **I36-R08** (original line 13, Outstanding acceptance — do not close on source completion): - [ ] Real Qwen3 0.6B output conformity: label-only, no thinking leakage, warm/cold latency/p95, low-RAM model switching and priority with STT/TTS.
- [ ] **I36-R09** (original line 14, Outstanding acceptance — do not close on source completion): - [ ] Full action loop: native command vs agentic tools, tool names/argument schema/authorization, no duplicate actions, no wrong-side effects.
- [ ] **I36-R10** (original line 15, Outstanding acceptance — do not close on source completion): - [ ] Verify partial/HALO_PROMPT streamed tool JSON never enters on-screen conversation, speech output, or stored reply; normal conversations still stream.
- [ ] **I36-R11** (original line 16, Outstanding acceptance — do not close on source completion): - [ ] Error/UNCERTAIN clarity: user-facing clarification when intent ambiguous rather than generic model answer; genuine missing tool-capable model setup.
- [ ] **I36-R12** (original line 17, Outstanding acceptance — do not close on source completion): - [ ] Opt-in model selection state after restart, package deletion and model change.
- [ ] **I36-R13** (original line 18, Outstanding acceptance — do not close on source completion): - [x] Finish Polish UI localization (classifier multilingual prompts alone are not complete UI support). Source/resources and native switch verified in the 2026-10-23 update below; human linguistic review remains separate.
- [ ] **I36-R14** (original line 20, Outstanding acceptance — do not close on source completion): Do not run GitHub Actions or CI. GitHub is storage only. Record phone-test artifacts and close only on verified acceptance.
