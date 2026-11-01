# Halo Zone 5 device polish and Kids parity

Production source: `aa53260fb645` (clean runtime build). Base/main before this work: `01e52a9c43510a80b051134e3009f9418f6a7c20`. The final documentation commit carries these unchanged production sources.

## Design authority and delivered behavior

The retained original Zone 5 handoff, approved mockups and [workspace contract](../design/halo-zone5/WORKSPACE-TOOL-CONTRACT.md) govern this pass. The user's later requests override the pack's thin orb, fixed instrument colours and larger footer. The original asset files are unchanged; EN/RU/LV Zone 5 resources contain 83 matching keys.

| Requested correction | Final implementation |
|---|---|
| Working space and navigation | Normal portrait gives 75% of usable height to work and 25% to the Zone. File identity/status, menus and permanent editor controls live below the work area. Android Back owns return navigation. |
| Compact behavior | Settings, keyboard and short windows use one navigation row. All actions retains scoped Library/Tools switching, utility and Studio overflow commands. Large text has a content-sized accessibility fallback. |
| Orb, colour and files | The older thick ring/glow family is used at authored Zone sizes; compact/reduced-motion paint matches it. All semantic UI and orb paint follow the active palette. File symbols use a distinct theme-derived companion ink; names/type shapes remain visible. Stored artwork/ink retains its content colours. |
| Five workspace instruments | HaloStudio: manual graphics. HaloNotes: text/documents. HaloStudy: learning/sources. HaloMemory: saved context. HaloCanvas: visual ideas across files. Existing IDs and canonical operations are retained. |
| Graphics and tool placement | Studio's fitted artboard occupies the upper editor. File/Edit/Add/View/More expose manual layers, text, shapes, drawing, image import, transforms, history, save and export. AI remains secondary. Notes/Canvas/Memory use the same scoped menu placement and existing form owners. |
| Guidance and wallpaper | Safe, current, enabled actions receive a quiet halo trace; a short helper uses only measured spare space and settles. Input/voice/modal/busy/lifecycle/visual-preference gates suppress coaching. Decorative wallpaper layers interpolate slowly and honour foreground, power, thermal, reduced-motion and contrast restrictions. |
| Kids parity | Hosted Kids identity/status/Type/Help/Stop/parent options move to the Zone; local input stays in the work area and Back closes input then returns to choices. Footer/actions retain 56dp targets, child classic orb assets and active themes. Retained lessons cannot label Home or Create. |
| Child policy | Home/Study remain the only child menu roots. Parent options still use Android device credentials. Source/share grants, child cloud controls, pre-reader/TalkBack, manual creation and Keep/Discard owners are preserved. Adult privacy/mode editing is absent from the child menu. |

New functionality belongs inside an existing instrument family. The user-managed Screen calendar/planning/weather/clock/alarm direction is documented as a roadmap; this UI pass does not invent replacement scheduling or weather backends.

## Verification

**PASS: 639 tests / 147 suites, zero failures, errors or skipped tests.** The complete host test task ran against clean runtime/host-test source `aa53260fb645`. The final successful gate reused those unchanged results after correcting the native fixture, and assembled both APKs. Subsequent commits change Android test capture/verification only; their final test-APK assembly also passed. Production tree identity is recorded in [final-summary.json](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/final-summary.json). The initial fixture compile/density/capture corrections are not presented as production failures or successful intermediate gates.

Reproduce host checks from the repository root with [verify-host.ps1](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/verify-host.ps1). New checks cover quarter-height work space, scoped compact commands, callback invalidation, classic orb/theme paint, file/tool colour distinction, guidance, child targets/Back and retained-lesson identity. Existing child grants, persistence and recovery regressions also run.

**PASS: installation and scoped phone checks** on Redmi 23124RA7EO, Android 15/API 35, 1080×2400 at 440dpi. Clean runtime `aa53260fb645`; final native fixture `9dce7a3bc`.

| Evidence | Result |
|---|---|
| Actual app routes | Five families, system Back, both orb-menu gestures and normal roots pass. Normal work is 1632px and Zone 544px: exactly 75%/25% of 2176px usable height. Settings has one 163px compact row. |
| Actual app adaptive flow | Home IME retains the draft and one navigation row; 200% portrait/landscape, compact Library→Tools and Studio copy/JPEG/discard discovery pass without editing content. |
| Actual app themes | Dark Mint/Natural and Light Rose/Warm repaint tools, tabs, buttons and orb together; durable accent/tone readback passes. |
| Synthetic native renderer | Two instrumented tests pass. Forty-eight animated classic-orb paints cover four states, two authored sizes, three palettes and adult/Kids families. Child fixture checks 25%/56dp targets, Type/Help, scoped Back, gate entry/cancel and real IME. Kids preferences are unchanged. |
| Wallpaper observation | In a 6.99-second sample, 5472 of 31840 decorative edge pixels change by at most one channel value. This is a limited two-frame observation, not performance or power-transition qualification. |
| Restoration | Latest evidenced active Light/Lilac/Natural/Mist, Russian, 1.0 system font, rotation 0 and automatic rotation 1 are restored with readback. The old October light snapshot was superseded; the inactive dark palette remains the last recorded Blue/Cool/Mist, with no claim that its complete November pre-QA baseline was retained. |

See [route results](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/accepted-route-results.json), [adaptive results](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/accepted-adaptive-results.json), [native results](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/final-native-instrumentation.log) and [restoration readback](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/final-restoration.json). The first theme-cycle restore assertion compared a stale October light background and correctly failed; the separate final restoration uses the later visible user selection.

Independent review: **usability 7.3/10; visual clarity 8.0/10**, medium confidence. No P0/P1 source blocker remains. See the [full UX analysis](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/UX-ANALYSIS.md), including measured geometry, sampled contrast, task costs and limits. Remaining minor issues are the orb's missing visible voice caption, long compact command lists and generic Studio error recovery.

Nine sound synthetic captures are packaged. Three Mint fixture captures exhibit incremental screenshot anomalies and are excluded from visual acceptance and publication; their native paint assertions pass, and the complete actual-app Mint image shows correct drawing. This capture limitation is not used to certify unseen states.

## Artifact and limits

Debug app: `releases/halo-alpha-debug.apk`, 291429688 bytes, SHA-256 `7184ec9041f7dd930ce67631ed7a7c7689d43aa96fbfc273116ba4925e920e7e`. The installed binary embeds runtime source `aa53260fb645`, `GIT_DIRTY=false`. Documentation and native-fixture commits do not alter its production source tree.

Selected evidence: [Kids](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/kids-blue.png), [Kids keyboard](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/kids-keyboard.png), [parent gate](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/kids-parent-boundary.png), [classic child orb](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/classic-kids-blue.png), [themed tools](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/accepted-tools-dark-mint.png), [Studio work area](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/accepted-studio-artboard.png), [compact Settings](artifacts/HALO-ZONE5-DEVICE-POLISH-2026-11-01/accepted-settings.png).

Host fixtures and native synthetic rendering are distinct from real phone journeys. No microphone/provider/paid dispatch, spoken acceptance, TalkBack gesture session or successful parent-credential unlock is claimed. The Kids renderer fixture does not enable a real child session or alter Kids preferences. Personal chat/library/memory captures and raw private XML remain outside Git. Packaged captures contain reviewed generic screens or synthetic content only.

The UX review is an expert heuristic assessment, not measured user success, a SUS score or a population study. It records the remaining first-use orb-label and generic Studio error-recovery limitations alongside tested behavior.
