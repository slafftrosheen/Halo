# Issue 62 - visual and interaction review

Latest delivery status and exact source/APK/test evidence: [integration ledger](ISSUE-62-INTERFACE-DELIVERY-2026-10-25.md). Checkpoint-specific sections below are historical review records; they do not override that ledger or establish final phone acceptance.

Date: 2026-10-25. Baseline: `d5fe5943c8c3e689c49b8f7513823f6e50054556`.
Authority: current GitHub #62; older Interface 4 fan/tab suggestions do not override the four fixed destinations and contextual carousel.

## Evidence boundary

This is a specialist source/fixture review with explicitly bounded physical evidence from the parent agent. Checkpoint 10 passed **92 affected UI tests**, including four Material-sheet locales with Activity-registry and 200% font inheritance checks, Home burst-input regressions and the short-IME viewport fixture. Checkpoint 11 full app tests and both APK builds are **RUNNING**, against source snapshot `798a5a44895119897a060a146a477f3d66f0c77ca041ac70bc55ec8e20698ce5`, after two reviewed editor-lifetime corrections. No checkpoint 11 success is claimed here.

The last APK actually installed on the phone was **checkpoint 05**. The checkpoint 09 installation could not run because the phone disconnected. Later host tests and source repairs do not establish current-binary native behavior. The visual agent performed no builds, ADB commands or phone interactions. Phone captures reviewed below came from the parent agent. Real speech, current-code TalkBack, sustained performance/energy and final installed-source identity remain **NOT_RUN** in this review.

## Created asset inventory

| Asset / location | Actual implementation | Boundaries |
| --- | --- | --- |
| `app/src/main/java/com/halo/app/ui/theme/HaloAmbientBackdrop.kt` | Procedural Canvas artwork: two oversized, cropped Open Ring fragments, with independently varying slow offsets; alternative two soft radial mist fields. Original geometry authored for this change; no external download, stock image or generated fake dashboard content. | Decoration only, no pointer input, no microphone or runtime state. Maximum alpha 0.055. Halo form strokes 0.025/0.035. Canvas-only phase reads prevent recomposing user content on each tick. |
| `HaloBackgroundStyle.STILL / HALO / MIST` | Saved separately with each light/dark palette in existing `halo_palettes`; backward-compatible missing-key defaults. | STILL has no changing phase. Reduced motion freezes motifs; higher contrast removes them. RESUMED-only ticking; screen-off, battery saver and moderate-or-worse thermal status stop ticking. Ambient full-screen dashboard explicitly disables drift. Actual energy cost remains unmeasured. |
| `BrandAppearance.kt` | New Sage/Rose accents and Paper/Slate surface families, each with light/dark foreground/background pairs. Original Mint/Blue/Lilac/Coral and Natural/Warm/Cool values retained. | Official mint ring, apricot attention and red errors retain identity. Existing `PaletteTest` enumerates all palette pairs; exact grouped test evidence belongs to the delivery ledger, not visual inspection. |
| `ScreenWorkspaceAppearance.kt` | Clear/Paper/Glass/Focus dashboard style presets combine existing font, background and translucency fields. | No new store, schema, network fetch or widget. Selection edits existing layout draft; current Apply/Cancel and source conflict check remain authoritative. |
| `strings_interface4_appearance.xml` in values, values-ru, values-lv, values-pl | Original localized names and preview explanation for all new appearance choices. | Does not claim spoken recognition qualification. |
| `HaloStaticFxAssets.kt` | New theme accents intentionally resolve existing mint state artwork. | No duplicate assistant, recoloring of official ring or claim that 166 existing animations were regenerated. |

## Before / after source map

