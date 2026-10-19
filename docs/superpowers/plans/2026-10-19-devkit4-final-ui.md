# DevKit 4.0 final UI continuation

**Goal:** Match the complete supplied Brand Book 3.0 design in DevKit 4.0 while preserving working runtime owners.
**Architecture:** Existing Compose routes, persistent preferences and shared components. Exact supplied artwork; no alternate visual direction.
**Spec:** docs/15-brand/v4/IMPLEMENTATION.md, USER_FACING.md and product/A–G plus SCREEN.
**Execution:** Inline, explicitly authorized continuation. No tests. Build debug APK at checkpoints. Device/pixel acceptance stays open.

- [x] Vendor all runtime artwork from the complete kit; retain provenance and canonical reference locations.
- [x] Apply shared theme semantics, including pastel primary fills on light surfaces, permanent Mint identity and correct semantic colors.
- [x] Implement P05 Appearance: live busy preview, mode/accent/tone, text size, higher contrast, reduced motion, guide light. Persist controls and wire every consumer.
- [x] Implement P06 instant language choice with Undo, separate voice languages and unavailable Polish.
- [x] Apply I01 How halo answers: real location/result status, grouped plain-language rows, technical details only in Advanced.
- [ ] Apply VO01–VO05 plain language and remove developer capture details from user pages.
- [ ] Reconcile F devices/skills with real readiness and Advanced boundary.
- [ ] Continue B/C/D/SCREEN layouts and state coverage against supplied PNGs.
- [ ] Reconcile G storage/backup, setup and advanced pages without inventing unsupported behavior.
- [ ] Build and preserve checkpoint APK with checksum; record remaining visual coverage without claiming device sign-off.

## Source checkpoint implementation

- DevKit 4.0 is canonical under `docs/15-brand/v4`; its E/F/G references are supplied and were read.
- Imported 904 source records, preserving complete-kit canonical overrides and SHA-256 provenance; boards, films and prototypes are excluded from Android assets.
- Appearance has persistent accessibility switches, a busy preview, 24 theme choices and 200% text enlargement. Android accessibility scale remains the minimum.
- Presence uses the exact supplied Mint artwork in every accent theme; technical state colors are preserved.
- Reduced-motion state is shared by presence, FX and guide rendering; guide-light preference removes decorative guidance without changing the next action or runtime state.
- Language applies in place with Undo, unavailable Polish, and a direct separate voice-language entry.
- I01 uses the actual selected location and exact selected model proof. It never shows a working answer based on connection alone.
- Shared answer history records remote attempts before execution, completion only from exact attempt/provider evidence, 30-day local retention, and confirmed clearing. It excludes content, keys, endpoints and raw identities. Other apps/voice transmissions are explicitly outside its coverage.
- Skill imports and technical metadata are exposed through Advanced › Skill packages. Everyday actions keep their existing grants and permission owners.
- Notebook has responsive creation actions, two recent items, review/lesson continuation and direct local-search navigation. Existing user edits to the local-search sheet are preserved.
- Routine builder uses When / Only if / Do / Tell me. Device status distinguishes Connected · getting ready from Ready.
- Source reviewer findings on cancellation receipts, retention, fallback attribution and missing lesson continuation were corrected. No tests were run.
- First compile exposed a remaining Kids-mode caller of PaletteControls; the shared entry was restored before the next build.

## Coverage still open

The checkmarks above record source implementation, not device/pixel acceptance. E model-detail/download sheets, setup, full F pairing/audio/robot layouts, storage/password-backup behavior, remaining G states and B/C/D/SCREEN per-state comparisons still need completion. Do not label the whole app pixel-perfect based on a successful APK build.
