# Halo visual identity and scaling

Baseline: main fast-forwarded to `5f867f8f` on 2026-10-13.

Halo keeps its mint action accents, calm surfaces and state-aware focus. The shared focus trail uses mint at different intensities instead of a mint/coral/blue gradient. The presence renderer uses a broken orbital ring with a satellite and flat core; the compact Halo glyph uses the same geometry. Runtime state glyphs, progress, reduced-motion and foreground behavior remain available.

## Shared component rules

- Controls: 12 dp corners, minimum 48 dp height, intrinsic height for enlarged labels.
- Cards: 18 dp corners; hero containers: 26 dp. Material and glass compatibility tokens delegate to the same shape definitions.
- Legacy HaloButton delegates to HaloActionButton so padding, shape and action hierarchy agree.
- Shared headers and setting rows wrap their actions/values rather than forcing narrow columns into a single line.
- Shared pages use 16 dp horizontal gutters below 360 dp screen width and 24 dp otherwise. The existing shell retains its constrained content width and tablet navigation rail.
- System accessibility text enlargement stays authoritative. No new UI strings are introduced.

## Scope and verification

The changes consolidate shared visuals; they do not establish that every specialized editor, hardware panel or dialog has passed physical acceptance. Existing provider/runtime contracts and screen-specific layouts are retained.

The pulled Activity UI required a local `nextOrdinal` value to compile across the module boundary. This is a compiler correction with no intended behavior change.

Fresh validation results and rendered previews are reported with the delivery. Earlier overhaul test results are not reused as acceptance for this patch.

## Visual review

Fresh light/dark identity renders and the 320 dp, 200% text shared-control render were inspected. Orbital strokes, satellite marks and state glyphs remain visible at 32/72/144 dp; the enlarged header action and settings value wrap without clipping.

Refactoring UI diagnostic: 8/10 (six of eight checks). The remaining app-wide checks are a complete spacing-scale audit of specialized screens and consistent prose-width constraints in those screens. Shared component consolidation should not be represented as exhaustive screen-by-screen acceptance.

## Editor workspace follow-up

Notebook > More tools again includes a Notebook launcher. Opening it selects Notes and closes the toolbox; New note remains available there.

Note and Canvas editors open with tools collapsed. A compact title/back/More tools header stays available. Expanded menus scroll within 40% of the available screen height after keyboard insets. Notes hide formatting, attachment insertion and per-block selectors/actions while the tools are collapsed. Document content, existing attachments, draft fields, save errors, conflicts, recovery, proposals and active-operation cancellation remain available.

The shell hides bottom navigation and the tablet rail on note/canvas routes; their Back controls return to the library. Tool visibility is saveable per editor and toggling it does not replace the document field. The landscape interaction test checks workspace height and draft preservation.

## Final validation (2026-10-13)

- Identity/home/settings/font-scale/Canvas/phone-workflow gate: 31 tests, zero failures/errors/skips; debug APK built.
- Editor/library gate after collapsible menus: 25 tests, zero failures/errors/skips; debug APK built.
- EN/RU/LV generator check: 2298 strings; diff whitespace and changed-file UTF-8 checks passed.
- Final APK installed and MainActivity launched on connected 23124RA7EO. Home and Settings were reviewed with the orbital identity. The restored Notebook toolbox entry was observed on the final APK.
- Full physical landscape-keyboard acceptance of the final editors remains open; the automated landscape interaction test passed. No provider, hardware or external-effect acceptance is inferred.
