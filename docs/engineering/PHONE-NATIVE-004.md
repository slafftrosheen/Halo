# PHONE-NATIVE-004 — Halo screen shortcuts, Settings aliases, and media picker

Date: 2026-10-13.

## Source audit
The repository already handles system document picker results (`ActivityResultContracts.OpenDocument`), receives camera capture results (`TakePicture` + FileProvider + durable capture import), and records bounded voice memos locally (`CanvasVoiceMemoRecorder`). These are genuine result-handling flows. Avoid generic ACTION_GET_CONTENT / camera intents from a headless tool executor that would lose their results.

## Added
- Deterministic Halo page commands (Notebook, Study, Devices, Home, Activity, Skills, System Check and Halo Settings) route to existing `halo_open_screen` through the native dispatcher. This prevents trying to launch unrelated Android apps called Notebook or Study.
- Spoken settings destinations now include default apps, airplane mode, data usage, storage, VPN, NFC, casting, language, keyboard, date/time, developer options, notification access, app info, overlay, unknown sources, per-app language, app links, volume, battery saver, and Wi-Fi panel.
- The existing `halo_open_settings` catalogue exposes platform-supported overlay, unknown sources, open-by-default and app-language screens with SDK gates/package URI where appropriate. Device handler discovery remains the source of availability truth.
- Inbox's actual result-handling import path adds the Android system Photo Picker (`PickVisualMedia`) using a pretranslated "Choose a photo" button. Photo Picker's selected content URI is imported into Halo's existing workspace pipeline; no additional broad storage permission is requested.

## Preserved
- AppFunctions / DirectActions / Accessibility semantic precedence and existing cross-app safety.
- Canvas/Notebook camera capture, file import, voice memo and sharing functionality already present.
- Page dispatch only; no unverified promise the destination rendered.
- No LLM dependency for exact Halo page or system Settings commands.

## Validation pending
Gradle and physical tests were not run in this environment. Test unsupported OEM settings, external app-name collisions, assistant overlay foreground handoff, Photo Picker cancellation/import success/failure, EN/LV/RU copy and restricted screen access.

Sources: https://developer.android.com/training/data-storage/shared/photopicker
https://developer.android.com/reference/android/provider/Settings
