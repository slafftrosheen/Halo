# Halo / HeyCyan Protocol Reverse-Engineering Package

Status: working protocol reference, compiled from the currently available vendor SDK documentation, manual BLE captures, and existing Halo/CyanBridge findings.

## Documents

- `01_VENDOR_SDK_COMMAND_REFERENCE.md` — commands and response semantics explicitly documented by the Qingcheng/QC Wireless Android SDK PDF.
- `02_COMMAND_CORRELATION_TABLE.md` — correlation of documented SDK operations with manually observed BLE packets.
- `03_PACKET_FAMILIES_AND_FIELD_MAP.md` — currently observed `BC 73` / `BC 59` structures and field-level observations.
- `04_EXPERIMENT_PLAN_AND_OPEN_QUESTIONS.md` — controlled captures needed to turn hypotheses into verified protocol facts.

## Evidence rules

Three evidence levels are deliberately kept separate:

1. **DOCUMENTED** — explicitly stated by the vendor SDK documentation.
2. **OBSERVED** — directly present in a manual capture.
3. **CORRELATED / INFERRED** — a relationship between documented behavior and observed packets that still needs a TX/RX capture or another controlled experiment.

Do not collapse inferred fields into production protocol constants until verified.

## Important architectural distinction

The vendor SDK's `glassesControl()` payloads such as `02 01 01` are an SDK command layer. The manually captured `BC 73 ...` and `BC 59 ...` frames are observed BLE traffic. We currently do not have enough evidence to state that the SDK payload bytes appear unchanged on the BLE wire.

The next decisive experiment is simultaneous TX + RX capture for each operation.
