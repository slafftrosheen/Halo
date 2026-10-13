# Halo — Native Android Capability Alpha Acceptance Gate

Date: 2026-10-13. Baseline: PHONE-NATIVE-001 through PHONE-NATIVE-005. Testing starts **after** implementation commits land.

This is a *test protocol*, not evidence of a completed test. Report every case as PASS / FAIL / BLOCKED / NOT TESTED; include the observed Android state and one correlated Halo receipt. A tool present in Skills, an available handler, or an ACTION_DISPATCHED log is never proof the user-visible effect happened.

## 0. Reproducible build and version truth

From the freshly updated repository on the developer's Android workstation, with no unrelated uncommitted changes:

```powershell
git pull --ff-only origin main
git rev-parse --short=12 HEAD
./gradlew.bat :libraries:halo-cognition-language:test :engines:interpretation:test :runtime:cognition:test :host-android:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --no-parallel --max-workers=1 --no-daemon --console=plain
adb devices -l
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
```

If Gradle runs out of memory, resolve the actual error and rerun; do not report PASS from stale results. Compare the APK's `HALO_GIT_COMMIT` / System Check build ID against `git rev-parse HEAD` so installed-code drift cannot mask native parser defects. No GitHub Actions.

Run these focused regressions before hardware:
- `NativeCommandParserTest`, `PhoneCommandsRuleTest` — exact utterances and explanatory negatives.
- `NativeCapabilityDispatcherTest` — provider-independent routing / authorization / no provider journal, native HOME.
- `HostActionToolsTest` — clock Intent extras, editor extras, invalid arguments.
- `HostToolDescriptorDispatchTest` — exactly one descriptor per tool; ALWAYS and NEVER_REPLAY for state-changing handoffs.
- `BuiltInSkillManifestTest` — skill/tool registry parity, per-tool permission isolation.
- Existing `SemanticPhoneOperator` / app-action, UI postcondition, recovery, accessibility, and notification tests.

## 1. Setup and evidence collection

Device 1: Xiaomi physical device with current production permissions. Device 2: non-Xiaomi Android 13+ device if available. Test phone microphone first, then the paired WH-CH520 or HeyCyan separately. Disable any unsafe external actions by using disposable test alarms, contacts, and calendar items.

For every attempt capture:
- Exact typed/spoken phrase, locale, origin device and STT transcript when voluntarily supplied by tester.
- `HALO_INTENT_CLASSIFIED` (intent), `HALO_EXECUTION_ROUTE`, `HALO_NATIVE_ACTION_ROUTE` and tool ID.
- `HALO_TOOL_EXECUTION_START/RESULT`, `HALO_NATIVE_ACTION_TRUTH`, correlation/run ID, Android observed state and duration.
- Provider selections and presence/absence of `ProviderTurnStarted`.
- Permission / handler availability, approval decision, and whether the app is locked or foregrounded.

Avoid raw secrets, contact numbers, photos, message text, or screen tree data in exported logs.

## 2. Deterministic language and routing

| ID | Say/type | Required result |
| --- | --- | --- |
| N01 | Open Chrome | Exact installed app launched; no LLM, dispatch receipt not proof of foreground |
| N02 | Open Settings / open settings | Both invoke `halo_open_settings`; no case-dependent fallback |
| N03 | Go home / go to the home screen | `halo_ui_global_action` home; effect only CONFIRMED after observed launcher |
| N04 | Turn on flashlight / turn on the flashlight | Both `halo_flashlight`; verify actual LED, not only dispatch |
| N05 | How do I turn on the flashlight? | Ordinary explanation, **no action** |
| N06 | Open Halo Notebook / Open Study | `halo_open_screen` exact Halo destination, not external package |
| N07 | Open default apps settings | `halo_open_settings(section=default_apps)`; opens or truthful unsupported |
| N08 | Open developer options | `halo_open_settings(section=developer)`; opens or truthful unsupported |
| N09 | Open overlay settings / Open app language settings | Platform-guarded settings destination; no false success |

Repeat N02–N07 with OpenRouter Free, installed LiteRT, and a selected-but-unavailable model. Deterministic actions must not invoke a provider in any of these states. For voice, a functioning STT path remains required; typed commands isolate parsing from speech recognition.

## 3. Clock action lifecycle

| ID | Exact instruction | Required platform/user truth |
| --- | --- | --- |
| C01 | Set a timer for five minutes | Existing `halo_timer`; verify system clock displays countdown |
| C02 | Show my alarms | `halo_clock_view(show_alarms)`; observe alarm list; DISPATCHED alone not a viewed-page confirmation |
| C03 | Open timers | `halo_clock_view(show_timers)`; observe timer page |
| C04 | Dismiss the next alarm | `halo_clock_modify(dismiss_next_alarm)`; approval REQUIRED, Android may show chooser; only dismiss a disposable test alarm |
| C05 | Snooze the alarm | `halo_clock_modify(snooze_alarm)`; test only while disposable alarm is ringing |
| C06 | Dismiss expired timers | `halo_clock_modify(dismiss_expired_timers)`; never assert this clears an active timer |
| C07 | Deny approval for C04/C06 | No Intent dispatch or clock change; receipt REJECTED/CANCELLED as appropriate |
| C08 | No compatible clock app/handler | Typed CAPABILITY_UNAVAILABLE, zero claimed success |

