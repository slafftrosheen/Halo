# Private and Full Power profiles — issue #47

Halo now stores two independent, versioned processing profiles through the existing
PrivacyModeStore. Fresh installs start Private; legacy Private never promotes;
legacy Cloud keeps its selection but must complete the new sharing explanation
before any remote dispatch. Profile migration explicitly maps route/model, role,
workload, speech and context settings. Credentials, endpoints, Android grants,
Kids restrictions, action authority and spend controls retain their existing owners.
A corrupt or failed activation closes dispatch and restores Private.

Full Power starts with every shipped online feature enabled. The native editor
separates the two profiles, uses saved provider/model metadata, and requires an
explicit local fallback choice. The persistent shell, first-enable explanation
and feature surfaces identify destinations and sharing categories. Enabling the
mode does not grant paid-media spending consent. EN/LV/RU copy is synchronized;
unsupported Polish additions use the existing honest fallback.

Actual HTTP clients acquire a revocable current-policy lease before DNS and again
at connection. This includes configured AI providers, LAN Ollama, hosted ElevenLabs,
web/search/account usage, MCP and Gmail. Redirects cannot silently authorize a new
destination. Rendered WebView extraction checks the same authority and destroys
its view on revocation. Supported calls are canceled when returning to Private.
Missing providers and disabled categories fail closed without a hidden service swap.

The original turn revision is captured before context preparation and retained
through inference, tool continuations, web/native handoffs and speech. Derived
Workspace/Screen/Memory material cannot become an ordinary text-only remote query
or hosted speech. Pending tool approvals persist enum metadata and revision through
the existing journal; changed arguments or historical unknown origins cannot borrow
new authority. Native Gmail activity recreation cancels its ephemeral request before
account authorization. Delegated legacy references and child results conservatively
retain Workspace/Screen/Memory categories; local child work remains available.

The local activity history contains only bounded metadata: provider, operation,
categories, timestamps, outcome and exact provider-reported cost when available.
HTTP success means Replied, not semantic task completion. Missing cost stays
unknown. Early and late cost observations correlate to their exact lease, and
clearing history does not resurrect in-flight entries. It stores no prompts,
message bodies, credentials, URLs, images or audio.

## Verification

Implementation and regression sources were complete before each serial gate.
Read-only review corrected policy/speech lock ordering, derived-output provenance,
recovery authority, Gmail recreation and the original inference revision envelope.
The first physical fixture passed all dispatch checks but failed while writing its
trace to the wrong UID cache path. The test now writes through targetContext;
that earlier run is recorded as failed, not counted as final device evidence.

The final combined gate passed in **21m 59s**:

- **100 app tests / 16 classes**.
- **9 provider SDK dispatch tests / 1 class**.
- **4 ElevenLabs tests / 1 class**.
- **30 cognition/runtime tests / 6 classes**.
- **143 total**, zero failures, errors or skips; debug app and instrumentation APKs assembled.
- **6 native disclosure renders**, visually inspected.

Native disclosure renders exercise light/dark, EN/LV/RU, short landscape and actual
200% font density. The font-scale assertion protects against Android Dialog replacing
the host density. No source changed after the final successful gate. The manifest
records the compiled Git trees, final APK identity and selected fresh test suites.
One worker, no parallel Gradle tasks and a measured 3 GB heap were used. No GitHub
Actions ran, and no APK binaries or private phone captures are committed.

Final APK: **287,166,217 bytes**, SHA256
`11e6fb31e0878f9f3a04009092511c921b331a31bf28eaffeef82dc39a15bde0`.

**Final device checks NOT RUN:** ADB reported no devices during this final gate.
The phone disconnected after the earlier fixture attempt. Final APK/test APK
installation and hash read-back, the two-case owner/WebView fixture rerun, synthetic
trace extraction, native profile/disclosure dismissal, Private persistence and
200% short-landscape checks remain explicitly pending in #43. The earlier failed
instrumentation log is preserved. No final-APK installed or physical PASS is claimed.
Real user profiles were not switched to Full Power and no live-provider request was
sent for this qualification.

Evidence: [manifest](artifacts/PRIVATE-POWER-047-2026-10-23/manifest.json),
[test XML](artifacts/PRIVATE-POWER-047-2026-10-23/tests),
[native renders](artifacts/PRIVATE-POWER-047-2026-10-23/renders), and
[sanitized phone observations](artifacts/PRIVATE-POWER-047-2026-10-23/device/device-observations.json).

## Scope of the evidence

Synthetic loopback fixtures exercise production owners and actual Android sockets.
They establish the measured request boundary and cancellation cases, not live-provider
quality, real billing, all-network UID silence or a global Android firewall. Model
installation and Google SDK account authorization are explicit OS/setup boundaries;
mode selection cannot recall an already-issued OS authorization. Local model quality,
wearable/audio hardware, live provider behavior and broader device qualification remain
in issue #43. The capability guide was delivered separately in issue #48.
