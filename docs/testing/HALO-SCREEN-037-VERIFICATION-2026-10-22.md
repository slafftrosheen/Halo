# HALO-SCREEN-037 verification — 2026-10-22

## Build and focused tests

- `:app:assembleDebug` passed.
- `:app:testDebugUnitTest --tests=com.halo.app.screen.LocalScreenStoreTest --tests=com.halo.app.screen.ScreenLayoutRequestsTest` passed: 9 tests, 0 failures.
- Installed debug APK SHA-256: `00CA9B8DDAC8DB34209AB933D742DE2E4EEE7BE09C827E2955E7BD86059671B6`.

## Connected phone

- Samsung SM-G990B2, Android 16 / API 36, device `R5CTA0TKLZZ` (1080 × 2340 capture).
- Opened Screen from Chat, selected Focus, and verified the setup page does not show a nonfunctional Customize action.
- Entered Customize and confirmed a single Customize/Done control plus Add card. Moved the clock card down; the task card appeared first and the change showed Undo.
- Used the bounded text composer with dodaj pogoda (add weather). It showed a native-card preview, applied it, and Undo restored the previous Focus layout. The composer also showed the missing local speech-model message and install route; text entry remained available.
- Force-stopped and relaunched the app. The Focus layout and reordered card order persisted.
- Entered full-screen mode. The exit action, Screen settings action, and Talk to Halo action were visible. Personal cards rendered as “Private card” while hide-private was enabled.
- Opened Screen settings from full-screen mode. The app exited ambient mode before showing settings; “Hide private details in full screen” remained enabled. Android Back closed the settings sheet, and Chat remained available.
- No `FATAL EXCEPTION` or app process death appeared in the final 1,200 logcat records checked.

ADB input was used to exercise phone touch handling. This is device-level UI evidence, not human accessibility or stylus testing.

## Still open for #37

This pass does not qualify sourced-research creation, a real local automation result end to end, voice composition, TalkBack traversal, tablet/rotation layouts, or long-duration ambient behavior. Keep #37 open until those remaining acceptance paths are verified.

## Current source checkpoint — 2026-10-22, commit `90f8bf91`

Installed the current debug APK on the attached Samsung SM-G990B2 (Android 16 / API 36, `R5CTA0TKLZZ`) with `adb install -r`, preserving app data. Pulled the installed base APK back from Package Manager; its SHA-256 matched the local artifact exactly:

```text
13E3626CADD15C0F969E613DC8DDBC86957B9DF0A925D43170DF2448766A0D00
```

Opened Halo Screen from Chat. The saved Focus layout rendered after the app update. Entered full-screen mode and observed the exit, Screen settings, and Talk to Halo actions; the personal card was masked while “Hide private details” was enabled. Tapping the visible exit control returned to standard Screen, and Android Back also exited full-screen mode. The persistent mode switch showed the new `Private / Power` label in Russian UI. No model was loaded and no inference was run. These checks establish a current-build phone UI smoke pass only; the remaining gates above are unchanged.

At system font scale `2.0` (200%), the Focus title and reading card remained within the viewport, longer text wrapped, and the page remained vertically scrollable with bottom navigation visible. The original system font scale was `1.0` and was restored after the check. This is a large-text phone check, not TalkBack or tablet-layout evidence.

With the phone's system rotation temporarily set to landscape, Halo Screen reflowed in the 2340 × 1080 capture; Chat/Screen switching, the Focus title, Customize, Add card, and bounded text-composition entry remained visible. Auto-rotation was enabled again and the saved rotation restored to portrait after the check. This does not qualify a tablet layout.