| Area | Baseline behavior | Implemented change / owner |
| --- | --- | --- |
| App appearance | Accent and tone selection wrote immediately; visual preview was only a sample. Four accents and three tones. | `PaletteControls.kt` keeps an unsaved preview, explicit Apply/Cancel and restore-defaults preview. `PalettePreferences.selectPersisted` publishes after durable commit; independent light/dark records remain. Six accents, five tones, three background styles. Mode/text-size/accessibility switches retain their existing immediate behavior. |
| Semantic appearance control | Existing `halo_get_preferences` / `halo_set_preferences` supported accents/tones and accessibility options. | `AppPreferencesActions.kt` extends the same enum schema with `background`, requires explicit paletteTheme, persists through the same owner, confirms disk readback and returns light/dark background values. `AssistantTools.kt` describes the new field. No provider, OS permission or sharing-policy changes. |
| Dashboard top chrome | Chat/Screen, settings, Customize/Ambient/New note and draft actions consumed working-area height. | `HaloScreen.kt` projects the Screen utility band through `BindShellUtilityBand`. Shell-hosted `ScreenWorkspaceChrome.kt` shows the single Chat/Screen switch and high-frequency Apply/Cancel; general actions are current-page carousel actions. Standalone presentation keeps its controls. |
| Dashboard cards | Flat low surface fill; optional 0.94 alpha; clock used displayLarge even in narrow cells. | `ScreenWidgetCard` uses restrained glass fill, adaptive border, solid high-contrast/private cards, a short color transition only on actual updatedAt change, and headlineLarge clock on normal phone cards. Data, freshness, edit handles, permissions, card identity and layout ownership remain. |
| Dashboard background | Flat selected color plus idle aura. | Shared procedural background on the selected Screen color; no extra assistant Orb. Saved Screen appearance still chooses background tint. |
| Global shell | Narrow footer, old fan/global destinations. | `HaloZone.kt` has four fixed roots, contextual carousel, adaptive compact layout and aligned left/right rails. The real `CompactPrivacyModeButton` projects existing Local/Power state as a Switch with a short label and full semantic description. Existing mode activation and confirmation owners remain. Subsequent fixtures exercise the actual control. Checkpoint 05 physical English root geometry is recorded below; later repairs still need physical requalification. |
| Whole-app background | Opaque route roots hid shared decoration. | `AppShell.kt` draws the procedural backdrop behind the NavHost; shell-hosted route roots use `haloWorkspaceBackground()` so it remains visible. Dashboard retains its own selected backdrop. Cards, editor paper and reading surfaces remain opaque; no motifs are overlaid on text. |

## Five specialist review dimensions

### 1. Information architecture and action parity

Fixed roots and contextual actions are now separate in HaloZone source. Dashboard utility controls retain existing callbacks. Screen settings, Customize, Add card, Appearance, ambient toggle and New note are projected with current layout/draft identity and availability. General settings controls use the existing persisted preference owner; semantic field discovery contains all new enum values.

Remaining: screen-by-screen route/action matrix reconciliation is still required for every nested Note/Canvas/Study/Studio/Office/Memory/settings page. This review did not inspect every nested editor. Appearance projects its containing Settings page identity, but currently does not describe the exact active appearance field and palette revision in the shared page context. Real text/STT-final invocations of new background fields are not established by schema inspection.

### 2. Interaction and touch ergonomics

HaloZone source uses fixed 48dp roots/bell and a separately scrollable, snapping contextual row. The center tool label now exposes the nearest icon's title. Explicit launcher menu remains the alternative to long press or drag. Dashboard Apply/Cancel remain direct controls while editing.

Earlier source findings identified an offset face/menu group and excessive stacked footer height. The integrated shell now uses symmetric 64dp side rails, a centered carousel/Orb column and an available-height-derived normal layout budget. Bell and real Local/Power control have corresponding lower-rail space; compact/large-text layout moves the profile into its own row. `CompactPrivacyModeButton` uses one toggleable Switch semantic owner, a short single-line Local/Power label and the full accessible mode description. The root agent refined alignment after the checkpoint 03 fixture review. Host fixtures cover root alignment, independent Orb/menu hit targets and actual-profile compact 200% captures. The checkpoint 05 English 360dp/200% phone captures showed matching root/Orb/bell geometry across Home, Library, Devices and Settings with legible separated controls. This does not qualify later repaired binaries or all whole-app states.

Remaining: real fast/reversed drag, cancellation, snap, no drag-to-click leakage, page changes mid-gesture, Back, IME and approval overlays. The exact 75/25 ratio must be measured on normal portrait usable height, excluding system insets; compact/large-text reflow is a separate justified layout.

### 3. Visual identity, motion and performance

