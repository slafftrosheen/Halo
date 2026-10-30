# Halo Zone 5 shared shell — implementation evidence

Date: 30 October 2026. Branch: `codex/halo-zone5-shell`. Implementation base: `e2fbba32395e65612dae8bb365c50404f0cca60e`; pack audit base: `4a227e33eb8e6812ad30481b36e0ceb0f17f4cbd`. The pre-existing Study changes are preserved. This is the asset/shared-shell milestone; roadmap batches 3–7 remain subsequent work.

## Production owners and scope

`AppShell` supplies the exact active entry, current page-action projection and six adult root destinations. `HaloZone` owns layout and transient disclosure metadata. `HaloZone5OrbLauncher` renders Menu and the distinct All actions list. `ContextToolOrbit` presents at most three manually scrolled, labelled actions and scopes its position to entry/instrument identity. Utility Back remains directly available. Destructive commands and shared context/typing utilities stay in the full list, outside the default satellites. An absent adult page-action lease shows no generic tool catalog fallback. Menus and scrolling do not read a source, attach context or execute a command.

Voice continues through `HomeOrbControl`/the existing scoped Kids override and the original `HaloOrbInteraction`. The normal tap still dispatches TALK/FINISH/STOP/INTERRUPT/REVIEW to those owners. The profile control is the existing `PrivacyModeControl(detail = true)` with its existing mode-change/acknowledgement and Kids gates; the dock shows only a read-only projection. No profile, event, draft, tool registry or cognitive session store was added.

`ShellPageProjection.invoke` retains the current entry/object/source/metadata lease checks. `AppShell` still rejects removed, changed or disabled page actions before invoking that projection. Visibility grants neither document access nor consent. The existing explicit page-context action retains its canonical version checks and cancellation owner.

The semantic adapter covers all 55 legacy glyphs and 187 canonical native vectors. Contextual overrides distinguish new conversation/history/attachment/import/context and named instrument actions. Existing fonts, theme and visual preferences are reused. All 187 imported XML files and 26 JSON compositions parse and match their manifest-listed supplied bytes; the unrelated legacy icon is preserved.

Only ongoing capture/thinking/speaking use the new shape compositions at 64/72dp. The 48dp Zone 5 path is static. Reduced motion, higher contrast and lifecycle/power restrictions select static treatment. Legacy presence surfaces keep their 80dp threshold. Measured progress never autoplays an indeterminate clip; result/decision one-shots await event-identity integration.

## Theme follow-up

The user's follow-up overrides the supplied pack's fixed-mint orb rule. Orb paint now follows the same existing Material palette as controls and surfaces. This includes compact/reduced-motion faces, Zone 5 and legacy Lottie compositions, SVG effects, wordmarks, native nearby/widget/floating paint and child/Lab presentations. Shared cards, links, status marks, assistant edges and approval/attention colors use theme roles. Error and attention retain semantic roles; color-choice swatches and saved artwork retain their represented colors.

The artwork adapter changes only color data. It preserves Lottie geometry, timings, gradient-stop count/positions and opacity, and SVG geometry/opacity. Palette repaint creates a fresh composition so cached gradient shaders cannot keep an old accent. A same-state repaint retains the mounted composition while loading and keeps playback progress; a theme change does not invent a new event. A reviewed two-color callback approach was removed after a real native-renderer regression reproduced an exception for three-stop gradients.

Pixel tests reproduce the old fixed-mint failure, then check both 72dp Zone and 160dp legacy static faces against BLUE/COOL/light, ROSE/WARM/light and SAGE/SLATE/dark for idle/capturing/thinking/speaking. Animation tests render a Zone shape composition and a legacy gradient composition while switching BLUE → ROSE → SAGE. Additional checks preserve three-stop gradient/opacity data, recolor SVG paint, and parse the authored motion assets after recoloring.

The initial shell APK was installed with `adb install -r` on Redmi `23124RA7EO` / `f01fe673`, package `com.aistudio.braintool.xkqzps`, preserving app data. Through the real Appearance UI, Rose was applied and read back from the existing preference owner; a screenshot reproduced Rose controls with the old Mint orb. The phone then disconnected. The user explicitly chose **Continue with host checks only**. Installation/native verification of the final themed build is therefore **NOT_RUN / deferred by user**, and no device polling continues. The disconnected phone remains on the temporary DARK / ROSE / WARM / STILL test palette. The original DARK / MINT / WARM / STILL setting must be restored through Appearance when device work resumes; light preferences were not changed (CORAL / COOL / HALO).

## Typed operation contract and registered-feature review

Internal navigation remains `AssistantTools` (`halo_list_screens`, `halo_open_screen`), with the original closed argument schemas, explicit exact destinations, NEVER_REPLAY policy and DISPATCHED receipt that does not claim a rendered page. Current-page discovery remains `CurrentScreenTools` (`halo_current_screen`), which returns bounded metadata only under its existing foreground/local scope and permission checks. Meaningful editing/study/canvas operations remain registered with their existing owners and unchanged schemas.

