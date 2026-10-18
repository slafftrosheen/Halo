> Historical UI verification: the DevKit v1.4 / issue #34 source work continued after this APK and these captures. They do not verify the current source. The later Russian large-text Appearance assertion failed. Issue #34 subsequently requested coding only; no further build, test, CI or deployment was run. See ISSUE-34-IMPLEMENTATION.md for current scope.

# halo UI finish pass - 18 October 2026

Base: `97c82273d1fe274a05f824d3b256361b4da0b506`, pulled from `origin/main` before editing.

## Design authority

`DevKit_v1.2/Implementation.md`, `tokens.json`, Brand Book v2.1 page 41 and Round 2 boards 11-13. Existing migration work was retained. Cards use 20dp corners, sheets 26dp, inputs 12dp and actions full-round geometry. Appearance uses the supplied palette and fonts, including the existing Cyrillic fallback.

## Delivered behavior

- Settings has explicit Everyday and Advanced tabs. Everyday contains companion, appearance/language, privacy/data, nearby surfaces and daily actions. Appearance, language, Kids mode and nearby controls open focused pages instead of expanding the root into a long control panel.
- Advanced owns runtime status, system checks, assistant/operator diagnostics, model routing, workload diagnostics, Vision/Audio/Speech labs and setup tools. Opening a tab or searching does not execute a probe or change a provider.
- One row model drives localized menu search and rendering. A search matching the other tab offers an explicit handoff and retains the query.
- Intelligence no longer embeds workload/model labs. Speech recognition no longer exposes Speech Lab as an everyday action. Android assistant diagnostics remain available under Advanced; ordinary assistant setup keeps its enrollment handlers.
- Mini halo keeps its header, input and handoff controls outside the scrolling reply. It uses supplied icons/presence Lotties, a capture-driven Summon and an ACTING-only edge pulse. The shared text renderer follows the active palette and language, including formatted replies and code blocks. Drafts clear only after their own completed submission, never after failure or a later user edit.
- Mini halo resolves the native Activity through localized/themed Context wrappers. Close/Back finish the native window; full-app handoff starts the normal host rather than displaying Home inside the mini window.
- Widgets use branded pill actions with native, pre-tinted icon bitmaps. MIUI rejects TextView compound-drawable tint reflection, so that unsupported call is avoided. Adaptive visibility preserves full labels and 48dp actions; the existing narrow launcher footprint has a dedicated regression case.
- Regenerated EN/RU/LV resources include 25 new labels and 67 already-reviewed catalogue entries that were absent from the generated resources on the base revision.
- Inactive edge glow no longer retains an infinite animation clock. This avoids idle frame work and allows static Compose captures to settle.
- Four pre-existing test API/import errors in Activity, Notebook and Study were repaired so the focused app test suite can compile.
- Asset parity checking now ignores CRLF/LF differences for text assets while retaining exact bitmap comparison. The original check reported seven false stale-icon failures on Windows.

## Verification record

- Final `:app:assembleDebug :app:testDebugUnitTest` succeeded: 46 tests, zero failures/errors/skips. Screenshot recording was enabled for this final run. The ten suites and counts are in `../testing/artifacts/HALO-UI-FINISH-2026-10-18/tests.csv`.
- Generated and reviewed representative captures from 21 fresh Settings, Mini halo and widget snapshots, including 240/320dp Mini layouts, 150x164dp widgets, EN/RU/LV and 2x text. These are recorded captures, not a stored pixel-diff baseline approval.
- Asset parity: 140 icons, 60 orb Lotties, 58 FX Lotties and both wordmarks match. All 2703 catalogue rows pass generator consistency checking. `git diff --check` passes.
- Physical phone: Xiaomi 23124RA7EO, 1080x2400, density 440. Verified Everyday/Advanced tabs; dedicated Appearance/nearby pages; light/lilac and dark/mint rendering; compact launcher widget with both Talk and Capture; native Mini Close returns to launcher; Open halo reaches full-size MainActivity; long formatted reply scrolls above the composer; the composer stays above the keyboard. No model request, microphone capture, camera capture or diagnostic probe was performed for these checks.
- The functional phone build included the tab split, widget compatibility/sizing and native Mini owner fix. The phone then disconnected. **The final APK containing the last Appearance spacing and inactive-edge clock fixes could not be installed.** It is built locally with SHA-256 `48edf88f5dc0dbe3dc9e68d0812184fb2465daa8228a10614e689cf2eb0ca804`.
- Restoration: original LIGHT appearance was restored. Original Russian language, palette selections and animator setting were not changed. **Original Kids mode was ON; it remains OFF after the UI checks and must be restored when the phone reconnects.** The reconnection request was sent in chat.
- Source changes are local and uncommitted; nothing was pushed. Physical phone screenshots were captured before the final spacing/idle-clock patch. The generated snapshots and final APK cover the final source.

A build or static screenshot does not certify every runtime animation, external provider or hardware side effect.

## Scope of visual acceptance

This pass targets Settings, its daily/advanced navigation, nearby controls, Mini halo, widgets and shared reply typography. It does not claim a pixel-by-pixel certification of every application screen across every palette, font scale and phone. The supplied existing redesign and its runtime owners remain the basis for the rest of the app.

## Review artifacts

- [Everyday settings on phone](../testing/artifacts/HALO-UI-FINISH-2026-10-18/settings-light-phone.png)
- [Advanced settings on phone](../testing/artifacts/HALO-UI-FINISH-2026-10-18/settings-advanced-phone.png)
- [Dark settings on phone](../testing/artifacts/HALO-UI-FINISH-2026-10-18/settings-dark-phone.png)
- [Dark Advanced on phone](../testing/artifacts/HALO-UI-FINISH-2026-10-18/advanced-dark-phone.png)
- [Widget on phone](../testing/artifacts/HALO-UI-FINISH-2026-10-18/widget-phone.png)
- [Mini halo final light capture](../testing/artifacts/HALO-UI-FINISH-2026-10-18/com.halo.app.ui.mini.MiniHaloVisualTest.light.png)
- [Mini halo final large Russian capture](../testing/artifacts/HALO-UI-FINISH-2026-10-18/com.halo.app.ui.mini.MiniHaloVisualTest.russianLarge.png)
- [Final validation log](../testing/artifacts/HALO-UI-FINISH-2026-10-18/validation.log)
