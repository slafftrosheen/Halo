# WEARABLE-CONTROLS-PHYSICAL-003

One consolidated suite for FEATURE-074. Status: BLOCKED_DEVICE_NOT_CONNECTED; pending execution/observations. No acoustic or physical-button PASS is inferred from platform routes.

Inventory: development phone 23124RA7EO, Android API 35; Sony WH-CH520 previously reported connected. HeyCyan and Wear OS hardware: NOT_AVAILABLE. FEATURE-073 watch owner: missing dependency.

| Row | Acceptance status |
| --- | --- |
| Idle music unaffected | UNVERIFIED; requires user observation |
| Headphone native assistant gesture | UNVERIFIED; requires actual hardware gesture |
| Scoped play/pause during real Halo speech | UNVERIFIED; platform callback and user observation required |
| Volume remains system-owned | UNVERIFIED on hardware; no volume interception in source |
| Speech reaches headphones with phone silent | UNVERIFIED; platform routing alone cannot establish hearing |
| HeyCyan voice/camera, normalized mapping, no duplicates | NOT_AVAILABLE |
| HeyCyan unverified touch/swipe | UNVERIFIED / NOT_ASSIGNABLE |
| Wear foreground stems, reserved keys, PTT release/cancel | NOT_AVAILABLE; FEATURE-073 integration absent |
| Cross-device capture/action deduplication | NOT_AVAILABLE |

The opt-in `WearableControlsPhysicalTest` uses the actual existing TTS and scoped MediaSession, sends no AI request, records no microphone audio and injects no keys. It temporarily configures StopSpeaking, waits for a platform control callback during real speech, and restores persisted settings. The report deliberately leaves hardware attribution, audible routing, music/volume and native assistant rows UNVERIFIED until the user observes them. A passing JUnit harness is not full physical acceptance.

Run only this suite with instrumentation argument `controlsPhysical=1`; do not rerun previous wearable/audio suites. Result JSON is stored in the target app's private files as `wearable-controls-physical-003.json`. No raw addresses/content are retained.

On the 2026-10-08 retry, both APK installation commands returned device not found; ADB listed no connected devices. No instrumentation suite was started. Software tests and APK assembly passed separately (912 unit tests). Reconnect/unlock the phone before installation and the one opt-in suite.
