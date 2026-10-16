# Installed APK and literal Brand Book review — 15 October 2026

Final frozen-source verification: **PASS — debug APK built; 85 selected tests, zero failures/skips.** All 1,118 snapshotted main-source Kotlin/XML files remained unchanged through verification. This is a build and bounded UI-verification result, not whole-app brand acceptance.

APK SHA-256: `3ab060989661a6c398039a8132fdde59fae3873831828d7b60ee3a66e7a29c0d`. Installed successfully on the phone and copied to `/sdcard/Download/halo-devkit176-ui-review-3ab06098.apk`; the device-side file hash matches.

[Open the ten side-by-side comparisons](../../../brand-review/devkit176-20261015/index.html). Each image places a direct Brand Book crop beside an unretouched physical screenshot. [Build/test manifest](../../../brand-review/devkit176-20261015/verification.json).

## Evidence and method

- Device: Redmi 23124RA7EO, serial f01fe673, 1080 × 2400 pixels, 440 dpi, Android three-button navigation.
- Base commit: 4528f4900e43c18ec20912ad6fc87af7abd6f909, with the local, uncommitted UI continuation.
- Authority: DevKit_v1.2/BrandBook.pdf pages 35, 38–41 and the matching Round 2 boards. PDF pages were rasterized directly with Poppler; the reference images are crops of those pages.
- Evidence directory: C:/Users/slavd/HaloEco/brand-review/devkit176-20261015. Raw device PNGs and UI hierarchies are retained. The 24-theme images are Robolectric renders of the real Language screen, not physical-device screenshots.
- Side-by-side panels preserve screenshot content and scale both columns to the same width. They are visual comparisons, not pixel-equality assertions: the book uses sample data, a different device frame, and some controls the app does not implement.

## Findings fixed during this review

| Finding from installed screenshot | Change | Verification |
|---|---|---|
| Bottom nav labels overlapped Android's three-button bar in every root view | Apply navigation-bar insets outside the floating pill, retaining its 64 dp height and 12 dp margins | Corrected physical screenshots show labels above the system bar |
| Settings location remained English with Russian app language | Add the three missing location catalogue entries in EN/RU/LV | Final catalogue generation and UI checks |
| Settings header and identity occupied more space than the board | Remove the root subtitle, reduce identity/logo size, combine factual status text, tighten destination rows | Physical before/after captures |
| Reply card had a gray cast and a dark frame | Preserve the canonical surface under elevation, remove reply tonal elevation and the 72% outline-alpha override | Final light reply capture and theme assertion |
| Home wordmark and Updates control were too large; Continue preceded an existing answer | Compact logo and icon control; place Continue after an existing reply | Final Home captures |
| Language search differed visibly from the filled reference pill | Filled secondary surface, visible focus outline, stronger native-language names | Final physical capture and 24-theme renders |
| Render harness never became idle around looping presence artwork | Capture visual tests with reduced motion | Render task completes with 24 image outputs |
| Composer tests expected the previous simultaneous text buttons | Assert the current spoken labels, 72 dp primary target, editable busy draft, Stop behavior and independent voice setup/interrupt action | Focused interaction tests |

## Literal comparison: remaining gaps

| Screen | Assessment against the board |
|---|---|
| Home | Shared colors, composer/mic geometry, compact branding and nav are closer. A long saved answer and its actual model state differ from the book's short example. Durable per-turn execution-location marks and fully correlated tool/overlay moments remain incomplete. |
| Settings | The clipping defect is fixed. Identity and row geometry are closer. Group order/density still differ: Language, Kids and nearby controls add real destinations; Privacy is lower than in the reference. |
| Language | Header-to-search spacing, row height and navigation proportions still differ visibly. The real picker supports English, Latvian and Russian. The board also shows German/Finnish and a reply-auto-detect switch; these are absent, not simulated. The app's font shapes follow the supplied Sora/Atkinson font specification and visibly differ from the raster mock's serif-looking headings. |
| Intelligence | Capture shows the actual setup state, not the reference's configured local model. Connection/model readiness cannot be invented to reproduce the example. Card balance and disclosures still need refinement. |
| Devices | Capture has a saved, disconnected device, rather than the reference's ready glasses. Battery/signal/ready-session layout is not accepted by this screenshot. Routing summary is factual and opens the existing sound-settings route. |
| Notebook | Four entry actions, a real saved Study continuation and the real library/empty state are visible. No pending MEMORY proposals were present, so the memory tip's nonempty physical state was not exercised. |
| Study | Goal-to-Material navigation is exercised without starting generation. Material choices and real four-stage setup are compared with page 39. Imported-material review, active lesson motion and completion were not physically exercised. |
| Activity | Three segments and real durable runs are visible. Some paused runs remain under the existing Running projection; event grouping, row density and exact action-plan parity remain open. No side effects were approved for visual matching. |
| System check | Capture and Refresh inspect the actual readiness projection. This is not a passing hardware, speech or inference probe. The board's five-item summary and best-fix card are not fully reproduced. |
| Voice | Actual saved speech/input/output state is visible. No microphone recording or remote speech request was initiated. |
| Pairing | The search-ready screen and stage path are visible. The reference's device silhouette/checklist composition still differs. Scanning, connection completion, code confirmation and rename were not physically exercised. |
| Canvas, Kids, approval, acting overlay | No physical signoff in this pass. Existing tests/static resources do not establish these device states. |

## Acceptance boundary

Build success, passing interaction tests, drawable inflation and 24 Language-theme renders do not establish whole-app pixel-perfect parity. The comparisons show remaining composition and capability gaps explicitly. No overall compliance percentage is assigned.

## Executed physical checks

Main tabs, Add device/back, Intelligence/back, Voice/back, Language/back, and Study Goal-to-Material navigation reached their expected destinations. Dark screenshots cover ten root/detail destinations plus Language and Study setup. Light Settings/Language and Russian Language at 1.5 font scale were also captured. No recording, remote generation, pairing completion or action approval was triggered. The final crash-buffer snapshot contains an older 17:11 app native crash; no new app crash appeared during this review.

The 1.5-scale Russian capture keeps Language content readable, but all five fixed-height bottom-navigation labels ellipsize heavily. This is an outstanding accessibility/layout issue, not an accessibility pass.

Original phone preferences were restored and the saved preference maps match: Light mode, Warm/Lilac light palette, Natural/Coral dark palette, Russian app language and font scale 1.0. Speech-recognition language remained Russian. Changes remain local and uncommitted.
