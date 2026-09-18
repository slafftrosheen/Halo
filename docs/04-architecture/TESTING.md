# Testing map and acceptance

Status: CURRENT SOURCE MAP / DESIGN TARGET. Reviewed 2026-09-18.

Test sources exist across runtime, providers, drivers and app.
[drivers/simulator](../../drivers/simulator) provides simulator helpers.
[App build configuration](../../app/build.gradle.kts) declares Robolectric, Roborazzi
and Compose testing dependencies. Presence is not proof of complete coverage or passing tests.

See [CI](CI_CD.md) for declared commands and gaps. No fresh tests were run for this
documentation correction.

Acceptance targets: contract/cancellation tests, provider wire fixtures, simulator flows,
UI state/accessibility testing, Android lifecycle/permission/audio instrumentation and
physical glasses acceptance. Synthetic inputs validate code, not hardware truth.
Record actual command, commit, environment and result for every verification claim.
