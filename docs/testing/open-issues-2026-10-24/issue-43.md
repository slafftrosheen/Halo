# #43: [P0][HALO-ALPHA] Stability gates, completed journeys and first-block consumer UX

Original: https://github.com/slafftrosheen/Halo/issues/43 · updated 2026-10-24T06:16:13Z. Full source is retained in source-issues.json.

No source/fixture pass automatically qualifies physical, spoken, provider, platform or store acceptance.

### I43-P01: Three central journeys

On the exact installed build demonstrate repeatable physical sessions for ten-turn voice, safe typed/spoken ask→act→respond, and genuine Kids plus adult Study question/file/photo→hint/practice→Notebook/Canvas save/resume.

**Pass evidence:** All three central journeys have saved object/version/readback and human observations; no high-severity data loss, unauthorized action or fabricated cloud/offline state. Link #33/#36/#38/#54 evidence without substituting unit fixtures.

Status: NOT_TESTED. Evidence: pending.

### I43-P02: Consumer/accessibility

Inspect Home composer/attachments/tools/orb, Settings/Advanced, Screen and Notebook/Canvas/Studio on phone/tablet, EN/RU/LV/PL, portrait/landscape and 200% text/TalkBack/reduced motion. Test Back and interruption around first-block flows.

**Pass evidence:** Approved BrandBook UX remains usable; important actions require no long scroll; no developer components/IDs leak; supported/in-development/disabled surfaces tell the truth.

Status: NOT_TESTED. Evidence: pending.

### I43-P03: Resilience/privacy matrix

Use 8GB and available12GB+ hardware; verified CPU/GPU combinations, WiFi/offline, BT on/off, foreground/background, rotation/process death, TTS switch and safe thermal/low-memory tests. Complete setup/model-removal/permission-denial recovery and permitted/denied direct-provider paths.

**Pass evidence:** One concise PASS/FAIL/BLOCKED/NOT_TESTED matrix records exact environment, source/APKs/models/language/provider/time and known limitations; no surprise functional server/cost or hidden request. Experimental unsupported surfaces are honestly gated.

Status: NOT_TESTED. Evidence: pending.

## Requirement-by-requirement review

- [ ] **I43-R01** (original line 5, Alpha gates / user journeys): 1. **Continuous voice:** on a physical Android phone, voice conversation for 10+ turns in supported languages with explicit session start, no repeated tap, stop/barge-in, offline/online state clarity; see #38 and #33.
- [ ] **I43-R02** (original line 6, Alpha gates / user journeys): 2. **Safe ask → act → respond:** spoken/typed command goes through final ASR, local intent, authorized native/tool schema execution, clear result; no duplicates/wrong actions or tool markup in chat/TTS; see #36 and #40.
- [ ] **I43-R03** (original line 7, Alpha gates / user journeys): 3. **Study:** one existing Kids and one adult journey from genuine question/file/photo → grounded explanation → hint/practice → save/resume via Notebook/Canvas, with privacy-consented multimodal fallback; historical implementation #34.
- [ ] **I43-R04** (original line 8, Alpha gates / user journeys): 4. **Core consumer navigation:** important chat attachment/mic/tool actions visible without long scrolling; use approved BrandBook final UX, no leaked developer/test components in user-facing Home/Settings. Diagnostics live in Advanced → Development. Existing Home remains intact; Screen #37 added as separate page.
- [ ] **I43-R05** (original line 9, Alpha gates / user journeys): 5. **Setup and recovery:** new user sees easy model/provider choices, first run without raw HF catalog, tested TTS/STT; reboot/process death/offline/permission denial/model removal error recovery, see #39 and #27 (historically closed).
- [ ] **I43-R06** (original line 10, Alpha gates / user journeys): 6. **Privacy and ownership:** runtime functions operate device-local or direct BYO provider, never via developer functional servers; billing exception only as #41/#42. No hidden third-party request or surprise charge.
- [ ] **I43-R07** (original line 13, Release qualification): - Source-complete != device-qualified. Build/install/physical tests are separately executed by owner/alpha testers; attach environment, device/RAM, model SHA, language, provider, timestamps, reproducible failure and logs WITHOUT sensitive payloads.
- [ ] **I43-R08** (original line 14, Release qualification): - Test phone matrix: 8GB and if available 12GB+, CPU vs GPU selection verified per artifact (do not assume GPU faster), WiFi/no network, BT off/on, foreground/background, device rotation/process death, TTS model switches and thermal/memory pressure.
- [ ] **I43-R09** (original line 15, Release qualification): - Keep one concise acceptance matrix showing PASS / FAIL / BLOCKED / NOT TESTED, and record explicit known limitations before inviting new testers.
- [ ] **I43-R10** (original line 16, Release qualification): - No new global UI rewrite: collaborate with Pixar's ongoing final Halo design. Do not gate all alpha on experimental Screen/multimodal embeddings beyond clearly labelled partial supported capability.
- [ ] **I43-R11** (original line 17, Release qualification): - GitHub is code/issues storage; no automated CI/Actions. Coding agents do not run builds or tests as already agreed.
- [ ] **I43-R12** (original line 20, Done when): At least first three central journeys are demonstrated in repeatable physical alpha sessions, no high-severity data loss or unauthorized tool call defects, onboarding reliably recovers, and visible features tell the truth about offline/cloud constraints.
