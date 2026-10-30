# Halo Zone 5.0 — implementation batches

Baseline: `slafftrosheen/Halo` at `4a227e33eb8e6812ad30481b36e0ceb0f17f4cbd` (30 October 2026, 12:35 Riga). The approved images establish visual direction; `DESIGN.md`, tokens, component contracts and the page plan establish behavior and measurable constraints.

## Working method

Work in complete vertical batches. The intended effort balance is approximately 90% implementation and 10% focused verification, without a build/device loop after every small edit. Keep an evidence log separating source review, desktop assets, native UI, real speech, provider and physical-device checks. Do not claim a gate passed when its dependency is unavailable. A provider outage does not block reviewing local navigation or visual completeness.

Before coding, compare the current branch with the pinned baseline, read the current `.agents/AGENTS.md`, and record changed owners/routes. Reuse existing domain code. Do not create a new ToolRegistry, CognitiveSession, privacy profile store, notification event store or parallel draft manager. The new UI consumes existing authorities.

## Batch 1 — foundations and asset adapters

**Goal:** establish one reusable visual system and semantic icon resolver before page migration.

1. Introduce the semantic icon catalog as a host UI concern. Copy or generate native VectorDrawable resources, preserve original names under `ic_halo_*`, and implement a context-aware action-to-icon map. The 55 existing enum mappings are a migration bridge; broad NOTE/TOOL mappings must be overridden by actual action IDs.
2. Reuse the existing Sora, Atkinson Hyperlegible Next and Onest font resources. Merge semantic dark/light/high-contrast tokens into the existing theme rather than creating a parallel theme authority.
3. Build the shared components described in `components/contracts.json`: labelled satellite, contextual orbit, global sheet, object row, instrument header, state surface, hint host and badge presentation.
4. Use minimum target sizes, scalable labels and disabled reasons from the start. Treat icon-plus-label as one semantics node. Keep the text string localized, outside SVG/XML artwork.
5. Load only assets used by the current surface. Do not put every icon variant or animation into a global eager cache. Android should use tintable vectors; PNGs and contact sheets are review/import conveniences.

**Evidence gate:** XML/SVG resources parse, build compiles after the full batch, representative icons render at 24/32/48dp in light/dark, labels remain clear at 1.0/1.4/2.0 font scale. Keep the previous UI usable during migration.

## Batch 2 — orb navigation and shared zone

**Primary owners:** `ui/shell/AppShell.kt`, `HaloZone.kt`, `ContextToolOrbit.kt`, `ui/presence/HaloOrbLauncher.kt`, `ShellOrbCatalog.kt`, and the current signal face renderer.

1. Wire the global destination list into orb disclosure. The baseline passes `destinations = emptyList()` inside the main shell; hiding fixed roots without changing this would remove navigation.
2. Preserve `primaryNavigationRoute`, existing root restoration rules and process-owned Home/session state. HaloScreen remains the existing Home/Screen surface alias, not a duplicate dashboard with new storage.
3. Implement a readable navigation sheet with all six destinations, current-location indication, attention summary and the existing Local/Power profile control.
4. Add the explicit Menu entry and accessibility custom action. Arbitration must separate tap, hold, upward orb swipe, satellite drag and system bottom-edge gestures. The Android Back action dismisses disclosure first.
5. Replace fixed navigation columns with full-width contextual orbit space only after every global destination and back path works. Keep page-action lease checks, owner/revision checks and enabled conditions intact.
6. Add per-satellite labels, stable semantic IDs, bounded snapping and one full action list with the same data. Remove the single selected label underneath the orb; that location becomes the orb's current action/state text.
7. Implement measured normal, composer, IME, large-font and short-window projections. Preserve a stable bottom orb anchor. Reduced motion uses immediate/static projections. Never clip or shrink targets to force a ratio.

**Evidence gate:** from a deep editor, reach each root by Menu, hold and swipe; Back closes menu; current draft/session survives. Dragging cannot invoke a command, and holding/swiping cannot start recording. Verify 360/390/412dp normal widths, one short window, IME and 200% text in one grouped pass.

## Batch 3 — Home, HaloScreen and Workspace architecture

1. Home keeps one composer and one voice owner. Put Attach/New chat/History in the default contextual set and keep mode/history/recovery available with truthful enabled states. Do not duplicate attachments or microphone controls in the field.
2. Keep live-follow semantics: follow the newest message at the end, pause when the user inspects history, resume on explicit submission/return. Incoming content must not pull a reading user away. Result cards refer to real object IDs and receipts.
3. HaloScreen receives Widgets/Arrange/Appearance with a visible edit mode, Apply/Cancel and local preview. Widget loading, denied calendar permission, weather unavailability and loaded-empty continuation states are independent.
4. Workspace becomes two explicit local views: Library and Tools. Keep Library default and the existing `notebook` route. Preserve saved IDs and links. Tools has direct Study/Memory/Halo Studio/Office Tools entries.
5. Remove silent `suite` changes that only replace orbital actions. A suite either has a visible landing view with a name or opens its actual editor directly. Default Library actions are New note/Import/Organize; selection mode gets the selected-object operations.
6. Keep unsupported format capability truthful. Office supports the operations its catalog exposes; do not turn limited PDF reading/image export into an advertised native PDF editor or claim DOCX/XLSX round-trip support.

**Evidence gate:** Home→saved note→Workspace→Tools→Studio→Library and Home→Study from a selected source. Confirm source/revision bindings, save receipts, Back behavior, title/context alignment and usable empty/offline states.

## Batch 4 — instruments and complex workflows

