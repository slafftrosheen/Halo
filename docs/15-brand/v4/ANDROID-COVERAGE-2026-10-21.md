# Brand Book 3.0: review and reading coverage

Reference: `IMPLEMENTATION.md`, Package C S05 front/back, Package D K04 review/project and K05 reading/big-text. Canonical PNGs and their editable source remain the design authority. This is an incremental implementation record, not acceptance of all 95 designs.

## Changes

| Screen/state | Implementation | Flow boundary |
|---|---|---|
| K04 review | Rounded preview card with the reference's 8% accent/surface blend, dashed unsaved outline, status pill, actual project title and formatted draft. Editing opens through Change, with Preview to return. | Preview and Change never commit a note. Keep still calls the existing project owner. |
| K04 unsaved actions | Discard, Change and Keep share 56dp targets and 17sp semibold labels; secondary actions use warm filled pills and wrap at enlarged text. Discard confirms before losing edits. Changing the idea is a separate secondary action. | Saved projects expose Change; redundant Keep and Discard are hidden until the text differs from the saved version. |
| K04 project | Saved preview retains Notebook, Canvas, story reading and adult-gated sharing entry points. | Existing saved-text hash, authorship and parent gate remain authoritative. |
| K05 page | Bordered reading card with Atkinson text, 22sp normal/26sp enlarged type and independent spacing control; localized page/sentence counters. | Only saved story text is shown. No decorative illustration, generated image, score or alignment is fabricated. |
| S05 front | Centered 300dp minimum question face with a full-width primary reveal action. Typed practice and AI comparison are optional behind Try my own answer. | Existing answer draft remains keyed to the actual card. Read-aloud stays available independently of typed practice. |
| S05 back | The actual stored answer becomes the main face, with the question retained as its caption. | Existing assessment feedback and the supported two self-assessment outcomes are preserved; the reference's third rating is not backed by current scheduling and is not simulated. |

## Verification

Checks cover preview-to-edit without implicit saving, explicit Keep, confirmed/cancelled Discard, saved-project controls, actual stored answer rendering and long story text at 200% scale. Native host compositions cover draft light/dark/200%, review front/back, and enlarged reading at 412dp. Build results and artifact paths are recorded after the gate finishes.

These host compositions omit route chrome and Android system UI. They are not website screenshots. The earlier physical phone pass proved child lesson creation, local-only refusal, Stop, parent-gate cancel/authentication and Study retry/Back. This new coverage batch still needs device comparison and complete K03/S04 source-intake journeys remain open.

Source is local, uncommitted and unpushed.

## Final result

Debug assembly and all 32 selected regression/visual checks passed with zero failures/errors/skips. All six final compositions were inspected, including light/dark draft review and 200% text wrapping. Localization consistency (3379 EN/LV/RU entries) and `git diff --check` passed. An intermediate resource-generation mismatch was rejected and the final assembly/tests were rerun together with source frozen.

Build log: `C:/Users/slavd/HaloEco/brand-review/brandbook-coverage-review-final.log`.
Evidence: `docs/testing/artifacts/HALO-REVIEW-FLOWS-2026-10-21/`.
Checkpoint: `releases/checkpoints/halo-brandbook3-review-flows-2026-10-21-debug.apk`.
SHA-256: `eadf6d574ba59b47e29286afe185278a4e7a75ac5ab83ef3c0ee3eb5194e000c`.

ADB showed no connected device at final verification. This checkpoint is not installed or device-accepted. Earlier on-phone results apply only to the earlier installed checkpoint specified in the Study/Kids report. No website screenshots were replaced by these host compositions.

## Next increment: homework intake and wording review

References: Package D K03 preview/check and Package C S04 check-text/material.

- Camera results now wait in a local photo review sheet. Only Use this invokes the existing capture import owner; take-again/cancel discard the pending file. Stop invalidates pending review through the existing epoch. The camera's adult gate is retained, and import continues to use `publishForInference=false`.
- Preview decoding uses the existing bounded image preparer (1200px edges, 1.44M pixels, 32MB source cap) without Workspace import, OCR or provider calls. Unreadable photos cannot be accepted through the UI; retake/cancel remain available.
- Wording review shows the full actual exercise in a card, with Fix it/Preview and explicit learner confirmation. Blank wording cannot start work. Actual uncertainty text is retained; no confidence highlights or exercise content are fabricated. The existing `confirmHomework` owner still persists the exercise and starts its tutor turn.
- PDF page navigation has a separate localized counter and wrapping, child-sized controls. Zoom/reset controls wrap as well. Material-details tab labels now use the localization catalogue.

Targeted checks cover explicit photo acceptance, separate retake/cancel callbacks, unreadable-image rejection, corrected wording confirmation, blank-input blocking, and both PDF navigation bounds at 200% text. Five host compositions cover Study/Kids wording review, dark/412dp, 200% text and photo-open failure. Verification and the exact checkpoint hash follow after this gate completes. No physical camera/OCR/provider acceptance is claimed for this increment.

Final verification: debug assembly and all 20 targeted checks passed, zero failures/errors/skips. The five renders were inspected; uncertainty panels use the reference's 13% attention tint and 40% border (Apricot in dark mode, its canonical light-mode ink). Localization consistency passed for 3391 EN/LV/RU entries, and the whitespace check passed.

Log: `C:/Users/slavd/HaloEco/brand-review/brandbook-homework-intake-final.log`.
Artifacts: `docs/testing/artifacts/HALO-HOMEWORK-INTAKE-2026-10-21/`.
Checkpoint: `releases/checkpoints/halo-brandbook3-homework-intake-2026-10-21-debug.apk`.
SHA-256: `41c92e0a3f4d828ccddcb5e42dc5c549f391d21981e3353a1b4e8aa33040ec23`.

The phone was subsequently reconnected. This checkpoint installed successfully with `adb install -r` and launched with Status ok (WARM) on 21 October, retaining the original app/data/provider configuration. Camera delivery, pending-preview lifecycle recovery, source/OCR/provider and pixel parity acceptance remain device checks. No host compositions were copied into website screenshot assets.
