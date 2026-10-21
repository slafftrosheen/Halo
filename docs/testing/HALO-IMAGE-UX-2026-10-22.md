# Image flow polish — 2026-10-22

Baseline: `80d919fca3b80c36cfb03a77bd9c6a86a8d1ebdf`, clean and synchronized with origin/main before edits.

## Changes

- Shared Notebook/Canvas/generated-image previews open managed files and content URIs correctly. Bounds are decoded first, with sampling initialized to one and a 1024-pixel maximum preview edge. Previously the shared image routines divided by a default zero sampling value, and Notebook's separate thumbnail routine attempted to open managed files as content URIs.
- Loading, failure, and retry states replace blank or prematurely failed previews. Chat thumbnails distinguish loading from unavailable media; local Canvas file previews offer retry.
- Images open a larger local viewer with pinch/pan and accessible zoom controls. Notebook opens it directly, without an intermediate dialog. Capture review and homework photo review use the same viewer. Viewing itself does not invoke AI or upload pixels. Zoom actions and the note save footer wrap at large text sizes.
- The image studio separates the original from the unsaved result, shows readable provider/model labels, wraps result actions, and uses a searchable model chooser with an empty state. Image-model settings fetch their list on entry.
- An unnamed or unreadable editing reference stays an editing reference. Catalogue refresh cannot silently enable generation without the source. Partial catalogue failures no longer fill the editor with technical provider errors.
- Capture review has one primary Ask halo action, secondary study/extraction/save actions, per-capture UI state, and an honest empty note picker. An already saved capture cannot be saved repeatedly from the same action.
- The existing Private/Cloud owner, Kids restrictions, cost approval, and original-preserving save behavior remain in force. New app copy is supplied in EN/RU/LV.
- The image editor pins that same Private/Cloud switch in its title area, including after scrolling. It replaces the separate switch-to-cloud button and stays available in both modes.

## Validation

The broad image/Notebook/Canvas/capture/homework check passed 166 tests in 44 suites, with zero failures, errors, or skips. Its source includes the decoder and the corrections from the first physical walkthrough (direct image viewing and large-text wrapping). The final pinned editor control receives a separate focused check and device confirmation below. The initial regression run caught the zero sampling defect before installation; that red run is not acceptance evidence.

Commands: serial `:app:testDebugUnitTest` filtered to `*Image*`, `*ContextualCaptureTest*`, `*Note*`, `*Canvas*`, and `*Homework*`; `:app:assembleDebug`; `:app:assembleDebugAndroidTest`. Standard 2 GiB, no-daemon, no-parallel, one-worker flags were used. Localization generation/check and its two Python tests passed (3,763 EN/RU/LV entries).

Connected hardware: Samsung SM-G990B2, serial `R5CTA0TKLZZ`, API 36, 1080-pixel wide display at 480 dpi (360 dp portrait). The walkthrough used a locally constructed 800×600 raster fixture, not a generated provider result. Light/normal-text and dark/200%-text captures exposed the redundant dialog and wrapping defects; those were corrected before final delivery. Search returned an honest empty state for a synthetic unmatched query. Original image display and the editing prompt/model path were checked in both configurations. System settings were restored to font scale 1.0 and night mode off.

Final focused check: 12 tests in four suites (`*WorkspaceImage*`, `*PrivacyMode*`, `*RuntimeAiPolicyTest*`), zero failures/errors/skips. Both APK assembly tasks succeeded. These tests overlap the broader run and must not be added as unique coverage. Results are recorded in the adjacent `-host-results.json`, `-focused-results.json`, and `-phone-results.json` files.

The final APK was installed with `adb install -r`, preserving app data. Size: 285,515,369 bytes; SHA-256: `37fa257b77b9ca7e9ab9a20ac8f39e6f0b7fb21b6457b36357c48b7943fe6b60`. The installed `base.apk` hash was read back and matches. The binary was built before the source commit, from the final production sources on top of `80d919fc` with a dirty worktree; it does not embed the later commit ID.

Final-APK phone checks passed:

- A single tap opens a Notebook photo directly in the viewer; zoom, fit, and Back/Close work without an intermediate dialog.
- The viewer controls and note save footer wrap cleanly at 200% text in dark mode. The image editor's pinned privacy switch also remains visible at 200% text.
- With a nonempty synthetic editing prompt, Generate is disabled in Private, enabled in Cloud, then disabled again after returning to Private. The editor exposes one actual mode switch after scrolling. No generation request was sent during this gating check.
- Moving only the test image out of Downloads produced the unavailable preview. Restoring it and tapping Retry displayed the image again. The temporary move was reversed in a `finally` block.
- Model search showed “No matching models” for a synthetic unmatched query; closing it returned to the editor.
- `ImageQuestionPhoneTest#cloudImageQuestions` passed both red and blue synthetic cases against OpenRouter `minimax/minimax-m3.1-flash:free` (run ID `image-polish-final-20261022`). This validates image understanding, not image generation or every advertised model.

Post-correction screenshots are in [the artifact folder](artifacts/HALO-IMAGE-UX-2026-10-22). The dedicated “QA polish image flow” note and synthetic image remain available for review. The phone was left in Private mode, font scale 1.0, night mode off.

## Limits

The last paid-generation attempt, documented in `HALO-CLOUD-PRIVACY-2026-10-22.md`, returned HTTP 402 (provider credit/limit rejection). It was not retried in this round. A synthetic raster fixture validates viewing and persistence, not successful paid image generation. No new local-model inference or heavyweight model downloads are part of this UX pass. Physical acceptance is limited to the connected Samsung and the paths listed above; tablet, wearable/camera capture, every screen, and other-device parity must not be inferred. The full repository gate and hosted CI were not rerun for this app-only UX change.
