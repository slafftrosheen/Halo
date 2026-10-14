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
