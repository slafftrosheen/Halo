# Security & Threat Model

## Privacy Firewall
Every outgoing external request passes through one gate (Privacy Engine).
`Request` -> `Permission Check` -> `Consent Check` -> `Log` -> `Send`.
Cannot be bypassed by any plugin or provider.

## Trust Levels
Every component gets a trust level:
* **Core**: Highest trust (Runtime, Database).
* **Trusted**: System provided drivers/providers.
* **Plugin**: Restricted, sandboxed execution.
* **Network**: Untrusted.

## Attack Vectors Handled
* **Plugin Abuse**: Mitigated by strict sandbox and Command/Query Bus isolation.
* **Prompt Injection**: Mitigated by entity extraction before LLM, and prompt sanitization.
* **Data Leakage**: Mitigated by Privacy Firewall logging all outbound payloads (size/destination).
* **BLE Attacks**: Handled by secure pairing inside the Driver SDK.
