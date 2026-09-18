# CI configuration

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

[android.yml](../../.github/workflows/android.yml) declares an Ubuntu/JDK 21 job for main
pushes/pull requests. It calls detekt, ktlintCheck, lintDebug, testDebugUnitTest,
:drivers:heycyan:testDebugUnitTest and assembleDebug, then uploads app-debug.apk.

A declared step is not proof of success. The inspected Gradle files/version catalog do
not configure Detekt or ktlint plugins/tasks. Resolve that mismatch before calling these
static-analysis gates operational.

The workflow does not declare instrumented/emulator testing, API compatibility checks,
KDoc generation, documentation validation, measured performance gates or release AAB
distribution. No current Actions/local build result was obtained in this docs review.
