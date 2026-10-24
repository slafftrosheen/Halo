# #38: HALO-VOICE: hands-free continuous local/hybrid conversations and barge-in

Original: https://github.com/slafftrosheen/Halo/issues/38 · updated 2026-10-23T17:30:12Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I38-P01: Ten real turns

Explicitly start Voice Mode from Home, Chat, Study and Notebook. Conduct at least ten real spoken turns without tapping the mic again in each claimed supported language; confirm audible Supertonic and supported Kokoro fallback. Record cold/warm p50/p95 and actual heard replies.

**Pass evidence:** Listening/response loops work foreground; no stealth capture, missing-model false readiness, speaker echo command or language promise without actual recognition/audible evidence.

Status: NOT_TESTED. Evidence: pending.

### I38-P02: Interrupt/control

Speak and touch Stop/Pause/Mute/Repeat/End while capturing, generating and playing. Interrupt offline and on a permitted hybrid turn; switch the TTS/reasoning model during playback and supersede queued controls.

**Pass evidence:** Old capture/generation/audio is cancelled, no duplicate action or stale resumed mic, mute is respected, and each control reflects observed state.

Status: NOT_TESTED. Evidence: pending.

### I38-P03: Audio/privacy/lifecycle

Switch speaker/wired/BT/glasses routes; interrupt audio focus, background/foreground, rotate, sleep/restart and revoke permission. Repeat denied versus explicitly permitted cloud and 30-minute voice use.

**Pass evidence:** Route/focus/echo handling and visible mic state are correct; privacy gates hold; no surprise transmission; recovery, battery/thermal/RSS limits and accessibility are measured.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I38-R01** (original line 5, Flows): - Start → VAD/endpoint + STT (partial hypotheses for UX, final for actions) → local intent/tools → local or selected online LLM → chunked streaming Supertonic TTS (Kokoro fallback) → auto-listen for next turn.
- [ ] **I38-R02** (original line 6, Flows): - Immediate voice/touch stop, pause, mute, repeat, end; cancellation/barge-in interrupts TTS, LLM and stale callbacks; do not treat speaker echo as human command.
- [ ] **I38-R03** (original line 7, Flows): - Android audio focus, speaker vs headset/BT/glasses, echo suppression, lifecycle/process death, foreground permissions, visibility, accessibility and battery/thermal budget.
- [ ] **I38-R04** (original line 8, Flows): - Local profile targets 12GB+ when measured, optional 8GB lighter path; no RAM-only guarantees. Hybrid profile local audio/intent + direct BYO cloud reasoning, with informed consent and no silent transmission.
- [ ] **I38-R05** (original line 9, Flows): - Measure warm/cold audio latency, p50/p95 end-of-utterance to audible answer, memory, CPU/GPU, model residency, interruption success and energy; ~2s warm response is goal, not promise.
- [ ] **I38-R06** (original line 10, Flows): - Strict tool safety: never act on partial/ambiguous ASR, do not speak hidden markers, no stale audio and no duplicate actions.
- [ ] **I38-R07** (original line 16, Implementation discipline): - Start from current main, inspect existing components and BrandBook/DevKit, reuse runtime and Compose; no global rewrites.
- [ ] **I38-R08** (original line 17, Implementation discipline): - Keep internal traces/experimental model settings in Advanced → Development.
- [ ] **I38-R09** (original line 18, Implementation discipline): - Code-only batches can commit to main; do not run builds/tests/CI/Actions during coding. Physical validation is owner/alpha tester work; never claim phone-tested from source review.
- [ ] **I38-R10** (original line 19, Implementation discipline): - EN/RU/LV/PL copy subject to actual language support; accessibility and privacy are requirements.
- [ ] **I38-R11** (original line 12, Acceptance): 10+ turn conversation on real Android without mic re-tap; interruption works offline and during generation, switch model during playback safely; no false cloud use; honest missing-model/permission recovery.
