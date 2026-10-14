> **Current status (2026-10-14):** This file records the provisional first pass and is superseded for present-tense implementation status by [ANDROID-BRAND-PARITY.md](ANDROID-BRAND-PARITY.md). The exact HaloWeb palette, traced ring/wordmark and licensed Sora/Atkinson/Onest fonts are now bundled. Gradle/emulator and original-PDF pixel sign-off are still pending.

# Android brand redesign — first integration pass (2026-10-14)

## Source materials and authority

Root artifacts: `HaloBrandKit (1).pdf`, `HaloBrandKit (1).png` through `(4).png`, and `HaloDesignMockup.png`. Preserve originals unmodified. The new kit supersedes historical design drafts for final visual specifications. The older `docs/15-brand/BRAND-IDENTITY.md` and workspace spec establish engineering direction but are **not** a substitute for visually inspecting the newly uploaded kit.

**Verification constraint:** an earlier brand review describes open mint ring, deep navy and cream, Sora headings and Atkinson Hyperlegible Next body. The GitHub connector returned metadata and blob SHAs for these binary files but no decoded PDF/image bytes. This pass does not claim pixel-accurate colour, logo, typography or image matching to the new kit. Do not infer exact values from filenames or old mockups.

## Implemented in this pass

- Deep-navy and cream canvas/surface hierarchy (provisional visual-match values) instead of green-tinted base surfaces; existing user-selected accent/tone preferences remain wired and unchanged.
- Material 3 semantic container levels for legacy Glass components: lowest, low, regular and high, without pretending alpha overlays are real blur.
- Unified bottom navigation ground plane and divider, shared header action touch target, display typography, and Home presence status card sizing.
- Halo Home branding replaces generic companion headline; preserved all runtime-state labels, provider recovery, text submission, permissions and navigation callbacks.
- Mini/widget background adjusted to the same shared graphite surface scale.

## Next visual-verification pass

1. Open the root PDF and all reference PNGs locally; record the exact primary/secondary colours, logo construction and clearspace, typography and component motifs. Replace provisional token values with measured kit values.
2. Check light/dark, system font 100/150/200%, 320/360/412dp phones, tablet rail, EN/LV/RU labels and TalkBack.
3. Verify Home, Activity, Notebook, Canvas, Study, Devices, Settings, onboarding, Mini Halo and widgets screenshots against the supplied compositions.
4. Package the actual Sora and Atkinson Hyperlegible Next font assets with verified licences and check the open-mint-ring logo against the original kit artwork. Do not redraw a close approximation as an approved brand mark.
5. Run Android unit tests, Compose screenshot/regression checks and device builds. No Gradle build was executed in the remote-connector-only pass.
6. Keep runtime, model/provider state and hardware safety separate from decorative semantic design.

This is an incremental app redesign commit, **not** a completed brand-compliance sign-off.

## HALO-BRAND-003 to 012 — screen-by-screen integration (2026-10-14)

The following is a **first code-integrated visual migration**, not final approval against the uploaded PDF/PNGs. Source files were inspected before edits. All changes are limited to presentation components, layout, semantic controls and state indicators; the existing runtime and device action wiring remain in place.

| Surface | Implemented in Compose | Behaviour that must remain intact |
| --- | --- | --- |
| Home | 20dp content inset, breathing room, outlined 48dp Notifications action, inherited navy/cream status and card scheme | Ask/voice/stop, session-state glyphs, drafts, provider recovery and capture |
| Activity | Compact 20dp inset, multi-line filters, clearer review heading, aligned card surfaces and focused-run outline | Timeline, provenance, action outcome truth, pending confirmations and filtering |
| Notebook | Larger library heading, padded search/collection panel, consistent accent cards, larger tool pictograms | Persistent notes, collections, bulk actions, context selection and importing |
| Canvas + note editor | Shared surfaced editor header with 48dp back/tools controls; actual board card/edge count and viewport zoom visible without expanding tools | Pan/zoom/drag, undo, selections, file and voice imports, image/AI tools |
| Study | Higher-contrast study-step cards, spaced lesson sections, increased live-learning guide padding | Source coverage, practice/review, audio stop, safety of explanations and answer gating |
| Devices | Consistent card tiers, connected/saved group spacing, readable live connection status | Pairing, auto-connect, Bluetooth and BuddyBot hardware safety |
| Settings | Shared surface/card hierarchy, harmonized appearance segmented options | Appearance and palette persistence, provider/voice/privacy setup, EN/LV/RU |
| Guided setup | Cream/navy stage canvas, stage counter and per-stage indicator, fixed action footer, branded selected radio rows with actual RadioButton | One-time setup semantics, stage persistence, permission denial, inference/audio checks and limited-mode escape |

### Required acceptance tests before releasing

- Compile and test: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`. Build was not executed during these GitHub-connector commits.
- Review existing Robolectric/Roborazzi `HaloVisualLanguageSnapshotTest`, `PaletteVisualTest` and `PaletteTest`. Refresh screenshots only after reviewing diffs; do not blindly overwrite baselines.
- Screenshot all eight surfaces plus Canvas with keyboard open, dark/light/system themes, 320dp and 412dp widths, 200% font scale and EN/LV/RU.
- Verify TalkBack focus order, 48dp+ actionable touch targets, physical-device statuses, reduced motion and real provider failure/recovery.
- Inspect `HaloBrandKit (1).pdf`, the four matching PNG pages and `HaloDesignMockup.png` in a local visual workflow; the connector only exposed metadata for binary assets. Confirm exact brand colours, open-ring icon and typography. Do **not** declare them implemented from the approximate source tokens alone.
- Obtain properly licensed font distribution assets before applying Sora/Atkinson globally; do not substitute invented font files.

### Out-of-scope for this batch

Logo/icon replacement, all secondary settings screens, detailed device controllers, and every state of every dialog and overlay. Those require their own visual pass and device validation. No Android UI build, emulator test, screenshot comparison or manual hardware test was performed in this environment.
