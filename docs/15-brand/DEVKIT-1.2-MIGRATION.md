# Developer Kit v1.2 migration — Batch 01

Authority: `DevKit_v1.2/Implementation.md`, `DevKit_v1.2/tokens.json` and Brand Book v2.1.
Round 2 supersedes Round 1 wherever they disagree.

## Integrated
- Shared component radii: cards 20dp, large/sheet 26dp, form controls 12dp.
- Bottom navigation: v1.2 floating 64dp pill, 12dp outer margins, 32dp radius, accent selection fill, active dot + heavier label; existing navigation routes unchanged. Surface uses compositor-safe transparency rather than mandatory GPU background blur.
- Home app-bar wordmark: supplied dark/light outlined PNG resources; fixed brand colors, no accent tint. Ready-state Aura is gated by real provider, review, voice and session status.
- 58 new FX Lottie JSON files in `app/src/main/assets/halo/fx`, new FX moment wrapper and reduced-motion-aware Compose primitives in `ui/fx/HaloFx.kt`. FX is opt-in; callers must supply real runtime states.
- Six missing v1.2 Android vector icons. Existing 134 icons, standard/kids orb Lotties and fonts already match the DevKit blobs; those identical files are not repackaged.
- Source `DevKit_v1.2` stays in-repository as canonical. `BrandAssets` remains temporarily for historical tooling until migration removes its remaining consumers.

## Not yet finished
- Side-effect-free runtime verification, actual Android build and device visual tests still required.
- Tool and pairing animations have safe phase-clipped APIs (start/working/done and connecting/connected) but are not yet bound to runtime events. Edge pulse, cloud handoff, Guide and other FX must be bound to real events per the motion map. No claims that a decorative effect proves a tool/provider executed.
- Wordmark and UI copy on remaining screens; full Round 2 screen-by-screen overhaul, Kids 2.0, translations and legacy-reference retirement.

## Source asset audit

\`python scripts/brand/audit_devkit_v1_2.py\` checks 140 icons, 60 existing orb Lotties, 58 FX Lotties, source identity and phase markers against canonical v1.2 assets. This is not a compiled-build or device acceptance gate.

## Acceptance gates
```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest
```
Then verify phone screenshots in system/light/dark, all four accents, EN/LV/RU, 320dp and large text, reduced motion, safe-area navigation and background/foreground lifecycle.