New artwork uses oversized incomplete rings and mist, not a second interactive orb or listening animation. Original brand state artwork and timing ownership remain. Dashboard freshness transition reuses `HaloMotion.standardMs`; reduced motion makes its color change immediate. Decorative phase advances at most ten times per second, only while resumed, interactive and unconstrained; state is read in drawing only. Previews are static.

Remaining: actual light/dark contrast over blended cards; on-device static/animated comparison; sustained idle and interaction CPU/frame/memory/battery sample; screen-off/power-save/thermal pause/resume. Source lifecycle guards do not establish efficiency. Existing Lottie handoffs and whole-app typography/coherence still require rendered inspection.

### 4. Accessibility, localization and small displays

New controls use wrapping FlowRows, minimum 48dp controls and localized EN/RU/LV/PL names. High contrast removes background decoration and translucency. Clock text is reduced to an appropriate normal-phone display hierarchy while saved emphasis remains honored. Existing grid height expands for content and uses one column at large font scales.

The checkpoint 05 English 360dp/200% phone review found no new clipping on the four major roots or repaired Appearance mode rows. Russian 360dp/200% exposed a real footer failure: the legacy full-power label consumed the row and forced the hold hint into a tall column. Source now bounds the profile slot, uses dedicated short localized mode labels and bounds the hint while preserving its full accessibility description. Checkpoint 10 host regressions pass; physical repair verification remains pending. Remaining: current-binary landscape/short-window and IME review, TalkBack reading order/custom actions, keyboard access, focus restoration, tablet/stylus where available. Root navigation icons expose names semantically but novice recognition and selected-state contrast require physical review.

### 5. Novice discoverability and real tasks

Appearance now distinguishes preview from saving, with Cancel retaining saved values and restore-defaults staging a preview. Clear/Paper/Glass/Focus offers cohesive dashboard starting points without changing data. Screen content remains source-backed, including honest setup/offline/error/private-card states. Original Apply/Cancel layout conflict detection remains.

Settings search aliases now include background, sage, rose, paper and slate after review. The selected carousel label helps but does not replace task-based first-use qualification. Checkpoint 05 phone testing confirmed Theme Cancel, Apply/reopen and Dark appearance behavior. Dashboard customization, data/scroll/selection retention and foreground/background source-update behavior still require the scoped final acceptance matrix. Actual novice task completion is not inferred from code.

## Required grouped acceptance after integration

- Run existing palette contrast matrix and palette persistence tests; new persistence regression verifies independent Sage/Paper/Mist and Rose/Slate/Still profiles after recreation.
- Verify typed `halo_set_preferences` with `paletteTheme=light, background=STILL` then `halo_get_preferences`; reject missing paletteTheme/unknown background; test Kids/foreground gate separately.
- Render Appearance preview/apply/cancel and dashboard draft presets; confirm no preference write before Apply and restore-defaults only commits on Apply.
- Capture true before/after root and nested screen images with exact source/APK hashes. Do not use procedural previews or source review as phone evidence.
- Complete remaining issue #62 voice, protected approvals, cross-tool persistence and full-screen matrix in the canonical acceptance ledgers. Issue stays open until those gates pass or the owner explicitly accepts a scoped deferral.

## Checkpoint 01 correction

The grouped integration compile found a Kotlin name collision between the new background enum and the existing `Color.kt` `HaloBackground` color constant. The new enum was renamed to `HaloBackgroundStyle` throughout production, test and review references; original color constant and persisted STILL/HALO/MIST values are unchanged. The root agent owns the recovery build; no success is claimed here.

## Saved-chat route and typed-action parity

Integrated `SavedChatTools` delegates list/search/get/trash/restore to the existing `RoomConversationStore` owner. Reads use explicit ACTIVE/TRASHED scope and bounded results. Exact source versions hash actual canonical content/lifecycle; guarded writes revalidate within the owner transaction. Trash preserves turns and refuses the current writer through the typed path; restore leaves the active chat unchanged. Registration/access controls retain local-only saved-history handling, grown-up foreground admission, explicit confirmation and no automatic replay. Owner/stale-source, bounded transcript, metadata omission, access and action-truth regressions are tracked in the grouped delivery ledger. Their presence in source does not establish physical or spoken execution.

