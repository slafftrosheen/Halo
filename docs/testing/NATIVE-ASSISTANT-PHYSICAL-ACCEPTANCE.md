# Native assistant physical acceptance

Status: PHYSICAL ACCEPTANCE PENDING. Automated status is recorded below after the final build. Do not infer physical completion from compilation.

Record APK revision/hash, phone model, Android version, assistant selection, permissions and screen lock state. Do not record contacts, notification bodies or credentials in shared logs.

1. Choose Halo as assistant. Close Halo's main Activity, invoke the system gesture and verify the compact session appears without constructing HomeViewModel.
2. Repeat with CHAT set to Gemini, OpenAI, OpenRouter free, OpenRouter concrete text model and LiteRT text. Try app launch, two-minute timer, 07:30 alarm, flashlight, volume, brightness, settings, clipboard, sharing, media status/control and device insight. Capture native route/run/tool receipts and verify no provider turn starts. Repeat with the selected provider unavailable/503.
3. Ask a compound action with AGENTIC configured, then disabled. Confirm AGENTIC routing or clear setup requirement and unchanged CHAT binding. Ask an ordinary question and confirm NONE tool mode.
4. Deny contacts and calendar permissions; Check must explain availability without performing an action. Grant each permission independently and repeat without restarting Halo. Read calendar, then approve an explicit bounded event and verify it in the calendar app.
5. Exercise notification source policy: an allowed live notification is listed; one without RemoteInput cannot be replied to; approve a real RemoteInput reply; deny then approve dismissal. An expired key must not report success.
6. Lock the device. Sensitive host actions require unlock. App data, screenshots, clipboard, notifications and notes must not be revealed. Keyguard assistant launch remains disabled until verified.
7. Approve a native action after UI recreation/process restoration. Confirm the NATIVE run resumes once without provider execution. Unknown external outcomes must not automatically retry.
8. Invoke from HeyCyan. Verify DISALLOW_PHONE retains glasses output, including failed/unavailable route. Check audio prewarm and 90-second ASR residency.
9. Start local model loading/generation, then capture fresh speech. Verify cooperative cancellation and ASR lease admission without needless model reload; record actual timing.
10. API 36+ AppFunctions: inspect generated service/index, invoke each safe bounded function and verify authorization and IO dispatch. On lower API levels the normal app must remain usable.
11. Home cold and warm: capture first-state/interactive timestamps, verify SignalFace and four quick actions, attachment privacy/setup links, compact assistant status and persistent provider/ElevenLabs settings after restart.

No physical phone result or deployment is claimed in this batch until these checks are recorded.

## Automated result

The complete requested suite passes: 610 tests, no failures/errors/skips, and a successful debug APK build. See the [runtime validation record](../engineering/PHONE-ASSISTANT-002-HOST-FIRST-RUNTIME.md#automated-validation--2026-10-01) for commands, coverage limits and APK hash. Physical and live-provider results remain pending; no device is connected over ADB.
