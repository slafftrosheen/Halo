# Device compatibility — implementation versus qualification

Updated 2026-11-11. This matrix records source scope; UI and assistant capabilities come from the registered owners and live state. A device name, USB class or OS-advertised axis is never physical qualification.

| Family/path | Implemented source coverage | Qualification/remaining gate |
|---|---|---|
| COLMI A2 / HeyCyan | Existing exact-family classification, GATT profile/readiness guards, controls, capture/audio/media owners | User has A2; exact firmware and full HIL NOT_RUN in this environment. See [A2 qualification](COLMI_A2_HEYCYAN_QUALIFICATION.md). |
| EyeVue | Native BLE transport, decoded handshake/READY, control/photo/transfer engine and selected-device UI/runtime path | Physical validation and end-to-end Wi-Fi/media acceptance pending. |
| TuneBuds / AB Mate | Explicit Classic identity, RFCOMM native session, command/photo/transfer engine and selected-device UI/runtime path | Physical validation and end-to-end Wi-Fi/media acceptance pending. |
| XREAL **xbx a01+** via Android presentation | Generic external display, explicit selection, supplied paginated text, page navigation/Stop, assistant tools | User has exact product. Host/cable video, USB control endpoint and all physical output NOT_RUN. No model-specific USB/IMU/stereo/camera implementation claimed. |
| Huion Inspiroy 2 via Android input | Source-specific input handling, measured-axis normalization, cross-device gesture ownership, shared Canvas/Studio, OS metadata inventory | User has tablet; S/M/L unspecified. Pressure/tilt/buttons/OTG/mapping HIL NOT_RUN. No raw Huion USB driver. |
| Other Android stylus/mouse/keyboard/controller/rotary inputs | OS role/range inspection; pen/mouse pointer-class Canvas paths where Android delivers them | Advertised roles do not prove application button mapping, global key capture or physical fidelity. |
| Other USB peripherals | Read-only OS metadata, bounded interface/endpoint inspection | No driver implied; no interface claim, transfer, USB permission request or inferred camera/pose/audio. |
| Other Android presentation displays | Same generic selected-display owner, independent of brand | Requires OS presentation endpoint and actual host video support; no brand certification. |

For earlier native implementation evidence see [NATIVE_WEARABLES_IMPLEMENTATION.md](NATIVE_WEARABLES_IMPLEMENTATION.md). Generic audio/BuddyBot/Wear OS paths are preserved; this batch does not newly qualify their hardware.

Remaining #66 tracks include MYVU, MoYoung, Meta DAT, Mentra Live, MemoMind, Brilliant Labs Frame/Halo, Even G1/G2, Omi, Rokid/VITURE/other USB XR and Bangle.js. Their issue research/SDK requirements do not establish a packaged driver. This batch adds no production protocol adapter for those tracks. Record source inspection, licensing, package/build and hardware acceptance separately when implementing each one.

Android inventory compatibility: public APIs from API 26; external-vs-built-in classification is unknown on API 26–28. Current project compile SDK is 37. Desktop and Android OTG support must be qualified separately.
