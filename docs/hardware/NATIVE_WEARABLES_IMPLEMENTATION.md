# Native wearable integration — issue #66

Updated 2026-11-09. Source implementation is not physical hardware qualification.

## Delivered checkpoints

- `0d845345`: EyeVue/TuneBuds portable command, response, handshake, photo and transfer lifecycle engine.
- `717aca6a`: Android EyeVue GATT AA12/AA13/AA14/AA15 and TuneBuds Classic RFCOMM links; native sessions registered with the existing DeviceSessionManager; saved-device reconnect.
- Current UI integration: device-family selection; exact BLE vs Classic identity handling; normal Devices route and capability-gated native controls; local photo review/Gallery; leased runtime visual-context acquisition; save-after-READY projection refresh.

The HeyCyan/COLMI A2 path remains independent. BW-AG1 Pro is unavailable for hardware tests. No foreign vendor is routed through HeyCyan to fabricate support.

## Evidence and protocol scope

Reference revision: FerSaiyan/Alternative-HeyCyan-App-and-SDK `d7e726e923943b94ae32cd1e42c20b3ee6343bd7`, especially `devices/eyevue`, `devices/tunebuds`, and `android/DATA_SYNC_COMMAND_AUDIT_20260909.md`. Original protocol implementations in Halo avoid requiring the HeyCyan vendor AAR for these families. Upstream licenses still govern any subsequently imported SDK/component.

EyeVue control uses exact service/command/notification properties and acknowledged CCCDs, then a decoded device identity and battery response before READY. AI photo is available only if the photo channel is subscribed. Media startup command 0x39 remains distinct from live-preview 0x67. TuneBuds independently decodes its RFCOMM protocol and outer sequence gaps; camera close busy=1 is not the same policy as file-manager busy=1/3/5. Transfer sessions retain ownership until camera cleanup finishes. Command ACK does not assert physical capture completion.

## Verification actually executed

`bash tools/wearables/check-native-sessions.sh` compiled the real portable wire/command/driver sources using Kotlin 1.9 and JDK 21 and passed **13 executable checks**. Coverage includes decoded READY, missing replies, late-response retirement, battery validation, disconnected actions, independent TuneBuds ACKs, media-vs-preview commands, photo-channel gating, asynchronous counts, hotspot TLVs and bounded JPEG validation.

Android transport/session/host sources also passed a limited Kotlin compilation against local API-signature stubs. This is **not** an Android build, integration test, or Android API compatibility proof. Compose/Robolectric tests are committed but **not executed** in this environment. No Android SDK, physical phone, or glasses was available. Full Gradle build, Android tests, and all hardware acceptance remain NOT RUN.

## Remaining acceptance

Wi-Fi topology/network binding and media import must be validated end-to-end per family. MYVU, Meta DAT, Mentra Live, MoYoung and MemoMind require their actual native integrations and applicable SDK/license/auth prerequisites; family identity alone is not implementation. Generic Android audio remains governed by the existing audio-route owner. This checkpoint does not close #66 or certify every device family.
