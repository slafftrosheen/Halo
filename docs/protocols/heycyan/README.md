# Halo / HeyCyan Protocol Reverse-Engineering Package

Status: Canonical protocol reference, compiled from vendor SDK documentation, decompiled vendor AAR bytecodes, physical BLE captures, and cross-platform iOS findings.

**Acknowledgments**: A significant portion of this reverse-engineering and protocol mapping was greatly assisted by the findings in the [FerSaiyan/Alternative-HeyCyan-App-and-SDK](https://github.com/FerSaiyan/Alternative-HeyCyan-App-and-SDK) repository.

## Documents

- `00_README.md` — Evidence package overview and entry points.
- `01_WIRE_PROTOCOL_VERIFIED.md` — Verified `0xBC` wire framing, CRC16 calculation, action constants, and golden vectors.
- `02_COMMAND_REFERENCE.md` — Verified command payloads and parameter ranges.
- `03_DEVICE_REPORTING.md` — Generic `0x73` device reporting notifications and length-payload structures.
- `04_NOTIFICATION_SEMANTICS.md` — Notification payload semantics and correlation notes.
- `05_TRANSPORT.md` — GATT topology, primary & candidate UUID sets, and Wi-Fi transfer architecture.
- `06_AAR_REVERSE_ENGINEERING.md` — Reverse-engineering findings from `glasses_sdk_20250723_v01.aar`.
- `07_NEXT_EXPERIMENTS.md` — Controlled hardware validation protocol.
- `08_REPO_INTEGRATION_GUIDE.md` — Halo driver module boundaries and implementation guide.
- `09_EVIDENCE_MATRIX.md` — Complete evidence provenance matrix.
- `10_COMMAND_VECTORS.txt` — Golden command vectors list.
- `IOS_SDK_PARITY_AND_BEHAVIOR.md` — iOS SDK API parity, multi-packet AI transfer, pacing, and reconnect state machine.
- `VENDOR_AAR_REVERSE_ENGINEERING.md` — Reverse engineering breakthrough, javap disassembly, and golden vector tables.

## Evidence Hierarchy

Five explicit evidence levels govern all protocol statements in this repository:

1. **VENDOR-DOCUMENTED** — Explicitly documented in vendor SDK guides or header files.
2. **VENDOR-IMPLEMENTATION-VERIFIED** — Reconstructed directly from decompiled vendor AAR bytecode disassembly (`LargeDataHandler`, `GlassModelControl`, `CRC16`).
3. **PHYSICAL-CAPTURE-VERIFIED** — Directly observed in reproducible real-device BLE capture logs.
4. **HARDWARE-VERIFIED** — Confirmed by independent physical device execution (Halo transmitting command -> physical device action -> response event).
5. **CORRELATED / INFERRED** — Hypothesized relationship between operations and raw traffic requiring further physical testing.

## Verified Epistemic Boundary

Vendor AAR source reconstruction establishes that `glassesControl()` payloads are encapsulated by the `BC 41 LEN CRC PAYLOAD` wire encoder. Physical Halo TX capture has not yet independently confirmed that Halo emits the same frame for a live operation on physical hardware.

The next decisive experiment is isolated physical TX + RX capture for each operation.
