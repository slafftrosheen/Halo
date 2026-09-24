# Deferred CI workflows

These workflow files are prepared but intentionally **not active**. CI is being reorganised later,
once the architecture is confirmed, so `.github/workflows/android.yml` has been left exactly as it
was found.

Move them into `.github/workflows/` to enable them:

```text
android-emulator.yml   hermetic instrumentation on an emulator, with the infrastructure-only retry
android-hil.yml        opt-in self-hosted hardware suite (never runs for a pull request)
```

Both depend on the layered Gradle entry points in the root `build.gradle.kts`
(`haloUnitCore`, `haloUnitHeyCyan`, `haloUnitLocalAi`, `haloUnitAndroidLibs`, `haloUnitApp`,
`haloAppBuild`, `haloStatic`) and on `tools/ci/run_instrumentation_with_retry.sh`.

Two notes for whoever enables this:

* The current `.github/workflows/android.yml` calls `./gradlew detekt` and `./gradlew ktlintCheck`.
  Neither task exists in this repository — there is no detekt or ktlint configuration, and no
  `.editorconfig`. Those steps cannot have been passing. Android Lint (`haloStatic`) is the static
  analysis this project actually has.
* `haloStatic` is currently red on pre-existing Android Lint errors in `app/` and `host-android/`
  (`MissingPermission`), which is why `haloVerify` does not depend on it yet.
