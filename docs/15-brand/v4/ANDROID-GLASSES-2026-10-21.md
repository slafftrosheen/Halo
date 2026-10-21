# BrandBook 3.0 continuation: glasses controls (D04)

Authority: `IMPLEMENTATION.md`, `USER_FACING.md`, Package F `PACKAGE_F.md`, D04 reference frames and `spec/package_f.json`.
Baseline: `da67070485a193ee791ad72c22a807e9f9b109f9`, main, synchronized on 21 October 2026.

## Implementation

- Replace the technical glasses page with connection/capture/photo-copy/audio/buttons/preferences sections. Preserve recording, auto-connect, control assignments and explicit Forget. Controls wrap at enlarged type.
- Capture controls require the typed READY session and matched live glasses telemetry. Capture, copy and audio requests also recheck the selected device against the active driver. A saved page cannot operate a different active pair.
- Replace the former text-only visual-question submission with fresh local photo capture through the existing review owner (`publishForInference=false`, `review=true`). Provider inference remains a separate explicit action in that review.
- Keep audio connection separate from confirmation that the user heard halo. Do not claim sound works from an endpoint name or Bluetooth link.
- Replace static capability claims, model names, raw enums, protocol details and failure messages with localized everyday wording. Preserve the runtime records with their original owners.
- Associate transfer state with its source device, expose typed waiting/error/progress fields and distinguish photo requests from completed copies. Preserve prior successful copies for the same device; do not carry results to another pair.
- Forget confirms that copied photos stay in Notebook; cancel never invokes removal.
- Keep real control assignments and routine selection, using localized control/gesture/action labels instead of raw enums and evidence strings.

## Acceptance boundaries

This increment does not complete all 95 kit designs. Exact D04 pixel parity, real glasses camera/video/audio, photo-transfer recovery, audible playback confirmation and a transfer-cancel control remain open. The existing transfer owner has no user cancellation contract; this increment does not simulate one.

The physical phone was connected and the previous installed page was inspected in Russian. It showed untranslated AI/model status in the everyday page. Final build, selected automated checks, renders and installation evidence are recorded below after verification.
