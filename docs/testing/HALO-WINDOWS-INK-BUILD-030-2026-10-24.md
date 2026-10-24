# Windows Ink native build and JVM load (#30)

Source base: freshly fetched main `1fcec6bab36a3a210c8fae046f72898141c07918`,
clean/0:0 before the verification-script additions. The existing `pen_input.cpp`
was not changed to obtain this result. Its SHA256 is
`ee5503d0ba314b64198f7981e813e01fa928fd94dfeb84964e61b07f0c4518f0`.

## Executed check

**PASS: Windows x86_64 C++17 compilation/link, all four expected JNI exports, and
actual JVM library load.** No drawing window, capture, pen event, remote session
or production activation was started. This result does not qualify the native
input loop, physical pen, latency, lifecycle, or second-host synchronization.

The compiler ran `-Wall -Wextra -Werror -shared -static`, `_WIN32_WINNT=0x0602`,
Unicode and JNI headers, linking user32. Inspection identifies a COFF x86_64 DLL
and `DesktopPenSource` prepareNative/readWindows/readLinux/stopNative exports.
Java 17 source-file mode then loaded the exact DLL with System.load, with no
toolchain PATH modification, and returned `NATIVE_LIBRARY_LOADED x86_64`.

Host: Windows NT 10.0.26300.0, x86_64. JVM/JNI headers: Temurin17.0.20+8.
Compiler: LLVM23.1.4, LLVM-MinGW20261020, x86_64 UCRT portable distribution.
The official [publisher release](https://github.com/mstorsjo/llvm-mingw/releases/tag/20261020)
asset was downloaded into the workspace's `toolchains` directory and its SHA256
matched the publisher API digest before extraction:
`fd5462b7f5d93b2ff981fcd3f9c7e427e47f3203c64ea91c27bcc444eebd74cd`
(190612575 bytes). No system installation or global PATH change was made.

DLL: 507904 bytes, SHA256
`5bd8f16ffb13a770fa904a7b937d832bce482a27fb7b0757e86eaebf96edffed`.
The DLL and toolchain binaries remain local; [committed evidence](artifacts/WINDOWS-INK-BUILD-030-2026-10-24/)
contains compiler/JVM versions, actual arguments, source/header/compiler/probe/DLL
hashes, PE imports/exports, compile output, publisher metadata and the load result.

## Repeat

Use PowerShell7 with the explicitly selected portable toolchain and a complete
x86_64 JDK containing `include/jni.h` and `include/win32/jni_md.h`. The Android
Studio runtime here lacks JNI headers and was correctly rejected at preflight;
the existing full Gradle-provisioned Java17 JDK was used instead.

```powershell
pwsh -File scripts/verification/build_desktop_ink_windows.ps1 `
  -ToolchainRoot C:\Users\slavd\HaloEco\toolchains\llvm-mingw-20261020-ucrt-x86_64 `
  -JdkRoot C:\Users\slavd\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2
```

Choose actual local paths on another machine. The script requires a fresh empty
output directory, preserves failures, checks compiler/inspection/load exit codes,
and records its scope. It does not download anything or invoke native input.
CMake configuration was not exercised by this direct-compiler check.

## Original acceptance stays open

Windows advances from SOURCE_ONLY to BUILD_AND_JVM_LOAD_VERIFIED, with physical
pen and native-loop status NOT_TESTED. Linux is still SOURCE_ONLY; no local Linux
environment is installed. Huion/OpenTabletDriver/Pi and Android tablet + second
host require exact independent records. Production activation remains closed.
No Android source changed: all60 inputs in the current sealed APK witness still
match. No Android rebuild, unchanged model campaign, phone install or issue
closure follows from this native check.
