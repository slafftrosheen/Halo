# Remaining device phase (not run on delivery binary)

The owner confirmed the phone is unavailable for now. These are queued checks, not passing evidence. Do not poll for reconnection, start recording/playback, grant OS permissions, change credentials, or invoke a paid provider to satisfy them.

1. Read current device/app settings and restore only test overrides: original Android font1.0, density with no override, automatic rotation/user rotation0; original Light/Mint/Cool/Halo forms. English and Power were already restored before disconnection. A guarded local display-restoration script is retained beside the raw evidence.
2. Replacement-install both delivery APKs without clearing data. Verify installed-base SHA256 against the delivery manifest, then verify cold theme persistence and the previously created QA62 note/linked Canvas.
3. Check exact Home hardware/IME typing, caret/composition, startup draft recovery, Send admission in an isolated fixture, history lockout, Chat/Screen switch, navigation/rotation/background return, normal and200% portrait/landscape with keyboard. Check short-footer scrolling reaches every original-size control.
4. Check real page-only carousel/full-list selection and guarded route/callback effects, four fixed roots, bell subview/filter retention, duplicate-header removal, current search label, EN/RU/LV/PL at360/412dp, dark/contrast/reduced motion. Inspect actual accessibility focus and actions; host semantics are not a physical TalkBack claim.
5. Run the vetted silent instrumentation group with fresh runId/sourceStamp and save raw results. Most suites use isolated fixtures; real Activity cases cover navigation/lifecycle. Exclude GlobalTalkCapturePhysicalTest. MainThreadIoGuardTest has weak optional selectors and is not accepted as UI/IO proof.
6. Complete only the missing real integration boundaries: source Note to linked/edited Canvas to exact exported note and source-linked Study progress; current Studio edit/save/export/reopen; delayed approval/export and stale/interrupted/uncertain effects through original owners. Measure controlled foreground/background/static/reduced-motion rendering and lifecycle cost. Human speech/active-capture/Mini and audible reply remain separately deferred.

Vetted no-audio classes:

```text
com.halo.app.ink.AndroidInkInputTest
com.halo.app.ink.RemoteInkPhoneTest
com.halo.app.ui.workspace.WorkspaceContinuityDeviceTest
com.halo.app.ui.workspace.WorkspaceOperabilityDeviceTest
com.halo.app.ui.workspace.WorkspaceFileToolsDeviceTest
com.halo.app.ui.workspace.StudioDeviceTest
com.halo.app.ui.screens.notebook.CanvasDraftReceiptDeviceTest
com.halo.app.ui.screens.notebook.CanvasPdfInspectionDeviceTest
com.halo.app.ui.screens.notebook.CanvasPreviewDeviceTest
com.halo.app.notebook.NoteEditorControlsDeviceTest
com.halo.app.notebook.NoteAttachmentControlsDeviceTest
com.halo.app.notebook.NotePurgeDeviceTest
com.halo.app.study.StudyLearnerControlsDeviceTest
com.halo.app.language.PolishLanguageDeviceTest
com.halo.app.ai.PrivateDispatchOwnerTraceTest
com.halo.app.ai.LocalRolePackRecoveryDeviceTest
com.halo.app.ui.presence.InterfaceFourDeviceTest
com.halo.app.ui.shell.MainActivityScreenIntentPhysicalTest
com.halo.app.ui.mini.MiniHaloActivityPhysicalTest
```

Loopback/provider-denial fixtures do not establish real provider or speech success. InterfaceFourDeviceTest is an isolated native Orb fixture; it does not establish the complete AppShell journey. The new main action list is context-only; standalone/Mini legacy destination behaviour remains supported.

The owner authorized moving to a subsequent issue batch after62 is finished. Keep62 open until its mandatory acceptance or a specific exception is explicitly accepted; publication and green host tests do not satisfy that condition.
