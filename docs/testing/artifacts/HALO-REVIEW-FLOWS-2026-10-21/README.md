# Study review and Kids creation/reading previews

Six native host component compositions from the final source, inspected after the build. They omit route chrome and Android system UI. They are not authentic Android screenshots, website assets or full pixel-parity acceptance.

- [Kids draft, light](draftLight360.png)
- [Kids draft, dark](draftDark360.png)
- [Kids draft, 200% text](draftLargeText360.png)
- [Study review front](reviewFront360.png)
- [Study review back](reviewBack360.png)
- [Story reading, 412dp and 200% text](storyLargeText412.png)

`manifest.json` records dimensions, hashes, the exact APK hash and the final 32-check result. `test-results/` contains the final JUnit XML evidence. All 32 checks passed with zero failures/errors/skips.

APK: `releases/checkpoints/halo-brandbook3-review-flows-2026-10-21-debug.apk` in the repository. SHA-256: `eadf6d574ba59b47e29286afe185278a4e7a75ac5ab83ef3c0ee3eb5194e000c`.

The phone was disconnected at handoff. These new flows have not been installed or device-verified. Earlier physical-device evidence is recorded separately under `HALO-STUDY-KIDS-2026-10-20/device/` with its own installed APK hash.