Use the exhaustive page plan for all registered patterns and embedded views. Complete each owner before switching to another.

1. Notes/Office: title, source, save state, formatting/insert/export, document/CSV/PDF reading distinctions, file picker and external share return states.
2. Canvas/Studio: library/canvas context, selected nodes/layers, draw/select/insert, undo/redo, inspector/palette sheets, export scope, unsaved state and truthful format limits. Human freehand gestures remain human interactions; supported structured operations remain assistant-operable.
3. Study: source selection and exact anchor preservation, pre-knowledge probe, plan, lesson, practice, review and progress. Each voice action belongs to the active lesson/draft. Retain pending work on Back or process recreation according to the existing owner.
4. Kids: voice-first guidance, large labelled targets, constrained actions, parent gates, age-appropriate explanation, scoped stop/answer ownership and calm feedback.
5. Memory: facts, sources, suggestions, review, edit, remember and forget. Keep approval and forgetting consequences explicit. Do not badge the same suggestion in several places as several items.
6. Research/translation/media/handoffs: clear source and provenance, waiting and failure states, cancellation, truthful costs/permissions, one route back to the active work.

**Evidence gate:** one full success and one blocked/recovery journey per owner, plus all direct route entry/back contracts. Verify structured action receipt and assistant parity for every changed feature; navigation-only proofs are insufficient.

## Batch 5 — Devices, Activities, Settings, onboarding and special hosts

1. Devices: list, add/scan, selected device, connect/ready/failure, media/capture controls, battery and technical details. Ready animation triggers only from canonical READY. Preserve explicit selection, permissions and device ownership. Do not rework discovery protocol or promote unverified hardware behavior.
2. Activities/notifications: two named subviews, Needs you/Running/Completed filters, run detail, confirmation and recovery. Keep real run IDs and deduplicate badges by canonical IDs. Retire intrusive duplicate banners.
3. Settings: migrate all30 actual destinations and their embedded pages, including Brain, Voice & Reading, Vision, appearance, privacy, providers, models/downloads, accessibility, storage, about/help and gated development surfaces. User-oriented names precede implementation details; provider details remain reachable for a meaningful choice.
4. Onboarding/setup: all stages, back/skip/resume, permission denial, unavailable model/provider, interrupted downloads, default assistant role and final readiness. Do not turn optional cloud use into an implied requirement.
5. Mini Halo, overlay, ambient and Android host-entry flows: use the same state and action owners with adapted layout. Avoid a second unscoped microphone or menu. Developer-only routes remain isolated.

**Evidence gate:** fresh setup→Home, Settings→provider unavailable→return, device scan→connecting→READY or failure, Activity review→outcome, and Mini/overlay return. Preserve independent navigation when speech/provider/device is unavailable.

## Batch 6 — purposeful motion, hints and badges

1. Integrate only mapped motion replacements. Keep the existing166-animation catalog unless a state is explicitly migrated. The new13 clips are small purposeful state assets, not a replacement for every existing FX.
2. Handle the <80dp compact signal face intentionally. Use simplified Lottie when verified acceptable or retain the static/vector fallback. Do not assume a smaller orb automatically plays the new JSON.
3. Use the manifest trigger, loop and fallback contracts. Only visible RESUMED active states animate. Reduced motion, screen off, battery saver, thermal limits and high contrast use static presentation.
4. Bind verified progress to actual completed/total values. Never autoplay the progress clip or infer a percentage from elapsed time. Animation completion cannot dispatch work.
5. Integrate the hint catalog through existing preferences; one eligible unsolicited hint at a time, remembered dismissal, no repeated permission nagging. Help remains rediscoverable.
6. Derive badges from canonical events. Distinguish needs-review, unread, active and failed states. Preserve the orb's voice state while exposing attention on Menu/Activity.

**Evidence gate:** representative real state transitions and reduced-motion fallback; repeated entry does not replay acknowledged hints; the same decision visible in two projections counts once; progress remains truthful during stalls and unknown totals.

## Batch 7 — cleanup and release evidence

1. Remove obsolete fixed roots, duplicate menus, old generic action mappings and unreachable visual wrappers after call-site review. Keep compatibility routes and saved objects intact.
2. Check all 66 registered route patterns against the page plan; cover aliases, conditional Kids/Home/Screen, embedded Settings pages, onboarding stages, sheets and OS return flows. Mark genuinely orphaned sources instead of exposing them to users just to satisfy a file list.
3. Run the registered-feature/ToolOperability review required by the repository constitution. Preserve meaningful typed operations, source revisions, truthful receipts and protected gates.
4. One batched visual/native run for representative root and deep-tool surfaces across normal/large text/IME/light/dark/reduced motion. Fix the actual findings in one implementation batch, then one confirmation pass.
5. Actual speech, physical touch, provider availability and device behavior have separate evidence. Do not label a desktop mock or typed synthetic call as proof of those outcomes.

**Exit:** every page has an owner, navigation path, contextual action set, state handling, accessibility rules and assistant-operability disposition. The user can identify each action before tapping and can always tell what instrument is open.

## Hand-off instructions for the implementation agent

Read in order: `DESIGN.md`, `tokens/design-tokens.json`, `components/contracts.json`, `docs/PAGE_IMPLEMENTATION_PLAN.md`, `docs/MOTION_HINTS_BADGES.md`, `docs/AI_OPERABILITY.md`, then the current repository instructions. Treat route/object/domain identifiers as compatibility contracts. The bundled implementation examples are adapters/reference logic, not a second runtime. Make the global menu functional before removing old navigation. Implement complete batches and verify them at the stated gates.
