# Issue 65 independent source review

Reviewed range `cd08e9ad6..b4b364549`, fresh-context `gpt-6-astra`, high reasoning, read-only, 3 November 2026. No builds/tests/device/provider operations or source mutations by the reviewer. Initial verdict: **not ready to merge**. Authored regressions are not executable evidence.

| # | Severity | Finding | Resolution |
|---|---|---|---|
| 1 | Critical | Paint exit drops the input session before queued writes can fail, including Back/close | Source corrected; regression authored, executable outcome pending |
| 2 | Important | Global Save/Undo invalidates the active Paint writer while leaving its frozen surface alive | Source corrected; regression authored, executable outcome pending |
| 3 | Important | Direct Paint places raw ink over all other layers and omits real stack/mask/effect/group styling and colour alpha | Source corrected; regression authored, executable outcome pending |
| 4 | Important | Ungroup after reorder restores old child z indices; group tone adjustments can be silently lost | Source corrected; regression authored, executable outcome pending |
| 5 | Important | Rotating normalized coordinates disagrees with physical Canvas rotation on non-square documents | Source corrected; regression authored, executable outcome pending |
| 6 | Important | Group mask remains axis-aligned when the group rotates | Source corrected; regression authored, executable outcome pending |
| 7 | Important | Grain/pixelate/blur use different effective spatial coordinates between preview and export | Source corrected; regression authored, executable outcome pending |
| 8 | Important | Connected fill lacks final pinned-source validation and atomic file/layer publication | Source corrected; regression authored, executable outcome pending |
| 9 | Important | Artboard producer can retain and sample an old document bitmap as current | Source corrected; regression authored, executable outcome pending |
| 10 | Important | Schema-2 encoding overhead can reject a near-limit valid schema-1 history envelope | Source corrected; regression authored, executable outcome pending |
| 11 | Important | Typed readback omits masks, background and global recipe despite supporting edits | Source corrected; regression authored, executable outcome pending |
| 12 | Minor (reviewer) | Four primary groups omit disabled explanations | Source corrected with visible reasons and semantics; regression authored, executable outcome pending |

The author separately identified an aggregate Ink quota concern: recovery must preserve valid new marks even if reconstructing the entire old composition would exceed document limits. This is included with finding 1.

Deferred acceptance evidence: compiler/tests, native appearance/gestures/accessibility/GPU/memory/process loss, actual microphone STT, and actual provider/output/billing/consent. None is inferred from source review. Professional vector/path editing, segmentation, desktop brush-engine parity and downloadable packs remain the issue's explicit outside scope; rectangular masks and real implemented brush behavior remain in scope.

The coherent author correction source pass is complete. There will be no second reviewer run; regressions and the serial one-worker gate must supply executable correction evidence. Paint now drains through the host application scope and retains failed input after navigation; Save/Undo resume a writer at the confirmed revision. Both Paint surfaces use the canonical compositor. Group ordering/physical geometry/masks, logical 640-edge effects, atomic fill publication, revision-bound preview sampling, legacy-compatible schema-1 encoding, complete bounded readback and primary-group explanations have source corrections. The author will add actual gate outcomes below.

Executable correction evidence is still pending on 4 November. KSP passed; full app compilation did not complete, and no app test ran. Host restarts and repeated new undervoltage persisted through a one-worker/core, temporary 1.5 GHz retry. The author stopped that gate and restored the CPU maximum. See [attempts and host evidence](artifacts/HALO-STUDIO-65-2026-11-04/README.md). These infrastructure outcomes neither validate nor reject any individual source correction.
