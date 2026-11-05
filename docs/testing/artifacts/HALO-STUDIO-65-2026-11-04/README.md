# Latest correction gate — 5 November 2026

Production `e1a39a177` compiled, packaged, signature/source checked and installed as an update with matching on-device hash. The later test-only cold-Room fixture correction passed **652/652 cases, 113 suites, zero failures/errors/skips** in one worker/core/fork. See [the final summary](phone-batch-07/phone-batch-07-junit-summary.json), [JUnit archive](phone-batch-07/phone-batch-07-junit.zip), [command/result](phone-batch-07/phone-batch-07-result.json) and [APK identity](phone-batch-07/apk-e1a39a177.json). Production tasks reused the already verified e1a outputs; no new APK is attributed to a test-only commit. Final evidence explicitly reconciles a stale launch patch fingerprint with the actual source/class hashes and timestamps.

[Gate06](phone-batch-07/phone-batch-06-junit-summary.json) had 651/652 passes: cold Room setup consumed the race fixture's ten-second timeout before its held read began. Setup moved outside that race bound; actual disclosure-revocation assertions and bound remain. All four failures from gate04 are corrected in the completed gate07.

Native e1a checks passed normal Studio primary labels/busy-to-idle and [Organizer Save/Cancel above the IME at 200%](phone-batch-07/organizer-footer-e1a39a177.json). Studio's 200% labels remain awkward; adaptive layout is in the new proposed navigation scope. Physical reboot, exhaustive themes/motion and all physical widget variants remain unverified; paid generation stays skipped. See [the updated phone report](../../ISSUES-63-65-PHONE-2026-11-04.md). The records below remain historical and their pending-correction status is superseded by this section.

---

# Latest verification checkpoint

Clean `88291b18f` compiled, packaged and installed as an update. APK SHA-256 `fe1fd84c122bb2b91f8ef4dd759b32698e6b7fae2d6869852e166330ecf7b97a`; on-device bytes and signature matched. Actual spoken Russian Inbox capture saved the exact requested task, survived reopening and did not crash. A real timer notification was observed while Android remained in forced deep idle; normal mode was restored and the own timer stopped. Studio's four groups remained visible through add/save/undo/save; named Blur and Grain switches operated independently.

The completed C1-only related run executed **649 cases: 645 passed, four failed, zero errors/skips**. Its 256MiB code cache remained enabled. The four failures are oversized Studio JSON readback, narrow ИИ label clipping, a recurring-receipt fixture assuming one occurrence, and a callback assertion before the asynchronous writer finished. The next coherent source batch addresses these and the native Organizer editor's buried Save control. That batch still requires a fresh compile/test/APK/device gate. [Current evidence](phone-batch-04/summary.json), [phone report](../../ISSUES-63-65-PHONE-2026-11-04.md), [status](status.json).

Paid image generation remains skipped by the user's instruction. Physical reboot delivery, all widget variants, 200% native text and final corrected APK acceptance remain separate, unfinished checks.

---

## Historical 175 checkpoint

# Current evidence — issues 63–65, 4 November 2026

The standard APK now **compiles, packages, verifies and runs on the connected Android 15 phone**. Clean source `175298336bf0052b91aa76221c395e5db9a2955d`; APK SHA-256 `a188616e7fe7b04ecf6a2f5c1b2c144ee9909a0c01de51c0f912bd5c52e68aba`, 298,468,085 bytes. Installed as an update, matching the existing debug signature; on-device APK hash matched. No app data was cleared.

The related desktop suite executed **589 tests: 48 failures, zero errors/skips**. This is a failed gate; neither compilation nor the 541 passing cases constitutes full acceptance. The correction batch addresses invalid JUnit return types, manifest parity, Morning Study card, real mode-profile image fixtures and real-IO workflow timing, plus a native Studio busy-to-idle layout regression. New source still requires a fresh gate. Common Compose internal failures are under investigation: a fresh-worker diagnostic exited 139 and the interpreter follow-up was interrupted by a host restart. No cause is inferred from that restart. The unchanged isolated case subsequently passed in interpreter mode. C1-only then ran all 13 instrument-contract cases: 12 passed; one reported real Russian label clipping, without internal Compose errors. That clipping is corrected in source; the next full gate uses C1-only and still must verify the complete suite. See [diagnostic details](phone-batch-01/ui-host-diagnostic.json).

See [the native report](../../ISSUES-63-65-PHONE-2026-11-04.md) and [current machine-readable results](phone-batch-01/phone-batch-01-failures.json). Paid image generation was explicitly skipped by the user. Actual Russian microphone/STT produced a durable Inbox entry with imperfect wording, then an uncaught background brief-revocation exception crashed the app. Gate02 was stopped before app compilation completed so the crash correction and regressions can join the next batch. Full spoken-flow acceptance, Doze/reboot delivery and final corrected APK acceptance remain pending.

Historical build notes: compiler errors were fixed in `b090b2fac`; the normal APK built in 31m32s. The first later build termination was caused by the author's `cpulimit 2.8 -s` fallthrough into its kill option, not an application failure. That helper was removed. Explicit G1 resolved a separate single-core SerialGC stall. The original 5A supply and 16GiB swap were subsequently used; this supersedes the old power-blocked status below without erasing its evidence.

---

# Studio 65 validation record — 4 November 2026

Source checkpoint: `08e1217d8369e083bc7527a9c64afe2cad5eb795`, branch `codex/issue-65-studio`. See [status.json](status.json) for outcomes and original artifact hashes. All source changes and review corrections are committed; executable acceptance is incomplete.

| Attempt | Actual result |
|---|---|
| 01 | KSP EOF from three zero-byte classpath cache files left by interrupted runs; only generated KSP outputs/cache quarantined |
| 02 / 03 / 05 | KSP passed; author stopped full Kotlin compile after measured sustained full-GC saturation at 2/3/4 GB respectively. No app test execution |
| 04 | Gradle could not probe the x64 Java wrapper outside a JRE directory; moved wrapper into verified JRE/bin layout |
| 06 / 07 | Host restarted before the gate completed; no exit result or complete app compile/test outcome. Kernel undervoltage evidence retained |
| 08 | One core, one worker, one test fork; 6 GB heap. Repeated new undervoltage continued under a temporary 1.5 GHz maximum, including 02:23:58 CST. Author stopped gate (exit 143); maximum restored to 2.4 GHz |

No app test, APK, phone, actual spoken recognition or live provider pass is claimed. KSP success is not an app compilation pass. Source-only language generation/whitespace checks passed for 5,052 EN/RU/LV/PL keys.

Commands/init files describe the configured Pi paths at the time of the attempt. They are evidence, not a portable CI recipe. The official [Temurin archive](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.21%2B9) checksum and Box64 version probes are recorded; unchanged native Robolectric tests have not yet executed through the emulation route. Attempt07 contains interrupted output bytes: `<NUL>` markers in its text artifact preserve those positions; original hashes and whitespace normalization are recorded in status.json.

Stable power under load is required to complete the focused gate, fix any real compiler/test failures, then assemble the APK and perform device acceptance. ADB detected no phone. No issue closure, merge or release acceptance is implied.
