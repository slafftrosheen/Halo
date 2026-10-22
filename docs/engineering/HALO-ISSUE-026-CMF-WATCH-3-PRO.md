# HALO Issue 26 — CMF Watch 3 Pro reconnaissance

**Checked:** 2026-10-22  
**Observed host:** Samsung SM-G990B2 running Halo's installed Android package. The Android Bluetooth service listed a nearby `CMF Watch 3 Pro` advertisement/name. It was not bonded. No GATT connection, pairing prompt, or watch command was sent.

## Evidence reviewed

The WCMF public repository at commit [`45b3998754022b0448817a4ac8af13d91f3b8a76`](https://github.com/lexp-hub/wcmf/tree/45b3998754022b0448817a4ac8af13d91f3b8a76) is MIT licensed and names Watch 3 Pro among its supported watchface export/OTA-flash targets. Its README warns that direct pairing, authentication, and flashing can softbrick the watch or trigger a factory reset. The README says firmware compatibility is not guaranteed.

The current WCMF source contains a broad list of candidate GATT services and characteristics: Goodix command read/write (`FFF1`/`FFF2`), data transfer (`02f00000…FFE2`/`FFE1`), an AT shell (`77d4ff02…`/`77d4ff01…`), and the standard Battery Level characteristic (`2A19`). Its connect flow requests a device by service/name/manufacturer filters and then enumerates primary services and characteristics. This is source evidence for WCMF's implementation, **not a verified Watch 3 Pro GATT capture**.

The source's pairing path writes `AT GETSECRET`, waits for a user tap on the watch, validates a challenge response, derives an authentication key, and persists that key in browser local storage. The battery read is only attempted after that pairing/session path. Its packet codec uses an internal MTU target of 180 bytes for framing, but the reviewed source does not prove the watch's negotiated ATT MTU or establish per-firmware negotiation behavior. The visible write workflow proceeds into watchface-slot allocation and OTA chunks; it is not a read-only device adapter.

## Capability matrix

| Capability | Status | Evidence / limit |
|---|---|---|
| Nearby device name | **Observed** | Android Bluetooth service listed `CMF Watch 3 Pro`; the device was unbonded. This does not prove model number D399 or firmware identity. |
| Exact advertising identity and data | **Unknown** | No sanitized advertisement capture was taken. |
| GATT services, characteristics, descriptors | **Plausible, unverified** | WCMF publishes candidate UUIDs, but no Watch 3 Pro GATT discovery was performed. Descriptors/properties are not confirmed for this physical unit. |
| Pairing/authentication | **Plausible, not qualified safe** | WCMF uses a watch-confirmed `AT GETSECRET` challenge and a derived key. Its own warning reports softbrick/factory-reset risk during direct pairing/authentication. No pairing attempt was made. |
| Battery level | **Plausible, unverified** | WCMF includes standard `2A19` and a read after session authentication. No read was performed. |
| Device state / firmware / capabilities | **Unknown** | No read result verified against this device. |
| Notifications, haptics, crown/button input, media controls, sensors | **Unknown** | No read-only or event implementation/evidence verified for Watch 3 Pro. Watchface complications are rendered into a watchface and do not prove a live Halo data/control API. |
| Audio capture / voice recordings | **Unknown** | No safe supported path found in the reviewed implementation. |
| MTU | **Unknown** | WCMF uses 180 as an internal framing target; no actual negotiated MTU was measured. |
| Halo end-to-end interaction | **Not performed** | Halo has no verified CMF adapter, and authenticated pairing has not been shown safe for this device. |

## Decision

Do not add a production `CmfWatchAdapter` from this evidence. WCMF is an unofficial OTA/watchface tool and its public implementation is broader than a read-only transport; copying or adapting its connection/pairing flow would pull in unqualified authentication behavior and flash-oriented code. The MIT license permits reuse subject to its notice, but does not establish protocol correctness, safety, or compatibility with Halo's device owners.

The useful next step requires an owner-assisted, explicitly approved session on this exact watch: confirm its model/firmware in the companion app; capture only sanitized advertising/GATT metadata; assess the official companion's pairing/lock behavior; and determine whether battery/state can be read without entering any OTA/write path. Do not use WCMF's flash UI as a diagnostic tool. If safe read-only access cannot be established, keep the device unsupported and close the adapter proposal.

## Primary references

- [WCMF repository and README](https://github.com/lexp-hub/wcmf/tree/45b3998754022b0448817a4ac8af13d91f3b8a76)
- [WCMF MIT license](https://github.com/lexp-hub/wcmf/blob/45b3998754022b0448817a4ac8af13d91f3b8a76/LICENSE)
