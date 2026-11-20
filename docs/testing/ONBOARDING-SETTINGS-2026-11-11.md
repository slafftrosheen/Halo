# Guided setup uses the current Settings owners

Base: `257a0136349f933783eb64d96aa726c2c4206359` (`main`), with the local build and policy-test fixes.

## Problem and change

Fresh setup exposed separate provider/key forms, a truncated model picker, and hardcoded speech downloads. The user reports that the corresponding Settings screens work. Guided setup now opens those existing screens through AppShell's guarded navigation, then returns to the same guide entry and stage.

| Setup area | Existing destination and owner |
|---|---|
| Service keys | `settings/intelligence/providers`; ProviderControlRepository and protected ProviderDetailScreen credential/OAuth controls |
| Answer selection | `settings/intelligence/models?role=CHAT`; ModelBrowserViewModel, provider catalogue and existing router eligibility checks |
| Local models | `settings/intelligence/on-device`; IntelligenceViewModel and LocalRolePackInstaller |
| Listening | `settings/voice/input`; VoiceSettingsViewModel, language repository and current speech model catalogue |
| Speaking | `settings/voice/output`; VoiceSettingsViewModel and existing speech output coordinator/providers |
| Devices | `devices`; DevicesViewModel and existing host/device/audio registries |

The guide reads the shared provider's generation proof, including a check completed in Settings. The provider repository already invalidates this proof when the configuration or selected model changes. A provider-resolved model ID need not equal a requested alias. A later failed/unavailable connection remains unready. Microphone capture, accepted recognition, audible confirmation and first-interaction verification remain distinct checks; a download or navigation dispatch does not establish success.

## Existing typed operation contracts

This change removes duplicate presentation and fixes navigation. It adds no provider, download, microphone or device operation and makes no new claim of assistant functional parity.

- Provider configuration uses the existing v1 `host.assistant.providers` contracts for `halo_provider_catalog`, `halo_provider_state`, `halo_provider_refresh_models` and `halo_provider_test_connection`. Configuration checks retain exact source-version guards, foreground/grown-up admission, durable approval and non-replay rules. Protected credential/OAuth editing stays in the human Settings flow. The guide's answer check now uses the same per-test paid-usage consent dialog as Settings.
- Local model acquisition retains the v1 `halo_local_setup_status/start/pause/keep_files/rollback` contracts and canonical owner. Source/license review, transaction identity, download policy, capacity checks, rollback approval and uncertain-write handling remain in those owners.
- Voice configuration retains `halo_get_voice_configuration` and `halo_set_voice_configuration`, typed revision/snapshot guards and existing permissions. These metadata operations do not grant capture, playback, credential or audible-acceptance authority. Android permission prompts and the user's audible confirmation remain human actions.
- Devices continue through the same existing guarded Devices screen. This change neither changes BLE discovery/protocol behavior nor bypasses pairing, permission, Kids, Private/Power or approval gates.

The focused gate includes `CompleteConfigurationDescriptorTest` and `LocalModelSetupToolExecutorTest`, whose registered-feature `operabilityReport` assertions require an empty problem list. It also includes provider and voice owner regressions. Source declarations and desktop tests are separate from physical device/provider acceptance below.

## Regression and build evidence

Before the navigation fix, the real Navigation Compose fixture reproduced both failures:

- Opening a root configuration destination restored `voice_settings` instead of the requested `notebook`.
- Returning from Devices created a different `guided_setup` entry and lost its saved check state.

Baseline: 5 tests, 2 failures; `.gradle/onboarding-navigation-before.log` and `.gradle/onboarding-navigation-before.xml`.

Added coverage checks the same entry/saved state, fresh-setup Back behavior, ordinary tab restoration, shared answer proof, resolved model aliases, changed/unverified selections, and failed connections. Existing onboarding tests retain progress, limited-mode and truthful interaction checks.

Verification command (after loading `.gradle/dev-env.ps1`):

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest --tests 'com.halo.app.ui.onboarding.*' --tests 'com.halo.app.ui.shell.ExactPrimaryNavigationTest' --tests 'com.halo.app.ai.ProviderControlSafetyTest' --tests 'com.halo.app.di.tools.CompleteConfigurationDescriptorTest' --tests 'com.halo.app.di.tools.LocalModelSetupToolExecutorTest' --tests 'com.halo.app.di.tools.VoiceConfigurationToolsTest' haloUnitCore --console=plain
```

Result: **PASS**, `BUILD SUCCESSFUL in 18m 32s`, 539 tasks (24 executed, 515 up-to-date). The focused app gate ran **49 tests in 9 suites**, zero failures/errors/skips. Core gate remains green with the existing **850 passing tests**; its unchanged inputs were up-to-date in this run. Both previously failing navigation regressions now pass. Log: `.gradle/onboarding-build-tests.log`; preserved JUnit: `.gradle/issue-audit/onboarding-results/`.

APK SHA-256: `f5ece6407298cc8f9a5edc22ed6b09d66f639f548d5d2a4923b57a5c054ca061`. Signing certificate matches the fresh app installed earlier in this session. Source change hashes: `.gradle/issue-audit/source-changes.json`.

## Device, spoken and remote acceptance

After the user requested an update and testing against all open issues, this APK was installed on the connected Redmi with `adb install -r`: **Success**, data retained. MainActivity launch returned **Status: ok**; the existing Russian language setting and conversation remained visible. The user-configured Power/OpenRouter route is still shown. No credentials were read. Native setup/workflow acceptance is now in progress in [the open-issue report](OPEN-ISSUES-ACCEPTANCE-2026-11-11.md); successful installation/startup alone does not establish the workflows below.

| Workflow | Typed on device | Spoken on device | Typed permitted Power | Spoken permitted Power | Durable device readback |
|---|---|---|---|---|---|
| Configure a provider/model, test an answer, return to guide | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Configure local listening/output, capture speech and confirm audio | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |
| Configure an accessory, return to guide and verify device audio | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN | NOT_RUN |

Desktop verification does not close physical microphone, audible-output, wearable, actual STT-final or remote-provider acceptance. No new CI/Actions workflow is introduced.

### Observed phone navigation

On the Redmi/Android 15, the guide opened the full provider list, answer-model browser, local-model manager, speech input, speech output and Devices screens. Each returned to the same guide stage (AI 4/10, speech 5/10, devices 6/10); the Devices return also preserved its scroll position. Existing OpenRouter selection, downloaded recognition and configured voice settings were visible. No keys were read or replaced and no model download was initiated. Save-and-exit displayed its confirmation and returned to Home.

These observations establish the shared-settings navigation. They do not establish actual recognized speech, audible output or a provider generation result. Raw UI evidence is in `.gradle/issue-audit/phone/guide-*.xml`. A debug LeakCanary screen interrupted one model-return attempt; after dismissing it, the same path returned correctly.