`HostFirstSkillAuditTest` checks registered-feature discovery/ToolOperability and protected confirmation/replay policies. `AssistantToolsTest`, `CurrentScreenToolsTest`, `CurrentCanvasContextOwnerTest`, and `ShellPageActionsTest` cover exact navigation, no invented object routes, stale or revoked scope, truthful receipts and current-page lease behavior. Disclosure and a human drag are host presentation affordances, not new domain tools. Their test callbacks establish synthetic dispatch only.

## Verification gates

Focused edge regressions also reproduced the child Study selection, destructive satellite promotion, stale disclosure across same-pattern entries, a 1.482:1 light static-ring contrast, and a three-line Russian compact label.

The initial four interaction regressions failed against the old fixed-root shell at the expected missing Menu/All actions/global destinations. The first integration run passed 13 tests: four interaction, six catalog, three motion-policy tests. A subsequent regression pass reproduced carousel position leakage between owners and an 86dp orb displacement when actions became empty; both are covered by dedicated interaction tests.

The shared-shell gate passed **158/158** tests with zero skips and built the debug APK. After the theme follow-up, the broad gate ran **277** checks: **275 passed**, and two new animation waits timed out. Those waits did not drain Robolectric's paused Android main handler, where LottieTask delivers its parse result. The test fixture now explicitly drains that handler and waits for Compose idle; the focused animation suite passed **5/5**, keeping the same composition-identity and rendered-pixel assertions. Production source and APK bytes did not change between those runs. The latest result for each of the **277 unique checks is PASS**, with zero skipped checks; this is a broad run plus a focused recheck, not a claim that the initial broad invocation was green.

APK: `app/build/outputs/apk/debug/app-debug.apk` (also `releases/halo-alpha-debug.apk`), 291,387,092 bytes, package `com.aistudio.braintool.xkqzps`, SHA-256 `3058110259a19dd9c784880b837df5205788741954f01ce01068238ff74582c5`. The focused Gradle invocation completed **BUILD SUCCESSFUL**. Build output includes existing Android/Kotlin deprecation warnings and existing native-library strip notices; they are not physical-device evidence.

Exact tasks/filters, suite counts, verification sequence, APK identity and production-source hashes are in [theme-green-summary.json](artifacts/HALO-ZONE5-SHELL-2026-10-30/theme-green-summary.json). The initial timeout results are retained in [theme-broad-summary.json](artifacts/HALO-ZONE5-SHELL-2026-10-30/theme-broad-summary.json); the earlier shell gate is in [shell-green-summary.json](artifacts/HALO-ZONE5-SHELL-2026-10-30/shell-green-summary.json). Imported-resource provenance is in [resource-integrity.json](artifacts/HALO-ZONE5-SHELL-2026-10-30/resource-integrity.json).

Inspected host renders: [normal footer](artifacts/HALO-ZONE5-SHELL-2026-10-30/host-normal-footer.png), [Russian at 200%](artifacts/HALO-ZONE5-SHELL-2026-10-30/host-russian-200-percent.png), [Latvian large text](artifacts/HALO-ZONE5-SHELL-2026-10-30/host-latvian-large-text.png). The large-text All actions row remains full-width above the voice/Menu dock. [Device before the theme fix](artifacts/HALO-ZONE5-SHELL-2026-10-30/device-before-theme-fix.png) records the initial mismatch only; it is not final themed-device evidence.

| Acceptance layer | Disposition |
|---|---|
| Supplied ZIP CRC and native resource import/preflight | PASS |
| Imported native XML/JSON syntax and byte provenance | PASS |
| Android compilation and focused host regressions | PASS; split broad/focused sequence above |
| Registered-feature/ToolOperability review | PASS |
| Native-vector host renders and adaptive footer bounds | PASS |
| Static orb, shape/gradient motion and SVG theme repaint | PASS on host; final device acceptance deferred |
| Initial shell install and native reproduction of fixed-mint mismatch | PASS; superseded pre-theme candidate only |
| Final themed APK install/native journeys | NOT_RUN; deferred by user after disconnection |
| Physical touch, actual keyboard/OS return journeys, TalkBack focus | NOT_RUN |
| Actual microphone/STT-final, audible response, remote provider/device behavior | NOT_RUN |
| Complete 130-entry page migration and final RU/LV linguistic review | Subsequent batches |

## Functional acceptance boundaries

| Workflow | Synthetic host | Typed on device | Spoken on device | Permitted Power | Durable domain readback |
|---|---|---|---|---|---|
| Deep page → global menu → exact root | Host regression | NOT_RUN | NOT_RUN | NOT_RUN | Navigation only |
| Current page action → existing guarded owner | Lease/owner regression | NOT_RUN | NOT_RUN | NOT_RUN | Existing owner contract; no new mutation |
| Orb tap versus hold/upward swipe → existing voice owner versus disclosure | Synthetic gesture regression | NOT_RUN | NOT_RUN | NOT_RUN | No speech acceptance claimed |

The remaining Home/Library/Tools/instrument migrations, one-shot feedback, hints and deduplicated attention badges were explicitly deferred to roadmap batches 3–7. Physical/native speech and provider checks need their actual dependencies. The imported animation files are available resources; installing them does not establish native playback acceptance for all 26 compositions.