Check Xiaomi's built-in clock as well as a second provider/device where possible. Reject unsupported Android versions without crash. Never claim alarm state CONFIRMED without independent observed state.

## 4. Contacts, Calendar, messaging

| ID | Command/action | Required result |
| --- | --- | --- |
| P01 | Create new contact | `halo_contact_editor(create)`; user-owned editor opens, nothing saved until user confirms |
| P02 | Edit a contact | `halo_contact_editor(choose_or_edit)`; never silently select or overwrite a contact |
| P03 | Tool call contact editor with name/phone/email | Bounded prefills; invalid phone/email rejects; editing with supplied name rejects |
| P04 | Create a calendar event | `halo_calendar_editor`; editor opens, user supplies missing fields and saves or cancels |
| P05 | Calendar editor with title and two valid times | Prefill when provided, reject only-one-time or invalid ranges |
| P06 | Revoke READ/WRITE_CALENDAR | Event *editor* still launches if the system handler permits; `halo_calendar_create` must reject direct write |
| P07 | Revoke READ_CONTACTS | Contact *create editor* still launches if handler permits; `halo_find_contact` must reject lookup |
| P08 | Compose text/email | Opens a draft through existing `SENDTO`; never claims send/delivery |
| P09 | App handler missing / handler throws | Truthful unavailable/permission failure; no invented saved contact/event |
| P10 | Approve/deny, rotate, cancel, restart | No replay of pending external mutations or private drafts |

Use disposable names/numbers and calendars. Inspect approval descriptions and ensure no raw personal values are written to exported journal summaries.

## 5. Media / Workspace / semantic app bridge

| ID | Action | Required result |
| --- | --- | --- |
| W01 | Notebook → Import file | OpenDocument URI returned, durable workspace object visible |
| W02 | Notebook → Choose a photo | Photo Picker URI returned, original imported into Inbox |
| W03 | Photo Picker → Cancel | No new object; no false success |
| W04 | Notebook → Photo | Actual TakePicture result imported; cancel discards staging file |
| W05 | Record memo → Stop & save | Audio saved locally as workspace object; short/failed capture not reported saved |
| W06 | Android Share to Halo | Imported text/file into Inbox, no automatic model upload |
| W07 | Current app capabilities | Supported AppFunction/DirectAction discovered only when platform and app expose it |
| W08 | Execute named current-app action | User approval, exact fresh token, no arbitrary deep-link guessing |
| W09 | Unsupported current app, revoked consent, stale token | Typed unavailability/manual takeover, zero unauthorized fallback |
| W10 | Foreground app UI goal needing tools | Accessibility/agentic route only when policy permits; no model-independent success claim for inferred multi-step goal |

## 6. Stability, negative paths, and source truth

- Interrupt a voice response with Stop; check a single terminal lifecycle and no resumed chunks.
- On wearable-origin input verify actual TTS output on the selected headset/glasses endpoint, never an unintended phone-speaker fallback.
- Test Home/Back on blocked, unreadable, switched or absent Accessibility windows; no stale token reuse.
- Change provider during idle/active operations and remove API credentials; native instructions must remain independent.
- Turn Bluetooth off/on during a request, rotate Halo, background/foreground it and restart during a pending native approval. No duplicate external action on recovery.
- Run System Check / Skills read-only audit: **zero** unresolved, orphaned, duplicated tool definitions and permission mismatches, and every newly registered capability discoverable. A capabilities listing is not E2E action proof.
- Check English, Latvian and Russian surfaces for new approvals, errors, and Photo Picker button. The phrase “Choose a photo” already has translations; new platform errors are currently English pending the translation audit.

## Sign-off rule

Mark alpha gate PASS only if: (1) all Gradle/assemble tasks pass, (2) the exact updated APK is running on device, (3) deterministic language actions work with and without a usable LLM, (4) approval/recovery/postcondition rules hold, (5) import and editor workflows produce truthful user-visible results, and (6) remaining Bluetooth/STT/LiteRT uncertainties are either physically verified or explicitly BLOCKED. Do not call the release fully stable from unit tests alone.

Output a concise result table per ID with PASS/FAIL/BLOCKED/NOT TESTED, Android state, Halo receipt, key timings, device information, and actionable fixes. Keep modifications separate from the test pass; regression fixes require a new commit and a full rerun.
