# #29 selected-region shape preservation

Base `1929bba08e29e2b33f07d8a79bd8176f03ba216a`, fetched main0/0 and clean before source edits. Original #29 acceptance remains open.

## Source correction

The sketch attachment renderer clamped the selected-region aspect into0.125..8 and imposed minimum bitmap dimensions, then stretched selected coordinates across the entire result. A narrow selection could therefore be widened or heightened many times. Legacy aspect0 additionally used1.4 for the whole crop without accounting for selected bounds. Preview separately rewrote points, added margins and clamped its aspect, producing a different interpretation.

Shared host presentation geometry now derives selected-region aspect from source aspect and normalized bounds, uniformly fits it into the available viewport and derives the original source rectangle from that fit. Preview retains selected original points and clips to the exact fitted region. Sketch PNG allocation remains96..768 per dimension; extreme regions are fitted with blank margins rather than stretched. Crossing selected strokes are clipped before they can enter those margins. The existing rounded page-background/annotation crop mapping retains its full destination rectangle.

Legacy0 uses a common1.4 source-aspect fallback in these two selection paths; this is consistent display interpretation, not recovery of unknown original dimensions. Saved document bytes, stable stroke IDs, source/revision/privacy checks, local attachment ownership, assistant operations, pressure paint rules and ordinary unselected preview remain on their existing paths. No new storage, provider, permission, dependency, UI copy or tool contract.

## Verification

Production/regression sources completed before validation. Authored6 pure geometry cases (source/bounds ratio, legacy fallback, extreme portrait/landscape, mapping across viewports and zero-sized composition) and3 native bitmap regressions (tall/wide clipping and legacy bounds). The2 existing bitmap cases retain selected-only clipping and image-background alignment coverage. Read-only review found no production issue, but caught an overly dark test-pixel threshold for one-pixel antialiased lines; the two visibility thresholds now allow partial coverage while keeping strict white-margin checks. This correction preceded the first gate.

First focused serial gate90530 PASS6m35s: production/test Kotlin compilation and11 desktop cases (6 geometry,5 Robolectric native-graphics bitmap), zero failures/errors/skips.403 tasks,10 executed/393 up-to-date. All five pre-gate source identities still match. [Raw XML, terminal log and input identities](artifacts/INK-SELECTION-GEOMETRY-029-2026-10-24) are retained; native graphics here means desktop Skia, not physical Android testing.

This batch does not assemble/install APKs or repeat unrelated model/localization/voice/storage suites. Current physical phone evidence belongs to the preceding qualified-intent batch and is not Ink visual or spoken acceptance. No CI/Actions.

## Remaining acceptance

Keep #29 open for actual typed/spoken exact-selection workflows, provider/privacy behavior, save/reopen/rotation/tablet and complete original acceptance. Geometry/bitmap regressions do not establish handwriting recognition, pressure-pen hardware or real spoken referents.
