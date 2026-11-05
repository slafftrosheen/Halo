# Local development on the 8 GB Pi 5

Verified 5 November 2026 for `/home/pi/Projects/Halo`. These are machine-local settings, not CI defaults. Finish a coherent coding batch before building/testing; run one build or test process at a time.

## Installed profile

- Android SDK: `/home/pi/Android/Sdk`, build tools 37.0.0. Gradle uses the repository wrapper and pinned dependencies.
- Launcher JDK: `/home/pi/.local/share/halo-toolchains/jdk-21.0.13+9`. The repository's daemon criteria select native ARM JDK 17.
- `/home/pi/.gradle/gradle.properties`: one worker, parallel execution disabled, in-process Kotlin, native Gradle heap 6 GiB, G1 and one active processor/GC thread.
- `/home/pi/.local/bin/halo-gradle`: launches this checkout on CPU 0, sets the launcher JDK and native build concurrency to one, and passes through requested tasks.
- Persistent `/var/swap`: 16 GiB, `CONF_SWAPSIZE=16384` and `CONF_MAXSWAP=16384`.

## Building and testing

Use the local launcher from any directory:

```bash
halo-gradle :app:assembleDebug
halo-gradle :app:testDebugUnitTest --tests '*Organizer*' --tests '*Studio*'
```

The examples are separate invocations. Do not run them concurrently. Add `--offline` when all required dependencies are already cached. Select the related regression set for the whole batch; these two example filters alone are not all issue 63–65 coverage.

The pinned Robolectric native distribution needs x86-64 Java on this ARM host. `/home/pi/.gradle/init.d/halo-pi-tests.gradle` applies only when the root checkout is exactly `/home/pi/Projects/Halo`. It sets one test fork, a 1 GiB test heap, G1, one active processor, C1-only compilation and a 256 MiB code cache. It uses the verified Temurin 17.0.21+9 x64 JRE wrapper at `/home/pi/.local/share/halo-toolchains/jdk-17.0.21+9-jre/bin/java`, backed by Box64 and `java-x86_64`. Keep application/Gradle compilation on native Java.

The profile was configuration-checked before a completed 652-case related run. C1-only with the larger cache completed the suite; previous default-JIT/internal Compose failures and a smaller code-cache exhaustion remain documented historical attempts. Do not add another init file with duplicate JVM flags. Moving the checkout requires updating this local canonical-path scope.

Regular production changes must compile their production tasks. The final test-only correction run reused already built production classes with four explicit task exclusions; this was a recorded exception because only a test fixture changed, not a recommended build command. Never reuse those exclusions after changing application code.

## Phone and evidence

Install updates with `adb install -r`; preserve the user's data, original projects, model choice and widgets. Verify APK signature/source marker and compare the installed APK hash before claiming a matching build. No emulator needs to run alongside the real phone on this machine.

The latest built/installed production source is `e1a39a177321e6238434cc81c9764d3887a4136e`. The subsequent fixture-only correction passed **652 cases, zero failures/errors/skips, 113 suites** on one worker/fork. See [the validation record](../testing/ISSUES-63-65-PHONE-2026-11-04.md). New navigation/knowledge work will require a fresh production build and device validation after its complete coding batch.
