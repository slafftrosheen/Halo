# Halo BrandBook v1.6 — Settings acceptance checklist

Source of design intent: `BrandBook.pdf`, pages 44–45; `BrandAssets/README.md`.
Scope: Settings, Intelligence, Voice, Context & privacy, Permissions, Advanced.

## Build and targeted tests

Run on an Android development machine with the repository's configured SDK:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest \
  --tests "com.halo.app.ui.screens.settings.BrandSettingsV16Test" \
  --tests "com.halo.app.ui.screens.settings.BrandVoiceMicPolicyTest" \
  --tests "com.halo.app.ui.screens.settings.BrandAdvancedScreenTest" \
  --tests "com.halo.app.ui.shell.BrandPermissionAbilitiesTest"
```

If Gradle does not accept `--tests` across both tasks, run `:app:compileDebugKotlin` separately, then `:app:testDebugUnitTest` with the listed `--tests` filters.

## Functional smoke test (English, Latvian, Russian; dark and light)

- [ ] Settings opens with the real selected provider and speech readiness; no fabricated cloud/local readiness.
- [ ] Search icon opens a text field. Query `privacy` finds Privacy; query `runtime` finds Advanced. Clearing restores all groups. An unknown query shows an empty state.
- [ ] Settings entry to Intelligence presents exactly three location choices. Opening each navigates to its existing configuration screen; merely opening a card does not change the selected provider/model.
- [ ] If a remote provider is actually selected, only In the cloud is marked current. An unconfigured system must **not** show a preselected cloud target. On this phone is recommended, not implicitly selected.
- [ ] Existing provider configuration, generation proof, model selection and model-role routes still work.
- [ ] Privacy labels the lack of a verified daily network audit honestly. Local storage and remote-context switches have distinct meanings.
- [ ] Privacy → Permissions opens the Android access owner. Denying microphone permission never blocks typed questions.
- [ ] Permissions shows the same seven ability names as Devices, without treating Android grants as complete skill approval.
- [ ] Opening Voice does **not** begin capture or show a mic permission request. Start mic test prompts only if required. Finish recording ends capture; the test produces a result but does not submit a command.
- [ ] Backgrounding during mic capture cancels the active SpeechRecognitionProbe. Re-entering Voice shows real probe state.
- [ ] Advanced reports real runtime counts and links to Runtime Status, System Check, and Storage & Backup; it contains no nonfunctional reset button.

## Accessibility / layout

- [ ] Touch targets remain at least 48dp at default scale.
- [ ] Test 320dp portrait, landscape with keyboard, and 200% Android font scale.
- [ ] TalkBack announces location status by text (not solely accent color) and identifies Voice test controls.
- [ ] Check EN/LV/RU for clipping and incorrect technical labels.
- [ ] Verify Lottie pauses when the Activity stops; returning must not restart an already completed terminal animation.

## Limits / non-claims

- The repository changes are **not** an Android compilation or emulator verification.
- No complete daily per-provider egress log is currently wired to Privacy. A zero-transfer hero or Privacy Report would be misleading without a measured source.
- Per-capability Allowed / Ask first / Off policy is not invented from Android runtime permissions. The Permissions screen uses a conservative, truthful status until a canonical effective-policy projection is connected.
- BrandBook's proposed Reset Halo and live technical log shortcut are deferred until the actual owners and recovery/export contracts are verified.
- Do not use GitHub Actions for this repository; run builds on the local dev machine.
