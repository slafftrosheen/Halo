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

