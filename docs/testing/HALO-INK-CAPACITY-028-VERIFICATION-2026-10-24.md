# Ink serialized capacity and history — #28

Date: 2026-10-24. Base: `a7a3b90042db084e2928bbc9eff9c5cdc16f7bb8`.

## Changed behavior

A contact can fit the portable point quotas but exceed the 262,144-character saved format. Previously `InkEditor.finish` committed that contact and cleared the redo branch before the Canvas publication callback discovered the encoding overflow. The callback's undo restored the drawing but could not restore the discarded redo branch.

The canonical portable editor now checks the exact v3 encoded length, including legacy aspect binding, before changing either history stack. Overflow rejects the entire contact, ends its preview, sets the existing capacity indicator, and preserves the document and prior undo/redo state. The codec checks the same limit before allocating its output buffer. Wire format, numeric metadata, stroke identity and legacy decoding are unchanged.

## Owners and functional coverage

- Owners: `InkEditor` and `InkCodec` in `sdk/runtime-sdk`; the existing Android pointer adapter and Canvas publication callback consume their existing return values. The adapter already refreshes the preview after pointer up, including rejected contacts.
- No new interactive tool, permission, provider, transport, execution or persistence owner. This changes admission of a human pen/finger contact, not assistant dispatch. Existing saved-sketch tool contracts are unchanged. Physical pointer gestures remain the documented hardware exception to semantic assistant parity.
- Typed request, STT-final spoken request, permitted Power route and native device acceptance: **NOT_RUN**. No new APK was built or installed for this portable-only correction.

## Regressions and evidence

Production and three regression sources were complete before the focused SDK gate. Cases cover rejection without document/history changes; exact wire-limit round trip and one-character overflow; exact length across all tools, unknown and measured axes, signed orientation, timestamps and legacy geometry. Existing portable Ink tests are included in the same focused gate. Read-only source review: CLEAR.

Focused gate: **PASS**, exit 0, 57 seconds, 10 tasks (4 executed, 6 up-to-date). `:sdk:runtime-sdk:test --tests 'com.halo.sdk.runtime.ink.*'` passed **14 cases** (3 new capacity, 4 stable identity, 7 selection context), zero failures/errors/skips. Serial Gradle, one worker, 3 GB heap. No application/Robolectric or Android instrumentation tasks were run. [Raw log, three JUnit XML files and the three changed compiled-source hashes](artifacts/INK-CAPACITY-028-2026-10-24/) are retained. No CI/Actions.

## Remaining acceptance

Keep #28 open: physical pressure pen/eraser/palm behavior, tablet layout, accessibility, rotation, OS process death and long-session performance remain unqualified. This does not satisfy #30's prerequisite verification or implement cross-device Ink.
