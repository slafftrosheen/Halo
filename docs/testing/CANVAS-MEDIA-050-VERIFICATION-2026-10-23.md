# Canvas media previews — issue #50

Canvas image and PDF cards now give the available card area to an aspect-fit preview. A compact header keeps selection, explicit full-screen inspection and the existing Open source action separate. Read-only inspection offers pinch/pan, bounded zoom, Fit, and page navigation for PDFs. Source identity/version changes dismiss the previous inspection. The original document and its ownership remain unchanged; detailed editing belongs to Studio (#51).

Canonical Workspace files and their annotations take precedence over duplicate attachment projections. An unavailable canonical file cannot fall back to stale attachment pixels. Existing normalized ink uses the exact fitted image rectangle. Existing board/card geometry, selection, long-press drag and pan owners remain in place.

Crowded boards admit at most 16 visible thumbnail sources, prioritize the most recently selected card, and omit offscreen/invalid candidates. Decodes run asynchronously through a shared two-operation gate; card edges are bounded to 512 pixels and explicit inspection to 1536. Owned bitmaps are released on replacement/culling/cancellation. This bounds decoder concurrency and per-image pixels; it is not a measured whole-board memory/performance guarantee.

EN/LV/RU labels are supplied together. Native regression sources cover actual image fitting, canonical file/legacy ink alignment, PDF page pixels/count, source immutability, deferred loading, selection/drag distinction, source replacement, light/dark, extreme aspect ratios, and 200% text. Pure policy regressions cover admission limits and transform bounds. Android fixtures exercise the real image/PDF decoders with temporary synthetic files.

## Verification status

Implementation and regression sources were completed before the final focused serial gate. Read-only review found no actionable P1/P2 findings. Resource XML parses with all eight keys matching across EN/LV/RU, and the diff whitespace check passes.

Final results are recorded below. No GitHub Actions or live provider requests are part of this gate.

Broader physical Canvas interaction, stylus/tablet/TalkBack and sustained board memory qualification remain distinct from the bounded decoder fixtures. Original source metadata and ink geometry are preserved; this issue adds read-only presentation over existing source access rather than a new data-editing or assistant execution path.

The first gate assembled both APKs and ran 27 desktop cases: 24 passed and three annotated-source fixtures failed. Two attempted to call Android PDF natives absent from the installed Robolectric shadows; one relied on an unregistered ShadowContentResolver file stream. The image fixture now registers a fresh stream supplier for each decoder pass. PDF inspection coverage moved to executable Android instrumentation rather than substituting fake PDF pixels. A follow-up dependency-resolution attempt caught missing Compose BOM alignment on the new Android-test dependency; this was corrected before compilation. Production source was unchanged by these fixture repairs. The first real-phone decoder run passed both cases, with installed hash matching the assembled app; final combined results supersede that run below.

A later gate attempt stopped while writing the shared Gradle build cache, held by a separate Halo-main-hml build. The retry disables build-cache use for this invocation only; the other checkout/process and project source/configuration are left unchanged.

The image timeout persisted after the explicit stream supplier, so the initial resolver diagnosis was insufficient. The fitted image/ink tags are children of the clickable card's merged semantics node. The geometry assertion now explicitly queries the unmerged tree, without weakening the pixel/aspect/source checks or changing production composition. The second completed desktop run passed 24 of 25 tests; its only failure was that tag lookup. The real Android run passed all three cases, including PDF inspection with actual 200% text. Its synthetic PDF capture was visually inspected: page 2, navigation, fit/zoom/source/close controls and safe insets are visible in the phone's landscape viewport.

## Final measured results

Final focused serial gate: **BUILD SUCCESSFUL, 2m 52s**, both APK assembly tasks successful (cached from the preceding successful assembly). **25 desktop tests / 7 classes**, zero failures/errors/skips, and **5 native renders**, visually inspected. The final Android test package passed **3 real-phone cases** on model 23124RA7EO / API35. The PDF UI case verified actual Russian 200% text and page 2 of 3, Fit and Close; the landscape capture was visually inspected. Synthetic source bytes remained unchanged and temporary input/capture files were removed. No user Workspace data or OS preferences were changed.

Installed app: **289,019,053 bytes**, SHA256 `28349e247104d78d174e23f755ba2240a189545809a6a13979165135c919f67d`, matching installed base.apk read-back. The manifest records the exact compiled app tree. Only test fixture/build-dependency sources changed after this app assembly; the production app binary remained identical. Whole-board frame times, sustained memory, tablet/stylus and broader accessibility qualification remain in #43.

Main advanced independently with lab-bridge commits a9d41429 and d1c87d58 during validation. Their six files do not overlap this issue's source changes. This report and manifest qualify the recorded issue-50 app snapshot; integration of the separate upstream changes does not imply a new combined-binary device run. The next issue's final gate will compile the integrated source. No redundant full rebuild was performed solely for those unrelated commits, following the user's request for less building.
