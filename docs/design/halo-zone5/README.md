# Halo Zone 5 shared-shell integration

Source handoff: `Halo_Zone_5_Asset_Pack.zip` (30 October 2026), SHA-256 `105831ba90909dd0e303590266117f3d0479a1b896f1987c08bf9a0404497404`.
The original archive remains in Downloads; an unpacked catalog and references are at `C:/Users/slavd/HaloEco/zone5-handoff/Halo_Zone_5_Asset_Pack`.

The accompanying design, roadmap, manifests, tokens and attribution files are preserved source documents. Their validation claims describe the supplied asset pack. Android integration evidence is recorded separately in `docs/testing/HALO-ZONE5-SHELL-2026-10-30.md`.

This milestone integrates 187 native VectorDrawables, 26 Lottie resources, the exhaustive semantic icon adapter and the shared shell. Existing Sora/Atkinson/Onest fonts, theme preferences, profile control, navigation, speech and current-page action owners are reused.

Adult global destinations are HaloScreen, Home, Workspace, Devices, Activities & notifications, Settings. Child mode retains the existing Home/Study allowlist. Menu, orb hold and upward orb swipe disclose the same destination sheet. All actions is a separate list of the current entry's actions. A large-text/IME/short-window layout keeps voice and labelled disclosures reachable without shrinking targets. Only ongoing capture/thinking/speaking motion is mapped to the new 64/72dp path; compact 48dp and restricted motion are static. Result/decision one-shots remain unmapped until canonical event identity is integrated.

The shell milestone does not establish full 130-entry route coverage or physical-device acceptance. Russian and Latvian copy requires native language/layout review before release.

Batch 3 now migrates Home, HaloScreen and the explicit Workspace Library/Tools views, including their existing instrument entry/return paths. Implementation, canonical-owner boundaries and host verification are recorded in [the batch 3 evidence report](../../testing/HALO-ZONE5-BATCH3-2026-10-30.md). Complex instrument editors, Settings/onboarding, guidance and badge migrations remain roadmap batches 4–7.

User override, 30 October: appearance applies to the orb and colored interface elements, superseding the source pack's fixed-mint rule. Static/native and animated artwork now derive paint from the existing theme roles. Gradient stops, geometry, timing, opacity and semantic error/attention meaning remain intact; color swatches and user artwork keep their represented colors. The user deferred final phone testing and selected host checks only after ADB disconnected; final native acceptance remains open.
