# DevKit 1.4 build and connected-phone verification

## Source and artifact

- Synchronized base: `49895796ff90b76fcc593f43da50b0c1c71e591e`, matching `origin/main`.
- Debug APK SHA-256: `E78938271536846A2AF16E2FAF5162A1AC16C93484FD6396B9E398F68480BDC4`.
- This APK was built from a dirty workspace before the owner requested publishing all local changes. Its source stamp is the base above plus the compilation/verification fixes below. The 624 pre-existing DevKit v1.2 deletions were preserved during testing and are included in the subsequent owner-requested commit. This is not a clean committed release artifact.
- Phone: Xiaomi Redmi `23124RA7EO` / `sapphiren`, serial `f01fe673`.
- Package: `com.aistudio.braintool.xkqzps`. Both app and instrumentation APKs installed with `adb install -r`; no uninstall or app-data clear.

## Build and automated checks

Gradle ran serially with the Android Studio JBR, `--no-daemon --no-parallel --max-workers=1 -Dorg.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`.

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: PASS.
- 35 targeted app tests: PASS. Includes Settings Everyday/Advanced navigation, appearance in light/dark/Russian large text, Kids choices/preferences/read-along/responsiveness/visuals, Study lesson count, catalogue/tool filtering and provider configuration safety.
- 22 LiteRT provider tests: PASS. Hugging Face catalogue and downloader behavior.
- Physical `LocalModelManagerPhoneSmokeTest`: PASS, 3 tests on the final installed APK. Real installed artifact import through FileProvider, uppercase extension, fingerprint/size/canonical identity, duplicate detection, invalid-file cleanup, token trim/apply/clear and header-injection rejection.
- Final APK Qwen 3 0.6B CPU / 1024 context probe: PASS, including raw generation and Halo provider verification (`providerPassed=true`). Installed inventory verification also passed. No new models were downloaded and no GPU run was performed during this verification pass.
- The new blocked-inventory provider setup test first failed with a timeout against the compiled pre-fix implementation, then passed in the full final run. The red run intentionally reused the baseline compiled main classes; the final build used no compilation exclusions.

## Downloads import and interface inspection

Android's real Downloads picker imported `gemma-4-tiny-random.litertlm`, 25,907,888 bytes. The original Downloads file and Halo's private copy both hash to `d0c6bec1f1b8b72a05eb99e9f7930a57f14bf3fa8bc905367381f1855e921bf0`. Inventory changed from 16 present/verified to 17 present and 16 verified. The imported model is visibly unverified; it was not selected or claimed runnable. Two larger `.pending-…` Downloads files were left untouched.

Russian phone inspection confirmed separate Everyday/Advanced settings, provider configuration rows, on-device counts and the imported model in the on-device filter/search. Imported calibration/runtime-profile and developer-test controls are now gated by Advanced. Screenshots show the before/after result.

Kids mode was restored to enabled through Settings; its persisted preference was read back as true. Home → Explore and Home → Create navigation both worked. No cloud grant or device-credential bypass was performed. Remaining visual observations: Russian Explore wraps a final letter onto a second line, some new Kids copy falls back to English, and the dock's unsized side icons are not visible. These were recorded when the owner requested closing issue 34 and moving to issue 33; no fix or acceptance is claimed for them. The phone disconnected after these checks.

## Fixes made during verification

- Fixed an out-of-scope Kids options error state and a nonexistent voice-dock shape token that prevented compilation.
- Made hostless design previews render text fields without instantiating Android microphone/playback services. Layout/navigation tests explicitly use that preview environment; live speech behavior is not inferred from those tests.
- Updated stale Kids home and tool-filter assertions to the implemented two-entry flow and Halo prompt tool candidates; native-tool evidence flags remain unchanged.
- Published provider configuration before large local inventory fingerprint scans, preventing an empty setup screen while scanning.
- Gated imported-model calibration/developer controls under Advanced.

## Boundaries

Authenticated Hugging Face account/model access, gated-license acceptance, native tool-call completion, full Kids learning outcomes, widget/Mini Halo acceptance and pixel-perfect completion of issue 34 are not established by these checks. The owner explicitly requested closing issue 34 and moving to issue 33; closure does not change these evidence limits. No hosted CI or release upload was requested or claimed.

Evidence: [artifact folder](artifacts/HALO-DEVKIT14-PHONE-2026-10-18/).
