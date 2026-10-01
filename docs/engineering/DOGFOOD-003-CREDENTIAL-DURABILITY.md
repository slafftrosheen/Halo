# DOGFOOD-003 — Credential durability and runtime diagnostics

**Status:** IMPLEMENTED / CI STARTUP FAILURE / PHYSICAL DEVICE VERIFICATION PENDING  
**Baseline:** `dba5eb061596606ea0b0fb747a4b5fcd6594c9f5`

## Scope

This corrective batch addresses failures observed during physical dogfooding after DOGFOOD-002.

### ElevenLabs TTS

- Provider construction no longer performs synchronous secure-storage reads.
- Persisted credential/configuration loading runs on the provider IO scope.
- Credential, voice, model, catalogue and style are retained as in-memory snapshots after load.
- Credential writes are verified by immediate secure readback before the provider reports configured.
- Stale asynchronous configuration loads are discarded with a configuration version guard.
- Protected credential read/decrypt failures are no longer silently converted to "not configured".
- Settings distinguish loading, configured and unreadable secure-storage states.
- Provider-owned coroutine scope is cancelled on close.
- TTS synthesis errors emit sanitized provider/stage/code telemetry before optional Android TTS fallback.
- Added provider-level durability tests.

### Runtime action diagnostics

- `HALO_ACTION_RESULT` again includes plan, execution and task correlation identifiers.
- Deterministic action identity no longer hashes raw `Task.toString()`; only privacy-safe operation shape is used.
- Host tool registration now fails fast on duplicate tool ownership.

## Verification

GitHub Actions run for the pull request ended with `startup_failure` before any jobs were created. No Gradle job ran, so this document does not claim CI compilation or unit verification.

Required physical verification:

1. Save ElevenLabs credential, choose voice/model, select ElevenLabs and synthesize a test phrase.
2. Force-stop Halo and reopen; configuration must remain.
3. Reboot the phone and reopen; configuration must remain.
4. Run selected local LiteRT model in airplane mode for a normal question.
5. Run deterministic phone actions and inspect correlated `HALO_ACTION_*` traces.
6. Confirm no Halo-owned StrictMode secure-storage reads occur on the main thread.
