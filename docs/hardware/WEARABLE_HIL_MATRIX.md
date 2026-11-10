# Wearable and input hardware acceptance

Updated 2026-11-11. **No physical tests have run in this agent environment.** Ownership, source code, synthetic input and API compilation are separate evidence.

| Target | Availability | Journey | Result |
|---|---|---|---|
| COLMI A2 | Available to user | Explicit pairing/READY, battery/identity, camera/AI photo, audio association, Wi-Fi media, reconnect/cancel | NOT_RUN |
| XREAL xbx a01+ | Available to user | Android video endpoint; selected neutral card; 8,000-character paginated text; stale-page/Stop rejection | NOT_RUN |
| XREAL xbx a01+ | Available to user | USB inventory independently of video: observed VID/PID/interfaces, permissions and no unsolicited command | NOT_RUN |
| XREAL xbx a01+ | Available to user | Unplug, configuration change, background, lock, Kids, process death; no text restored | NOT_RUN |
| Huion Inspiroy 2 | Available to user, S/M/L unknown | Actual pointer source/ranges; pressure/tilt/hover/buttons; Canvas and Studio save/reopen/export | NOT_RUN |
| Huion + touchscreen/mouse | Available to user subject to host/hub | Pen priority; unrelated device cannot splice draw/pan/selection; unplug mid-gesture; pause/resume | NOT_RUN |
| XREAL + Huion together | Host/hub/power/video path unverified | Independent output/input inventories and deliberate display selection; no VID/PID-based identity merging | NOT_RUN |
| EyeVue / TuneBuds | No local test device reported | Native handshake, controls, capture and transfer, selected-device reconnect | NOT_RUN |

For each actual run record: UTC timestamp, tester, model/size, firmware, host model/OS/API, cable/hub/power, app commit/APK identity, exact selected endpoint, expected behavior, observed result, and sanitized evidence path. Record PASS/FAIL/BLOCKED for that one operation, preserving NOT_RUN elsewhere. Do not commit serials, MAC addresses, private images, speech transcripts, OS device paths or credentials.

Use [A2 qualification](COLMI_A2_HEYCYAN_QUALIFICATION.md) for the first glasses regression gate and [batch 002](../engineering/HALO-DEVICES-066-EXPANSION-002.md) for local code checks and new input/display journeys. None of these entries authorizes OTA, reset, unbind or speculative USB protocol writes.
