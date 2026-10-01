# SETTINGS-PERSISTENCE-001 — ElevenLabs setup and durable settings

Implemented 2026-10-01; final validation recorded below.

## Causes and changes

The halo_settings migration originally migrated every halo_storage preference and removed those legacy entries. Most provider, voice, routing, capability and policy settings still read halo_storage, so they appeared lost after migration. The DataStore migration now owns only its eight typed settings. A recovery migration restores historical misplaced values without replacing newer SharedPreferences values. Misplaced credentials are migrated directly to the encrypted secret store, verified before removal from DataStore, and preserve newer protected credentials. Recovery remains retryable on a persistence failure.

Settings and credential migration now share one process-wide DataStore owner. This also removes concurrent DataStore factories for the same file. Application components that consume persisted preferences wait for storage bootstrap rather than writing defaults first. Existing speech selector/provider state can reload after recovery.

AndroidStorage single-value saves, deletion and clearing now check durable commit rather than acknowledging an asynchronous write. ElevenLabs voice style is saved as one batch. Speech provider/fallback selection and ElevenLabs configuration saves run on IO and show failure feedback; visible selection changes follow a successful save.

ElevenLabs setup now saves the key and loads voice/model choices in the same explicit action. A failed save retains the entered key for retry; a failed catalogue fetch distinguishes the saved key from the unsuccessful fetch. Voice/model choices and style have save feedback, and the configuration page has an explicit Use ElevenLabs for speech output action. The existing shared speech coordinator still owns preview and production playback. No credentials are logged or sent to a different endpoint.

Choosing the System theme before initial appearance loading completes now counts as an explicit choice and wins over the stored theme. A previously exposed startup directory-creation race is fixed by rechecking the resulting directory; application termination also cancels its background scope.

## Validation

Regression checks cover scoped migration, recovery of a historical migrated file after reopening DataStore, preservation of newer values and protected credentials, idempotency, safe failure on credential recovery, ElevenLabs configuration/provider reconstruction, and real Compose voice/model/provider selection. A deterministic test covers the initial theme race. Full app and Android-host suites and debug assembly are required for this batch.

Final validation: `:app:testDebugUnitTest :host-android:testDebugUnitTest :app:assembleDebug` and `git diff --check` passed. JUnit XML reports app 164 and Android host 18: **182 tests, zero failures/errors/skips**. Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (229,470,338 bytes).

Live ElevenLabs account authentication/synthesis and physical force-stop/relaunch checks remain unverified without a connected device and account credentials. A network failure retains saved choices and reports the provider error; automated cached-catalogue checks do not prove remote account readiness.

## Device check

Update using the debug APK without clearing app data. Open Settings > Voice > Speech output > Configure ElevenLabs. Save the key and load choices, select a voice and TTS model, then Use ElevenLabs for speech output and play the phone preview. Change a voice style and output fallback. Force-stop/relaunch, and verify provider, voice, model, style and fallback. Repeat for speech input/language, appearance, routing and Intelligence selections; verify an older affected installation restores retained migrated values on first startup.
