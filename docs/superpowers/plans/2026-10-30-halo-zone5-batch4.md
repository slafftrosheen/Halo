# Halo Zone 5 — batch 4 instrument migration

Base: `70ee2a113915ea3a046d48a7fc21b92218169624`. Branch: `codex/halo-zone5-batch4`, reusing the clean isolated Halo-zone5 worktree and its Android host build setup. The user requests Notes/Office, Canvas/Studio, Study, Kids and Memory, host checks only, then merge to main and push.

## Authority and boundaries

Follow `docs/design/halo-zone5/DESIGN.md`, roadmap batch 4, `component-contracts.json`, the original pack's `docs/AI_OPERABILITY.md`, page-plan R004/R007–R009/R012–R015 and E004/E018–E031/E037–E046. Existing asset manifests and approved reference images remain authoritative. The user's reactive appearance requirement overrides fixed mint/suite colors. UI paint follows current Material roles; document pixels, ink, represented swatches and semantic error/attention retain their actual meaning.

This is a host presentation migration, using existing domain owners and registered typed operations. Keep object/layer/block/session/source IDs, versions, confirmation, replay protection, scope, privacy, Kids, execution choice and paid-provider admission. Android picker/credential and human freehand gestures remain explicit protected host exceptions. Do not redesign runtime, providers, schemas, persistence or hardware protocols. Preserve embedded research/media/source/handoff entry and return paths within these five instruments; separate root migrations remain later roadmap work.

## Task 1 — characterize the gaps

Write grouped regressions against existing presentation/lease APIs and canonical-owner fixtures before changing production. Cover native semantic action IDs, visible Back and narrow/large-text instrument identity; Notes Insert/Outline/Find; Office type-aware export; Canvas Add/Arrange/Fit; Studio Add layer/Layers/Export; Study step-specific actions and active-session scope; Memory visible local tabs and a single Remember action. Watch the relevant new assertions fail for the intended old UI, retaining existing passing source/save/cancel/selection tests. Author real owner success and stale/unavailable/recovery coverage where not already present.

## Task 2 — complete the instruments, in order

Shared presentation: reusable Zone 5 instrument identity/state, readable local tabs, native semantic assets and a labeled Back in the existing utility band. Keep direct Save/Stop/Undo and selection controls reachable; adapt large text and IME without shrinking text or targets. Add localized EN/RU/LV labels only where needed.

Notes/Office: identify tool/document/source and true save state in the work area; Notes defaults Insert/Outline/Find and keeps formatting/Undo/Redo/selection direct. Preserve canonical note drafts, CAS/proposal/recovery and literal file modes. Office visibly opens its supported task/document list; New/Open/Export use existing type-specific owners, never a generic unsupported export. Keep text/Markdown/CSV editing, PDF reading/page extraction/annotation, deterministic image/batch/collage, receipts and external picker/share returns truthful.

Canvas/Studio: show canvas/project identity and selected source/layer context. Canvas defaults Add/Arrange/Fit, preserving board gestures and selected-node operations. Studio defaults Add layer/Layers/Export, retaining direct dirty Save/Undo/Redo and readable layer/property/ink/transform/history/output flows. Preserve exact source/page/crop and expected revision for all draft/apply/export operations; cancellation creates no output. No provider dispatch from appearance, opening or animation.

Study/Kids: Study landing offers New lesson; the four-step draft exposes only relevant Material/Options actions. Active lessons show Sources/Practice/Read aloud through the same lesson/exercise owners, with direct Check/Hint/Next/Stop and truthful measured progress. Preserve explicit execution choice, source anchors and scoped narration/answer leases. Kids retains large labeled local choices, one scoped orb, no default adult satellites, protected parent controls, child allowlists and calm feedback from actual progress. Use native Zone 5 assets and current theme roles throughout the changed controls.

Memory: visible Memories/Suggestions/Learning history local tabs, a single Remember default and exact selected section on return. Keep provenance/scope and suggestion versus saved-fact status explicit. Candidate review/edit/accept/reject and detail source/edit/forget use existing owners, actual revision/admission and confirmation. Loading/empty/failure/retry and missing sources remain distinct; no silent capture or approval.

Complete all production and regression source before the grouped host gate, per repository directive 19. No device polling or provider requests.

## Task 3 — verify, review, record and publish

Run focused instrument UI/owner/typed/source-version/receipt/persistence suites plus shared shell/theme/registered-feature audits and APK assembly. Inspect native-renderer captures for all five owners and representative embedded editors in non-default dark/light themes, EN/RU/LV, narrow/large text/IME/static orb. Include one actual success and blocked/recovery case per owner; distinguish callback/navigation evidence from domain effects. Record exact results and production/APK identities, keeping physical touch/TalkBack/spoken/provider results NOT_RUN.

Run one fresh-context whole-branch review under executing-plans. Regrade by user effect, fix material findings with RED-to-GREEN regressions, and record boundary rulings. No per-owner reviewer or re-review. Commit implementation/evidence, fast-forward clean synchronized main and push as explicitly requested. Verify remote SHA, main divergence `0 0`, and clean linked worktrees; keep the debug APK available in the primary checkout.

Review focus: stale entry/action/source/layer/exercise leases; instrument identity versus current actions; save versus export/preview truth; failed-load empty states; hidden mutations or provider calls; unsaved/cancel/undo behavior; scoped source/page/crop; child/adult action isolation and parent gates; suggestion approval/forget consequences; native artwork caching after palette changes; long labels/200% text/IME/clipped direct controls; embedded sheet and external picker return paths; unsupported Office format claims.
