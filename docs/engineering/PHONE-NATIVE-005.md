# PHONE-NATIVE-005 — capability contract reconciliation

Date: 2026-10-13. Scope: Android host only, model-independent deterministic entry points.

## Reconciliation of actual repository features

The repository already has:
- `AppCommandParser` deterministic screen scripts (bounded steps), `NativeCapabilityDispatcher` host authority, `SpeechRule` and `RulePipeline`.
- `SystemTools`: resolver-validated Android Settings panel destinations with package-specific arguments where supported. **No programmatic Wi-Fi/Bluetooth toggles**.
- `AlarmTools`: system alarm and timer requests. PHONE-NATIVE-002 adds view/dismiss/snooze via official `AlarmClock` Intents.
- `CommunicationTools`: contact lookup/dialer, with PHONE-NATIVE-003 adding reviewed contact-editor handoff; `MessagingTools`: SMS/email draft compositors (not delivery).
- `CalendarTools`: permission-gated event read/direct creation and a new reviewed event-editor handoff.
- `AppSemanticTools`, `AndroidAppFunctionConsumer`, `NativeAssistantActions`: guarded current-app AppFunctions/DirectActions with bounded schema and approval. Unstructured in-app activity remains accessibility/agentic, not a universal Intent.
- `MediaTools`: selection among active sessions, and `NotificationControlTools`: policy-gated observation/reply/dismissal.
- `AndroidFileSelectionAdapter`, `InboxCaptureDialog` and `SendIntentHandler`: already own SAF/chooser URI results, photo capture, local voice memos and imported workspace references. PHONE-NATIVE-004 adds a Photo Picker entry to that *existing* result-handling flow.
- `AssistantTools.halo_open_screen`: exact Halo-owned destinations. PHONE-NATIVE-004 adds text/voice deterministic command aliases.

## Test-contract correction

`HostToolDescriptorDispatchTest` maintains a *strict allowlist of tool names per executor family*. Without extending that allowlist, newly registered `halo_clock_view`, `halo_clock_modify`, `halo_contact_editor`, and `halo_calendar_editor` would fail the ownership test. Updated ownership/approval assertions require exact family ownership, NEVER_REPLAY + ALWAYS for edits/dismissals, and no registry fallback.

`BuiltInSkillManifestTest` now pins editor tools as requiring no Contacts/Calendar write permission while the existing direct read/write tools retain their permissions.

## Non-goals / unsupported by public Android API
- Unrestricted Intent URI execution, shell/root commands, and OEM-private toggles.
- Silent SMS/email delivery, arbitrary contact modifications, or calendar writes without an authorized provider action.
- Guessing app-specific deep links, unverified app state changes, or claiming a delivered message from an Intent dispatch.
- Background/autonomous Accessibility operations, bypassing protected apps, or replaying stale screen evidence.
- Invented file imports: a picker without a *verified returned URI* is not a completed Halo action.

These restrictions are intentional; the capability discovery layer should report unsupported or missing target handlers.

## Validation

**Source complete; build, unit tests and physical device acceptance NOT RUN in this environment.**
Next step is the exact serial validation and device matrix recorded in `docs/testing/PHONE-NATIVE-ALPHA-ACCEPTANCE.md`.

Android contracts:
- https://developer.android.com/reference/android/provider/AlarmClock
- https://developer.android.com/reference/android/provider/Settings
- https://developer.android.com/identity/providers/contacts-provider/modify-data
- https://developer.android.com/identity/providers/calendar-provider
- https://developer.android.com/training/data-storage/shared/photopicker
- https://developer.android.com/ai/appfunctions/add-appfunctions
