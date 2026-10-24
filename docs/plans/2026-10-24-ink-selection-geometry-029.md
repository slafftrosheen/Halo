# #29 exact selected-region geometry

Base `1929bba08e29e2b33f07d8a79bd8176f03ba216a`, fetched main0/0 and clean before edits. Existing Canvas/Notebook owners and revision/privacy checks remain authoritative.

Confirmed source bug: sketch crop dimensions clamp derived region aspect and minimum dimensions, then map the selected bounds across the whole result, stretching extreme selections. Legacy aspect0 skips bounds ratio entirely. Preview changes point coordinates and clamps/stretches separately, so it can disagree with the attached region.

Use one host geometry helper: source aspect (legacy fallback1.4 explicitly approximate) times selected normalized width/height, fitted uniformly into a bounded viewport. Keep bitmap96..768 and letterbox extreme aspect instead of distorting. Preview draws unchanged selected source strokes in the source rectangle implied by the fitted crop and clips to the exact crop. Renderer uses the same fitted rectangle. No saved document migration, recognition, cloud, additional selection/approval owner, or new assistant operation.

Keep source page/image crop alignment on the existing rounded source rectangle. Do not apply sketch letterboxing to page backgrounds independently. Add pure geometry regressions for portrait/landscape/extreme/legacy and native bitmap cases for the resulting placement, omission of unselected marks, boundary clipping and source immutability. Complete source/tests before the next focused gate; do not repeat unrelated suites.

Review of proposed geometry confirmed the defect and minimal approach. Physical visual/voice/pen acceptance and #29 stay open.

Actual base1929bba08e29e2b33f07d8a79bd8176f03ba216a, fetched0/0clean before edits. Implementation uses shared aspect/frame/source-rectangle geometry. Preview preserves original selected points and clips crossing marks; sketch bitmap clamps allocation dimensions only and fits actual region inside. Page background/ink crop path retains its existing pixel-aligned mapping. Six pure projection cases and three native-bitmap regressions authored; existing two crop cases retained. No new strings, permissions, dependencies, storage, schemas or assistant operation. Source review and focused11-case gate are next; no APK rebuild/install in this source slice.

Completed first focused gate90530PASS6m35s:11cases/2classes, zero failure/error/skip, production/test compilation. Fivepre-gateinputhashesmatch; rawXML/logretained in docs/testing/artifacts/INK-SELECTION-GEOMETRY-029-2026-10-24. NoAPKassembly/install/newphysicalacceptance. Source and report reviewed; original#29remainsOPEN.
