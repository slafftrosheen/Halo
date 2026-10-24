# Linux Ink cross-build (#30)

Base: freshly fetched main `881b089d6c7aa1194f0b3693b2b059e01b24e4f3`,
clean/0:0 before adding the runner. Existing `pen_input.cpp` was unchanged:
SHA256 `ee5503d0ba314b64198f7981e813e01fa928fd94dfeb84964e61b07f0c4518f0`.

## Executed result and limits

**PASS: Linux x86_64 cross-compilation, shared-library link with no undefined
symbols, and all four expected exported JNI entries.** The actual output is
ELF64 x86_64 SharedObject, 18376 bytes, SHA256
`0a8487a22bd6a8e0c5e79cb638730eb7664f99472d39577b402648cd6b66127d`.

This was compiled on Windows, with explicit `--target=x86_64-linux-gnu`, a Debian
bookworm sysroot and Unix JNI platform headers. It is not a Linux runtime result.
No Linux JVM loaded it, no evdev device was opened, no native loop or physical pen
was exercised, no second host connected, and no activation policy was enabled.
ARM64/Pi5 is NOT_TESTED. CMake was not exercised by this direct-compiler check.

The compiler uses C++17, `-Wall -Wextra -Werror -fPIC -shared` and
`-Wl,--no-undefined`, LLVM23.1.4/LLD from the already verified portable toolchain.
ELF inspection confirms prepareNative/readWindows/readLinux/stopNative JNI
exports and runtime dependencies libstdc++.so.6, libm.so.6, libgcc_s.so.1, libc.so.6.
Those target libraries must be present and compatible on an actual Linux host;
successful cross-linking does not prove target loading or input behavior.

## Exact build inputs

The isolated sysroot was extracted locally from seven Debian amd64 archives:
libgcc-12-dev, libgcc-s1, libstdc++-12-dev and libstdc++6 at12.2.0-14+deb12u1;
libc6/libc6-dev at2.36-9+deb12u14; linux-libc-dev at6.1.176-1. Package archive size
and SHA256 matched the Debian HTTPS Packages index. Index size/SHA256 matched
the HTTPS Release metadata. No package install scripts or Linux executables ran.
No WSL, VM, system package or global PATH change was installed.

The [recorded package manifest](artifacts/LINUX-INK-CROSS-BUILD-030-2026-10-24/sysroot-manifest.json)
contains every exact archive path/version/size/hash and unmaterialized optional
links. Package links used by this build were materialized as in-root regular
files; directory and unused runtime/document links were not required for this
compile. Paths and link targets were checked to remain inside the new sysroot.
The package index is from [Debian bookworm](https://deb.debian.org/debian/dists/bookworm/Release).
Unix `jni_md.h` was fetched from the exact
[OpenJDK17.0.20+8 source](https://github.com/openjdk/jdk17u/blob/jdk-17.0.20%2B8/src/java.base/unix/native/include/jni_md.h).
Generic jni.h is from the existing full Temurin17 JDK. [Provenance](artifacts/LINUX-INK-CROSS-BUILD-030-2026-10-24/provenance.json)
and the build result retain their hashes. Publisher signature verification was
not performed; this records checksum comparison against HTTPS metadata.

## Repeat and complete qualification

The runner takes explicit compiler, Linux sysroot, generic JDK and Unix JNI header
directories. Use the recorded package identities when reconstructing that sysroot;
do not substitute Windows JNI headers or a different target and call it the same
result. On this workstation:

```powershell
pwsh -File scripts/verification/build_desktop_ink_linux.ps1 `
  -ToolchainRoot C:\Users\slavd\HaloEco\toolchains\llvm-mingw-20261020-ucrt-x86_64 `
  -Sysroot C:\Users\slavd\HaloEco\toolchains\debian-bookworm-ink\sysroot `
  -JdkRoot C:\Users\slavd\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2 `
  -LinuxJniDirectory C:\Users\slavd\HaloEco\toolchains\debian-bookworm-ink\jni-linux
```

The script requires a fresh empty evidence directory and records failures. It
does not download inputs, install packages, run Linux code or access a pen.
The library remains local; [committed raw evidence](artifacts/LINUX-INK-CROSS-BUILD-030-2026-10-24/)
contains the exact source/header/compiler hashes, arguments, compile output,
ELF dependencies and exports.

An actual Linux host must still build/load through its target JDK/toolchain,
exercise an explicitly selected absolute evdev pen and run the original
[lifecycle/physical/performance protocols](open-issues-2026-10-24/issue-30.md).
This cross-build supersedes SOURCE_ONLY for its exact compile/link scope and
leaves Linux loading/physical compatibility unqualified. All60 sealed Android
inputs still match: no APK rebuild or unchanged model campaign was performed.
The phone remains absent, so the latest compiled Android pair is still awaiting
installation. #30 remains open and production activation remains closed.
