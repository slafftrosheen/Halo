# Release readiness

Status: NOT ESTABLISHED FOR CURRENT CHECKOUT. Reviewed 2026-09-18.

The previous RC1 GREEN/test/security claims are withdrawn as current evidence.
The app declares versionName 1.0-RC1, versionCode 2 and targetSdk 36; labels do not
establish release acceptance. No fresh build/test/hardware run was performed in this audit.

Historical reports mention GATT status 147; present reproducibility needs a dated
phone/OS/build/firmware result. See [audit](docs/engineering/DOCUMENTATION-AUDIT.md)
for CI, credential and capability issues.

Required evidence:
- [ ] Current reproducible build and correctly configured CI.
- [ ] Relevant unit/provider/UI/lifecycle test results.
- [ ] Phone commands and voice/text cancellation accepted.
- [ ] Physical advertised connection/media/speech behavior accepted.
- [ ] Credential/backup/redaction/permission behavior reviewed.
- [ ] Named-hardware frame/latency/memory/thermal/battery measurements.
- [ ] Execute and record the concrete [real-device burn-in matrix](docs/04-architecture/REAL_DEVICE_BURN_IN.md), then complete the [hardware-week](docs/testing/7-day-hardware-test.md).
- [ ] Known issues and actual Android signing/distribution process recorded.

Record commit/build, environment, scenario and evidence for each gate.
TestFlight terminology does not apply to this Android release assessment.


## Current pre-burn-in source boundary — 2026-09-18

The current source is prepared to test recovery rather than treating prerequisites as application-wide gates:

- microphone permission is voice-scoped; denying RECORD_AUDIO must not block Home, keyboard input,
  Notebook, Settings or Devices;
- Bluetooth permissions/radio state are device-flow prerequisites and are observed separately;
- saved device auto-connect is owned by a process-level coordinator and is opt-in per saved record;
- provider diagnostics refresh on foreground/network changes;
- missing local speech model is a recoverable Voice capability state;
- previously mis-persisted HeyCyan identity records from the constructor-order bug are normalized.

These are implementation claims only. None of the release gates above should be checked until the
referenced scenarios are executed on a named build/device and the evidence is recorded.
