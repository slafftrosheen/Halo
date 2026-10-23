# Erased sketches and model-skill packaging — #28 / #39 / #43

Base main a71bbf42df51784dcb71549e5ae56b00d37b79cc. All production/regression sources completed before combined validation; independent review of Ink and manifest changes found no actionable findings.

## Source fixes

Existing sketches can save a valid fully erased document through the dialog, ViewModel and transactional Canvas owner. New empty sketches remain blocked; malformed/future codec data remains rejected. Expected-source checks, stable node identity and recovery drafts are preserved. Drafts clear only after successful publication. Six new desktop cases cover actual last-stroke erasure/reopen, stale source/draft retention, invalid/new-empty rejection, recovered-empty save callback, and Cancel.

Phone verification of the preceding host-localization APK exposed an actual startup defect: runtime halo.models was registered without its required bundled manifest. Added that version2 manifest with the existing five operations, exact risk classifications (destructive rollback) and no new permissions. Loader remains strict. A new raw/default/merged descriptor parity regression joins the existing full built-in asset-ID guard.

The unchanged native locale fixture twice found Polish labels but failed viewport visibility. Captured synthetic fixture screen confirmed landscape, 200% type and the still-focused draft/visible IME competing with label scroll. The fixture now clears focus and closes the keyboard after text entry, preserving the draft and picker context before checking separate labels. No orientation/system setting or product focus behavior changed.

## Verification

The standalone Ink gate was intentionally cancelled at task-graph calculation after the physical startup blocker was discovered, before Kotlin/test execution. Retained initial log. First completed combined focused gate PASS7m34s:23 desktop cases across five classes (manifest4, model-tool4, persistence6, erased-sketch UI3, selection-bookmark6), zero failures/errors/skips; both matching APKs assembled. [Raw gate/XML/source identities and initial failures](artifacts/HALO-INK-MODEL-028-039-2026-10-24). Corrected package installation and native rerun now running; final phone result pending. Initial phone locale and startup failures are retained; no intent case ran before missing-manifest failure, so model suitability is NOT_TESTED.

No real pen/palm/tablet, spoken conversation/provider, notification/TalkBack, or language-quality acceptance follows from these changes. Original issues remain open.


## Completed phone follow-up

Installed app SHA58c642aff2626dadc0943778cc4b5f4d3e9f2edeaf727985b156e8e80234db91 and test SHAdc747ee32fa44c9ba262095f9847f74260e6c692f21f5a6812025674ac56a9d6; installed readback matched. Corrected Polish fixture PASS6.08s at200%/landscape, draft and picker context preserved. MainActivity resumed after the run. The missing-manifest startup blocker is resolved: the real production-router intent probe reached inference admission.

Intent matrix INCOMPLETE/NOT_TESTED:19 completed calls (first cold case plus18 prepared cases), all NO_ADVICE; cold return median10563ms, p9516641ms, range10102–16641ms. Native load cancellation at the2s coroutine deadline still waited for cold initialization/teardown, and the prepared matrix hit its180s outer deadline. No warm backend/label or model-quality conclusion. This is a newly measured #36 admission defect, addressed by the subsequent ready-only source batch. [Raw phone output and summary](artifacts/HALO-INK-MODEL-028-039-2026-10-24/final-phone). No CI/Actions or real spoken/pen acceptance.
