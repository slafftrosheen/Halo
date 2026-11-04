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
