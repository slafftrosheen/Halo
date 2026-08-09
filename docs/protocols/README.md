# Protocol Documentation

Protocol documentation in this directory is an evidence log, not a place for guessed device behavior. Every protocol claim should point to a capture, a reproducible observation, or an explicitly identified external reference.

## Protocols

- [HeyCyan BLE protocol](heycyan/BLE_PROTOCOL.md): GATT inventory, packet evidence, capture references, and open questions.
- [HeyCyan capture analysis](heycyan/CAPTURE_ANALYSIS.md): lossless import, annotation references, and evidence policy for PR-012.

## Documentation rules

1. Preserve raw bytes and transport metadata before decoding.
2. Mark unverified interpretations as hypotheses.
3. Reference the capture that supports every decoded field.
4. Do not add command or decoder behavior based only on an assumed UUID, packet shape, or vendor convention.
5. Keep transport observations separate from runtime cognition and device-specific behavior.