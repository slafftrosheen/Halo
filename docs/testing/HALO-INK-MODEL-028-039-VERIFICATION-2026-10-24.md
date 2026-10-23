# Erased sketches and model-skill packaging — #28 / #39 / #43

Base main a71bbf42df51784dcb71549e5ae56b00d37b79cc. All production/regression sources completed before combined validation; independent review of Ink and manifest changes found no actionable findings.

## Source fixes

Existing sketches can save a valid fully erased document through the dialog, ViewModel and transactional Canvas owner. New empty sketches remain blocked; malformed/future codec data remains rejected. Expected-source checks, stable node identity and recovery drafts are preserved. Drafts clear only after successful publication. Six new desktop cases cover actual last-stroke erasure/reopen, stale source/draft retention, invalid/new-empty rejection, recovered-empty save callback, and Cancel.

Phone verification of the preceding host-localization APK exposed an actual startup defect: runtime halo.models was registered without its required bundled manifest. Added that version2 manifest with the existing five operations, exact risk classifications (destructive rollback) and no new permissions. Loader remains strict. A new raw/default/merged descriptor parity regression joins the existing full built-in asset-ID guard.

The unchanged native locale fixture twice found Polish labels but failed viewport visibility. Captured synthetic fixture screen confirmed landscape, 200% type and the still-focused draft/visible IME competing with label scroll. The fixture now clears focus and closes the keyboard after text entry, preserving the draft and picker context before checking separate labels. No orientation/system setting or product focus behavior changed.

## Verification

The standalone Ink gate was intentionally cancelled at task-graph calculation after the physical startup blocker was discovered, before Kotlin/test execution. Retained initial log. First completed combined focused gate PASS7m34s:23 desktop cases across five classes (manifest4, model-tool4, persistence6, erased-sketch UI3, selection-bookmark6), zero failures/errors/skips; both matching APKs assembled. [Raw gate/XML/source identities and initial failures](artifacts/HALO-INK-MODEL-028-039-2026-10-24). Corrected package installation and native rerun now running; final phone result pending. Initial phone locale and startup failures are retained; no intent case ran before missing-manifest failure, so model suitability is NOT_TESTED.

No real pen/palm/tablet, spoken conversation/provider, notification/TalkBack, or language-quality acceptance follows from these changes. Original issues remain open.

