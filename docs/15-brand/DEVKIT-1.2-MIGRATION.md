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

## Round 2 · Activity and answer surfaces (this batch)
- Activity uses Needs you / Running / Done tabs, with truthful pending and review counts; the active tab is chosen from current durable evidence until a user makes a selection.
- Per-run cards show status with icon, state label and relevant error/attention color. Apricot marks outstanding user action, not progress.
- Grouped timeline events no longer report success when only start/request evidence exists. Added regression tests for pending and failure evidence.
- Activity timestamps follow the selected Compose app locale rather than a fixed device-system HH:mm pattern; added test cases for 12h/24h locales.
- Reply cards use lowercase brand voice and no longer label old replies with the currently selected provider.
- The full static PDF comparison uses owner supplied Brand Book v2.1: page 37 Home, 40 Activity, 42 reusable UI parts; no emulator/pixel-sign-off claimed.

## Round 2 · Home handoff and shared button shape
- Home uses the supplied lowercase wordmark without a redundant section label, with an actual Updates route button.
- An outstanding approval/clarification is shown as Apricot "needs you" with a working Review in Activity action; merely opening Activity does not grant approval.
- Outdated Home provider/brand caption no longer competes with the primary state; setup attention remains explicit.
- Shared actions now use full-round buttons; STOP is neutral outlined (red is reserved for failures). Inputs keep their separate 12dp field geometry.
- Floating navigation uses the DevKit 82% translucent surface and 18% selected tint, keeping the no-blur low-power fallback.
- All new Home and Activity copy added to generated EN/RU/LV resources using reviewed ui.tsv.

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