`halo_saved_chat_open` presents the existing saved-detail route and reports DISPATCHED with `continued=false`. It never silently rebinds the running conversation. The detail page's human Continue button now calls `HomeViewModel.openConversation`, preserving canonical cancellation, context clearing and activation, rather than directly bypassing that owner through store activation. Text/spoken discovery, protected approval, navigation rendering and actual continuation on a phone remain unverified.

## Responsive repairs and current evidence

- Russian Appearance mode clipping was reproduced in the 200% fixture and repaired with full-width options. The corrected fixture shows all three labels on single lines; later selector repair scopes assertions to mode radio controls rather than the separate text-size reset label.
- Duplicate normal-layout Back controls were addressed at presentation level. Header Back remains available when the carousel is absent in compact/large-text layouts; the full accessible action list and original guarded callbacks remain intact.
- At landscape/200% with a tall Gboard informational banner, the phone left roughly 77dp for the app. Source now uses a status-inset-safe scrollable footer at constrained heights, preserving control size and reachability instead of claiming all roots can fit simultaneously. Host reachability passes; this repair is not phone-qualified.
- Home burst-input corruption observed on checkpoint 05 led to editor-buffer/canonical-acknowledgement repairs. Checkpoint 10 regressions pass; checkpoint 11 adds the reviewed editor-lifetime corrections and remains running. No current phone typing success is claimed.
- Material's dialog used `LocalView.context` resources, so the initial outer-context localization wrapper failed three non-Russian locale tests. The corrected attached localized Material host retains the real view tree and restores the original Activity context for app content, with captured density/layout direction. All four locale regressions pass in checkpoint 10, including non-null Activity-result registry and 200% font checks. This is host evidence, not a TalkBack session.

## Physical comparison and acceptance boundary

The [Settings comparison](issue62/2026-10-25/README.md) contains unaltered before-checkpoint25 and after-checkpoint05 images from the same phone at density440/font1.0, with hashes in its provenance file. It demonstrates the initial visible hierarchy/shell change only. It does not show the subsequent editor, Russian footer, Material locale, copy or constrained-height repairs.

Checkpoint 05 physical English 360dp/200% captures were legible on the major roots, with matching root/Orb/bell geometry; Theme Cancel, Apply/reopen and Dark worked in that installed version. The Russian footer defect and exceptional short-IME failure remain recorded as discovered physical failures with source/host-test repairs awaiting reconnection. Existing saved transcripts are historical QA content and are not fresh speech acceptance.

Checkpoint 10: **92 affected UI tests PASS**. Checkpoint 11: **RUNNING**, full app suite plus both APK builds at the source snapshot above. Last phone installation: **05 only**. Current-code physical proof, TalkBack, real speech, protected approvals and sustained frame/CPU/memory/battery acceptance remain **NOT_RUN** here. This document alone does not support issue closure.

## Delivery recovery update

Checkpoint12 full app regression passed **2,150/2,150**, zero failures/errors/skips, with both APKs built. The main action list now uses only the current page actions; fixed destinations and standalone/Mini compatibility retain their original owners. Duplicate Note/Canvas utility actions are removed only when the identical enabled carousel action is available; compact/large-font headers and unique read-aloud controls remain. Library search has a persistent accessible input label. Saved-chat fixture collectors stop before database teardown, preserving every original version/data assertion.

An existing read-only headless Android17/API37 emulator is being used for independent silent checks. Setup permissions, provider keys, model downloads and device audio were skipped through actual optional UI. It exposed inherited scroll between setup stages; the only production change after full12 keys the saveable scroll state by setup stage while preserving form composition/drafts. Checkpoint13 recompiles both APKs; emulator verification is pending. This is not a fresh phone or human speech qualification. The owner confirmed the phone is unavailable for now.

Final coding checkpoint: full12 passed2,150 cases with zero failures/errors/skips;13 rebuilt both APKs after only the setup scroll-state key change. The owner then deferred further tests. Temporary emulator stopped; final phone/native/human acceptance remains pending. See the authoritative delivery ledger and machine-readable manifest for exact source/APK attribution.
