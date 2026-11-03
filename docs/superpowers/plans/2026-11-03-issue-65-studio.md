# Issue 65 Studio implementation plan

> For agentic workers: use superpowers:executing-plans inline. The user's coherent-batch/no-build-after-each-edit instruction overrides per-step test execution; author meaningful regression sources first, then execute one serial gate. No implementation delegation.

Goal: complete the unified Design/Paint/AI Studio workbench with real editable operations and Zone 5.0 UX.
Architecture: extend canonical StudioDocument/Codec/Command/Session/Renderer and existing StudioService, ToolRegistry, UI/Orb and image review owners.
Tech: Kotlin, Compose, Android Canvas/Bitmap, Room, portable Ink, existing governed image providers.
Spec: docs/superpowers/specs/2026-11-03-issue-65-studio-design.md and docs/studio/ISSUE-65-SOURCE.md.
Base: cd08e9ad6. Branch codex/issue-65-studio.

## Global constraints

- One instrument, same layers and revision owner in all modes; switching modes is presentation only.
- Keep legacy projects/history/archive, exact CAS/source identity and consent gates.
- No paid/new service dependency; no invented unsupported provider or brush-engine behavior.
- Source/regression batch before focused tests/build; Gradle max-workers=1, no parallel, one test fork.
- Upper working area is artboard; four clear primary groups live in Zone. All commands retain accessible labeled alternatives.

## Review focus

- Locked ancestors and groups must prevent mutation through every UI/typed path.
- Group duplication/reorder/removal and undo must preserve hierarchy and selection identity.
- Cancelled/revoked/stale image operations cannot publish to a newer revision or silently overwrite originals.
- Bounded memory and seeded effects must match preview/export and release only owned bitmaps.
- Gesture viewport transforms must not change documents, and touch edits must use exact revision fences.

## Tasks

- [ ] 1. Model/codec/commands: extend StudioDocument.kt and StudioDocumentCodec.kt; new StudioCreativeModel.kt and StudioLayoutOperations.kt. Add schema compatibility, typography/crop/blends/effects/textures/groups, group-safe commands and regression sources.
- [ ] 2. Rendering: StudioRenderer.kt plus StudioEffectRenderer.kt and StudioTextureRenderer.kt; real bounded seeded effects, crop, typography, blending/group compositing, cancellation. Author pixel-output and provenance regressions.
- [ ] 3. Typed parity/presets: StudioEditRequest.kt, StudioToolExecutor.kt, operability/result contracts, closed schemas for all new commands and bounded metadata readback; expand local preset contracts and regressions.
- [ ] 4. Zone shell/Design: StudioScreen.kt, StudioWorkbench.kt, StudioCanvasSurface.kt, StudioCreativePanels.kt and layer manager; four orbit groups, mode selector, selection and gesture tools, contextual inspectors/arrange/styles and accessibility.
- [ ] 5. Paint/effects/AI: brush presets/settings/picker/fill/selection, effect/texture editors and AI context/destination, real provider capability gating with existing consent. Retain source/version continuity and copy recovery.
- [ ] 6. EN/RU/LV/PL copy, UI/behavior/Room/archive regressions, acceptance/operability evidence map and final whole-batch review/corrections. Then one-worker focused gate and APK/device evidence when host permits; no false completion claim.
