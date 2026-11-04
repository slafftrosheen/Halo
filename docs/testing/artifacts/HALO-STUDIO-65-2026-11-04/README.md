# Current evidence — issues 63–65, 4 November 2026

The standard APK now **compiles, packages, verifies and runs on the connected Android 15 phone**. Clean source `175298336bf0052b91aa76221c395e5db9a2955d`; APK SHA-256 `a188616e7fe7b04ecf6a2f5c1b2c144ee9909a0c01de51c0f912bd5c52e68aba`, 298,468,085 bytes. Installed as an update, matching the existing debug signature; on-device APK hash matched. No app data was cleared.

The related desktop suite executed **589 tests: 48 failures, zero errors/skips**. This is a failed gate; neither compilation nor the 541 passing cases constitutes full acceptance. The correction batch addresses invalid JUnit return types, manifest parity, Morning Study card, real mode-profile image fixtures and real-IO workflow timing, plus a native Studio busy-to-idle layout regression. New source still requires a fresh gate. Common Compose internal failures are under investigation: a fresh-worker diagnostic exited 139 and the interpreter follow-up was interrupted by a host restart. No cause is inferred from that restart.

See [the native report](../../ISSUES-63-65-PHONE-2026-11-04.md) and [current machine-readable results](phone-batch-01/phone-batch-01-failures.json). Paid image generation was explicitly skipped by the user. Actual microphone/STT, Doze/reboot delivery and final corrected APK acceptance remain separate.

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
