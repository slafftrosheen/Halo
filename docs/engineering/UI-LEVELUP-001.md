# UI-LEVELUP-001 — human-facing UI polish

## What changed

- **Home:** compact context/provider area, a visible mode selector, reply-first reading order, neutral review guidance, grouped attachments/tools/history, and a composer anchored above navigation. Send, Talk and Stop have separate labelled positions. Translated action widths determine whether decorative icons fit or Talk/Send stack; interaction state never determines placement. Drafts remain editable during work; sending remains gated. Stop also handles the brief microphone-startup window before a capture exists.
- **Activity:** pending reviews appear first; each has an explicit Review action. Review rebuilds the existing journal projection and selects that run's prompt. Cards lead with state, request and summary; technical steps stay behind Details. Unconfirmed results, interruption, cancellation and failure keep distinct labels.
- **Shared actions:** existing Material/theme primitives provide filled primary, outlined secondary, muted error-outline Stop/discard, and text utility actions. Workspace and legacy glass buttons use the same treatment, with at least 48 dp targets. Shared rows use at least 56 dp, readable supporting text and more spacing.
- **Other screens:** Devices uses one scrolling list with labelled Add device and optional phone details. Study has larger material buttons and explicit Keep/discard card controls. Settings uses clearer destination descriptions. Workspace/Memory empty states explain a next step. Navigation translates its labels and keeps Workspace selected on Study routes.
- **Copy:** short state vocabulary and 102 new EN/RU/LV catalogue entries; regenerated all three resource files and the Kotlin catalogue using `scripts/generate_ui_languages.py`. A pre-existing sandbox compilation issue was corrected by resolving translated accessibility labels before semantics callbacks.

## Boundaries

This batch changes host presentation and a narrow Home cancellation fallback. Runtime admission, journal truth, confirmation resolution, tool dispatch, provider selection and wearable routing remain their existing owners. Review does not approve, retry or execute a task. Closing an input prompt hides it; Activity can reopen it. Incomplete answers retain their projection identity and cannot be saved as committed answers. Native-action failures direct users to the recorded outcome before trying again. No workflow changes or GitHub Actions runs are part of this batch.

## Validation

The serial local gate `:app:testDebugUnitTest :app:assembleDebug :runtime:cognition:test` passed: 482 app tests and 251 cognition tests, zero failures or skips, and successful debug APK assembly. This includes the new interaction, status and native-render fixtures. Large translated action labels were visually inspected at 150–200%; the complete composer stays readable. EN/RU/LV resource-key and format-placeholder parity passed for all 1,258 catalogue entries. Focused tests cover composer action identity/placement, editing while busy, talk interruption callbacks, cross-conversation status precedence, pending review, and avoiding blind native-action retries. Robolectric fixtures render the actual composer in light/dark themes and RU/LV at 150% text size and Russian at 200%; these are component checks, not full-screen or device acceptance.

## Hands-on checks still required

Check the full Home flow with the keyboard open, one-handed reach, 150–200% system text, TalkBack focus and announcements, long translated labels, and long replies. Exercise type/send, Talk/Stop during microphone startup, speech interruption, approval/decline/dismiss/reopen, Home → Activity → exact pending prompt, and two conversations with one active request. Confirm device-page scrolling and Study card controls on a small phone.

Advanced provider diagnostics, detailed journal/tool output and some older secondary screens still contain technical wording. This batch curates the core surfaces; it does not establish elderly/kids usability without observing real users, or phone/wearable/provider acceptance without physical testing.
