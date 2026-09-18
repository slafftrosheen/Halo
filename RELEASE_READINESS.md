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
- [ ] Burn-in and [hardware-week](docs/testing/7-day-hardware-test.md) results.
- [ ] Known issues and actual Android signing/distribution process recorded.

Record commit/build, environment, scenario and evidence for each gate.
TestFlight terminology does not apply to this Android release assessment.
