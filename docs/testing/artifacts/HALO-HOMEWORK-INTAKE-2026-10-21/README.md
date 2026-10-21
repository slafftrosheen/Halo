# Homework intake and wording review

Five inspected native host component compositions, not Android device screenshots or website assets:

- [Study wording review](wordingStudyLight360.png)
- [Kids wording review](wordingKidsLight360.png)
- [Kids review at 200% text](wordingKidsLargeText360.png)
- [Kids dark review at 412dp](wordingKidsDark412.png)
- [Unreadable photo recovery](unreadablePhoto360.png)

The final APK assembled and all 20 targeted checks passed with zero failures/errors/skips. `manifest.json` records dimensions, hashes and APK provenance; `test-results/` contains JUnit XML evidence. The decoder warning in the log comes from the intentionally invalid-file regression case, which passed.

Checkpoint: `releases/checkpoints/halo-brandbook3-homework-intake-2026-10-21-debug.apk` in the repository. SHA-256: `41c92e0a3f4d828ccddcb5e42dc5c549f391d21981e3353a1b4e8aa33040ec23`.

Subsequently installed on 21 October with `adb install -r` (Success) and launched (Status ok, WARM) in the original app, preserving its data and provider configuration. This is installation/launch proof only. Camera result delivery, rotation/background recovery, real photo quality, OCR and provider behavior still need physical-device testing. Host previews do not establish full screen pixel parity. Source remains local, uncommitted and unpushed.
